package com.prateekmahendrakar.metadatawiper.metadata.jpeg

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.tiff.MinimalExifWriter
import com.prateekmahendrakar.metadatawiper.metadata.tiff.TiffSummary
import com.prateekmahendrakar.metadatawiper.metadata.tiff.TiffSummaryReader
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/*
 * Rewrites a JPEG keeping only what decoding needs (frame, tables, scans, JFIF without its
 * thumbnail, Adobe APP14) plus, by option, the ICC profile and a minimal EXIF carrying
 * orientation. Everything else, including data after EOI, is dropped. Scan data is copied
 * byte for byte, so the image itself is never re-encoded.
 */
class JpegHandler : FormatHandler {

    // region State

    override val format = FileFormat.Jpeg

    private val segmentLabels = mapOf(JpegSegmentKind.Xmp to "XMP",
                                      JpegSegmentKind.ExtendedXmp to "Extended XMP",
                                      JpegSegmentKind.MultiPicture to "Multi-picture index",
                                      JpegSegmentKind.FlashPix to "FlashPix data",
                                      JpegSegmentKind.Photoshop to "IPTC / Photoshop",
                                      JpegSegmentKind.OtherApplication to "Application segment")

    private val copiedKinds = setOf(JpegSegmentKind.StartOfImage,
                                    JpegSegmentKind.Frame,
                                    JpegSegmentKind.Table,
                                    JpegSegmentKind.Scan,
                                    JpegSegmentKind.EndOfImage,
                                    JpegSegmentKind.Adobe)

