package com.prateekmahendrakar.metadatawiper.metadata.png

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.tiff.MinimalExifWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

class PngHandlerTest {

    private val harness = HandlerHarness(PngHandler())

    private val fixtureNames = listOf("png-animated.png", "png-clean.png", "png-icc.png", "png-metadata.png")

    // Everything a decoder or an APNG player reads pixels from; it must come through byte for byte.
    private val imageDataTypes = setOf(PngChunkType.IHDR, PngChunkType.IDAT, "fdAT")

    @Test
    fun inspectFindsMetadata() {
        val items = harness.inspect(harness.fixture("png-metadata.png")).items

        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        harness.assertHas(items, MetadataCategory.Device, "BodySerialNumber", "SN-0042-FIXTURE")
        harness.assertHas(items, MetadataCategory.People, "CameraOwnerName", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Orientation, "Orientation", "6")
        harness.assertHas(items, MetadataCategory.People, "Author", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Other, "Comment", "Fixture PNG comment")
        harness.assertHas(items, MetadataCategory.DateTime, "Modification time", "2026-09-12 14:30:00")
        assertTrue(items.any { it.label == "XMP" })
        assertTrue(items.any { it.label == "EXIF block" })
    }

    @Test
    fun inspectFindsNothingInCleanFile() {
        assertEquals(emptyList<MetadataItem>(), harness.inspect(harness.fixture("png-clean.png")).items)
    }

    @Test
    fun stripLeavesOnlyKeptCategoriesInEveryFixture() {
        for (name in fixtureNames) {
            harness.assertOnlyKeptCategoriesRemain(name, harness.strip(harness.fixture(name)).first)
        }
    }

    @Test
    fun stripKeepsImageDataByteForByte() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            assertArrayEquals(name, imageData(input), imageData(harness.strip(input).first))
        }
    }

    @Test
    fun stripIsIdempotent() {
        for (name in fixtureNames) {
            val once = harness.strip(harness.fixture(name)).first
            assertArrayEquals(name, once, harness.strip(once).first)
        }
    }

    @Test
    fun stripLeavesCleanFileUnchanged() {
        val input = harness.fixture("png-clean.png")
        val (output, report) = harness.strip(input)
        assertArrayEquals(input, output)
        assertEquals(StripReport(emptyList(), emptyList(), emptyList()), report)
    }

    @Test
    fun stripWritesMinimalExifAfterHeader() {
        val (output, report) = harness.strip(harness.fixture("png-metadata.png"))

        val chunks = chunks(output)
        assertEquals(listOf(PngChunkType.IHDR, PngChunkType.EXIF, PngChunkType.IDAT, PngChunkType.IEND), chunks.map { it.type })
        assertArrayEquals(pngChunk(PngChunkType.EXIF, MinimalExifWriter.tiff(6)), bytesOf(output, chunks[1]))
        assertEquals(listOf(MetadataItem(MetadataCategory.Orientation, "Orientation", "6")), harness.inspect(output).items)
        assertEquals(listOf(MetadataCategory.Orientation), report.kept.map { it.category })
    }

    @Test
    fun stripDropsExifWhenOrientationNotKept() {
        val (output, report) = harness.strip(harness.fixture("png-metadata.png"), StripOptions(keepOrientation = false))
        assertArrayEquals(harness.fixture("png-clean.png"), output)
        assertTrue(report.removed.any { it.category == MetadataCategory.Orientation })
        assertEquals(emptyList<MetadataItem>(), report.kept)
    }

    // png-icc.png is png-clean.png with an iCCP chunk added, so dropping it must give the clean file back.
    @Test
    fun keepsOrRemovesIccProfileByOption() {
        val input = harness.fixture("png-icc.png")
        assertEquals(listOf(MetadataCategory.ColorProfile), harness.inspect(input).items.map { it.category })

        val (kept, keptReport) = harness.strip(input, StripOptions(keepColorProfile = true))
        assertArrayEquals(input, kept)
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        val (removed, removedReport) = harness.strip(input, StripOptions(keepColorProfile = false))
        assertArrayEquals(harness.fixture("png-clean.png"), removed)
        assertEquals(listOf(MetadataCategory.ColorProfile), removedReport.removed.map { it.category })
    }

    @Test
    fun stripKeepsAnimationChunks() {
        val input = harness.fixture("png-animated.png")
        val types = harness.strip(input).first.let { output -> chunks(output).map { it.type } }
        assertEquals(listOf("IHDR", "acTL", "fcTL", "IDAT", "fcTL", "fdAT", "IEND"), types)
    }

    @Test
    fun stripRemovesUnknownAncillaryChunkAndTrailer() {
        val clean = harness.fixture("png-clean.png")
        val input = withChunks(clean, pngChunk("prVt", "Jane's laptop".toByteArray())) + ByteArray(32) { 0x55 }

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.Other, "Ancillary chunk", "prVt")
        assertTrue(items.any { it.category == MetadataCategory.EmbeddedMedia && it.byteSize == 32L })
        assertArrayEquals(clean, harness.strip(input).first)
    }

    @Test
    fun inspectReadsInternationalText() {
        val text = "Title".toByteArray() + bytes(0, 0, 0) + "en".toByteArray() + bytes(0) + "Titel".toByteArray() +
            bytes(0) + "Café".toByteArray(Charsets.UTF_8)
        val input = withChunks(harness.fixture("png-clean.png"), pngChunk(PngChunkType.INTERNATIONAL_TEXT, text))
        harness.assertHas(harness.inspect(input).items, MetadataCategory.Other, "Title", "Café")
    }

    @Test
    fun rejectsUnknownCriticalChunk() {
        val input = withChunks(harness.fixture("png-clean.png"), pngChunk("ABCD", ByteArray(4)))
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
    }

    @Test
    fun rejectsEveryTruncation() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, chunks(input).last().end.toInt())
        }
    }

    private fun chunks(png: ByteArray): List<PngChunk> = PngChunkReader(BinaryReader(ByteArrayChannel(png))).read()

    private fun imageData(png: ByteArray): ByteArray {
        return chunks(png).filter { it.type in imageDataTypes }
                          .fold(ByteArray(0)) { all, chunk -> all + bytesOf(png, chunk) }
    }

    private fun bytesOf(png: ByteArray,
                        chunk: PngChunk): ByteArray = png.copyOfRange(chunk.start.toInt(), chunk.end.toInt())

    // Inserts chunks straight after IHDR, so the base image still decodes.
    private fun withChunks(base: ByteArray,
                           vararg extra: ByteArray): ByteArray {
        val headerEnd = chunks(base).first().end.toInt()
        return base.copyOfRange(0, headerEnd) + extra.fold(ByteArray(0)) { all, next -> all + next } +
            base.copyOfRange(headerEnd, base.size)
    }

    private fun pngChunk(type: String,
                         data: ByteArray): ByteArray {
        val typeAndData = type.toByteArray(Charsets.US_ASCII) + data
        val crc = CRC32().apply { update(typeAndData) }.value.toInt()
        return ByteBuffer.allocate(4).putInt(data.size).array() + typeAndData + ByteBuffer.allocate(4).putInt(crc).array()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
