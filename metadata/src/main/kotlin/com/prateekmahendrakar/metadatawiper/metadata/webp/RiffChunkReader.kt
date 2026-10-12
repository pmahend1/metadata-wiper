package com.prateekmahendrakar.metadatawiper.metadata.webp

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.nio.ByteOrder

/*
 * Splits a RIFF file of the given form type ("WEBP", later "AVI " and "WAVE") into its
 * top-level chunks. The chunks must exactly fill the size the RIFF header declares; anything
 * after that is left for the caller to report.
 */
class RiffChunkReader(private val reader: BinaryReader,
                      private val formType: String) {

    // region State

    private companion object {
        const val RIFF = "RIFF"
        const val FOURCC_SIZE = 4
        const val CHUNK_HEADER_SIZE = 8L
        const val FORM_TYPE_OFFSET = 8L
    }

    // endregion State

    // region Methods

    fun read(): List<RiffChunk> {
        reader.seek(0)
        reader.byteOrder = ByteOrder.LITTLE_ENDIAN
        if (reader.remaining < FORM_TYPE_OFFSET + FOURCC_SIZE || reader.readAscii(FOURCC_SIZE) != RIFF) {
            throw MalformedFileException("File doesn't start with a RIFF header")
        }
        val riffEnd = FORM_TYPE_OFFSET + reader.readU32()
        if (reader.readAscii(FOURCC_SIZE) != formType) {
            throw MalformedFileException("RIFF file isn't of type $formType")
        }
        if (riffEnd > reader.size) {
            throw MalformedFileException("RIFF header declares ${riffEnd - FORM_TYPE_OFFSET} bytes, but the file is shorter")
        }

        val chunks = mutableListOf<RiffChunk>()
        while (reader.position < riffEnd) {
            chunks += readChunk(riffEnd)
        }
        if (chunks.isEmpty()) {
            throw MalformedFileException("RIFF file has no chunks")
        }
        return chunks
    }

    private fun readChunk(riffEnd: Long): RiffChunk {
        val start = reader.position
        if (riffEnd - start < CHUNK_HEADER_SIZE) {
            throw MalformedFileException("RIFF chunk header at offset $start runs past the end of the RIFF data")
        }
        val fourCc = reader.readAscii(FOURCC_SIZE)
        val dataLength = reader.readU32()
        val dataStart = reader.position
        val end = dataStart + dataLength + (dataLength and 1)
        if (end > riffEnd) {
            throw MalformedFileException("RIFF chunk '$fourCc' at offset $start runs past the end of the RIFF data")
        }
        reader.seek(end)
        return RiffChunk(fourCc, start, dataStart, dataLength, end)
    }

    // endregion Methods
}
