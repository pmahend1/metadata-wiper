package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import java.nio.ByteOrder

/*
 * Walks the NAL units of MP4 video samples, where each unit is preceded by its length in
 * [lengthSize] bytes (from `avcC`/`hvcC`). A chunk's samples are contiguous, so a chunk is one
 * run of units that must end exactly at the chunk's end. Only SEI units are read whole; for the
 * rest just the length and header are, so the pass costs a few bytes per unit, not per sample.
 */
class LengthPrefixedNalScanner(private val reader: BinaryReader,
                               private val codec: VideoCodec,
                               private val lengthSize: Int,
                               private val blanker: SeiBlanker) {

    // region State

    companion object {
        val lengthSizes = setOf(1, 2, 4)

        // MDPM is a few hundred bytes and x264's settings a few thousand; a larger SEI unit isn't worth holding in memory.
        const val MAX_SEI_SIZE = 1024 * 1024
    }

    // endregion State

    init {
        require(lengthSize in lengthSizes) { "NAL length size $lengthSize" }
    }

    // region Methods

    fun scan(chunk: FileSpan) {
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        var position = chunk.start
        while (position < chunk.end) {
            if (chunk.end - position < lengthSize) {
                throw MalformedFileException("NAL unit length at offset $position runs past its sample")
            }
            reader.seek(position)
            val length = readLength()
            position += lengthSize
            if (length > chunk.end - position) {
                throw MalformedFileException("NAL unit of $length bytes at offset $position runs past its sample")
            }
            if (length >= codec.headerSize) {
                scanUnit(position, length)
            }
            position += length
        }
    }

    private fun readLength(): Long {
        return when (lengthSize) {
            1 -> reader.readU8().toLong()
            2 -> reader.readU16().toLong()
            else -> reader.readU32()
        }
    }

    private fun scanUnit(start: Long,
                         length: Long) {
        if (codec.isSei(reader.readU8()) == false) {
            return
        }
        if (length > MAX_SEI_SIZE) {
            throw MalformedFileException("SEI unit of $length bytes at offset $start")
        }
        reader.seek(start)
        blanker.blank(codec, NalUnit(reader.readBytes(length.toInt()), listOf(FileSpan(start, start + length))))
    }

    // endregion Methods
}
