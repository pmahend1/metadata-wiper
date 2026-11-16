package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import java.io.ByteArrayOutputStream

/*
 * Splits an Annex B byte stream (NAL units separated by 00 00 01 start codes) that arrives in
 * pieces, as a transport stream delivers it a packet at a time. Only SEI units are collected,
 * with where each of their bytes is in the file, and passed to [onSei] once the next start code
 * or the end of the stream closes them. Zero bytes before a start code belong to it, not to the
 * unit, which never ends in a zero byte.
 */
class AnnexBNalSplitter(private val codec: VideoCodec,
                        private val onSei: (NalUnit) -> Unit) {

    // region State

    private var zeros = 0
    private var atHeader = false
    private val unit = ByteArrayOutputStream()
    private val pieces = mutableListOf<FileSpan>()
    private var pieceStart = -1L
    private var pieceEnd = -1L

    // True inside an SEI unit, which a scan mustn't stop in the middle of.
    var isCollecting = false
        private set

    private companion object {
        const val START_CODE_END: Byte = 0x01
    }

    // endregion State

    // region Methods

    // Bytes [from, to) of [bytes], the first of which is at [fileOffset] in the file.
    fun feed(bytes: ByteArray,
             from: Int,
             to: Int,
             fileOffset: Long) {
        for (index in from until to) {
            val byte = bytes[index]
            if (zeros >= 2 && byte == START_CODE_END) {
                if (isCollecting) {
                    close()
                }
                atHeader = true
                zeros = 0
                continue
            }
            if (atHeader) {
                atHeader = false
                isCollecting = codec.isSei(byte.toInt() and 0xFF)
            }
            if (isCollecting) {
                collect(byte, fileOffset + index - from)
            }
            zeros = if (byte == 0.toByte()) zeros + 1 else 0
        }
    }

    fun finish() {
        if (isCollecting) {
            close()
        }
    }

    private fun collect(byte: Byte,
                        offset: Long) {
        if (unit.size() >= LengthPrefixedNalScanner.MAX_SEI_SIZE) {
            throw MalformedFileException("SEI unit at offset ${pieces.firstOrNull()?.start ?: pieceStart} is too large")
        }
        unit.write(byte.toInt())
        if (offset != pieceEnd) {
            closePiece()
            pieceStart = offset
        }
        pieceEnd = offset + 1
    }

    private fun closePiece() {
        if (pieceStart >= 0) {
            pieces += FileSpan(pieceStart, pieceEnd)
        }
        pieceStart = -1L
        pieceEnd = -1L
    }

    private fun close() {
        closePiece()
        val bytes = unit.toByteArray()
        val length = bytes.indexOfLast { it != 0.toByte() } + 1
        isCollecting = false
        unit.reset()
        val kept = trimmed(pieces, length.toLong())
        pieces.clear()
        onSei(NalUnit(bytes.copyOf(length), kept))
    }

    private fun trimmed(spans: List<FileSpan>,
                        length: Long): List<FileSpan> {
        val kept = mutableListOf<FileSpan>()
        var left = length
        for (span in spans) {
            if (left == 0L) {
                break
            }
            val take = minOf(left, span.length)
            kept += FileSpan(span.start, span.start + take)
            left -= take
        }
        return kept
    }

    // endregion Methods
}
