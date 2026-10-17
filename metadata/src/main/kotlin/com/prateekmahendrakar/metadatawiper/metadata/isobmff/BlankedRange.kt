package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.io.OutputStream

/*
 * Bytes a size-preserving strip overwrites in place: [prefix] first, then [fill] up to the
 * end of [span]. A prefix longer than the span is cut short. Nothing moves, so offsets held
 * elsewhere in the file (`iloc`, `stco`) stay valid.
 */
class BlankedRange(val span: FileSpan,
                   private val prefix: ByteArray = ByteArray(0),
                   private val fill: Byte = 0) {

    // region State

    companion object {
        private const val CHUNK_SIZE = 64 * 1024

        // One logical payload stored in several spans, as an item with several extents is: the prefix continues across them.
        fun across(spans: List<FileSpan>,
                   prefix: ByteArray = ByteArray(0),
                   fill: Byte = 0): List<BlankedRange> {
            var consumed = 0L
            return spans.map { span ->
                val from = minOf(consumed, prefix.size.toLong()).toInt()
                val to = minOf(consumed + span.length, prefix.size.toLong()).toInt()
                consumed += span.length
                BlankedRange(span, prefix.copyOfRange(from, to), fill)
            }
        }
    }

    // endregion State

    // region Methods

    // True when the file already holds exactly these bytes, as it does after a previous strip.
    fun isAlreadyBlank(reader: BinaryReader): Boolean {
        reader.seek(span.start)
        var position = 0L
        while (position < span.length) {
            val chunk = reader.readBytes(minOf(span.length - position, CHUNK_SIZE.toLong()).toInt())
            for (index in chunk.indices) {
                if (chunk[index] != byteAt(position + index)) {
                    return false
                }
            }
            position += chunk.size
        }
        return true
    }

    fun writeTo(output: OutputStream) {
        val prefixLength = minOf(prefix.size.toLong(), span.length).toInt()
        output.write(prefix, 0, prefixLength)
        var left = span.length - prefixLength
        val fillChunk = ByteArray(minOf(left, CHUNK_SIZE.toLong()).toInt()) { fill }
        while (left > 0) {
            val count = minOf(left, fillChunk.size.toLong()).toInt()
            output.write(fillChunk, 0, count)
            left -= count
        }
    }

    private fun byteAt(position: Long): Byte = if (position < prefix.size) prefix[position.toInt()] else fill

    // endregion Methods
}
