package com.prateekmahendrakar.metadatawiper.metadata.webp

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
import java.nio.ByteOrder

class WebPHandlerTest {

    private val harness = HandlerHarness(WebPHandler())

    private val fixtureNames = listOf("webp-icc.webp", "webp-lossless-metadata.webp", "webp-metadata.webp", "webp-simple.webp")

    private val imageFourCcs = setOf("VP8 ", "VP8L", "ANMF", "ALPH")

    private companion object {
        const val ICC_FLAG = 0x20
        const val EXIF_FLAG = 0x08
        const val XMP_FLAG = 0x04
    }

    @Test
    fun inspectFindsMetadata() {
        val items = harness.inspect(harness.fixture("webp-metadata.webp")).items

        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        harness.assertHas(items, MetadataCategory.Device, "Make", "Fixture Camera Co")
        harness.assertHas(items, MetadataCategory.People, "Artist", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Orientation, "Orientation", "6")
        assertTrue(items.any { it.label == "XMP" })
        assertTrue(items.any { it.label == "EXIF block" })
    }

    @Test
    fun inspectFindsNothingInSimpleFile() {
        assertEquals(emptyList<MetadataItem>(), harness.inspect(harness.fixture("webp-simple.webp")).items)
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
    fun stripLeavesSimpleFileUnchanged() {
        val input = harness.fixture("webp-simple.webp")
        val (output, report) = harness.strip(input)
        assertArrayEquals(input, output)
        assertEquals(StripReport(emptyList(), emptyList(), emptyList()), report)
    }

    @Test
    fun stripWritesRiffSizeOfOutput() {
        for (name in fixtureNames) {
            val output = harness.strip(harness.fixture(name)).first
            assertEquals(name, output.size - 8, ByteBuffer.wrap(output, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        }
    }

    @Test
    fun stripReplacesExifWithMinimalOrientation() {
        val (output, report) = harness.strip(harness.fixture("webp-metadata.webp"))

        assertEquals(listOf("VP8X", "VP8 ", "EXIF"), chunks(output).map { it.fourCc })
        val exif = chunks(output).last()
        assertArrayEquals(MinimalExifWriter.tiff(6),
                          output.copyOfRange(exif.dataStart.toInt(), (exif.dataStart + exif.dataLength).toInt()))
        assertEquals(EXIF_FLAG, flags(output) and (EXIF_FLAG or XMP_FLAG))
        assertEquals(listOf(MetadataItem(MetadataCategory.Orientation, "Orientation", "6")), harness.inspect(output).items)
        assertEquals(listOf(MetadataCategory.Orientation), report.kept.map { it.category })
    }

    @Test
    fun stripClearsExifAndXmpFlagsWhenNothingKept() {
        val (output, report) = harness.strip(harness.fixture("webp-metadata.webp"), StripOptions(keepOrientation = false))
        assertEquals(listOf("VP8X", "VP8 "), chunks(output).map { it.fourCc })
        assertEquals(0, flags(output) and (EXIF_FLAG or XMP_FLAG))
        assertEquals(emptyList<MetadataItem>(), report.kept)
    }

    @Test
    fun keepsOrRemovesIccProfileByOption() {
        val input = harness.fixture("webp-icc.webp")
        assertEquals(listOf(MetadataCategory.ColorProfile), harness.inspect(input).items.map { it.category })

        val (kept, keptReport) = harness.strip(input, StripOptions(keepColorProfile = true))
        assertArrayEquals(input, kept)
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        val (removed, removedReport) = harness.strip(input, StripOptions(keepColorProfile = false))
        assertTrue(chunks(removed).none { it.fourCc == "ICCP" })
        assertEquals(0, flags(removed) and ICC_FLAG)
        assertEquals(listOf(MetadataCategory.ColorProfile), removedReport.removed.map { it.category })
    }

    @Test
    fun stripRemovesUnknownChunkAndTrailer() {
        val input = harness.fixture("webp-lossless-metadata.webp")
        val withExtra = withChunk(input, riffChunk("ABCD", "Jane's phone".toByteArray())) + ByteArray(16) { 0x55 }

        val items = harness.inspect(withExtra).items
        harness.assertHas(items, MetadataCategory.Other, "Unknown chunk", "ABCD")
        assertTrue(items.any { it.category == MetadataCategory.EmbeddedMedia && it.byteSize == 16L })
        assertArrayEquals(harness.strip(input).first, harness.strip(withExtra).first)
    }

    @Test
    fun rejectsFileWithoutImageChunkFirst() {
        val input = harness.fixture("webp-simple.webp")
        val image = chunks(input).single()
        val reordered = riffFile(riffChunk("ABCD", ByteArray(2)) + input.copyOfRange(image.start.toInt(), image.end.toInt()))
        assertThrows(MalformedFileException::class.java) { harness.inspect(reordered) }
    }

    @Test
    fun rejectsEveryTruncation() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, input.size)
        }
    }

    private fun chunks(webP: ByteArray): List<RiffChunk> = RiffChunkReader(BinaryReader(ByteArrayChannel(webP)), "WEBP").read()

    private fun flags(webP: ByteArray): Int = webP[chunks(webP).first().dataStart.toInt()].toInt() and 0xFF

    private fun imageData(webP: ByteArray): ByteArray {
        return chunks(webP).filter { it.fourCc in imageFourCcs }
                           .fold(ByteArray(0)) { all, chunk -> all + webP.copyOfRange(chunk.start.toInt(), chunk.end.toInt()) }
    }

    // Appends a chunk inside the RIFF data and fixes the RIFF size to match.
    private fun withChunk(webP: ByteArray,
                          chunk: ByteArray): ByteArray = riffFile(webP.copyOfRange(12, webP.size) + chunk)

    private fun riffFile(chunks: ByteArray): ByteArray {
        return "RIFF".toByteArray() + littleEndian(chunks.size + 4) + "WEBP".toByteArray() + chunks
    }

    private fun riffChunk(fourCc: String,
                          data: ByteArray): ByteArray {
        val padding = if (data.size % 2 == 1) ByteArray(1) else ByteArray(0)
        return fourCc.toByteArray() + littleEndian(data.size) + data + padding
    }

    private fun littleEndian(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
