package com.prateekmahendrakar.metadatawiper.metadata

import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

// Read-only in-memory channel, so tests don't need temp files.
class ByteArrayChannel(private val bytes: ByteArray) : SeekableByteChannel {

    // region State

    private var position = 0L
    private var open = true

    // endregion State

    // region Methods

    override fun read(destination: ByteBuffer): Int {
        if (position >= bytes.size) {
            return -1
        }
        val count = minOf(destination.remaining().toLong(), bytes.size - position).toInt()
        destination.put(bytes, position.toInt(), count)
        position += count
        return count
    }

    override fun write(source: ByteBuffer): Int = throw NonWritableChannelException()

    override fun position(): Long = position

    override fun position(newPosition: Long): SeekableByteChannel {
        position = newPosition
        return this
    }

    override fun size(): Long = bytes.size.toLong()

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }

    // endregion Methods
}
