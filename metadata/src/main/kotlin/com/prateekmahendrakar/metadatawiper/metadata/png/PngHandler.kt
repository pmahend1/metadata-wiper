package com.prateekmahendrakar.metadatawiper.metadata.png

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifBlockInspector
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifInspection
import com.prateekmahendrakar.metadatawiper.metadata.tiff.MinimalExifWriter
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.CRC32

/*
 * Rewrites a PNG keeping the critical chunks, the rendering hints, APNG animation control and,
 * by option, the ICC profile and a minimal eXIf carrying orientation. Every other ancillary
 * chunk is dropped. Kept chunks carry their own CRC, so they're copied verbatim.
 */
class PngHandler : FormatHandler {

    // region State

    override val format = FileFormat.Png

    private val copiedTypes = setOf(PngChunkType.IHDR,
                                    PngChunkType.PLTE,
                                    PngChunkType.IDAT,
                                    PngChunkType.IEND) + PngChunkType.rendering + PngChunkType.animation

    private val textTypes = setOf(PngChunkType.TEXT, PngChunkType.COMPRESSED_TEXT, PngChunkType.INTERNATIONAL_TEXT)

    // The predefined keywords from the PNG spec that say something about who, where or with what.
    private val keywordCategories = mapOf("Author" to MetadataCategory.People,
                                          "Copyright" to MetadataCategory.People,
                                          "Creation Time" to MetadataCategory.DateTime,
                                          "Software" to MetadataCategory.Software,
                                          "Source" to MetadataCategory.Device)

