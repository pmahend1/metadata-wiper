package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.nal.LengthPrefixedNalScanner
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiBlanker
import com.prateekmahendrakar.metadatawiper.metadata.nal.StreamUserData
import com.prateekmahendrakar.metadatawiper.metadata.nal.VideoCodec
import java.nio.ByteOrder

/*
 * Finds SEI user data inside the samples of H.264 and HEVC video tracks: Sony XAVC and some
 * Panasonic cameras write the recording date and GPS there, which no box holds. Every sample is
 * walked, which reads only the NAL headers. The blanked ranges are inside kept samples, so they
 * are kept apart from the planner's other ranges, which must never touch a sample.
 */
class MovieStreamScanner(private val reader: BinaryReader) {

    // region State

    private val userData = StreamUserData()
    private val blanker = SeiBlanker(userData)

    private val formats = mapOf("avc1" to VideoCodec.H264, "avc2" to VideoCodec.H264, "avc3" to VideoCodec.H264,
                                "avc4" to VideoCodec.H264, "dva1" to VideoCodec.H264, "dvav" to VideoCodec.H264,
                                "hvc1" to VideoCodec.Hevc, "hev1" to VideoCodec.Hevc, "dvh1" to VideoCodec.Hevc,
                                "dvhe" to VideoCodec.Hevc)

    val blanked: List<BlankedRange>
        get() = blanker.blanked

    private companion object {
        const val AVC_CONFIG = "avcC"
        const val HEVC_CONFIG = "hvcC"

        // A visual sample entry's fixed fields (ISO 14496-12) come before its child boxes.
        const val VISUAL_ENTRY_FIELDS_SIZE = 78L
        const val BOX_HEADER_SIZE = 8L

        // lengthSizeMinusOne is the low two bits of this byte of each record.
        const val AVC_LENGTH_SIZE_OFFSET = 4L
        const val HEVC_LENGTH_SIZE_OFFSET = 21L
    }

    // endregion State

    // region Methods

    fun scan(track: MovieTrack,
             chunks: List<FileSpan>) {
        val format = format(track) ?: return
        val scanner = LengthPrefixedNalScanner(reader, format.codec, format.lengthSize, blanker)
        chunks.forEach { scanner.scan(it) }
    }

    fun items(): List<MetadataItem> = userData.items()

    /*
     * Null when the track isn't H.264 or HEVC. Also null for an H.264 or HEVC entry without its
     * decoder configuration: without it no decoder can play the track, and its samples can't be
     * split into NAL units either. A track mixing formats can't be split reliably, so it fails.
     */
    private fun format(track: MovieTrack): VideoStreamFormat? {
        val entries = track.sampleEntries.filter { it.type in formats }
        if (entries.isEmpty()) {
            return null
        }
        if (entries.size != track.sampleEntries.size) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Video track ${track.id} mixes sample formats")
        }
        val found = entries.map { entry -> readFormat(entry, formats.getValue(entry.type)) }
        if (found.any { it == null }) {
            return null
        }
        val distinct = found.toSet()
        if (distinct.size != 1) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Video track ${track.id} changes its NAL length size")
        }
        return distinct.single()
    }

    // Walked by hand rather than with BoxReader: QuickTime can end a sample entry with a 4-byte terminator that isn't a box.
    private fun readFormat(entry: Box,
                           codec: VideoCodec): VideoStreamFormat? {
        val configType = if (codec == VideoCodec.H264) AVC_CONFIG else HEVC_CONFIG
        val lengthOffset = if (codec == VideoCodec.H264) AVC_LENGTH_SIZE_OFFSET else HEVC_LENGTH_SIZE_OFFSET
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        var position = entry.dataStart + VISUAL_ENTRY_FIELDS_SIZE
        while (position + BOX_HEADER_SIZE <= entry.end) {
            reader.seek(position)
            val size = reader.readU32()
            val type = reader.readAscii(4)
            if (size < BOX_HEADER_SIZE || size > entry.end - position) {
                return null
            }
            if (type == configType) {
                if (size <= BOX_HEADER_SIZE + lengthOffset) {
                    throw MalformedFileException("'$configType' box at offset $position is too short")
                }
                reader.seek(position + BOX_HEADER_SIZE + lengthOffset)
                val lengthSize = (reader.readU8() and 0x03) + 1
                if (lengthSize !in LengthPrefixedNalScanner.lengthSizes) {
                    throw MalformedFileException("'$configType' box at offset $position gives NAL lengths of $lengthSize bytes")
                }
                return VideoStreamFormat(codec, lengthSize)
            }
            position += size
        }
        return null
    }

    // endregion Methods
}
