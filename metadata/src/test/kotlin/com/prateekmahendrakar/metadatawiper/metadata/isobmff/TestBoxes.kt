package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import java.nio.ByteBuffer

// Byte-level helpers for building ISO BMFF boxes in tests. Everything is big-endian, as the format is.
object TestBoxes {

    // region Methods

    fun box(type: String,
            vararg parts: ByteArray): ByteArray {
        val payload = concat(*parts)
        return u32(8L + payload.size) + ascii(type) + payload
    }

    fun fullBox(type: String,
                version: Int,
                flags: Int,
                vararg parts: ByteArray): ByteArray = box(type, u32((version.toLong() shl 24) or flags.toLong()), *parts)

    // A box with a 64-bit size field, as writers use for media data over 4 GB.
    fun largeBox(type: String,
                 payload: ByteArray): ByteArray = u32(1) + ascii(type) + u64(16L + payload.size) + payload

    fun u8(value: Int): ByteArray = byteArrayOf(value.toByte())

    fun u16(value: Int): ByteArray = ByteBuffer.allocate(2).putShort(value.toShort()).array()

    fun u32(value: Long): ByteArray = ByteBuffer.allocate(4).putInt(value.toInt()).array()

    fun u64(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()

    fun sized(value: Long,
              size: Int): ByteArray {
        return when (size) {
            0 -> ByteArray(0)
            4 -> u32(value)
            8 -> u64(value)
            else -> throw IllegalArgumentException("Field size $size")
        }
    }

    fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)

    fun cString(text: String): ByteArray = text.toByteArray(Charsets.UTF_8) + 0

    fun concat(vararg parts: ByteArray): ByteArray = parts.fold(ByteArray(0)) { all, part -> all + part }

    // endregion Methods
}
