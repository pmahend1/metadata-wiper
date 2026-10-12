package com.prateekmahendrakar.metadatawiper.metadata.gif

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GifHandlerTest {

    private val harness = HandlerHarness(GifHandler())

    private val fixtureNames = listOf("gif-animated.gif", "gif-icc.gif", "gif-metadata.gif")

    @Test
    fun inspectFindsMetadata() {
        val items = harness.inspect(harness.fixture("gif-metadata.gif")).items
        harness.assertHas(items, MetadataCategory.Other, "Comment", "Fixture GIF comment")
        assertTrue(items.any { it.label == "XMP" })
    }

    @Test
    fun stripLeavesOnlyKeptCategoriesInEveryFixture() {
        for (name in fixtureNames) {
            harness.assertOnlyKeptCategoriesRemain(name, harness.strip(harness.fixture(name)).first)
        }
    }

    @Test
    fun stripKeepsFramesByteForByte() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            assertArrayEquals(name, frames(input), frames(harness.strip(input).first))
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
    fun stripKeepsLoopingAndFrameTiming() {
        val input = harness.fixture("gif-animated.gif")
        val output = harness.strip(input).first

        assertTrue(blocks(output).any { it.applicationId == "NETSCAPE2.0" })
        assertEquals(blocks(input).count { it.kind == GifBlockKind.GraphicControl },
                     blocks(output).count { it.kind == GifBlockKind.GraphicControl })
        assertEquals(3, blocks(output).count { it.kind == GifBlockKind.Image })
        assertEquals(listOf("Comment"), harness.strip(input).second.removed.map { it.label })
    }

    @Test
    fun keepsOrRemovesIccProfileByOption() {
        val input = harness.fixture("gif-icc.gif")
        assertEquals(listOf(MetadataCategory.ColorProfile), harness.inspect(input).items.map { it.category })

        val (kept, keptReport) = harness.strip(input, StripOptions(keepColorProfile = true))
        assertArrayEquals(input, kept)
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        val (removed, removedReport) = harness.strip(input, StripOptions(keepColorProfile = false))
        assertTrue(blocks(removed).none { it.kind == GifBlockKind.Application })
        assertEquals(input.size - blocks(input).single { it.kind == GifBlockKind.Application }.let { it.end - it.start },
                     removed.size.toLong())
        assertEquals(listOf(MetadataCategory.ColorProfile), removedReport.removed.map { it.category })
    }

    @Test
    fun stripRemovesUnknownExtensionsAndTrailer() {
        val clean = harness.strip(harness.fixture("gif-metadata.gif")).first
        val vendor = bytes(0x21, 0xFF, 11) + "VENDORID1.0".toByteArray() + bytes(3, 1, 2, 3, 0)
        val unknownLabel = bytes(0x21, 0x99, 2, 7, 7, 0)
        val input = withBlocks(clean, vendor, unknownLabel) + ByteArray(8) { 0x55 }

        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.Other, "Application extension", "VENDORID1.0")
        harness.assertHas(items, MetadataCategory.Other, "Extension", "0x99")
        assertTrue(items.any { it.category == MetadataCategory.EmbeddedMedia && it.byteSize == 8L })
        assertArrayEquals(clean, harness.strip(input).first)
    }

    @Test
    fun inspectFindsNothingInStrippedFile() {
        val output = harness.strip(harness.fixture("gif-metadata.gif")).first
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
    }

    @Test
    fun rejectsUnknownBlock() {
        val input = withBlocks(harness.fixture("gif-metadata.gif"), bytes(0x42))
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
    }

    @Test
    fun rejectsEveryTruncation() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, blocks(input).last().end.toInt())
        }
    }

    private fun blocks(gif: ByteArray): List<GifBlock> = GifBlockReader(BinaryReader(ByteArrayChannel(gif))).read()

    private fun frames(gif: ByteArray): ByteArray {
        return blocks(gif).filter { it.kind == GifBlockKind.Image }
                          .fold(ByteArray(0)) { all, block -> all + gif.copyOfRange(block.start.toInt(), block.end.toInt()) }
    }

    // Inserts blocks straight after the header, so the image still decodes.
    private fun withBlocks(base: ByteArray,
                           vararg extra: ByteArray): ByteArray {
        val headerEnd = blocks(base).first().end.toInt()
        return base.copyOfRange(0, headerEnd) + extra.fold(ByteArray(0)) { all, next -> all + next } +
            base.copyOfRange(headerEnd, base.size)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
