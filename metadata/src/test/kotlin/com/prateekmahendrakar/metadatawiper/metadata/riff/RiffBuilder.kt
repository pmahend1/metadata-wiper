package com.prateekmahendrakar.metadatawiper.metadata.riff

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Builds RIFF bytes for tests: chunks are padded to an even length, as the format requires.
object RiffBuilder {

    // region Methods

    fun chunk(fourCc: String,
              data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(ascii(fourCc))
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array())
        output.write(data)
        if (data.size % 2 == 1) {
            output.write(0)
        }
        return output.toByteArray()
    }

    fun list(listType: String,
             vararg children: ByteArray): ByteArray = chunk("LIST", ascii(listType) + concat(children))

    fun riff(formType: String,
             vararg children: ByteArray): ByteArray = chunk("RIFF", ascii(formType) + concat(children))

    // INFO and similar chunks hold NUL-terminated text.
    fun text(value: String): ByteArray = value.toByteArray(Charsets.UTF_8) + 0

    fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)

    private fun concat(parts: Array<out ByteArray>): ByteArray = parts.fold(ByteArray(0)) { all, part -> all + part }

    // endregion Methods
}
