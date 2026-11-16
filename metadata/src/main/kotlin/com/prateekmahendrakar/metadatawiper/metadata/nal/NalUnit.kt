package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

/*
 * One NAL unit's raw bytes, header included, as stored: emulation-prevention bytes still in.
 * [pieces] are where those bytes are in the file, in order. An MP4 sample holds a NAL unit in
 * one piece; in a transport stream it can be split across packets.
 */
class NalUnit(val bytes: ByteArray,
              val pieces: List<FileSpan>) {

    init {
        require(pieces.sumOf { it.length } == bytes.size.toLong()) { "NAL unit pieces don't add up to its ${bytes.size} bytes" }
    }

    // region Methods

    // Where raw bytes [from, to) are in the file, split where the unit is.
    fun spans(from: Int,
              to: Int): List<FileSpan> {
        val spans = mutableListOf<FileSpan>()
        var pieceStart = 0L
        for (piece in pieces) {
            val start = maxOf(from.toLong(), pieceStart)
            val end = minOf(to.toLong(), pieceStart + piece.length)
            if (start < end) {
                spans += FileSpan(piece.start + start - pieceStart, piece.start + end - pieceStart)
            }
            pieceStart += piece.length
        }
        return spans
    }

    // endregion Methods
}
