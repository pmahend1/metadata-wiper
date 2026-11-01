package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.SampleTableReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoMediaHandlerTest {

    // region State

    private val harness = HandlerHarness(IsoMediaHandler(FileFormat.Mp4))

    private val fixtures = listOf("mp4-android.mp4", "mov-apple.mov", "3gp-metadata.3gp", "m4v-cover.m4v", "mp4-fragmented.mp4", "mp4-rotated.mp4")

    private val identifyingText = listOf("Jane", "Fixture", "FX-100", "Paris", "ISO6709", "48.858", "Lavc", "VideoHandler", "2026")

    // endregion State

    // region Methods

    @Test
    fun readsAndroidUserData() {
        val items = harness.inspect(harness.fixture("mp4-android.mp4")).items
        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        harness.assertHas(items, MetadataCategory.Device, "Make", "Fixture Camera Co")
        harness.assertHas(items, MetadataCategory.Device, "Model", "FX-100")
        harness.assertHas(items, MetadataCategory.DateTime, "DateTimeOriginal", "2026-09-12T14:30:00-0400")
        harness.assertHas(items, MetadataCategory.DateTime, "Creation time", "2026-09-12T12:30:00Z")
        harness.assertHas(items, MetadataCategory.DateTime, "Modification time", "2026-09-12T12:30:00Z")
        harness.assertHas(items, MetadataCategory.DateTime, "Track timestamps", "2")
        harness.assertHas(items, MetadataCategory.Software, "Compressor name", "Lavc libx264")
        assertTrue(items.any { it.label == "XMP" })
    }

    @Test
    fun readsAppleKeys() {
        val items = harness.inspect(harness.fixture("mov-apple.mov")).items
        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858217, 2.294500")
        harness.assertHas(items, MetadataCategory.Device, "Make", "Fixture Camera Co")
        harness.assertHas(items, MetadataCategory.Device, "Model", "FX-100")
        harness.assertHas(items, MetadataCategory.Software, "Software", "Fixture Firmware 1.0")
        harness.assertHas(items, MetadataCategory.Other, "ImageUniqueID", "FIXTURE-0042-UUID")
        assertTrue(items.any { it.category == MetadataCategory.DateTime && it.label == "DateTimeOriginal" })
    }

    @Test
    fun readsThreeGppBoxes() {
        val items = harness.inspect(harness.fixture("3gp-metadata.3gp")).items
        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858215, 2.294495")
        harness.assertHas(items, MetadataCategory.People, "Artist", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Other, "Title", "Fixture clip")
    }

    @Test
    fun readsCoverArtAndItemList() {
        val items = harness.inspect(harness.fixture("m4v-cover.m4v")).items
        assertTrue(items.any { it.category == MetadataCategory.Thumbnail && it.label == "Thumbnail" && it.byteSize > 0 })
        harness.assertHas(items, MetadataCategory.People, "Artist", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Other, "Title", "Fixture clip")
    }

    @Test
    fun readsFragmentedUserData() {
        val items = harness.inspect(harness.fixture("mp4-fragmented.mp4")).items
        assertTrue(items.any { it.label == "GPS" })
        assertTrue(items.any { it.label == "Title" })
        assertTrue(items.any { it.label == "Creation time" })
    }

    @Test
    fun removesEverythingAndLeavesOnlyBlankedBytesChanged() {
        for (name in fixtures) {
            val input = harness.fixture(name)
            val (output, report) = harness.strip(input)

            assertTrue(name, report.removed.isNotEmpty())
            assertEquals(name, input.size, output.size)
            harness.assertOnlyKeptCategoriesRemain(name, output)
            assertArrayEquals(name, output, harness.strip(output).first)
            val structure = withoutMediaData(output)
            for (text in identifyingText) {
                assertEquals("$name still holds $text", -1, structure.indexOf(text.toByteArray()))
            }
        }
    }

    @Test
    fun keepsSamplesByteForByte() {
        for (name in fixtures) {
            val input = harness.fixture(name)
            val output = harness.strip(input).first
            val mediaData = topLevel(input).filter { it.type == "mdat" }
            assertTrue(name, mediaData.isNotEmpty())
            for (box in mediaData) {
                assertArrayEquals(name, input.copyOfRange(box.start.toInt(), box.end.toInt()), output.copyOfRange(box.start.toInt(), box.end.toInt()))
            }
            assertEquals(name, chunkSpans(input), chunkSpans(output))
        }
    }

    @Test
    fun keepsRotationMatrixAndPlaybackStructure() {
        val input = harness.fixture("mp4-rotated.mp4")
        val output = harness.strip(input).first
        val kept = listOf("stsd", "elst", "stco", "stsz", "stsc", "stts", "stss")
        for (type in kept) {
            val before = find(input, type)
            val after = find(output, type)
            assertEquals(type, before.map { it.start }, after.map { it.start })
            assertEquals(type, before.map { it.end }, after.map { it.end })
        }

        // The matrix sits after the times, id, reserved, duration and audio fields of a version 0 `tkhd`.
        val matrixOffset = 40
        val matrixSize = 36
        val tkhd = find(input, "tkhd").first()
        val start = tkhd.dataStart.toInt() + matrixOffset
        val matrix = input.copyOfRange(start, start + matrixSize)
        assertArrayEquals(matrix, output.copyOfRange(start, start + matrixSize))
        assertEquals("Fixture must really be rotated", 0, matrix[0].toInt())
    }

    @Test
    fun rejectsTruncatedFilesWithoutOutput() {
        for (name in fixtures - "mp4-fragmented.mp4") {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, input.size)
        }
    }

    // Encoders can sign the media itself (ffmpeg's AAC does), which is the payload we promise to keep.
    private fun withoutMediaData(bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf()
        for (box in topLevel(bytes).filter { it.type == "mdat" }) {
            copy.fill(0, box.dataStart.toInt(), box.end.toInt())
        }
        return copy
    }

    private fun topLevel(bytes: ByteArray): List<Box> = BoxReader(BinaryReader(ByteArrayChannel(bytes))).readTopLevel().boxes

    private fun find(bytes: ByteArray,
                     type: String): List<Box> {
        val reader = BoxReader(BinaryReader(ByteArrayChannel(bytes)))
        val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")
        val found = mutableListOf<Box>()
        fun walk(boxes: List<Box>) {
            for (box in boxes) {
                if (box.type == type) {
                    found += box
                }
                if (box.type in containers) {
                    walk(reader.readChildren(box))
                }
            }
        }
        walk(topLevel(bytes))
        return found
    }

    private fun chunkSpans(bytes: ByteArray): List<Any> {
        val tables = SampleTableReader(BinaryReader(ByteArrayChannel(bytes)))
        return find(bytes, "stbl").map { tables.read(it).chunks }
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        return (0..size - pattern.size).firstOrNull { start -> pattern.indices.all { this[start + it] == pattern[it] } } ?: -1
    }

    // endregion Methods
}
