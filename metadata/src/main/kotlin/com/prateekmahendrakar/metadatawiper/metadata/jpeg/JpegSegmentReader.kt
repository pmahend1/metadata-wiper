package com.prateekmahendrakar.metadatawiper.metadata.jpeg

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.util.Locale

/*
 * Splits a JPEG into segments from SOI to the first EOI. Anything after EOI is left for the
 * caller to report. A marker this reader doesn't know fails the file instead of being passed
 * through, because an unknown segment could hold anything.
 */
class JpegSegmentReader(private val reader: BinaryReader) {

    // region State

    private val applicationIdentifiers = listOf(JpegApplicationIdentifier(JpegMarker.APP0, "JFIF\u0000", JpegSegmentKind.Jfif),
                                                JpegApplicationIdentifier(JpegMarker.APP0, "JFXX\u0000", JpegSegmentKind.JfifExtension),
                                                JpegApplicationIdentifier(JpegMarker.APP1, "Exif\u0000", JpegSegmentKind.Exif),
                                                JpegApplicationIdentifier(JpegMarker.APP1, "http://ns.adobe.com/xap/1.0/\u0000", JpegSegmentKind.Xmp),
                                                JpegApplicationIdentifier(JpegMarker.APP1, "http://ns.adobe.com/xmp/extension/\u0000", JpegSegmentKind.ExtendedXmp),
                                                JpegApplicationIdentifier(JpegMarker.APP2, "ICC_PROFILE\u0000", JpegSegmentKind.IccProfile),
                                                JpegApplicationIdentifier(JpegMarker.APP2, "MPF\u0000", JpegSegmentKind.MultiPicture),
                                                JpegApplicationIdentifier(JpegMarker.APP2, "FPXR\u0000", JpegSegmentKind.FlashPix),
                                                JpegApplicationIdentifier(JpegMarker.APP13, "Photoshop 3.0\u0000", JpegSegmentKind.Photoshop),
                                                JpegApplicationIdentifier(JpegMarker.APP14, "Adobe", JpegSegmentKind.Adobe))

    private val longestIdentifier = applicationIdentifiers.maxOf { it.prefix.size }

    private companion object {
        const val SCAN_CHUNK_SIZE = 64 * 1024
        const val LENGTH_FIELD_SIZE = 2L

        // "Exif\0" plus the pad byte, then at least a TIFF header.
        const val MIN_EXIF_PAYLOAD = 6L
        const val JFIF_FIXED_PAYLOAD = 14L
    }

    // endregion State

    // region Methods

    fun read(): List<JpegSegment> {
        reader.seek(0)
        if (reader.remaining < 2 || reader.readU8() != JpegMarker.PREFIX || reader.readU8() != JpegMarker.SOI) {
            throw MalformedFileException("JPEG doesn't start with an SOI marker")
        }
        val segments = mutableListOf(JpegSegment(JpegMarker.SOI, JpegSegmentKind.StartOfImage, 0, 2, 0, 2))
        while (segments.last().kind != JpegSegmentKind.EndOfImage) {
            segments += readSegment()
        }
        if (segments.none { it.kind == JpegSegmentKind.Frame } || segments.none { it.kind == JpegSegmentKind.Scan }) {
            throw MalformedFileException("JPEG has no frame header or no image data")
        }
        return segments
    }

    private fun readSegment(): JpegSegment {
        val start = reader.position
        if (reader.remaining == 0L) {
            throw MalformedFileException("JPEG ends without an EOI marker")
        }
        if (reader.readU8() != JpegMarker.PREFIX) {
            throw MalformedFileException("Expected a JPEG marker at offset $start")
        }
        // Any number of 0xFF fill bytes may precede a marker code.
        var marker = reader.readU8()
        while (marker == JpegMarker.PREFIX) {
            marker = reader.readU8()
        }
        if (marker == JpegMarker.EOI) {
            return JpegSegment(marker, JpegSegmentKind.EndOfImage, start, reader.position, 0, reader.position)
        }

        val kind = kindOf(marker) ?: throw MalformedFileException("Unsupported JPEG marker 0x${"%02X".format(Locale.ROOT, marker)} at offset $start")
        val length = reader.readU16()
        if (length < LENGTH_FIELD_SIZE) {
            throw MalformedFileException("JPEG segment at offset $start has an invalid length $length")
        }
        val payloadStart = reader.position
        val payloadLength = length - LENGTH_FIELD_SIZE
        reader.requireRemaining(payloadLength)

        val identifiedKind = if (kind == JpegSegmentKind.OtherApplication) identifyApplication(marker, payloadLength) else kind
        val payloadEnd = payloadStart + payloadLength
        val end = if (kind == JpegSegmentKind.Scan) findScanEnd(payloadEnd) else payloadEnd
        reader.seek(end)
        return JpegSegment(marker, identifiedKind, start, payloadStart, payloadLength, end)
    }

    private fun kindOf(marker: Int): JpegSegmentKind? {
        return when {
            JpegMarker.isStartOfFrame(marker) -> JpegSegmentKind.Frame
            JpegMarker.isTable(marker) -> JpegSegmentKind.Table
            marker == JpegMarker.SOS -> JpegSegmentKind.Scan
            marker == JpegMarker.COM -> JpegSegmentKind.Comment
            JpegMarker.isApplication(marker) -> JpegSegmentKind.OtherApplication
            else -> null
        }
    }

    // Reads the payload's leading bytes; the reader is positioned at the payload start.
    private fun identifyApplication(marker: Int,
                                    payloadLength: Long): JpegSegmentKind {
        val prefix = reader.readBytes(minOf(payloadLength, longestIdentifier.toLong()).toInt())
        val identifier = applicationIdentifiers.firstOrNull { it.marker == marker && it.matches(prefix) }
        val kind = identifier?.kind ?: return JpegSegmentKind.OtherApplication

        // Too short to hold the fields the handler reads, so it's treated as an unknown segment and removed.
        val tooShort = (kind == JpegSegmentKind.Exif && payloadLength < MIN_EXIF_PAYLOAD) ||
                       (kind == JpegSegmentKind.Jfif && payloadLength < JFIF_FIXED_PAYLOAD)
        return if (tooShort) JpegSegmentKind.OtherApplication else kind
    }

    /*
     * Entropy-coded data ends at the first marker that isn't a stuffed zero (FF 00), a restart
     * marker (FF D0–D7) or a fill byte. Reads in chunks, since a byte-at-a-time channel read
     * would be far too slow for multi-megabyte photos. Returns the offset of that marker's 0xFF.
     */
    private fun findScanEnd(dataStart: Long): Long {
        var chunkStart = dataStart
        while (true) {
            reader.seek(chunkStart)
            if (reader.remaining < 2) {
                throw MalformedFileException("JPEG scan data runs to the end of the file without an EOI marker")
            }
            val chunk = reader.readBytes(minOf(reader.remaining, SCAN_CHUNK_SIZE.toLong()).toInt())
            var index = 0
            while (index < chunk.size - 1) {
                if (chunk[index].toInt() and 0xFF != JpegMarker.PREFIX) {
                    index++
                    continue
                }
                val next = chunk[index + 1].toInt() and 0xFF
                when {
                    next == JpegMarker.STUFFED_ZERO || JpegMarker.isRestart(next) -> index += 2
                    next == JpegMarker.PREFIX -> index++
                    else -> return chunkStart + index
                }
            }
            // A 0xFF in the chunk's last byte is re-read as the start of the next chunk.
            chunkStart += index
        }
    }

    // endregion Methods
}
