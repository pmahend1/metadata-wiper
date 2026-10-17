package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.ascii
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.cString
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u16
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u64
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifBlockInspector
import com.prateekmahendrakar.metadatawiper.metadata.tiff.TiffBuilder
import com.prateekmahendrakar.metadatawiper.metadata.tiff.TiffTag
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteOrder

class HeifSyntheticFileTest {

    private val harness = HandlerHarness(HeifHandler(FileFormat.Heif))

    // Larger than any metadata payload here, so a metadata extent pointed at it lies wholly inside it.
    private val image = ByteArray(256) { (it * 7).toByte() }

    private val exif = u32(6) + "Exif".toByteArray() + ByteArray(2) +
                       TiffBuilder(ByteOrder.BIG_ENDIAN).let { it.build(listOf(it.ascii(TiffTag.MAKE, "Synthetic Co"))) }

    private val xmp = "<x:xmpmeta xmlns:x='adobe:ns:meta/'><rdf:RDF><dc:creator>Jane</dc:creator></rdf:RDF></x:xmpmeta>".toByteArray()

    private fun imageItem(id: Int = 1,
                          name: String = ""): HeifTestItem = HeifTestItem(id, "hvc1", image, name)

    private fun xmpItem(id: Int,
                        extents: Int = 1): HeifTestItem {
        return HeifTestItem(id, "mime", xmp, contentType = "application/rdf+xml", extents = extents)
    }

    @Test
    fun blanksMetadataInIdatAndSplitExtents() {
        val builder = HeifBuilder(listOf(imageItem(name = "Jane's camera roll"),
                                         HeifTestItem(2, "Exif", exif, constructionMethod = 1),
                                         xmpItem(3, extents = 2)))
        builder.ilocVersion = 2
        builder.fieldSize = 8
        val input = builder.build()

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.Device, "Make", "Synthetic Co")
        harness.assertHas(items, MetadataCategory.Other, "XMP")
        harness.assertHas(items, MetadataCategory.Other, "Item names", "1")

