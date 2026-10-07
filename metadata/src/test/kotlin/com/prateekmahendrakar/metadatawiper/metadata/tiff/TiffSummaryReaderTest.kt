package com.prateekmahendrakar.metadatawiper.metadata.tiff

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteOrder

class TiffSummaryReaderTest {

    @Test
    fun summarizesIdentifyingTagsInBothByteOrders() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val summary = read(cameraPhoto(TiffBuilder(order)))

            assertEquals(6, summary.orientation)
            assertItem(summary, MetadataCategory.Device, "Make", "Pixel Maker")
            assertItem(summary, MetadataCategory.Device, "Model", "Pixel 99")
            assertItem(summary, MetadataCategory.People, "Artist", "Jane Example")
            assertItem(summary, MetadataCategory.DateTime, "DateTimeOriginal", "2026:09:12 14:30:00")
            assertItem(summary, MetadataCategory.People, "CameraOwnerName", "Jane")
            assertItem(summary, MetadataCategory.Device, "BodySerialNumber", "SN-123456")
            assertItem(summary, MetadataCategory.Location, "GPS", "48.858217, -2.294500")
            assertItem(summary, MetadataCategory.Orientation, "Orientation", "6")
            val thumbnail = summary.items.single { it.category == MetadataCategory.Thumbnail }
            assertEquals(5120L, thumbnail.byteSize)
            val makerNote = summary.items.single { it.label == "MakerNote" }
            assertEquals(40L, makerNote.byteSize)
        }
    }

    @Test
    fun readsTiffEmbeddedAtAnOffset() {
        val tiff = cameraPhoto(TiffBuilder(ByteOrder.BIG_ENDIAN))
        val file = ByteArray(10) + tiff + ByteArray(5)
        val summary = TiffSummaryReader(BinaryReader(ByteArrayChannel(file)), 10, tiff.size.toLong()).read()
        assertItem(summary, MetadataCategory.Device, "Make", "Pixel Maker")
    }

    @Test
    fun reportsNothingForMinimalExif() {
        val summary = read(MinimalExifWriter.tiff(orientation = 1))
        assertEquals(1, summary.orientation)
        assertEquals(listOf(MetadataCategory.Orientation), summary.items.map { it.category })
    }

    @Test
    fun skipsEmptyTextAndReportsGpsWithoutCoordinates() {
        val builder = TiffBuilder(ByteOrder.LITTLE_ENDIAN)
        val tiff = builder.build(ifd0 = listOf(builder.ascii(TiffTag.ARTIST, "")),
                                 gps = listOf(builder.ascii(TiffTag.GPS_LATITUDE_REF, "N")))
        val summary = read(tiff)
        assertNull(summary.orientation)
        assertEquals(listOf(MetadataItem(MetadataCategory.Location, "GPS", "")), summary.items)
    }

    @Test
    fun restoresReaderByteOrder() {
        val reader = BinaryReader(ByteArrayChannel(cameraPhoto(TiffBuilder(ByteOrder.LITTLE_ENDIAN))))
        TiffSummaryReader(reader, 0, reader.size).read()
        assertEquals(ByteOrder.BIG_ENDIAN, reader.byteOrder)
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsUnknownByteOrder() {
        read(byteArrayOf(0x41, 0x41, 0, 42, 0, 0, 0, 8))
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsIfdOffsetOutsideBlock() {
        val tiff = MinimalExifWriter.tiff(orientation = 1)
        tiff[4] = 0x7F
        read(tiff)
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsValueOffsetOutsideBlock() {
        val builder = TiffBuilder(ByteOrder.BIG_ENDIAN)
        val tiff = builder.build(ifd0 = listOf(builder.ascii(TiffTag.MAKE, "Long enough to be out of line")))
        // Bytes 18–21 hold the first entry's value offset.
        tiff[18] = 0x7F
        read(tiff)
    }

    // The TIFF block may be shorter than the file; offsets past its end are corrupt even if the file is longer.
    @Test(expected = MalformedFileException::class)
    fun respectsBlockLengthNotFileLength() {
        val tiff = cameraPhoto(TiffBuilder(ByteOrder.BIG_ENDIAN))
        TiffSummaryReader(BinaryReader(ByteArrayChannel(tiff)), 0, 30).read()
    }

    @Test
    fun ignoresIfd1PointingBackAtIfd0() {
        val tiff = MinimalExifWriter.tiff(orientation = 3)
        tiff[22] = 8
        assertEquals(3, read(tiff).orientation)
    }

    @Test
    fun throwsOnlyMalformedFileExceptionWhenTruncated() {
        val tiff = cameraPhoto(TiffBuilder(ByteOrder.LITTLE_ENDIAN))
        for (length in 0 until tiff.size) {
            try {
                read(tiff.copyOf(length))
            } catch (_: MalformedFileException) {
            }
        }
    }

    private fun cameraPhoto(builder: TiffBuilder): ByteArray {
        return builder.build(ifd0 = listOf(builder.ascii(TiffTag.MAKE, "Pixel Maker"),
                                           builder.ascii(TiffTag.MODEL, "Pixel 99"),
                                           builder.ascii(TiffTag.ARTIST, "Jane Example"),
                                           builder.short(TiffTag.ORIENTATION, 6)),
                             exif = listOf(builder.ascii(TiffTag.DATE_TIME_ORIGINAL, "2026:09:12 14:30:00"),
                                           builder.ascii(TiffTag.CAMERA_OWNER_NAME, "Jane"),
                                           builder.ascii(TiffTag.BODY_SERIAL_NUMBER, "SN-123456"),
                                           builder.undefined(TiffTag.MAKER_NOTE, 40)),
                             gps = listOf(builder.ascii(TiffTag.GPS_LATITUDE_REF, "N"),
                                          builder.rationals(TiffTag.GPS_LATITUDE, 48L to 1L, 51L to 1L, 2958L to 100L),
                                          builder.ascii(TiffTag.GPS_LONGITUDE_REF, "W"),
                                          builder.rationals(TiffTag.GPS_LONGITUDE, 2L to 1L, 17L to 1L, 4020L to 100L)),
                             ifd1 = listOf(builder.long(TiffTag.THUMBNAIL_OFFSET, 0),
                                           builder.long(TiffTag.THUMBNAIL_LENGTH, 5120)))
    }

    private fun read(tiff: ByteArray): TiffSummary {
        return TiffSummaryReader(BinaryReader(ByteArrayChannel(tiff)), 0, tiff.size.toLong()).read()
    }

    private fun assertItem(summary: TiffSummary,
                           category: MetadataCategory,
                           label: String,
                           detail: String) {
        assertTrue("Missing $label=$detail in ${summary.items}",
                   summary.items.any { it.category == category && it.label == label && it.detail == detail })
    }
}
