package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FormatDetector
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.accessUnitDelimiter
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.annexB
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.cameraMdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.indexOf
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpmUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.message
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.sei
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.slice
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.userData
import com.prateekmahendrakar.metadatawiper.metadata.nal.VideoCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TransportStreamHandlerTest {

    // region State

    private val harness = HandlerHarness(TransportStreamHandler())

    private val timing = message(1, byteArrayOf(0x00, 0x00, 0x00, 0x22))

    private val cameraSei = sei(VideoCodec.H264, timing, userData(mdpmUuid, cameraMdpm))

    // Starts the SEI 150 bytes into the stream, so it straddles the first packet's end.
    private fun cameraUnit(fill: Int): ByteArray {
        return annexB(accessUnitDelimiter(VideoCodec.H264), slice(VideoCodec.H264, 0x30, 135), cameraSei, slice(VideoCodec.H264, fill, 400))
    }

    private fun plainUnit(fill: Int): ByteArray = annexB(accessUnitDelimiter(VideoCodec.H264), slice(VideoCodec.H264, fill, 400))

    // Enough null packets to push what follows past the first 4 MB.
    private val pastTheSample = 4 * 1024 * 1024 / 188 + 1

    // endregion State

    // region Methods

    // region Fixtures

    @Test
    fun cleansTheCamcorderFixture() {
        val input = harness.fixture("m2ts-camcorder.m2ts")
        assertEquals(FileFormat.MpegTs, FormatDetector.detect(input.copyOf(FormatDetector.HEADER_SIZE)))
        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.Location, MetadataLabel.RECORDING_DATA)
        harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, "2026:09:12 14:30:15")
        harness.assertHas(items, MetadataCategory.Location, MetadataLabel.GPS, "48.858219, 2.294500")

        val output = assertCleaned("m2ts-camcorder.m2ts", input)
        assertEquals(-1, output.indexOf("MDPM".toByteArray()))
    }

    @Test
    fun cleansTheHevcFixture() {
        val input = harness.fixture("ts-hevc.ts")
        val encoder = harness.inspect(input).items.single()
        assertEquals(MetadataLabel.ENCODER_SETTINGS, encoder.label)
        assertTrue(encoder.detail, encoder.detail.startsWith("x265 (build "))

        val output = assertCleaned("ts-hevc.ts", input)
        assertEquals(-1, output.indexOf("x265".toByteArray()))
    }

    @Test
    fun rejectsTruncatedFixturesWithoutOutput() {
        for (name in listOf("m2ts-camcorder.m2ts", "ts-hevc.ts")) {
            val input = harness.fixture(name)
            for (length in (1 until input.size step 97).filter { it % 188 != 0 && it % 192 != 0 }) {
                assertRejected(input.copyOf(length), MalformedFileException::class.java)
            }
        }
    }

    // endregion Fixtures

    // region Synthetic

    @Test
    fun blanksSeiSplitAcrossPackets() {
        for (layout in PacketLayout.entries) {
            val input = TransportStreamBuilder(layout).build(listOf(cameraUnit(0x41), cameraUnit(0x42)))
            assertEquals(layout.name, -1, input.indexOf(cameraSei))

            val (_, report) = harness.strip(input)
            assertEquals(layout.name, cameraMdpm.size * 2L, report.removed.single { it.label == MetadataLabel.RECORDING_DATA }.byteSize)
            val output = assertCleaned(layout.name, input)
            val changed = input.indices.count { input[it] != output[it] }
            assertTrue(layout.name, changed in 1..cameraSei.size * 2)
        }
    }

    @Test
    fun scansTheWholeFileWhenTheStartHasUserData() {
        val builder = TransportStreamBuilder()
        builder.nullPacketsAfter = mapOf(0 to pastTheSample)
        val input = builder.build(listOf(cameraUnit(0x41), cameraUnit(0x42)))

        val recording = harness.inspect(input).items.single { it.label == MetadataLabel.RECORDING_DATA }
        assertEquals(cameraMdpm.size * 2L, recording.byteSize)
        assertCleaned("past the sample", input)
    }

    // Decided for Phase 2: camcorders write user data from the first frame, so a file whose first 4 MB has none isn't read on.
    @Test
    fun stopsAfterTheSampleWhenTheStartHasNoUserData() {
        val builder = TransportStreamBuilder()
        builder.nullPacketsAfter = mapOf(0 to pastTheSample)
        val input = builder.build(listOf(plainUnit(0x41), cameraUnit(0x42)))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(input).items)
    }

    @Test
    fun reportsNothingForAPlainStream() {
        val input = TransportStreamBuilder().build(listOf(plainUnit(0x41)))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(input).items)
        assertArrayEquals(input, harness.strip(input).first)
    }

    @Test
    fun refusesVideoWhoseUserDataIsntRead() {
        for (type in listOf(0x02, 0x10, 0xEA)) {
            val builder = TransportStreamBuilder()
            builder.videoStreamType = type
            assertEquals(UnsupportedReason.InStreamMetadata, assertRejected(builder.build(listOf(plainUnit(0x41))), UnsupportedFileException::class.java).reason)
        }
    }

    @Test
    fun refusesScrambledVideo() {
        val builder = TransportStreamBuilder()
        builder.scrambleVideo = true
        assertEquals(UnsupportedReason.Encrypted, assertRejected(builder.build(listOf(plainUnit(0x41))), UnsupportedFileException::class.java).reason)
    }

    @Test
    fun warnsAboutStreamsItKeepsWithoutKnowing() {
        val builder = TransportStreamBuilder()
        builder.extraStreams = listOf(0x0F to ByteArray(0),
                                      0x15 to ByteArray(0),
                                      0x06 to TransportStreamBuilder.registration("KLVA"),
                                      0x06 to TransportStreamBuilder.registration("AC-3"),
                                      0x99 to ByteArray(0))
        val warnings = harness.inspect(builder.build(listOf(plainUnit(0x41)))).warnings
        assertEquals(listOf(MetadataWarning(MetadataWarningKind.UnknownTracksKept, listOf("0x15", "KLV", "0x99"))), warnings)
    }

    @Test
    fun readsHevcStreams() {
        val builder = TransportStreamBuilder()
        builder.videoStreamType = 0x24
        val unit = annexB(slice(VideoCodec.Hevc, 0x41), sei(VideoCodec.Hevc, userData(mdpmUuid, cameraMdpm)), slice(VideoCodec.Hevc, 0x42))
        val input = builder.build(listOf(unit))
        harness.assertHas(harness.inspect(input).items, MetadataCategory.Location, MetadataLabel.GPS, SeiTestData.CAMERA_POSITION)
        assertCleaned("HEVC", input)
    }

    @Test
    fun rejectsDamagedStreams() {
        val input = TransportStreamBuilder().build(listOf(cameraUnit(0x41)))
        assertRejected(input + 0x47, MalformedFileException::class.java)
        assertRejected(input.copyOf().also { it[188 * 2] = 0x48 }, MalformedFileException::class.java)
        assertRejected(input.copyOfRange(188 * 2, input.size), MalformedFileException::class.java)
        assertRejected(ByteArray(0), MalformedFileException::class.java)

        // A PES that doesn't start with a header can't be told apart from the stream inside it.
        assertRejected(input.copyOf().also { it[188 * 2 + 4] = 0x01 }, MalformedFileException::class.java)
    }

    // endregion Synthetic

    // The Phase 2 contract: removed items are gone, nothing moved, nothing else changed, and a second strip changes nothing.
    private fun assertCleaned(name: String,
                              input: ByteArray): ByteArray {
        val (output, report) = harness.strip(input)
        assertTrue(name, report.removed.isNotEmpty())
        assertEquals(name, input.size, output.size)
        harness.assertOnlyKeptCategoriesRemain(name, output)
        assertArrayEquals(name, output, harness.strip(output).first)

        val written = setOf(0xFF.toByte(), 0x00.toByte(), 0x03.toByte())
        assertTrue(name, input.indices.all { input[it] == output[it] || output[it] in written })
        return output
    }

    private fun <T : Exception> assertRejected(input: ByteArray,
                                               type: Class<T>): T {
        val output = ByteArrayOutputStream()
        assertThrows(type) { harness.inspect(input) }
        val error = assertThrows(type) { TransportStreamHandler().strip(ByteArrayChannel(input), output, StripOptions()) }
        assertEquals(0, output.size())
        return error
    }

    // endregion Methods
}
