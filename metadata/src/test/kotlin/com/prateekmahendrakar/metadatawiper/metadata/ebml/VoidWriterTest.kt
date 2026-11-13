package com.prateekmahendrakar.metadatawiper.metadata.ebml

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class VoidWriterTest {

    // region Methods

    @Test
    fun everyLengthParsesBackAsOneVoidOfThatLength() {
        val lengths = (2L..300L) + listOf(1_000L, 16_383L, 16_384L, 65_536L, 1_000_003L)
        for (length in lengths) {
            val output = ByteArrayOutputStream()
            VoidWriter.blank(FileSpan(0, length)).writeTo(output)
            val bytes = output.toByteArray()
            assertEquals(length, bytes.size.toLong())

            val element = EbmlReader(BinaryReader(ByteArrayChannel(bytes))).readElement(0, length, 0)
            assertEquals("Length $length", EbmlId.VOID, element.id)
            assertEquals("Length $length", length, element.end)
            assertEquals("Length $length", 0, bytes.drop(element.dataStart.toInt()).count { it != 0.toByte() })
        }
    }

    // endregion Methods
}
