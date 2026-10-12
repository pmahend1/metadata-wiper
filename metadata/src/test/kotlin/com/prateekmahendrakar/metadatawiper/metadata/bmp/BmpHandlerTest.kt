package com.prateekmahendrakar.metadatawiper.metadata.bmp

import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BmpHandlerTest {

    private val harness = HandlerHarness(BmpHandler())

    private val fixtureNames = listOf("bmp-linked-profile.bmp", "bmp-plain.bmp")

    // Offsets into bmp-linked-profile.bmp, which the fixture script builds by hand.
    private companion object {
        const val CS_TYPE_POSITION = 70
        const val PROFILE_DATA_POSITION = 126
        const val PROFILE_START = 154
        const val PROFILE_LENGTH = 42
        const val LCS_SRGB = 0x73524742
        const val PROFILE_EMBEDDED = 0x4D424544
    }

    @Test
    fun inspectFindsLinkedProfilePath() {
        val items = harness.inspect(harness.fixture("bmp-linked-profile.bmp")).items
        assertEquals(listOf(MetadataItem(MetadataCategory.People,
                                         "Linked color profile path",
                                         "C:\\Users\\JaneFixture\\Profiles\\fixture.icc",
                                         PROFILE_LENGTH.toLong())),
                     items)
    }

    @Test
    fun inspectFindsNothingInPlainFile() {
        assertEquals(emptyList<MetadataItem>(), harness.inspect(harness.fixture("bmp-plain.bmp")).items)
    }

    @Test
    fun stripLeavesOnlyKeptCategoriesInEveryFixture() {
        for (name in fixtureNames) {
            harness.assertOnlyKeptCategoriesRemain(name, harness.strip(harness.fixture(name)).first)
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
    fun stripLeavesPlainFileUnchanged() {
        val input = harness.fixture("bmp-plain.bmp")
        val (output, report) = harness.strip(input)
        assertArrayEquals(input, output)
        assertEquals(StripReport(emptyList(), emptyList(), emptyList()), report)
    }

    @Test
    fun stripBlanksLinkedPathInPlace() {
        val input = harness.fixture("bmp-linked-profile.bmp")
        val (output, report) = harness.strip(input)

        val expected = input.copyOf()
        littleEndian(LCS_SRGB).copyInto(expected, CS_TYPE_POSITION)
        expected.fill(0, PROFILE_START, PROFILE_START + PROFILE_LENGTH)
        assertArrayEquals(expected, output)
        assertEquals(listOf(MetadataCategory.People), report.removed.map { it.category })
    }

    @Test
    fun keepsOrRemovesEmbeddedProfileByOption() {
        val input = harness.fixture("bmp-linked-profile.bmp").copyOf()
        littleEndian(PROFILE_EMBEDDED).copyInto(input, CS_TYPE_POSITION)
        assertEquals(listOf(MetadataItem(MetadataCategory.ColorProfile, "ICC profile", byteSize = PROFILE_LENGTH.toLong())),
                     harness.inspect(input).items)

        val (kept, keptReport) = harness.strip(input, StripOptions(keepColorProfile = true))
        assertArrayEquals(input, kept)
        assertEquals(listOf(MetadataCategory.ColorProfile), keptReport.kept.map { it.category })

        val (removed, removedReport) = harness.strip(input, StripOptions(keepColorProfile = false))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(removed).items)
        assertEquals(listOf(MetadataCategory.ColorProfile), removedReport.removed.map { it.category })
    }

    @Test
    fun rejectsProfileOutsideFile() {
        val input = harness.fixture("bmp-linked-profile.bmp").copyOf()
        littleEndian(input.size).copyInto(input, PROFILE_DATA_POSITION)
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
    }

    @Test
    fun rejectsEveryTruncation() {
        for (name in fixtureNames) {
            val input = harness.fixture(name)
            harness.assertRejectsEveryTruncation(name, input, input.size)
        }
    }

    private fun littleEndian(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
