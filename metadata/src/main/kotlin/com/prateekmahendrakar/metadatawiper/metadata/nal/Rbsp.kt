package com.prateekmahendrakar.metadatawiper.metadata.nal

import java.io.ByteArrayOutputStream

/*
 * Converts between a NAL unit's raw bytes and its payload (the RBSP). Encoders insert 0x03 after
 * any two zero bytes that are followed by 0x00–0x03, so the payload never looks like a start
 * code, and decoders drop a 0x03 after two zeros wherever it is.
 */
object Rbsp {

    // region State

    private const val ESCAPE: Byte = 0x03
    private const val MAX_ESCAPED = 0x03

    // endregion State

    // region Methods

    fun unescape(raw: ByteArray,
                 from: Int): ByteArray {
        val output = ByteArrayOutputStream(raw.size - from)
        var zeros = 0
        for (index in from until raw.size) {
            val byte = raw[index]
            if (zeros >= 2 && byte == ESCAPE) {
                zeros = 0
                continue
            }
            output.write(byte.toInt())
            zeros = if (byte == 0.toByte()) zeros + 1 else 0
        }
        return output.toByteArray()
    }

    fun escape(payload: ByteArray,
               output: ByteArrayOutputStream) {
        var zeros = 0
        for (byte in payload) {
            if (zeros >= 2 && (byte.toInt() and 0xFF) <= MAX_ESCAPED) {
                output.write(ESCAPE.toInt())
                zeros = 0
            }
            output.write(byte.toInt())
            zeros = if (byte == 0.toByte()) zeros + 1 else 0
        }
    }

    // endregion Methods
}
