package com.prateekmahendrakar.metadatawiper.metadata.io

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel

/*
 * Endian-aware reads over a seekable channel. Every read and seek is checked against the
 * file size first, so a corrupt length field fails with MalformedFileException instead of
 * allocating a huge buffer or reading past the end.
 */
class BinaryReader(private val channel: SeekableByteChannel) {

    // region State

    private val scratch = ByteBuffer.allocate(8)

    var byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    val size: Long = channel.size()

    val position: Long
        get() = channel.position()

    val remaining: Long
        get() = size - position

    // endregion State

    // region Methods

    fun seek(position: Long) {
        if (position < 0 || position > size) {
            throw MalformedFileException("Offset $position is outside the file ($size bytes)")
        }
        channel.position(position)
    }

    fun skip(count: Long) {
        requireRemaining(count)
        channel.position(position + count)
    }

    fun readU8(): Int = readScratch(1).get().toInt() and 0xFF

    fun readU16(): Int = readScratch(2).short.toInt() and 0xFFFF

    fun readU32(): Long = readScratch(4).int.toLong() and 0xFFFFFFFFL

    fun readS32(): Int = readScratch(4).int

    // Lengths above Long.MAX_VALUE can't describe a real file, so they're treated as corrupt.
    fun readU64(): Long {
        val value = readScratch(8).long
        if (value < 0) {
            throw MalformedFileException("64-bit value at ${position - 8} is too large")
        }
        return value
    }

    fun readBytes(count: Int): ByteArray {
        requireRemaining(count.toLong())
        val buffer = ByteBuffer.allocate(count)
        readFully(buffer)
        return buffer.array()
    }

    fun readAscii(count: Int): String = String(readBytes(count), Charsets.US_ASCII)

    fun requireRemaining(count: Long) {
        if (count < 0 || count > remaining) {
            throw MalformedFileException("Need $count bytes at offset $position, but only $remaining remain")
        }
    }

    private fun readScratch(count: Int): ByteBuffer {
        requireRemaining(count.toLong())
        scratch.clear()
        scratch.limit(count)
        readFully(scratch)
        scratch.flip()
        scratch.order(byteOrder)
        return scratch
    }

    private fun readFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) == -1) {
                throw MalformedFileException("Unexpected end of file at offset $position")
            }
        }
    }

    // endregion Methods
}
