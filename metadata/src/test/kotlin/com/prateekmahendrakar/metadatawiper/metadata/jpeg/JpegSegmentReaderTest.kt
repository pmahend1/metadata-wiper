package com.prateekmahendrakar.metadatawiper.metadata.jpeg

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.applicationSegment
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.segment
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.segments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class JpegSegmentReaderTest {

    private val soi = bytes(0xFF, 0xD8)
    private val eoi = bytes(0xFF, 0xD9)
    private val frame = segment(JpegMarker.SOF0, ByteArray(3))
    private val table = segment(JpegMarker.DQT, ByteArray(3))
    private val scanHeader = segment(JpegMarker.SOS, ByteArray(3))

    @Test
    fun splitsSegmentsWithExactOffsets() {
        val jfif = applicationSegment(JpegMarker.APP0, "JFIF\u0000", ByteArray(9))
        val jpeg = soi + jfif + table + frame + scanHeader + bytes(0x01, 0x02) + eoi

        val segments = segments(jpeg)

        assertEquals(listOf(JpegSegmentKind.StartOfImage,
                            JpegSegmentKind.Jfif,
                            JpegSegmentKind.Table,
                            JpegSegmentKind.Frame,
                            JpegSegmentKind.Scan,
                            JpegSegmentKind.EndOfImage), segments.map { it.kind })
        assertEquals(JpegSegment(JpegMarker.APP0, JpegSegmentKind.Jfif, 2, 6, 14, 20), segments[1])
        val scan = segments[4]
        assertEquals(jpeg.size - 2L, scan.end)
        assertEquals(scan.end, segments[5].start)
        assertEquals(jpeg.size.toLong(), segments[5].end)
    }

    // FF 00 is a stuffed data byte and FF D0–D7 are restart markers: neither ends the scan.
    @Test
    fun keepsStuffedBytesRestartMarkersAndFillInsideScan() {
        val scanData = bytes(0x01, 0xFF, 0x00, 0x02, 0xFF, 0xD3, 0x03, 0xFF, 0xFF)
        val jpeg = soi + frame + scanHeader + scanData + eoi

        val segments = segments(jpeg)

        assertEquals(JpegSegmentKind.Scan, segments[2].kind)
        assertEquals(JpegSegmentKind.EndOfImage, segments[3].kind)
        assertEquals(jpeg.size.toLong(), segments[3].end)
    }

    @Test
    fun readsMultipleScansOfProgressiveImage() {
        val jpeg = soi + frame + scanHeader + bytes(0x11) + table + scanHeader + bytes(0x22) + eoi
        assertEquals(2, segments(jpeg).count { it.kind == JpegSegmentKind.Scan })
    }

    @Test
    fun skipsFillBytesBeforeMarker() {
        val jpeg = soi + bytes(0xFF, 0xFF) + frame + scanHeader + eoi
        val segments = segments(jpeg)
        assertEquals(JpegSegment(JpegMarker.SOF0, JpegSegmentKind.Frame, 2, 8, 3, 11), segments[1])
    }

    // Scan data is read in 64 KiB chunks; a marker split across two chunks must still be found.
    @Test
    fun findsScanEndAcrossChunkBoundaries() {
        val chunkSize = 65_536
        val cases =
            mapOf("EOI split" to scanData(chunkSize - 1),
                  "EOI after full chunk" to scanData(chunkSize),
                  "stuffed byte split" to scanData(chunkSize + 1, chunkSize - 1 to 0xFF, chunkSize to 0x00),
                  "restart marker split" to scanData(chunkSize + 1, chunkSize - 1 to 0xFF, chunkSize to 0xD3),
                  "several chunks" to scanData(200_000,
                                               chunkSize - 1 to 0xFF,
                                               chunkSize to 0x00,
                                               2 * chunkSize to 0xFF,
                                               2 * chunkSize + 1 to 0xD0))

        for ((name, scanData) in cases) {
            val jpeg = soi + frame + scanHeader + scanData + eoi
            val scan = segments(jpeg).single { it.kind == JpegSegmentKind.Scan }
            assertEquals(name, jpeg.size - 2L, scan.end)
        }
    }

    @Test
    fun identifiesApplicationSegments() {
        val cases =
            mapOf(applicationSegment(JpegMarker.APP0, "JFXX\u0000", ByteArray(4)) to JpegSegmentKind.JfifExtension,
                  applicationSegment(JpegMarker.APP1, "Exif\u0000\u0000", ByteArray(8)) to JpegSegmentKind.Exif,
                  applicationSegment(JpegMarker.APP1,
                                     "http://ns.adobe.com/xap/1.0/\u0000",
                                     ByteArray(4)) to JpegSegmentKind.Xmp,
                  applicationSegment(JpegMarker.APP1,
                                     "http://ns.adobe.com/xmp/extension/\u0000",
                                     ByteArray(4)) to JpegSegmentKind.ExtendedXmp,
                  applicationSegment(JpegMarker.APP2, "ICC_PROFILE\u0000", bytes(1, 1)) to JpegSegmentKind.IccProfile,
                  applicationSegment(JpegMarker.APP2, "MPF\u0000", ByteArray(4)) to JpegSegmentKind.MultiPicture,
                  applicationSegment(JpegMarker.APP2, "FPXR\u0000", ByteArray(4)) to JpegSegmentKind.FlashPix,
                  applicationSegment(JpegMarker.APP13, "Photoshop 3.0\u0000", ByteArray(4)) to JpegSegmentKind.Photoshop,
                  applicationSegment(JpegMarker.APP14, "Adobe", ByteArray(7)) to JpegSegmentKind.Adobe,
                  applicationSegment(JpegMarker.APP0 + 12,
                                     "Ducky",
                                     ByteArray(4)) to JpegSegmentKind.OtherApplication, // Right identifier, wrong APPn.
                  applicationSegment(JpegMarker.APP2,
                                     "Exif\u0000\u0000",
                                     ByteArray(8)) to JpegSegmentKind.OtherApplication, // Too short for the fields the handler reads.
                  applicationSegment(JpegMarker.APP0, "JFIF\u0000", ByteArray(2)) to JpegSegmentKind.OtherApplication,
                  applicationSegment(JpegMarker.APP1, "Exif\u0000") to JpegSegmentKind.OtherApplication,
                  segment(JpegMarker.APP1, ByteArray(0)) to JpegSegmentKind.OtherApplication,
                  segment(JpegMarker.COM, ByteArray(3)) to JpegSegmentKind.Comment)

        for ((segment, expected) in cases) {
            val jpeg = soi + segment + frame + scanHeader + eoi
            assertEquals(expected, segments(jpeg)[1].kind)
        }
    }

    @Test
    fun rejectsMalformedStructure() {
        val cases =
            mapOf("no SOI" to frame + scanHeader + eoi,
                  "empty" to ByteArray(0),
                  "unknown marker" to soi + segment(0xF7, ByteArray(3)) + frame + scanHeader + eoi,
                  "restart marker outside scan" to soi + bytes(0xFF, 0xD0) + frame + scanHeader + eoi,
                  "garbage between segments" to soi + bytes(0x00) + frame + scanHeader + eoi,
                  "length below 2" to soi + bytes(0xFF, JpegMarker.DQT, 0x00, 0x01) + frame + scanHeader + eoi,
                  "length past end" to soi + bytes(0xFF, JpegMarker.COM, 0xFF, 0xFF, 0x00),
                  "missing EOI" to soi + frame + table,
                  "scan without EOI" to soi + frame + scanHeader + bytes(0x01, 0x02, 0x03),
                  "no frame" to soi + scanHeader + eoi,
                  "no scan" to soi + frame + eoi)

        for ((name, jpeg) in cases) {
            assertThrows(name, MalformedFileException::class.java) { segments(jpeg) }
        }
    }

    // Random entropy-coded bytes without 0xFF, so only the placed bytes can look like markers.
    private fun scanData(length: Int, vararg placed: Pair<Int, Int>): ByteArray {
        val random = Random(length)
        val data = ByteArray(length) { random.nextInt(0, 0xFF).toByte() }
        placed.forEach { (index, value) -> data[index] = value.toByte() }
        return data
    }
}
