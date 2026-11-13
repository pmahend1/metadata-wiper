package com.prateekmahendrakar.metadatawiper.metadata.riff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.nio.ByteOrder

/*
 * Reads RIFF files (WebP, AVI, later WAV): a `RIFF` header of some form type, then chunks, some
 * of which are `LIST`s holding more chunks. Children must exactly fill the size their parent
 * declares. Anything after a `RIFF` is left for the caller to report.
 */
class RiffChunkReader(private val reader: BinaryReader) {

    // region State

    companion object {
        const val RIFF = "RIFF"
        const val LIST = "LIST"
        const val FOURCC_SIZE = 4
        const val CHUNK_HEADER_SIZE = 8L
    }

    // endregion State

    // region Methods

    // The chunks of a file that is one RIFF of [formType] starting at offset 0.
    fun read(formType: String): List<RiffChunk> {
        val chunks = children(readForm(0, formType))
        if (chunks.isEmpty()) {
            throw MalformedFileException("RIFF file has no chunks")
        }
        return chunks
    }

    // A `RIFF` chunk of [formType] at [start]. Its children follow the form type, as a `LIST`'s follow the list type.
    fun readForm(start: Long,
                 formType: String): RiffChunk {
        if (start < 0 || reader.size - start < CHUNK_HEADER_SIZE + FOURCC_SIZE) {
            throw MalformedFileException("No room for a RIFF header at offset $start")
        }
        reader.seek(start)
        reader.byteOrder = ByteOrder.LITTLE_ENDIAN
        if (reader.readAscii(FOURCC_SIZE) != RIFF) {
            throw MalformedFileException("No RIFF header at offset $start")
        }
        val dataLength = reader.readU32()
        if (reader.readAscii(FOURCC_SIZE) != formType) {
            throw MalformedFileException("RIFF at offset $start isn't of type $formType")
        }
        val dataStart = start + CHUNK_HEADER_SIZE
        if (dataLength < FOURCC_SIZE || dataStart + dataLength > reader.size) {
            throw MalformedFileException("RIFF header at offset $start declares $dataLength bytes, but the file is shorter")
        }
        return RiffChunk(RIFF, start, dataStart, dataLength, dataStart + dataLength)
    }

    // True when a `RIFF` of [formType] starts at [start]; it isn't checked any further.
    fun hasForm(start: Long,
                formType: String): Boolean {
        if (start < 0 || reader.size - start < CHUNK_HEADER_SIZE + FOURCC_SIZE) {
            return false
        }
        reader.seek(start)
        if (reader.readAscii(FOURCC_SIZE) != RIFF) {
            return false
        }
        reader.skip(FOURCC_SIZE.toLong())
        return reader.readAscii(FOURCC_SIZE) == formType
    }

    // The form type of a `RIFF` or the list type of a `LIST`: the four bytes its children follow.
    fun listType(list: RiffChunk): String {
        if (list.dataLength < FOURCC_SIZE) {
            throw MalformedFileException("'${list.fourCc}' chunk at offset ${list.start} is too short for a list type")
        }
        reader.seek(list.dataStart)
        return reader.readAscii(FOURCC_SIZE)
    }

    fun isList(chunk: RiffChunk,
               listType: String): Boolean = chunk.fourCc == LIST && listType(chunk) == listType

    fun children(list: RiffChunk): List<RiffChunk> {
        listType(list)
        reader.byteOrder = ByteOrder.LITTLE_ENDIAN
        val end = list.dataStart + list.dataLength
        val chunks = mutableListOf<RiffChunk>()
        var position = list.dataStart + FOURCC_SIZE
        while (position < end) {
            val chunk = readChunk(position, end)
            chunks += chunk
            position = chunk.end
        }
        return chunks
    }

    private fun readChunk(start: Long,
                          parentEnd: Long): RiffChunk {
        if (parentEnd - start < CHUNK_HEADER_SIZE) {
            throw MalformedFileException("RIFF chunk header at offset $start runs past the end of its parent")
        }
        reader.seek(start)
        val fourCc = reader.readAscii(FOURCC_SIZE)
        val dataLength = reader.readU32()
        val dataStart = reader.position
        val end = dataStart + dataLength + (dataLength and 1)
        if (end > parentEnd) {
            throw MalformedFileException("RIFF chunk '$fourCc' at offset $start runs past the end of its parent")
        }
        return RiffChunk(fourCc, start, dataStart, dataLength, end)
    }

    // endregion Methods
}
