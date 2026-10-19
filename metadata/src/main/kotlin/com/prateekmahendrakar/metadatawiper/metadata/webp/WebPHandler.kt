package com.prateekmahendrakar.metadatawiper.metadata.webp

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifBlockInspector
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifInspection
import com.prateekmahendrakar.metadatawiper.metadata.tiff.MinimalExifWriter
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/*
 * Rewrites a WebP keeping the image and animation chunks and, by option, the ICC profile and a
 * minimal EXIF carrying orientation. EXIF, XMP and unknown chunks are dropped, the VP8X flags
 * are updated to match, and the RIFF size is recomputed. Image data is copied byte for byte.
 */
class WebPHandler : FormatHandler {

    // region State

    override val format = FileFormat.WebP

    private val copiedFourCcs = setOf(VP8X, ANIM, ANMF, ALPH, VP8, VP8L)

    private val firstChunkFourCcs = setOf(VP8X, VP8, VP8L)

    private companion object {
        const val RIFF = "RIFF"
        const val WEBP = "WEBP"
        const val VP8X = "VP8X"
        const val VP8 = "VP8 "
        const val VP8L = "VP8L"
        const val ANIM = "ANIM"
        const val ANMF = "ANMF"
        const val ALPH = "ALPH"
        const val ICCP = "ICCP"
        const val EXIF = "EXIF"
        const val XMP = "XMP "

        const val ICC_FLAG = 0x20
        const val EXIF_FLAG = 0x08
        const val XMP_FLAG = 0x04

        const val VP8X_DATA_LENGTH = 10L
        const val CHUNK_HEADER_SIZE = 8L
        const val FORM_TYPE_SIZE = 4L
        const val MAX_RIFF_SIZE = 0xFFFFFFFFL
    }

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val reader = BinaryReader(input)
        val chunks = readChunks(reader)
        val warnings = mutableListOf<MetadataWarning>()
        return InspectionResult(format, findItems(reader, chunks, warnings), warnings)
    }

    /*
     * The whole file is parsed before the first byte is written, so a malformed file produces
     * no output. A simple (VP8 or VP8L only) file has no VP8X to flag an EXIF chunk with, so
     * orientation can only be kept for an extended file.
     */
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val chunks = readChunks(reader)
        val warnings = mutableListOf<MetadataWarning>()
        val items = findItems(reader, chunks, warnings)
        val isExtended = chunks.first().fourCc == VP8X
        val orientation = if (isExtended) keptOrientation(reader, chunks, options) else null
        val exif = orientation?.let { MinimalExifWriter.tiff(it) }

        val keptChunks = chunks.filter { it.fourCc in copiedFourCcs || (it.fourCc == ICCP && options.keepColorProfile) }
        val exifChunkSize = if (exif == null) 0L else CHUNK_HEADER_SIZE + exif.size
        val riffSize = FORM_TYPE_SIZE + keptChunks.sumOf { it.end - it.start } + exifChunkSize
        check(riffSize <= MAX_RIFF_SIZE) { "Stripped WebP is too large for RIFF ($riffSize bytes)" }

        output.write(RIFF.toByteArray(Charsets.US_ASCII))
        output.write(littleEndian(riffSize.toInt()))
        output.write(WEBP.toByteArray(Charsets.US_ASCII))
        for (chunk in keptChunks) {
            if (chunk.fourCc == VP8X) {
                writeExtendedHeader(reader, chunk, output, options, keepsExif = exif != null)
            } else {
                reader.copyTo(output, chunk.start, chunk.end)
            }
        }
        // The container spec orders EXIF after the image data.
        if (exif != null) {
            output.write(EXIF.toByteArray(Charsets.US_ASCII))
            output.write(littleEndian(exif.size))
            output.write(exif)
        }

        return StripReport.of(items, warnings, options, orientationWritten = exif != null)
    }

    private fun readChunks(reader: BinaryReader): List<RiffChunk> {
        val chunks = RiffChunkReader(reader, WEBP).read()
        val first = chunks.first()
        if (first.fourCc !in firstChunkFourCcs) {
            throw MalformedFileException("WebP starts with a '${first.fourCc}' chunk instead of image data")
        }
        if (first.fourCc == VP8X && first.dataLength < VP8X_DATA_LENGTH) {
            throw MalformedFileException("WebP VP8X chunk is too short (${first.dataLength} bytes)")
        }
        return chunks
    }

    private fun keptOrientation(reader: BinaryReader,
                                chunks: List<RiffChunk>,
                                options: StripOptions): Int? {
        val orientation = chunks.filter { it.fourCc == EXIF }
                                .firstNotNullOfOrNull { inspectExif(reader, it).orientation }
        return options.orientationToWrite(orientation)
    }

    private fun findItems(reader: BinaryReader,
                          chunks: List<RiffChunk>,
                          warnings: MutableList<MetadataWarning>): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()
        for (chunk in chunks) {
            when (chunk.fourCc) {
                in copiedFourCcs -> continue
                ICCP -> items += MetadataItem(MetadataCategory.ColorProfile, MetadataLabel.ICC_PROFILE, byteSize = chunk.dataLength)
                XMP -> items += MetadataItem(MetadataCategory.Other, MetadataLabel.XMP, byteSize = chunk.dataLength)
                EXIF -> {
                    val exif = inspectExif(reader, chunk)
                    if (exif.isDamaged) {
                        warnings += ExifBlockInspector.DAMAGED_WARNING
                    }
                    items += exif.items
                }
                else -> items += MetadataItem(MetadataCategory.Other,
                                              MetadataLabel.UNKNOWN_CHUNK,
                                              chunk.fourCc.trim(),
                                              chunk.dataLength)
            }
        }

        val trailerSize = reader.size - chunks.last().end
        if (trailerSize > 0) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, MetadataLabel.TRAILING_DATA, byteSize = trailerSize)
        }
        return items
    }

    private fun inspectExif(reader: BinaryReader,
                            chunk: RiffChunk): ExifInspection {
        return ExifBlockInspector.inspect(reader, chunk.dataStart, chunk.dataLength)
    }

    // XMP is always dropped; EXIF and ICC stay flagged only if their chunk is written.
    private fun writeExtendedHeader(reader: BinaryReader,
                                    chunk: RiffChunk,
                                    output: OutputStream,
                                    options: StripOptions,
                                    keepsExif: Boolean) {
        reader.seek(chunk.dataStart)
        var flags = reader.readU8() and XMP_FLAG.inv()
        if (keepsExif == false) {
            flags = flags and EXIF_FLAG.inv()
        }
        if (options.keepColorProfile == false) {
            flags = flags and ICC_FLAG.inv()
        }
        reader.copyTo(output, chunk.start, chunk.dataStart)
        output.write(flags)
        reader.copyTo(output, chunk.dataStart + 1, chunk.end)
    }

    private fun littleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    // endregion Methods
}
