package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.nio.ByteOrder

// Parses an `iloc` box, versions 0–2, with any of the field sizes the spec allows.
class ItemLocationReader(private val reader: BinaryReader) {

    // region State

    private val fieldSizes = setOf(0, 4, 8)

    private companion object {
        // Extents with zero-sized fields take no bytes, so the box size alone can't bound them.
        const val MAX_EXTENTS = 1_000_000
    }

    // endregion State

    // region Methods

    fun read(iloc: Box): List<ItemLocation> {
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        reader.seek(iloc.dataStart)
        val version = reader.readU8()
        reader.skip(3)
        if (version > 2) {
            throw MalformedFileException("Item location version $version isn't supported")
        }

        val sizes = reader.readU8()
        val offsetSize = sizes shr 4
        val lengthSize = sizes and 0x0F
        val moreSizes = reader.readU8()
        val baseOffsetSize = moreSizes shr 4
        val indexSize = if (version == 0) 0 else moreSizes and 0x0F
        if (listOf(offsetSize, lengthSize, baseOffsetSize, indexSize).any { it !in fieldSizes }) {
            throw MalformedFileException("Item location field sizes $sizes/$moreSizes aren't valid")
        }

        val itemCount = if (version < 2) reader.readU16().toLong() else reader.readU32()
        val locations = mutableListOf<ItemLocation>()
        var extentTotal = 0
        for (item in 0 until itemCount) {
            requireInside(iloc)
            val itemId = if (version < 2) reader.readU16().toLong() else reader.readU32()
            val constructionMethod = if (version == 0) ItemLocation.FILE_OFFSET else reader.readU16() and 0x0F
            val dataReferenceIndex = reader.readU16()
            val baseOffset = readSized(baseOffsetSize)
            val extentCount = reader.readU16()
            extentTotal += extentCount
            if (extentTotal > MAX_EXTENTS) {
                throw MalformedFileException("Item location box lists more than $MAX_EXTENTS extents")
            }
            val extents = List(extentCount) {
                readSized(indexSize)
                ItemExtent(readSized(offsetSize), readSized(lengthSize))
            }
            locations += ItemLocation(itemId, constructionMethod, dataReferenceIndex, baseOffset, extents)
        }
        requireInside(iloc)
        return locations
    }

    private fun readSized(size: Int): Long {
        return when (size) {
            4 -> reader.readU32()
            8 -> reader.readU64()
            else -> 0L
        }
    }

    private fun requireInside(iloc: Box) {
        if (reader.position > iloc.end) {
            throw MalformedFileException("Item location box at offset ${iloc.start} runs past its end")
        }
    }

    // endregion Methods
}
