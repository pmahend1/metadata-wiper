package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException

/*
 * Splits an SEI payload into its messages. H.264 and HEVC share the syntax: a type and a size,
 * each written as a run of 0xFF bytes plus a final byte, then that many payload bytes. The
 * messages end at the stop bit in the last non-zero byte. A trailing 0x80 can't be told apart by
 * value alone, since 128 is a valid HEVC payload type.
 */
object SeiReader {

    // region State

    private const val EXTENSION_BYTE = 0xFF
    private const val STOP_BYTE = 0x80

    // endregion State

    // region Methods

    fun messages(bytes: ByteArray): List<SeiMessage> {
        val stop = bytes.indexOfLast { it != 0.toByte() }
        if (stop < 0 || bytes[stop].toInt() and 0xFF != STOP_BYTE) {
            throw MalformedFileException("SEI unit has no stop bit")
        }

        val messages = mutableListOf<SeiMessage>()
        var position = 0
        while (position < stop) {
            val type = readValue(bytes, position, stop)
            position = type.next
            val size = readValue(bytes, position, stop)
            position = size.next
            if (size.value > stop - position) {
                throw MalformedFileException("SEI message of ${size.value} bytes runs past its unit")
            }
            messages += SeiMessage(type.value, position, position + size.value)
            position += size.value
        }
        return messages
    }

    private fun readValue(bytes: ByteArray,
                          start: Int,
                          stop: Int): Value {
        var value = 0
        var position = start
        while (true) {
            if (position >= stop) {
                throw MalformedFileException("SEI message header runs past its unit")
            }
            val byte = bytes[position++].toInt() and 0xFF
            value += byte
            if (byte != EXTENSION_BYTE) {
                return Value(value, position)
            }
        }
    }

    // endregion Methods

    private class Value(val value: Int,
                        val next: Int)
}
