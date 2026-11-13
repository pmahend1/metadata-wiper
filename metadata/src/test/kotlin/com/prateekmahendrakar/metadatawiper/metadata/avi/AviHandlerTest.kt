package com.prateekmahendrakar.metadatawiper.metadata.avi

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunk
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunkReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AviHandlerTest {

    // region State

    private val harness = HandlerHarness(AviHandler())

    private val fixture = "avi-info.avi"

    private val identifyingText = listOf("Jane", "Fixture clip", "Fixture comment", "Lavf", "2026-09-12")

    // endregion State

    // region Methods

    @Test
    fun readsInfoMetadata() {
        val result = harness.inspect(harness.fixture(fixture))
        val items = result.items
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.TITLE, "Fixture clip")
        harness.assertHas(items, MetadataCategory.People, MetadataLabel.ARTIST, "Jane Fixture")
        harness.assertHas(items, MetadataCategory.People, MetadataLabel.COPYRIGHT, "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.COMMENT, "Fixture comment")
        harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.CREATION_TIME, "2026-09-12")
        harness.assertHas(items, MetadataCategory.Software, MetadataLabel.SOFTWARE, "Lavf63.1.102")
        assertEquals(6, items.size)
        assertEquals(emptyList<Any>(), result.warnings)
    }

    @Test
    fun removesEverythingAndKeepsTheSize() {
        val input = harness.fixture(fixture)
        val (output, report) = harness.strip(input)

        assertEquals(6, report.removed.size)
        assertEquals(input.size, output.size)
        harness.assertOnlyKeptCategoriesRemain(fixture, output)
        assertArrayEquals(output, harness.strip(output).first)
        val headers = withoutMovie(output)
        for (text in identifyingText) {
            assertEquals("Still holds $text", -1, indexOf(headers, text.toByteArray()))
        }
    }

    @Test
    fun renamesTheInfoListToJunk() {
        val input = harness.fixture(fixture)
        val output = harness.strip(input).first
        val info = topLevel(input).first { it.fourCc == "LIST" && fourCc(input, it.dataStart) == "INFO" }
        val blanked = topLevel(output).first { it.start == info.start }
        assertEquals("JUNK", blanked.fourCc)
        assertEquals(info.dataLength, blanked.dataLength)
        assertTrue(output.copyOfRange(blanked.dataStart.toInt(), blanked.end.toInt()).all { it == 0.toByte() })
    }

    @Test
    fun keepsMediaHeadersAndIndexByteForByte() {
        val input = harness.fixture(fixture)
        val output = harness.strip(input).first
        assertEquals(topLevel(input).map { it.start to it.end }, topLevel(output).map { it.start to it.end })

        val kept = topLevel(input).filter { it.fourCc == "idx1" || fourCc(input, it.dataStart) in setOf("hdrl", "movi") }
        assertEquals(3, kept.size)
        for (chunk in kept) {
            assertArrayEquals(slice(input, chunk), slice(output, chunk))
        }
    }

    // ffmpeg reserves JUNK for an OpenDML index it fills in later; empty, it holds nothing worth reporting or rewriting.
    @Test
    fun leavesEmptyIndexPlaceholdersAlone() {
        val input = harness.fixture(fixture)
        val output = harness.strip(input).first
        assertTrue(harness.inspect(input).items.none { it.label == MetadataLabel.LEFTOVER_DATA })
        assertEquals("odmldmlh", String(input, 8652, 8, Charsets.US_ASCII))
        assertEquals("odmldmlh", String(output, 8652, 8, Charsets.US_ASCII))
    }

    @Test
    fun rejectsEveryTruncation() {
        val input = harness.fixture(fixture)
        harness.assertRejectsEveryTruncation(fixture, input, input.size)
    }

    private fun topLevel(bytes: ByteArray): List<RiffChunk> = RiffChunkReader(BinaryReader(ByteArrayChannel(bytes))).read("AVI ")

    // Encoders write their name into the video frames too, which this handler doesn't parse.
    private fun withoutMovie(bytes: ByteArray): ByteArray {
        val movie = topLevel(bytes).first { it.fourCc == "LIST" && fourCc(bytes, it.dataStart) == "movi" }
        return bytes.copyOf().also { it.fill(0, movie.start.toInt(), movie.end.toInt()) }
    }

    private fun fourCc(bytes: ByteArray,
                       offset: Long): String = String(bytes, offset.toInt(), 4, Charsets.US_ASCII)

    private fun slice(bytes: ByteArray,
                      chunk: RiffChunk): ByteArray = bytes.copyOfRange(chunk.start.toInt(), chunk.end.toInt())

    private fun indexOf(bytes: ByteArray,
                        target: ByteArray): Int {
        return (0..bytes.size - target.size).firstOrNull { start -> target.indices.all { bytes[start + it] == target[it] } } ?: -1
    }

    // endregion Methods
}
