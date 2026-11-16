package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeiReaderTest {

    // region Methods

    @Test
    fun readsTypesAndSizesWrittenAsRunsOfFf() {
        val payload = concat(message(0, bytes(0x81)), message(300, ByteArray(600) { 0x11 }), message(5, ByteArray(16))) + bytes(0x80)
        val messages = SeiReader.messages(payload)

        assertEquals(listOf(0, 300, 5), messages.map { it.payloadType })
        assertEquals(listOf(1, 600, 16), messages.map { it.payloadSize })
        assertEquals(payload.size - 1, messages.last().payloadEnd)
    }

    // HEVC's structure-of-pictures message is type 128, the same byte as the stop bit.
    @Test
    fun tellsTypeOneTwentyEightFromTheStopBit() {
        val messages = SeiReader.messages(message(128, bytes(0x01, 0x02)) + bytes(0x80))
        assertEquals(listOf(128), messages.map { it.payloadType })
    }

    @Test
    fun rejectsBrokenPayloads() {
        assertThrows(MalformedFileException::class.java) { SeiReader.messages(message(5, ByteArray(16))) }
        assertThrows(MalformedFileException::class.java) { SeiReader.messages(bytes(0x05, 0x20, 0x01, 0x80)) }
        assertThrows(MalformedFileException::class.java) { SeiReader.messages(bytes(0xFF, 0xFF, 0x80)) }
        assertThrows(MalformedFileException::class.java) { SeiReader.messages(ByteArray(4)) }
    }

    // endregion Methods
}
