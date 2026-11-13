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

    private val transportStream = ByteArray(188 * 4).also { bytes -> (0 until 4).forEach { bytes[it * 188] = 0x47 } }
    private val programStream = byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte()) + ByteArray(64)

    // endregion State

    // region Methods

    // Reporting "nothing found" would claim a file is clean when its video stream may hold the date and location.
    @Test
    fun refusesBothCallsWithTheInStreamReason() {
        for ((format, bytes) in listOf(FileFormat.MpegTs to transportStream, FileFormat.MpegPs to programStream)) {
            val handler = MpegStreamHandler(format)
            val inspected = assertThrows(UnsupportedFileException::class.java) { handler.inspect(ByteArrayChannel(bytes)) }
            assertEquals(UnsupportedReason.InStreamMetadata, inspected.reason)

            val output = ByteArrayOutputStream()
            val stripped = assertThrows(UnsupportedFileException::class.java) {
                handler.strip(ByteArrayChannel(bytes), output, StripOptions())
            }
            assertEquals(UnsupportedReason.InStreamMetadata, stripped.reason)
            assertEquals(0, output.size())
        }
    }

    @Test
    fun handlesOnlyMpegStreams() {
        assertThrows(IllegalArgumentException::class.java) { MpegStreamHandler(FileFormat.Mp4) }
    }

    // endregion Methods
}
