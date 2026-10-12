package com.prateekmahendrakar.metadatawiper.metadata.gif

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.util.Locale

/*
 * Rewrites a GIF keeping the header, frames, frame timing, plain-text overlays (they're drawn
 * as part of the picture), the looping extensions and, by option, the ICC profile. Comments,
 * XMP, other application extensions and anything after the trailer are dropped. Frames are
 * copied byte for byte, so the LZW data is never re-encoded.
 */
class GifHandler : FormatHandler {

    // region State

    override val format = FileFormat.Gif

    private val copiedKinds = setOf(GifBlockKind.Header,
                                    GifBlockKind.Image,
                                    GifBlockKind.GraphicControl,
                                    GifBlockKind.PlainText,
                                    GifBlockKind.Trailer)

    private val loopingApplicationIds = setOf("NETSCAPE2.0", "ANIMEXTS1.0")

    private companion object {
        const val ICC_APPLICATION_ID = "ICCRGBG1012"
        const val XMP_APPLICATION_ID = "XMP DataXMP"
        const val EXTENSION_LABEL_OFFSET = 1L
        const val COMMENT_DATA_OFFSET = 2L
        const val MAX_COMMENT_LENGTH = 200
    }

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val reader = BinaryReader(input)
        val blocks = GifBlockReader(reader).read()
        return InspectionResult(format, findItems(reader, blocks))
    }

    // The whole file is parsed before the first byte is written, so a malformed file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val blocks = GifBlockReader(reader).read()
        val items = findItems(reader, blocks)

        for (block in blocks) {
            if (isKept(block, options)) {
                reader.copyTo(output, block.start, block.end)
            }
        }

        return StripReport.of(items, emptyList(), options, orientationWritten = false)
    }

    private fun isKept(block: GifBlock,
                       options: StripOptions): Boolean {
        if (block.kind in copiedKinds) {
            return true
        }
        if (block.kind != GifBlockKind.Application) {
            return false
        }
        return block.applicationId in loopingApplicationIds ||
            (block.applicationId == ICC_APPLICATION_ID && options.keepColorProfile)
    }

    private fun findItems(reader: BinaryReader,
                          blocks: List<GifBlock>): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()
        for (block in blocks) {
            val size = block.end - block.start
            when (block.kind) {
                in copiedKinds -> continue
                GifBlockKind.Comment -> items += MetadataItem(MetadataCategory.Other, "Comment", readComment(reader, block), size)
                GifBlockKind.OtherExtension -> items += MetadataItem(MetadataCategory.Other,
                                                                     "Extension",
                                                                     extensionLabel(reader, block),
                                                                     size)
                GifBlockKind.Application -> when (block.applicationId) {
                    in loopingApplicationIds -> continue
                    ICC_APPLICATION_ID -> items += MetadataItem(MetadataCategory.ColorProfile, "ICC profile", byteSize = size)
                    XMP_APPLICATION_ID -> items += MetadataItem(MetadataCategory.Other, "XMP", byteSize = size)
                    else -> items += MetadataItem(MetadataCategory.Other,
                                                  "Application extension",
                                                  block.applicationId.trim(),
                                                  size)
                }
                else -> continue
            }
        }

        val trailerSize = reader.size - blocks.last().end
        if (trailerSize > 0) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, "Data after end of image", byteSize = trailerSize)
        }
        return items
    }

    // A comment is split across sub-blocks of up to 255 bytes; only the start is needed for display.
    private fun readComment(reader: BinaryReader,
                            block: GifBlock): String {
        val text = ByteArrayOutputStream()
        reader.seek(block.start + COMMENT_DATA_OFFSET)
        var size = reader.readU8()
        while (size != 0 && text.size() < MAX_COMMENT_LENGTH) {
            text.write(reader.readBytes(size))
            size = reader.readU8()
        }
        return String(text.toByteArray(), Charsets.UTF_8).take(MAX_COMMENT_LENGTH)
                                                         .trim { it.isWhitespace() || it == '\u0000' }
    }

    private fun extensionLabel(reader: BinaryReader,
                               block: GifBlock): String {
        reader.seek(block.start + EXTENSION_LABEL_OFFSET)
        return "0x%02X".format(Locale.ROOT, reader.readU8())
    }

    // endregion Methods
}
