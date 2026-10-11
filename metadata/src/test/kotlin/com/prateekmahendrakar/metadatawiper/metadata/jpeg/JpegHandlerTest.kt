package com.prateekmahendrakar.metadatawiper.metadata.jpeg

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.applicationSegment
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.bytesOf
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.fixture
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.fixtureNames
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.segment
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.segments
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegTestData.withSegments
import com.prateekmahendrakar.metadatawiper.metadata.tiff.MinimalExifWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class JpegHandlerTest {

    private val handler = JpegHandler()

    private val keptCategories = setOf(MetadataCategory.ColorProfile, MetadataCategory.Orientation)

    // Everything a decoder needs; these must come through byte for byte.
    private val imageKinds = setOf(JpegSegmentKind.StartOfImage,
                                   JpegSegmentKind.Frame,
                                   JpegSegmentKind.Table,
                                   JpegSegmentKind.Scan,
                                   JpegSegmentKind.EndOfImage,
                                   JpegSegmentKind.Adobe)

    @Test
    fun inspectFindsCameraMetadata() {
        val items = inspect(fixture("jpeg-camera.jpg")).items

        assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        assertHas(items, MetadataCategory.Device, "Make", "Fixture Camera Co")
        assertHas(items, MetadataCategory.Device, "BodySerialNumber", "SN-0042-FIXTURE")
        assertHas(items, MetadataCategory.People, "CameraOwnerName", "Jane Fixture")
        assertHas(items, MetadataCategory.DateTime, "DateTimeOriginal", "2026:09:12 14:30:00")
        assertHas(items, MetadataCategory.Orientation, "Orientation", "6")
        assertHas(items, MetadataCategory.Other, "Comment", "Fixture JPEG comment")
        assertTrue(items.any { it.category == MetadataCategory.Thumbnail })
        assertTrue(items.any { it.label == "EXIF block" })
        assertTrue(items.any { it.label == "XMP" })
        assertTrue(items.any { it.label == "IPTC / Photoshop" })
    }

    @Test
    fun inspectFindsNothingInCleanFile() {
        assertEquals(emptyList<MetadataItem>(), inspect(fixture("jpeg-clean.jpg")).items)
    }

    @Test
    fun stripLeavesOnlyKeptCategoriesInEveryFixture() {
        for (name in fixtureNames) {
            val output = strip(fixture(name)).first
            val remaining = inspect(output).items.filter { it.category !in keptCategories }
            assertEquals(name, emptyList<MetadataItem>(), remaining)
        }
    }

    @Test
    fun stripKeepsImageDataByteForByte() {
        for (name in fixtureNames) {
            val input = fixture(name)
            val output = strip(input).first
            assertArrayEquals(name, imageData(input), imageData(output))
        }
    }

    @Test
    fun stripIsIdempotent() {
        for (name in fixtureNames) {
            val once = strip(fixture(name)).first
            val twice = strip(once).first
            assertArrayEquals(name, once, twice)
        }
    }

    @Test
    fun stripLeavesCleanFileUnchanged() {
        val input = fixture("jpeg-clean.jpg")
        val (output, report) = strip(input)
        assertArrayEquals(input, output)
        assertEquals(StripReport(emptyList(), emptyList(), emptyList()), report)
    }

    @Test
    fun stripReplacesExifWithMinimalOrientation() {
        val (output, report) = strip(fixture("jpeg-camera.jpg"))

        val exif = segments(output).filter { it.kind == JpegSegmentKind.Exif }
        assertEquals(1, exif.size)
        assertArrayEquals(segment(JpegMarker.APP1, MinimalExifWriter.jpegApp1Payload(6)), bytesOf(output, exif.single()))
        assertEquals(listOf(MetadataItem(MetadataCategory.Orientation, "Orientation", "6")), inspect(output).items)
        assertEquals(listOf(MetadataCategory.Orientation), report.kept.map { it.category })
        assertTrue(report.removed.any { it.category == MetadataCategory.Location })
    }

    @Test
    fun stripDropsExifWhenOrientationNotKept() {
        val (output, report) = strip(fixture("jpeg-camera.jpg"), StripOptions(keepOrientation = false))
        assertTrue(segments(output).none { it.kind == JpegSegmentKind.Exif })
        assertEquals(emptyList<MetadataItem>(), inspect(output).items)
        assertTrue(report.removed.any { it.category == MetadataCategory.Orientation })
        assertEquals(emptyList<MetadataItem>(), report.kept)
    }

    // Orientation 1 is the default, so there's nothing worth keeping.
    @Test
    fun stripDropsExifWhenOrientationIsDefault() {
        val output = strip(fixture("jpeg-progressive.jpg")).first
        assertTrue(segments(output).none { it.kind == JpegSegmentKind.Exif })
    }

    @Test
    fun stripKeepsEveryScanOfProgressiveImage() {
        val input = fixture("jpeg-progressive.jpg")
        val scanCount = segments(input).count { it.kind == JpegSegmentKind.Scan }
        assertTrue(scanCount > 1)
        assertEquals(scanCount, segments(strip(input).first).count { it.kind == JpegSegmentKind.Scan })
    }

    @Test
    fun stripKeepsAdobeSegmentOfCmykImage() {
        val input = fixture("jpeg-cmyk.jpg")
        val adobe = segments(input).single { it.kind == JpegSegmentKind.Adobe }
        val output = strip(input).first
        assertArrayEquals(bytesOf(input, adobe),
                          bytesOf(output, segments(output).single { it.kind == JpegSegmentKind.Adobe }))
    }

    @Test
    fun stripRemovesMotionPhotoTrailer() {
        val input = fixture("jpeg-motion-photo.jpg")
        val imageEnd = segments(input).last().end
        assertTrue(imageEnd < input.size)

        val trailer = inspect(input).items.single { it.category == MetadataCategory.EmbeddedMedia }
        assertEquals(input.size - imageEnd, trailer.byteSize)

        val output = strip(input).first
        assertEquals(segments(output).last().end, output.size.toLong())
    }

    @Test
    fun keepsOrRemovesIccProfileByOption() {
        val chunk1 = applicationSegment(JpegMarker.APP2, "ICC_PROFILE\u0000", bytes(1, 2) + ByteArray(100))
        val chunk2 = applicationSegment(JpegMarker.APP2, "ICC_PROFILE\u0000", bytes(2, 2) + ByteArray(50))
        val input = withSegments(fixture("jpeg-clean.jpg"), chunk1, chunk2)

        assertEquals(listOf(MetadataItem(MetadataCategory.ColorProfile, "ICC profile", byteSize = 150)),
                     inspect(input).items)

        val (kept, keptReport) = strip(input, StripOptions(keepColorProfile = true))
        assertArrayEquals(input, kept)
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        val (removed, removedReport) = strip(input, StripOptions(keepColorProfile = false))
        assertArrayEquals(fixture("jpeg-clean.jpg"), removed)
        assertEquals(listOf(MetadataCategory.ColorProfile), removedReport.removed.map { it.category })
    }

    @Test
    fun stripRemovesJfifThumbnailButKeepsJfif() {
        val clean = fixture("jpeg-clean.jpg")
        val originalJfif = segments(clean)[1]
        val jfifHeader = clean.copyOfRange(originalJfif.payloadStart.toInt(), originalJfif.payloadStart.toInt() + 12)
        val withThumbnail = segment(JpegMarker.APP0, jfifHeader + bytes(2, 1) + ByteArray(6) { 0x7F })
        val input = clean.copyOfRange(0, 2) + withThumbnail + clean.copyOfRange(originalJfif.end.toInt(), clean.size)

        assertEquals(listOf(MetadataItem(MetadataCategory.Thumbnail, "JFIF thumbnail", "2×1", 6)), inspect(input).items)
        assertArrayEquals(clean, strip(input).first)
    }

    @Test
    fun stripRemovesOtherApplicationSegmentsAndComments() {
        val input = withSegments(fixture("jpeg-clean.jpg"),
                                 applicationSegment(JpegMarker.APP0, "JFXX\u0000", ByteArray(20)),
                                 applicationSegment(JpegMarker.APP1,
                                                    "http://ns.adobe.com/xmp/extension/\u0000",
                                                    ByteArray(20)),
                                 applicationSegment(JpegMarker.APP2, "MPF\u0000", ByteArray(20)),
                                 applicationSegment(JpegMarker.APP2, "FPXR\u0000", ByteArray(20)),
                                 applicationSegment(JpegMarker.APP0 + 12, "Ducky", ByteArray(20)),
                                 applicationSegment(JpegMarker.APP15, "", ByteArray(20)),
                                 segment(JpegMarker.COM, "Jane's phone".toByteArray()))

        val (output, report) = strip(input)

        assertArrayEquals(fixture("jpeg-clean.jpg"), output)
        assertEquals(listOf("JFIF extension thumbnail",
                            "Extended XMP",
                            "Multi-picture index",
                            "FlashPix data",
                            "Application segment",
                            "Application segment",
                            "Comment"),
                     report.removed.map { it.label })
        assertEquals(listOf("APP12", "APP15"), report.removed.filter { it.label == "Application segment" }.map { it.detail })
    }

    @Test
    fun stripRemovesDamagedExifWithWarning() {
        val damaged = applicationSegment(JpegMarker.APP1, "Exif\u0000\u0000", bytes(0x41, 0x41, 0, 42, 0, 0, 0, 8))
        val input = withSegments(fixture("jpeg-clean.jpg"), damaged)

        val inspection = inspect(input)
        assertEquals(listOf("EXIF block"), inspection.items.map { it.label })
        assertEquals(1, inspection.warnings.size)

        val (output, report) = strip(input)
        assertArrayEquals(fixture("jpeg-clean.jpg"), output)
        assertEquals(1, report.warnings.size)
    }

    // Every cut before EOI loses the end of the image, so it must fail cleanly and write nothing.
    @Test
    fun rejectsEveryTruncationWithoutWritingOutput() {
        for (name in fixtureNames) {
            val input = fixture(name)
            val imageEnd = segments(input).last().end.toInt()
            for (length in 0 until imageEnd) {
                val truncated = input.copyOf(length)
                assertThrows("$name cut at $length", MalformedFileException::class.java) { inspect(truncated) }
                val output = ByteArrayOutputStream()
                assertThrows("$name cut at $length", MalformedFileException::class.java) {
                    handler.strip(ByteArrayChannel(truncated), output, StripOptions())
                }
                assertEquals(0, output.size())
            }
        }
    }

    @Test
    fun rejectsUnknownMarker() {
        val input = withSegments(fixture("jpeg-clean.jpg"), segment(0xF7, ByteArray(4)))
        assertThrows(MalformedFileException::class.java) { inspect(input) }
    }

    private fun inspect(jpeg: ByteArray): InspectionResult = handler.inspect(ByteArrayChannel(jpeg))

    private fun strip(jpeg: ByteArray,
                      options: StripOptions = StripOptions()): Pair<ByteArray, StripReport> {
        val output = ByteArrayOutputStream()
        val report = handler.strip(ByteArrayChannel(jpeg), output, options)
        return output.toByteArray() to report
    }

    private fun imageData(jpeg: ByteArray): ByteArray {
        return segments(jpeg).filter { it.kind in imageKinds }
            .fold(ByteArray(0)) { all, segment -> all + bytesOf(jpeg, segment) }
    }

    private fun assertHas(items: List<MetadataItem>,
                          category: MetadataCategory,
                          label: String,
                          detail: String) {
        assertTrue("Missing $label=$detail in $items",
                   items.any { it.category == category && it.label == label && it.detail == detail })
    }
}
