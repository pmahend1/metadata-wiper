package com.prateekmahendrakar.metadatawiper.metadata.gif

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.nio.ByteOrder
import java.util.Locale

// Splits a GIF into blocks from the header to the trailer. Anything after the trailer is left for the caller to report.
class GifBlockReader(private val reader: BinaryReader) {

    // region State

    private val signatures = setOf("GIF87a", "GIF89a")

    private companion object {
        const val SIGNATURE_SIZE = 6
        const val SCREEN_SIZE_FIELDS = 4L
        const val BACKGROUND_AND_ASPECT_FIELDS = 2L
        const val IMAGE_POSITION_AND_SIZE_FIELDS = 8L
        const val LZW_CODE_SIZE_FIELD = 1L
        const val APPLICATION_ID_SIZE = 11

        const val EXTENSION_INTRODUCER = 0x21
        const val IMAGE_SEPARATOR = 0x2C
        const val TRAILER = 0x3B

        const val GRAPHIC_CONTROL_LABEL = 0xF9
        const val PLAIN_TEXT_LABEL = 0x01
        const val COMMENT_LABEL = 0xFE
        const val APPLICATION_LABEL = 0xFF

        const val COLOR_TABLE_FLAG = 0x80
        const val COLOR_TABLE_SIZE_MASK = 0x07
    }

    // endregion State

    // region Methods

    fun read(): List<GifBlock> {
        reader.seek(0)
        reader.byteOrder = ByteOrder.LITTLE_ENDIAN
        if (reader.remaining < SIGNATURE_SIZE || reader.readAscii(SIGNATURE_SIZE) !in signatures) {
            throw MalformedFileException("GIF doesn't start with a GIF signature")
        }
        reader.skip(SCREEN_SIZE_FIELDS)
        val screenFlags = reader.readU8()
        reader.skip(BACKGROUND_AND_ASPECT_FIELDS)
        skipColorTable(screenFlags)

        val blocks = mutableListOf(GifBlock(GifBlockKind.Header, 0, reader.position))
        while (blocks.last().kind != GifBlockKind.Trailer) {
            blocks += readBlock()
        }
        if (blocks.none { it.kind == GifBlockKind.Image }) {
            throw MalformedFileException("GIF has no image data")
        }
        return blocks
    }

    private fun readBlock(): GifBlock {
        val start = reader.position
        if (reader.remaining == 0L) {
            throw MalformedFileException("GIF ends without a trailer")
        }
        return when (val introducer = reader.readU8()) {
            TRAILER -> GifBlock(GifBlockKind.Trailer, start, reader.position)
            IMAGE_SEPARATOR -> readImage(start)
            EXTENSION_INTRODUCER -> readExtension(start)
            else -> throw MalformedFileException("Unexpected GIF block 0x${"%02X".format(Locale.ROOT, introducer)} at offset $start")
        }
    }

    private fun readImage(start: Long): GifBlock {
        reader.skip(IMAGE_POSITION_AND_SIZE_FIELDS)
        skipColorTable(reader.readU8())
        reader.skip(LZW_CODE_SIZE_FIELD)
        skipSubBlocks()
        return GifBlock(GifBlockKind.Image, start, reader.position)
    }

    private fun readExtension(start: Long): GifBlock {
        val kind = when (reader.readU8()) {
            GRAPHIC_CONTROL_LABEL -> GifBlockKind.GraphicControl
            PLAIN_TEXT_LABEL -> GifBlockKind.PlainText
            COMMENT_LABEL -> GifBlockKind.Comment
            APPLICATION_LABEL -> GifBlockKind.Application
            else -> GifBlockKind.OtherExtension
        }
        val applicationId = if (kind == GifBlockKind.Application) readApplicationId() else ""
        skipSubBlocks()
        return GifBlock(kind, start, reader.position, applicationId)
    }

    // The identifier is the first sub-block. One of the wrong size can't be identified, so it's treated as unknown.
    private fun readApplicationId(): String {
        val size = reader.readU8()
        if (size == 0) {
            reader.seek(reader.position - 1)
            return ""
        }
        if (size != APPLICATION_ID_SIZE) {
            reader.skip(size.toLong())
            return ""
        }
        return reader.readAscii(APPLICATION_ID_SIZE)
    }

    private fun skipSubBlocks() {
        var size = reader.readU8()
        while (size != 0) {
            reader.skip(size.toLong())
            size = reader.readU8()
        }
    }

    private fun skipColorTable(flags: Int) {
        if (flags and COLOR_TABLE_FLAG == 0) {
            return
        }
        val entries = 1 shl ((flags and COLOR_TABLE_SIZE_MASK) + 1)
        reader.skip(3L * entries)
    }

    // endregion Methods
}
