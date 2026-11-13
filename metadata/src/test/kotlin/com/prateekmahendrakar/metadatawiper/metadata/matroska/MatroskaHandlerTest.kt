package com.prateekmahendrakar.metadatawiper.metadata.matroska

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlElement
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlId
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class MatroskaHandlerTest {

    // region State

    private val harness = HandlerHarness(MatroskaHandler(FileFormat.Matroska))

    private val fixtures = listOf("mkv-tags.mkv", "webm-browser.webm")

    private val identifyingText = listOf("Jane", "Fixture clip", "48.8582", "Lavf", "Lavc", "cover.jpg", "fixture.ttf", "DATE_RECORDED", "LOCATION")

    // Elements the strip descends into, so the tests can check what's inside them.
    private val masters = setOf(EbmlId.SEGMENT, EbmlId.SEEK_HEAD, EbmlId.SEEK, EbmlId.INFO, EbmlId.TRACKS, EbmlId.TRACK_ENTRY,
                                EbmlId.CLUSTER, EbmlId.CUES, EbmlId.ATTACHMENTS, EbmlId.ATTACHED_FILE, EbmlId.TAGS)

    // endregion State

    // region Methods

    @Test
    fun readsMatroskaMetadata() {
        val result = harness.inspect(harness.fixture("mkv-tags.mkv"))
        val items = result.items
        harness.assertHas(items, MetadataCategory.Other, "Title", "Fixture clip")
        harness.assertHas(items, MetadataCategory.DateTime, "Creation time", "2026-09-12T12:30:00Z")
        harness.assertHas(items, MetadataCategory.Software, "Software", "Lavf63.1.102")
        harness.assertHas(items, MetadataCategory.Other, "Track name", "Jane Fixture camera")
        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858200, 2.294500")
        harness.assertHas(items, MetadataCategory.DateTime, "DateTimeOriginal", "2026-09-12")
        harness.assertHas(items, MetadataCategory.People, "Artist", "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Software, "Software", "Lavc63.1.102 libx264")
        harness.assertHas(items, MetadataCategory.Other, "File name", "fixture.ttf")
        assertTrue(items.any { it.category == MetadataCategory.Thumbnail && it.detail == "cover.jpg" && it.byteSize > 228 })
        assertTrue(items.any { it.label == "Segment ID" })
        assertFalse("Stream statistics aren't listed", items.any { it.label == "DURATION" })
        assertTrue(MetadataWarning(MetadataWarningKind.FontAttachmentsKept) in result.warnings)
    }

    @Test
    fun readsWebMMetadata() {
        val items = MatroskaHandler(FileFormat.WebM).inspect(ByteArrayChannel(harness.fixture("webm-browser.webm"))).items
        harness.assertHas(items, MetadataCategory.Other, "Title", "Fixture clip")
        harness.assertHas(items, MetadataCategory.Software, "Software", "Lavf63.1.102")
        harness.assertHas(items, MetadataCategory.Other, "Track name", "Jane Fixture camera")
        harness.assertHas(items, MetadataCategory.Location, "GPS", "48.858200, 2.294500")
    }

    @Test
    fun removesEverythingAndKeepsTheSize() {
        for (name in fixtures) {
            val input = harness.fixture(name)
            val (output, report) = harness.strip(input)

            assertTrue(name, report.removed.isNotEmpty())
            assertEquals(name, input.size, output.size)
            harness.assertOnlyKeptCategoriesRemain(name, output)
            assertArrayEquals(name, output, harness.strip(output).first)
            val structure = withoutClusters(output)
            for (text in identifyingText) {
                assertEquals("$name still holds $text", -1, indexOf(structure, text.toByteArray()))
            }
        }
    }

    @Test
    fun keepsMediaAndPlaybackStructureByteForByte() {
        for (name in fixtures) {
            val input = harness.fixture(name)
            val output = harness.strip(input).first
            val kept = segmentChildren(input).filter { it.id == EbmlId.CLUSTER || it.id == EbmlId.CUES }
            assertTrue(name, kept.any { it.id == EbmlId.CLUSTER })
            for (element in kept) {
                assertArrayEquals(name, slice(input, element), slice(output, element))
            }
            assertEquals(name, segmentChildren(input).map { it.start to it.end }, segmentChildren(output).map { it.start to it.end })

            val codecPrivate = all(input).filter { it.id == 0x63A2L }
            assertTrue(name, codecPrivate.isNotEmpty())
            for (element in codecPrivate) {
                assertArrayEquals(name, slice(input, element), slice(output, element))
            }
        }
    }

    @Test
    fun keepsFontsButNotTheirNames() {
        val output = harness.strip(harness.fixture("mkv-tags.mkv")).first
        assertTrue(indexOf(output, "Not a real font".toByteArray()) >= 0)
        assertTrue(indexOf(output, "application/x-truetype-font".toByteArray()) >= 0)
        assertEquals(-1, indexOf(output, "image/jpeg".toByteArray()))
    }

    @Test
    fun recomputesEveryCrc() {
        val input = harness.fixture("mkv-tags.mkv")
        val output = harness.strip(input).first
        val owners = all(input).filter { firstChildIsCrc(input, it) }
        assertTrue(owners.any { it.id == EbmlId.INFO })
        assertTrue(owners.all { crcMatches(input, it) })

        val outputOwners = all(output).filter { firstChildIsCrc(output, it) }
        assertTrue(outputOwners.map { it.id }.containsAll(listOf(EbmlId.SEEK_HEAD, EbmlId.INFO, EbmlId.TRACKS, EbmlId.ATTACHMENTS)))
        for (owner in outputOwners) {
            assertTrue("CRC of ${owner.id.toString(16)} at ${owner.start}", crcMatches(output, owner))
        }
    }

    @Test
    fun voidsSeekEntriesForVoidedElements() {
        val output = harness.strip(harness.fixture("mkv-tags.mkv")).first
        val reader = reader(output)
        val segment = segment(output)
        val seekHead = reader.children(segment).first { it.id == EbmlId.SEEK_HEAD }
        val targets = reader.children(seekHead).filter { it.id == EbmlId.SEEK }.map { seek ->
            segment.dataStart + reader.readUnsigned(reader.children(seek).first { it.id == EbmlId.SEEK_POSITION })
        }

        // Attachments stay because they hold a font; Tags are voided, and so is their entry.
        val targetIds = targets.map { target -> reader.children(segment).first { it.start == target }.id }
        assertEquals(listOf(EbmlId.INFO, EbmlId.TRACKS, EbmlId.ATTACHMENTS, EbmlId.CUES), targetIds)
    }

    @Test
    fun rejectsEveryTruncation() {
        val input = harness.fixture("webm-browser.webm")
        harness.assertRejectsEveryTruncation("webm-browser.webm", input, input.size)
    }

    private fun firstChildIsCrc(bytes: ByteArray,
                                element: EbmlElement): Boolean {
        return element.id in masters && reader(bytes).children(element).firstOrNull()?.id == EbmlId.CRC32
    }

    private fun crcMatches(bytes: ByteArray,
                           owner: EbmlElement): Boolean {
        val crc = reader(bytes).children(owner).first()
        val stored = slice(bytes, crc).let { it.copyOfRange(it.size - 4, it.size) }
        val value = CRC32().apply { update(bytes, crc.end.toInt(), (owner.end - crc.end).toInt()) }.value
        return stored.contentEquals(ByteArray(4) { (value shr (8 * it)).toByte() })
    }

    private fun all(bytes: ByteArray): List<EbmlElement> {
        val reader = reader(bytes)
        val found = mutableListOf<EbmlElement>()
        fun walk(element: EbmlElement) {
            found += element
            if (element.id in masters) {
                reader.children(element).forEach { walk(it) }
            }
        }
        walk(segment(bytes))
        return found
    }

    // Encoders write their name into the audio and video streams too, which this handler doesn't parse.
    private fun withoutClusters(bytes: ByteArray): ByteArray {
        val clusters = segmentChildren(bytes).filter { it.id == EbmlId.CLUSTER }
        return bytes.copyOf().also { copy -> clusters.forEach { copy.fill(0, it.start.toInt(), it.end.toInt()) } }
    }

    private fun segmentChildren(bytes: ByteArray): List<EbmlElement> = reader(bytes).children(segment(bytes))

    private fun segment(bytes: ByteArray): EbmlElement {
        val reader = reader(bytes)
        val header = reader.readElement(0, bytes.size.toLong(), 0)
        return reader.readElement(header.end, bytes.size.toLong(), 0)
    }

    private fun reader(bytes: ByteArray): EbmlReader = EbmlReader(BinaryReader(ByteArrayChannel(bytes)))

    private fun slice(bytes: ByteArray,
                      element: EbmlElement): ByteArray = bytes.copyOfRange(element.start.toInt(), element.end.toInt())

    private fun indexOf(bytes: ByteArray,
                        target: ByteArray): Int {
        return (0..bytes.size - target.size).firstOrNull { start -> target.indices.all { bytes[start + it] == target[it] } } ?: -1
    }

    // endregion Methods
}
