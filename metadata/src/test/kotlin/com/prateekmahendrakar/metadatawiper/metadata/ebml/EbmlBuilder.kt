package com.prateekmahendrakar.metadatawiper.metadata.ebml

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

// Writes EBML elements for synthetic test files, with the shortest size that fits unless told otherwise.
object EbmlBuilder {

    // region State

    private const val UNKNOWN_SIZE_BYTE = 0xFF

    // endregion State

    // region Methods

    fun element(id: Long,
                data: ByteArray): ByteArray = id(id) + size(data.size.toLong()) + data

    fun master(id: Long,
               vararg children: ByteArray): ByteArray = element(id, concat(children))

    // A 1-byte size with every value bit set, as live recorders write Segment and Cluster.
    fun unknownSize(id: Long,
                    vararg children: ByteArray): ByteArray = id(id) + byteArrayOf(UNKNOWN_SIZE_BYTE.toByte()) + concat(children)

    // A master whose first child is a valid CRC-32 over the rest.
    fun masterWithCrc(id: Long,
                      vararg children: ByteArray): ByteArray {
        val rest = concat(children)
        val checksum = CRC32().apply { update(rest) }.value
        val crc = element(EbmlId.CRC32, ByteArray(4) { (checksum shr (8 * it)).toByte() })
        return element(id, crc + rest)
    }

    fun text(id: Long,
             value: String): ByteArray = element(id, value.toByteArray(Charsets.UTF_8))

    fun unsigned(id: Long,
                 value: Long): ByteArray {
        val length = maxOf(1, (64 - java.lang.Long.numberOfLeadingZeros(value) + 7) / 8)
        return element(id, ByteArray(length) { (value shr (8 * (length - 1 - it))).toByte() })
    }

    fun header(docType: String): ByteArray = master(EbmlId.EBML, text(0x4282L, docType))

    fun id(id: Long): ByteArray {
        val length = (64 - java.lang.Long.numberOfLeadingZeros(id) + 7) / 8
        return ByteArray(length) { (id shr (8 * (length - 1 - it))).toByte() }
    }

    fun size(value: Long): ByteArray {
        var length = 1
        while (value >= (1L shl (7 * length)) - 1) {
            length++
        }
        val bytes = ByteArray(length) { (value shr (8 * (length - 1 - it))).toByte() }
        bytes[0] = (bytes[0].toInt() or (0x80 shr (length - 1))).toByte()
        return bytes
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        parts.forEach { output.write(it) }
        return output.toByteArray()
    }

    // endregion Methods
}