        val output = harness.strip(input).first
        harness.assertOnlyKeptCategoriesRemain("idat", output)
        assertEquals(input.size, output.size)
        assertTrue(output.indexOf(image) >= 0)
        assertEquals(-1, output.indexOf("Jane".toByteArray()))
        assertEquals(-1, output.indexOf("Synthetic".toByteArray()))
    }

    @Test
    fun turnsUnblankableBoxesIntoFreeBoxes() {
        val builder = HeifBuilder(listOf(imageItem()))
        builder.extraMetaBoxes = listOf(box("xml ", "<doc>Jane</doc>".toByteArray()))
        builder.trailingBoxes = listOf(box("mpvd", ByteArray(32) { 1 }), box("uuid", ByteArray(16) { 2 } + "Jane".toByteArray()))
        val input = builder.build()

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.EmbeddedMedia, "Motion photo video")
        harness.assertHas(items, MetadataCategory.Other, "Unknown box", "uuid")
        harness.assertHas(items, MetadataCategory.Other, "XML metadata")

        val output = harness.strip(input).first
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf("Jane".toByteArray()))
        val topLevel = boxes(output)
        assertEquals(listOf("ftyp", "meta", "mdat", "free", "free"), topLevel.map { it.type })
        for (box in topLevel.takeLast(2)) {
            assertTrue(output.copyOfRange(box.headerEnd.toInt(), box.end.toInt()).all { it == 0.toByte() })
        }
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
    }

    @Test
    fun warnsAboutWhatItKeeps() {
        val builder = HeifBuilder(listOf(imageItem(1),
                                         imageItem(2),
                                         imageItem(3),
                                         imageItem(4),
                                         HeifTestItem(5, "abcd", ByteArray(4))))
        builder.properties = listOf(fullBox("auxC", 0, 0, cString("urn:com:apple:photo:2020:aux:hdrgainmap")),
                                    fullBox("auxC", 0, 0, cString("urn:mpeg:mpegB:cicp:systems:auxiliary:alpha")),
                                    box("zzzz", ByteArray(4)))
        builder.associations = listOf(Triple(3, 1, false), Triple(4, 2, true), Triple(1, 3, false))
        builder.references = listOf(box("thmb", u16(2), u16(1), u16(1)),
                                    box("auxl", u16(3), u16(1), u16(1)),
                                    box("auxl", u16(4), u16(1), u16(1)))
        builder.extraMetaBoxes = listOf(box("fooo", ByteArray(2)))
        builder.trailingBoxes = listOf(box("moov", ByteArray(8)))
        val input = builder.build()

        val result = harness.inspect(input)
        assertEquals(emptyList<MetadataItem>(), result.items)
        assertEquals(listOf(HeifStripPlanner.SEQUENCE_WARNING,
                            "Boxes this app doesn't recognise (fooo) were left in place.",
                            "Items of a type this app doesn't recognise (abcd) were left in place.",
                            "Image properties this app doesn't recognise (zzzz) were left in place.",
                            HeifStripPlanner.THUMBNAIL_WARNING,
                            HeifStripPlanner.AUXILIARY_WARNING),
                     result.warnings)
        assertArrayEquals(input, harness.strip(input).first)
    }

    @Test
    fun clearsEssentialFlagWhenRemovingIcc() {
        val builder = HeifBuilder(listOf(imageItem()))
        builder.properties = listOf(box("colr", ascii("prof"), ByteArray(32) { 3 }), box("ispe", ByteArray(12)))
        builder.associations = listOf(Triple(1, 1, true), Triple(1, 2, false))
        val input = builder.build()

        assertArrayEquals(input, harness.strip(input, StripOptions(keepColorProfile = true)).first)

        val output = harness.strip(input, StripOptions(keepColorProfile = false)).first
        val meta = meta(output)
        assertEquals(listOf("free", "ispe"), meta.properties.map { it.box.type })
        assertEquals(listOf(1 to false, 2 to false), meta.associations.map { it.propertyIndex to it.isEssential })
    }

    @Test
    fun blanksDescriptiveProperties() {
        val builder = HeifBuilder(listOf(imageItem()))
        builder.properties = listOf(fullBox("crtt", 0, 0, u64(3_000_000_000)),
                                    fullBox("udes", 0, 0, cString("en"), cString("Jane"), cString("Birthday"), cString("party")))
        builder.associations = listOf(Triple(1, 1, false), Triple(1, 2, false))
        val input = builder.build()

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.DateTime, "Creation time")
        harness.assertHas(items, MetadataCategory.Other, "Image description")

        val output = harness.strip(input).first
        harness.assertOnlyKeptCategoriesRemain("descriptive", output)
        assertEquals(-1, output.indexOf("Jane".toByteArray()))
        assertEquals(listOf("crtt", "udes"), meta(output).properties.map { it.box.type })
    }

    @Test
    fun blanksRegionAnnotationsAndOtherMimeData() {
        val regions = u32(0) + u16(64) + u16(48) + byteArrayOf(1) + u16(1) + byteArrayOf(0) + u16(10) + u16(10)
        val builder = HeifBuilder(listOf(imageItem(),
                                         HeifTestItem(2, "rgan", regions),
                                         HeifTestItem(3, "mime", "Jane".toByteArray(), contentType = "text/plain")))
        val input = builder.build()

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.People, "Region annotations")
        harness.assertHas(items, MetadataCategory.Other, "Embedded data", "text/plain")

        val output = harness.strip(input).first
        harness.assertOnlyKeptCategoriesRemain("regions", output)
        assertEquals(-1, output.indexOf("Jane".toByteArray()))
    }

    @Test
    fun reportsDamagedExif() {
        val damaged = u32(1000) + ByteArray(10) { 9 }
        val input = HeifBuilder(listOf(imageItem(), HeifTestItem(2, "Exif", damaged))).build()

        val result = harness.inspect(input)
        harness.assertHas(result.items, MetadataCategory.Other, "EXIF block")
        assertEquals(listOf(ExifBlockInspector.DAMAGED_WARNING), result.warnings)
        harness.assertOnlyKeptCategoriesRemain("damaged", harness.strip(input).first)
    }

    @Test
    fun readsLargeMediaDataBox() {
        val builder = HeifBuilder(listOf(imageItem(), HeifTestItem(2, "Exif", exif)))
        builder.largeMediaData = true
        val input = builder.build()

        harness.assertHas(harness.inspect(input).items, MetadataCategory.Device, "Make", "Synthetic Co")
        val output = harness.strip(input).first
        harness.assertOnlyKeptCategoriesRemain("large", output)
        assertEquals(input.size, output.size)
    }

    @Test
    fun rejectsMetadataItCantBlankSafely() {
        val cases = mapOf("construction method 2" to HeifTestItem(2, "Exif", exif, constructionMethod = 2),
                          "external data" to HeifTestItem(2, "Exif", exif, dataReferenceIndex = 1),
                          "outside media data" to HeifTestItem(2, "Exif", exif, mediaOffset = -100),
                          "overlapping image" to HeifTestItem(2, "Exif", exif, mediaOffset = 0))
        for ((name, item) in cases) {
            val input = HeifBuilder(listOf(imageItem(), item)).build()
            assertThrows(name, MalformedFileException::class.java) { harness.inspect(input) }
        }
    }

    @Test
    fun rejectsFilesWithoutOneMetaBox() {
        val ftyp = box("ftyp", ascii("heic"), u32(0), ascii("mif1"))
        val valid = HeifBuilder(listOf(imageItem())).build()
        val metaBox = boxes(valid).single { it.type == "meta" }
        val metaBytes = valid.copyOfRange(metaBox.start.toInt(), metaBox.end.toInt())

        val cases = mapOf("no meta" to ftyp + box("mdat", image),
                          "two metas" to valid + metaBytes,
                          "no ftyp" to box("free", ByteArray(4)) + valid)
        for ((name, input) in cases) {
            assertThrows(name, MalformedFileException::class.java) { harness.inspect(input) }
        }

        val compact = assertThrows(MalformedFileException::class.java) { harness.inspect(ftyp + box("mini", ByteArray(8))) }
        assertTrue(compact.message!!.contains("'mini'"))
    }

    private fun boxes(bytes: ByteArray): List<Box> = BoxReader(BinaryReader(ByteArrayChannel(bytes))).read(0, bytes.size.toLong())

    private fun meta(bytes: ByteArray): HeifMeta {
        val reader = BinaryReader(ByteArrayChannel(bytes))
        val boxReader = BoxReader(reader)
        return HeifMetaReader(reader, boxReader).read(boxes(bytes).single { it.type == "meta" })
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        return (0..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1
    }
}
