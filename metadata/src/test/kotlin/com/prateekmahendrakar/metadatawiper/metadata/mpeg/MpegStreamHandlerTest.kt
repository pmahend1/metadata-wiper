package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class MpegStreamHandlerTest {

    // region State

    private val programStream = byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte()) + ByteArray(64)

    // endregion State

    // region Methods

    // Reporting "nothing found" would claim a file is clean when its video stream may hold the date and location.
    @Test
    fun refusesBothCallsWithTheInStreamReason() {
        val handler = MpegStreamHandler()
        val inspected = assertThrows(UnsupportedFileException::class.java) { handler.inspect(ByteArrayChannel(programStream)) }
        assertEquals(UnsupportedReason.InStreamMetadata, inspected.reason)

        val output = ByteArrayOutputStream()
        val stripped = assertThrows(UnsupportedFileException::class.java) {
            handler.strip(ByteArrayChannel(programStream), output, StripOptions())
        }
        assertEquals(UnsupportedReason.InStreamMetadata, stripped.reason)
        assertEquals(0, output.size())
        assertEquals(FileFormat.MpegPs, handler.format)
    }

    // endregion Methods
}
