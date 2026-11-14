package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.bytes
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class RbspTest {

    // region Methods

    @Test
    fun escapesSmallBytesAfterTwoZeros() {
        val payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x04, 0x00, 0x00, 0x03)
        val expected = bytes(0x00, 0x00, 0x03, 0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x00, 0x04, 0x00, 0x00, 0x03, 0x03)
        assertArrayEquals(expected, escape(payload))
    }

    @Test
    fun unescapeUndoesEscape() {
        val payload = bytes(0x10, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x03, 0xFF, 0x00, 0x00, 0x01, 0x80)
        val raw = bytes(0x06) + escape(payload)
        assertArrayEquals(payload, Rbsp.unescape(raw, 1))
    }

    // Decoders drop a 0x03 after two zeros even when what follows didn't need escaping.
    @Test
    fun unescapeDropsEveryEscape() {
        assertArrayEquals(bytes(0x00, 0x00, 0xFF), Rbsp.unescape(bytes(0x00, 0x00, 0x03, 0xFF), 0))
    }

    private fun escape(payload: ByteArray): ByteArray = ByteArrayOutputStream().also { Rbsp.escape(payload, it) }.toByteArray()

    // endregion Methods
}