    private companion object {
        const val XMP_KEYWORD = "XML:com.adobe.xmp"
        const val TIME_LENGTH = 7L
        const val MAX_TEXT_READ = 512
        const val MAX_DETAIL_LENGTH = 200
        const val UNCOMPRESSED = 0
    }

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val reader = BinaryReader(input)
        val chunks = PngChunkReader(reader).read()
        val warnings = mutableListOf<String>()
        return InspectionResult(format, findItems(reader, chunks, warnings), warnings)
    }

    /*
     * The whole file is parsed before the first byte is written, so a malformed file produces
     * no output. A minimal eXIf goes straight after IHDR: it must precede the first IDAT, and
     * a fixed spot keeps a second strip byte-identical to the first.
     */
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val chunks = PngChunkReader(reader).read()
        val warnings = mutableListOf<String>()
        val items = findItems(reader, chunks, warnings)
        val orientation = keptOrientation(reader, chunks, options)

        reader.copyTo(output, 0, chunks.first().start)
        for (chunk in chunks) {
            if (chunk.type in copiedTypes || (chunk.type == PngChunkType.ICCP && options.keepColorProfile)) {
                reader.copyTo(output, chunk.start, chunk.end)
            }
            if (chunk.type == PngChunkType.IHDR && orientation != null) {
                writeChunk(output, PngChunkType.EXIF, MinimalExifWriter.tiff(orientation))
            }
        }

        return StripReport.of(items, warnings, options, orientationWritten = orientation != null)
    }

    private fun keptOrientation(reader: BinaryReader,
                                chunks: List<PngChunk>,
                                options: StripOptions): Int? {
        val orientation = chunks.filter { it.type == PngChunkType.EXIF }
                                .firstNotNullOfOrNull { inspectExif(reader, it).orientation }
        return options.orientationToWrite(orientation)
    }

    private fun findItems(reader: BinaryReader,
                          chunks: List<PngChunk>,
                          warnings: MutableList<String>): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()
        for (chunk in chunks) {
            when (chunk.type) {
                in copiedTypes -> continue
                in textTypes -> items += textItem(reader, chunk)
                PngChunkType.ICCP -> items += MetadataItem(MetadataCategory.ColorProfile,
                                                           "ICC profile",
                                                           readKeyword(reader, chunk),
                                                           chunk.dataLength)
                PngChunkType.TIME -> items += MetadataItem(MetadataCategory.DateTime,
                                                           "Modification time",
                                                           readTime(reader, chunk),
                                                           chunk.dataLength)
                PngChunkType.EXIF -> {
                    val exif = inspectExif(reader, chunk)
                    if (exif.isDamaged) {
                        warnings += ExifBlockInspector.DAMAGED_WARNING
                    }
                    items += exif.items
                }
                else -> items += MetadataItem(MetadataCategory.Other, "Ancillary chunk", chunk.type, chunk.dataLength)
            }
        }

        val trailerSize = reader.size - chunks.last().end
        if (trailerSize > 0) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, "Data after end of image", byteSize = trailerSize)
        }
        return items
    }

    private fun inspectExif(reader: BinaryReader,
                            chunk: PngChunk): ExifInspection {
        return ExifBlockInspector.inspect(reader, chunk.dataStart, chunk.dataLength)
    }

    // The text itself is shown only when it's stored uncompressed; zTXt and compressed iTXt show just the keyword.
    private fun textItem(reader: BinaryReader,
                         chunk: PngChunk): MetadataItem {
        val bytes = readData(reader, chunk, minOf(chunk.dataLength, MAX_TEXT_READ.toLong()).toInt())
        val keywordEnd = bytes.indexOfNull(0) ?: bytes.size
        val keyword = String(bytes, 0, keywordEnd, Charsets.ISO_8859_1)
        if (chunk.type == PngChunkType.INTERNATIONAL_TEXT && keyword == XMP_KEYWORD) {
            return MetadataItem(MetadataCategory.Other, "XMP", byteSize = chunk.dataLength)
        }

        val text = when (chunk.type) {
            PngChunkType.TEXT -> textAfter(bytes, keywordEnd, Charsets.ISO_8859_1)
            PngChunkType.INTERNATIONAL_TEXT -> internationalText(bytes, keywordEnd)
            else -> ""
        }
        val category = keywordCategories[keyword] ?: MetadataCategory.Other
        return MetadataItem(category,
                            keyword.ifEmpty { "Text" },
                            text.trim().take(MAX_DETAIL_LENGTH),
                            chunk.dataLength)
    }

    // iTXt: keyword, null, compression flag, method, language tag, null, translated keyword, null, UTF-8 text.
    private fun internationalText(bytes: ByteArray,
                                  keywordEnd: Int): String {
        val flagIndex = keywordEnd + 1
        if (flagIndex >= bytes.size || bytes[flagIndex].toInt() != UNCOMPRESSED) {
            return ""
        }
        val languageEnd = bytes.indexOfNull(flagIndex + 2) ?: return ""
        val translatedEnd = bytes.indexOfNull(languageEnd + 1) ?: return ""
        return textAfter(bytes, translatedEnd, Charsets.UTF_8)
    }

    private fun textAfter(bytes: ByteArray,
                          separator: Int,
                          charset: Charset): String {
        if (separator + 1 >= bytes.size) {
            return ""
        }
        return String(bytes, separator + 1, bytes.size - separator - 1, charset)
    }

    private fun readKeyword(reader: BinaryReader,
                            chunk: PngChunk): String {
        val bytes = readData(reader, chunk, minOf(chunk.dataLength, MAX_TEXT_READ.toLong()).toInt())
        val keywordEnd = bytes.indexOfNull(0) ?: bytes.size
        return String(bytes, 0, keywordEnd, Charsets.ISO_8859_1)
    }

    private fun readTime(reader: BinaryReader,
                         chunk: PngChunk): String {
        if (chunk.dataLength != TIME_LENGTH) {
            return ""
        }
        val buffer = ByteBuffer.wrap(readData(reader, chunk, TIME_LENGTH.toInt()))
        val year = buffer.short.toInt() and 0xFFFF
        val fields = List(5) { buffer.get().toInt() and 0xFF }
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02d", year, *fields.toTypedArray())
    }

    private fun readData(reader: BinaryReader,
                         chunk: PngChunk,
                         count: Int): ByteArray {
        reader.seek(chunk.dataStart)
        return reader.readBytes(count)
    }

    private fun ByteArray.indexOfNull(from: Int): Int? {
        for (index in from until size) {
            if (this[index].toInt() == 0) {
                return index
            }
        }
        return null
    }

    private fun writeChunk(output: OutputStream,
                           type: String,
                           data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        output.write(ByteBuffer.allocate(4).putInt(data.size).array())
        output.write(typeBytes)
        output.write(data)
        output.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    // endregion Methods
}
