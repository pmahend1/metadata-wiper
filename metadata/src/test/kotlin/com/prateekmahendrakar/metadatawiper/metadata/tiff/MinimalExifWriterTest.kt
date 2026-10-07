package com.prateekmahendrakar.metadatawiper.metadata.tiff

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MinimalExifWriterTest {

    @Test
    fun writesExpectedTiffBytes() {
        val expected = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,  // header, IFD0 at 8
                                   0x01, 0x00,                                      // one entry
                                   0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,  // Orientation, SHORT, count 1
                                   0x06, 0x00, 0x00, 0x00,                          // value 6, padded
                                   0x00, 0x00, 0x00, 0x00)                          // no IFD1
        assertArrayEquals(expected, MinimalExifWriter.tiff(orientation = 6))
    }

    @Test
    fun prefixesJpegPayloadWithExifIdentifier() {
        val payload = MinimalExifWriter.jpegApp1Payload(orientation = 8)
        assertEquals(6 + MinimalExifWriter.TIFF_SIZE, payload.size)
        assertArrayEquals("Exif\u0000\u0000".toByteArray(Charsets.US_ASCII), payload.copyOf(6))
        assertArrayEquals(MinimalExifWriter.tiff(orientation = 8), payload.copyOfRange(6, payload.size))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidOrientation() {
        MinimalExifWriter.tiff(orientation = 9)
    }
}
