package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.FileSpan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeifHandlerTest {

    private val heif = HandlerHarness(HeifHandler(FileFormat.Heif))

    private val fixtures = mapOf("heif-metadata.heic" to heif,
                                 "avif-metadata.avif" to HandlerHarness(HeifHandler(FileFormat.Avif)),
                                 "heif-thumbnail-icc.heic" to heif)

    private val optionSets = listOf(StripOptions(), StripOptions(keepColorProfile = false, keepOrientation = false))

    private val metadataItemTypes = setOf("Exif", "mime")

    @Test
    fun inspectFindsExifXmpAndDescriptiveProperties() {
        val result = heif.inspect(heif.fixture("heif-thumbnail-icc.heic"))
        val items = result.items

        heif.assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        heif.assertHas(items, MetadataCategory.People, "CameraOwnerName", "Jane Fixture")
        heif.assertHas(items, MetadataCategory.Device, "BodySerialNumber", "SN-0042-FIXTURE")
        heif.assertHas(items, MetadataCategory.Other, "XMP")
        heif.assertHas(items, MetadataCategory.Other, "Image description")
        heif.assertHas(items, MetadataCategory.ColorProfile, "ICC profile")
        assertEquals(listOf(HeifStripPlanner.THUMBNAIL_WARNING), result.warnings)
        assertEquals(FileFormat.Heif, result.format)
    }

    @Test
    fun inspectReportsAvifAsAvif() {
        val avif = fixtures.getValue("avif-metadata.avif")
        val result = avif.inspect(avif.fixture("avif-metadata.avif"))
        assertEquals(FileFormat.Avif, result.format)
        avif.assertHas(result.items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
    }

    @Test
    fun stripRemovesEverythingButKeptCategories() {
        for ((name, harness) in fixtures) {
            for (options in optionSets) {
                val (output, report) = harness.strip(harness.fixture(name), options)
                harness.assertOnlyKeptCategoriesRemain("$name $options", output)
                assertTrue(name, report.removed.any { it.category == MetadataCategory.Location })
            }
        }
    }

    // Size-preserving: only meta box bytes and metadata item payloads may change; image data never does.
    @Test
    fun stripKeepsSizeAndImageData() {
        for ((name, harness) in fixtures) {
            for (options in optionSets) {
                val input = harness.fixture(name)
                val output = harness.strip(input, options).first
                assertEquals(name, input.size, output.size)

                val allowed = metadataSpans(input) + metaBox(input)
                for (index in input.indices) {
                    if (input[index] != output[index]) {
                        assertTrue("$name changed byte $index", allowed.any { index >= it.start && index < it.end })
                    }
                }
                for (span in imageSpans(input)) {
                    assertArrayEquals(name, slice(input, span), slice(output, span))
                }
            }
        }
    }

    @Test
    fun stripBlanksExifToEmptyTiff() {
        val output = heif.strip(heif.fixture("heif-metadata.heic")).first
        val exif = slice(output, itemSpans(output, "Exif").single())
        val expected = byteArrayOf(0, 0, 0, 6) + "Exif".toByteArray() + byteArrayOf(0, 0, 0x49, 0x49, 0x2A, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertArrayEquals(expected, exif.copyOf(expected.size))
        assertTrue(exif.drop(expected.size).all { it == 0.toByte() })
    }

    @Test
    fun stripBlanksXmpToEmptyDocument() {
        val output = heif.strip(heif.fixture("heif-metadata.heic")).first
        val xmp = String(slice(output, itemSpans(output, "mime").single()), Charsets.US_ASCII)
        val root = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>"
        assertTrue(xmp.startsWith(root))
        assertTrue(xmp.drop(root.length).all { it == ' ' })
    }

    @Test
    fun keepsOrRemovesIccProfileByOption() {
        val input = heif.fixture("heif-thumbnail-icc.heic")
        val before = propertyTypes(input)

        val (kept, keptReport) = heif.strip(input, StripOptions(keepColorProfile = true))
        assertEquals(before, propertyTypes(kept))
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        // The ICC `colr` becomes `free` in place, so every later property keeps its index.
        val (removed, removedReport) = heif.strip(input, StripOptions(keepColorProfile = false))
        val iccIndex = before.indexOf("colr")
        assertEquals(before.toMutableList().also { it[iccIndex] = "free" }, propertyTypes(removed))
        assertEquals(emptyList<MetadataCategory>(), removedReport.kept.map { it.category })
        assertTrue(removedReport.removed.any { it.category == MetadataCategory.ColorProfile })
    }

    @Test
    fun stripIsIdempotent() {
        for ((name, harness) in fixtures) {
            for (options in optionSets) {
                val once = harness.strip(harness.fixture(name), options).first
                assertArrayEquals(name, once, harness.strip(once, options).first)
            }
        }
    }

    @Test
    fun rejectsEveryTruncation() {
        for ((name, harness) in fixtures) {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, input.size)
        }
    }

    private fun meta(bytes: ByteArray): HeifMeta {
        val reader = BinaryReader(ByteArrayChannel(bytes))
        val boxReader = BoxReader(reader)
        return HeifMetaReader(reader, boxReader).read(boxReader.read(0, reader.size).single { it.type == "meta" })
    }

    private fun metaBox(bytes: ByteArray): FileSpan {
        val box = BoxReader(BinaryReader(ByteArrayChannel(bytes))).read(0, bytes.size.toLong()).single { it.type == "meta" }
        return FileSpan(box.start, box.end)
    }

    // The fixtures keep all item data in mdat, as single extents relative to the start of the file.
    private fun itemSpans(bytes: ByteArray,
                          type: String): List<FileSpan> {
        val meta = meta(bytes)
        return meta.items.filter { it.type == type }.map { item ->
            val location = meta.locations.getValue(item.id)
            val extent = location.extents.single()
            FileSpan(location.baseOffset + extent.offset, location.baseOffset + extent.offset + extent.length)
        }
    }

    private fun metadataSpans(bytes: ByteArray): List<FileSpan> = metadataItemTypes.flatMap { itemSpans(bytes, it) }

    private fun imageSpans(bytes: ByteArray): List<FileSpan> {
        return meta(bytes).items.map { it.type }.toSet().minus(metadataItemTypes).flatMap { itemSpans(bytes, it) }
    }

    private fun propertyTypes(bytes: ByteArray): List<String> = meta(bytes).properties.map { it.box.type }

    private fun slice(bytes: ByteArray,
                      span: FileSpan): ByteArray = bytes.copyOfRange(span.start.toInt(), span.end.toInt())
}
