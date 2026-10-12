package com.prateekmahendrakar.metadatawiper.metadata.png

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader

/*
 * Splits a PNG into chunks from the signature to IEND. Anything after IEND is left for the
 * caller to report. A critical chunk this reader doesn't know fails the file: the PNG spec
 * says a decoder can't render without understanding it, so passing it through is a guess.
 */
class PngChunkReader(private val reader: BinaryReader) {

    // region State

    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private val knownCriticalTypes = setOf(PngChunkType.IHDR, PngChunkType.PLTE, PngChunkType.IDAT, PngChunkType.IEND)

    private companion object {
        const val TYPE_SIZE = 4
        const val CRC_SIZE = 4L
        const val MAX_CHUNK_LENGTH = 0x7FFFFFFFL
    }

    // endregion State

    // region Methods

    fun read(): List<PngChunk> {
        reader.seek(0)
        if (reader.remaining < signature.size || reader.readBytes(signature.size).contentEquals(signature) == false) {
            throw MalformedFileException("PNG doesn't start with the PNG signature")
        }

        val chunks = mutableListOf<PngChunk>()
        while (chunks.lastOrNull()?.type != PngChunkType.IEND) {
            chunks += readChunk()
        }
        if (chunks.first().type != PngChunkType.IHDR) {
            throw MalformedFileException("PNG doesn't start with an IHDR chunk")
        }
        if (chunks.none { it.type == PngChunkType.IDAT }) {
            throw MalformedFileException("PNG has no image data")
        }
        return chunks
    }

    private fun readChunk(): PngChunk {
        val start = reader.position
        if (reader.remaining == 0L) {
            throw MalformedFileException("PNG ends without an IEND chunk")
        }
        val length = reader.readU32()
        if (length > MAX_CHUNK_LENGTH) {
            throw MalformedFileException("PNG chunk at offset $start has an invalid length $length")
        }

        val typeBytes = reader.readBytes(TYPE_SIZE)
        if (typeBytes.all { it in 'A'.code..'Z'.code || it in 'a'.code..'z'.code } == false) {
            throw MalformedFileException("PNG chunk at offset $start has an invalid type")
        }
        val type = String(typeBytes, Charsets.US_ASCII)
        if (isCritical(type) && type !in knownCriticalTypes) {
            throw MalformedFileException("Unsupported critical PNG chunk $type at offset $start")
        }

        val dataStart = reader.position
        reader.skip(length + CRC_SIZE)
        return PngChunk(type, start, dataStart, length, reader.position)
    }

    // The case of the first letter is the critical bit.
    private fun isCritical(type: String): Boolean = type[0].isUpperCase()

    // endregion Methods
}
