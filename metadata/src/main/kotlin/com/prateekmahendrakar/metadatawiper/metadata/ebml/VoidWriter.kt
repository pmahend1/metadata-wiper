package com.prateekmahendrakar.metadatawiper.metadata.ebml

import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

/*
 * Turns any element into a Void element of exactly the same length: the Void ID, a size, then
 * zeros. A 1-byte size covers totals up to 128 bytes (its largest value, 127, means "unknown")
 * and an 8-byte size covers everything longer, so every total of 2 bytes or more fits.
 */
object VoidWriter {

    // region State

    private const val ONE_BYTE_MAX_TOTAL = 128L
    private const val ONE_BYTE_HEADER = 2
    private const val EIGHT_BYTE_HEADER = 9
    private const val EIGHT_BYTE_MARKER = 0x01

    // endregion State

    // region Methods

    fun blank(span: FileSpan): BlankedRange = BlankedRange(span, header(span.length))

    fun header(totalLength: Long): ByteArray {
        require(totalLength >= ONE_BYTE_HEADER) { "A Void element is at least $ONE_BYTE_HEADER bytes long" }
        if (totalLength <= ONE_BYTE_MAX_TOTAL) {
            val size = totalLength - ONE_BYTE_HEADER
            return byteArrayOf(EbmlId.VOID.toByte(), (0x80 or size.toInt()).toByte())
        }
        val size = totalLength - EIGHT_BYTE_HEADER
        val header = ByteArray(EIGHT_BYTE_HEADER)
        header[0] = EbmlId.VOID.toByte()
        header[1] = EIGHT_BYTE_MARKER.toByte()
        for (index in 2 until EIGHT_BYTE_HEADER) {
            header[index] = (size shr (8 * (EIGHT_BYTE_HEADER - 1 - index))).toByte()
        }
        return header
    }

    // endregion Methods
}