    private companion object {
        const val COPY_CHUNK_SIZE = 64 * 1024
        const val EXIF_IDENTIFIER_SIZE = 6L
        const val ICC_CHUNK_HEADER_SIZE = 14L
        const val JFIF_FIXED_PAYLOAD = 14
        const val JFIF_THUMBNAIL_WIDTH = 12
        const val JFIF_THUMBNAIL_HEIGHT = 13
        const val MAX_COMMENT_LENGTH = 200
        const val MAX_PAYLOAD_LENGTH = 0xFFFF - 2
    }

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val reader = BinaryReader(input)
        val segments = JpegSegmentReader(reader).read()
        val warnings = mutableListOf<String>()
        return InspectionResult(format, findItems(reader, segments, warnings), warnings)
    }

    // The whole file is parsed before the first byte is written, so a malformed file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val segments = JpegSegmentReader(reader).read()
        val warnings = mutableListOf<String>()
        val items = findItems(reader, segments, warnings)
        val orientation = keptOrientation(reader, segments, options)

        val firstExif = segments.firstOrNull { it.kind == JpegSegmentKind.Exif }
        for (segment in segments) {
            when {
                segment.kind in copiedKinds -> copy(reader, segment.start, segment.end, output)
                segment.kind == JpegSegmentKind.IccProfile && options.keepColorProfile -> copy(reader,
                                                                                               segment.start,
                                                                                               segment.end,
                                                                                               output)
                segment.kind == JpegSegmentKind.Jfif -> writeSegment(output,
                                                                     segment.marker,
                                                                     jfifWithoutThumbnail(reader, segment))
                segment == firstExif && orientation != null -> writeSegment(output,
                                                                            JpegMarker.APP1,
                                                                            MinimalExifWriter.jpegApp1Payload(orientation))
            }
        }

        val (kept, removed) = items.partition { isKept(it, options, orientation) }
        return StripReport(removed, kept, warnings)
    }

    private fun isKept(item: MetadataItem,
                       options: StripOptions,
                       keptOrientation: Int?): Boolean {
        return when (item.category) {
            MetadataCategory.ColorProfile -> options.keepColorProfile
            MetadataCategory.Orientation -> keptOrientation != null
            else -> false
        }
    }

    // Orientation 1 is the default, so a minimal EXIF is only worth writing for the other seven.
    private fun keptOrientation(reader: BinaryReader,
                                segments: List<JpegSegment>,
                                options: StripOptions): Int? {
        if (options.keepOrientation == false) {
            return null
        }
        val orientation = segments.filter { it.kind == JpegSegmentKind.Exif }
            .firstNotNullOfOrNull { readExif(reader, it)?.orientation }
        return orientation?.takeIf { it in 2..8 }
    }

    private fun findItems(reader: BinaryReader,
                          segments: List<JpegSegment>,
                          warnings: MutableList<String>): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()
        val iccSegments = segments.filter { it.kind == JpegSegmentKind.IccProfile }
        for (segment in segments) {
            when (segment.kind) {
                JpegSegmentKind.Exif -> items += exifItems(reader, segment, warnings)
                JpegSegmentKind.Jfif -> items += jfifThumbnailItems(reader, segment)
                JpegSegmentKind.JfifExtension -> items += MetadataItem(MetadataCategory.Thumbnail,
                                                                       "JFIF extension thumbnail",
                                                                       byteSize = segment.payloadLength)
                JpegSegmentKind.Comment -> items += MetadataItem(MetadataCategory.Other,
                                                                 "Comment",
                                                                 readComment(reader, segment),
                                                                 segment.payloadLength)
                // A profile is split across several APP2 segments but is one thing to the user.
                JpegSegmentKind.IccProfile -> if (segment == iccSegments.first()) {
                    items += MetadataItem(MetadataCategory.ColorProfile,
                                          "ICC profile",
                                          byteSize = iccSegments.sumOf { it.payloadLength - ICC_CHUNK_HEADER_SIZE })
                }
                else -> {
                    val label = segmentLabels[segment.kind] ?: continue
                    val category =
                        if (segment.kind == JpegSegmentKind.MultiPicture) MetadataCategory.EmbeddedMedia else MetadataCategory.Other
                    val detail = if (segment.kind == JpegSegmentKind.OtherApplication) applicationName(segment) else ""
                    items += MetadataItem(category, label, detail, segment.payloadLength)
                }
            }
        }

        val trailerSize = reader.size - segments.last().end
        if (trailerSize > 0) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, "Data after end of image", byteSize = trailerSize)
        }
        return items
    }

    /*
     * The summary lists the tags worth naming, but an EXIF block can hold plenty it doesn't
     * name, so the block itself is reported too. The one exception is the minimal EXIF this
     * handler writes, which must re-inspect as orientation only for verification to pass.
     */
    private fun exifItems(reader: BinaryReader,
                          segment: JpegSegment,
                          warnings: MutableList<String>): List<MetadataItem> {
        val blockItem = MetadataItem(MetadataCategory.Other, "EXIF block", byteSize = segment.payloadLength)
        val summary = readExif(reader, segment)
        if (summary == null) {
            warnings += "An EXIF block is damaged, so its contents can't be listed. It is removed all the same."
            return listOf(blockItem)
        }
        if (isMinimalExif(reader, segment, summary.orientation)) {
            return summary.items
        }
        return summary.items + blockItem
    }

    // A damaged EXIF block is removed anyway, so it shouldn't stop the rest of the file being cleaned.
    private fun readExif(reader: BinaryReader,
                         segment: JpegSegment): TiffSummary? {
        return try {
            TiffSummaryReader(reader,
                              segment.payloadStart + EXIF_IDENTIFIER_SIZE,
                              segment.payloadLength - EXIF_IDENTIFIER_SIZE).read()
        } catch (_: MalformedFileException) {
            null
        }
    }

    private fun isMinimalExif(reader: BinaryReader,
                              segment: JpegSegment,
                              orientation: Int?): Boolean {
        if (orientation == null || orientation !in 1..8) {
            return false
        }
        val minimal = MinimalExifWriter.jpegApp1Payload(orientation)
        if (segment.payloadLength != minimal.size.toLong()) {
            return false
        }
        reader.seek(segment.payloadStart)
        return reader.readBytes(minimal.size).contentEquals(minimal)
    }

    private fun jfifThumbnailItems(reader: BinaryReader,
                                   segment: JpegSegment): List<MetadataItem> {
        val thumbnailSize = segment.payloadLength - JFIF_FIXED_PAYLOAD
        if (thumbnailSize == 0L) {
            return emptyList()
        }
        val header = readPayload(reader, segment, JFIF_FIXED_PAYLOAD)
        val width = header[JFIF_THUMBNAIL_WIDTH].toInt() and 0xFF
        val height = header[JFIF_THUMBNAIL_HEIGHT].toInt() and 0xFF
        return listOf(MetadataItem(MetadataCategory.Thumbnail, "JFIF thumbnail", "${width}×$height", thumbnailSize))
    }

    // Density and version stay; the thumbnail dimensions are zeroed and its pixels dropped.
    private fun jfifWithoutThumbnail(reader: BinaryReader,
                                     segment: JpegSegment): ByteArray {
        val payload = readPayload(reader, segment, JFIF_FIXED_PAYLOAD)
        payload[JFIF_THUMBNAIL_WIDTH] = 0
        payload[JFIF_THUMBNAIL_HEIGHT] = 0
        return payload
    }

    private fun readComment(reader: BinaryReader,
                            segment: JpegSegment): String {
        val bytes = readPayload(reader, segment, minOf(segment.payloadLength, MAX_COMMENT_LENGTH.toLong()).toInt())
        return String(bytes, Charsets.UTF_8).trim { it.isWhitespace() || it == '\u0000' }
    }

    private fun applicationName(segment: JpegSegment): String = "APP${segment.marker - JpegMarker.APP0}"

    private fun readPayload(reader: BinaryReader,
                            segment: JpegSegment,
                            count: Int): ByteArray {
        reader.seek(segment.payloadStart)
        return reader.readBytes(count)
    }

    private fun copy(reader: BinaryReader,
                     from: Long,
                     to: Long,
                     output: OutputStream) {
        reader.seek(from)
        var remaining = to - from
        while (remaining > 0) {
            val chunk = reader.readBytes(minOf(remaining, COPY_CHUNK_SIZE.toLong()).toInt())
            output.write(chunk)
            remaining -= chunk.size
        }
    }

    private fun writeSegment(output: OutputStream,
                             marker: Int,
                             payload: ByteArray) {
        check(payload.size <= MAX_PAYLOAD_LENGTH) { "JPEG segment payload is too large (${payload.size} bytes)" }
        val length = payload.size + 2
        output.write(JpegMarker.PREFIX)
        output.write(marker)
        output.write(length shr 8)
        output.write(length and 0xFF)
        output.write(payload)
    }

    // endregion Methods
}
