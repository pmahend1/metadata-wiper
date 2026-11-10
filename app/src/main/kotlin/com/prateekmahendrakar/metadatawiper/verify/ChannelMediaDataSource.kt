package com.prateekmahendrakar.metadatawiper.verify

import android.media.MediaDataSource
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

// Lets the media framework read the original through the channel the use case already has. The channel stays the caller's to close.
class ChannelMediaDataSource(private val channel: SeekableByteChannel) : MediaDataSource() {

    // region Methods

    @Synchronized
    override fun readAt(position: Long,
                        buffer: ByteArray,
                        offset: Int,
                        size: Int): Int {
        if (position >= channel.size()) {
            return -1
        }
        channel.position(position)
        return channel.read(ByteBuffer.wrap(buffer, offset, size))
    }

    @Synchronized
    override fun getSize(): Long = channel.size()

    override fun close() {
    }

    // endregion Methods
}
