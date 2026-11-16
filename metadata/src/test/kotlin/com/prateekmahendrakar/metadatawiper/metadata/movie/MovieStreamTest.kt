package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.accessUnitDelimiter
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.cameraMdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.indexOf
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.lengthPrefixed
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpmUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.message
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.sei
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.slice
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.userData
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.visualEntryExtras
import com.prateekmahendrakar.metadatawiper.metadata.nal.VideoCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// SEI user data inside MP4 video samples: Sony XAVC S and Panasonic write the recording date and GPS there.
class MovieStreamTest {

    // region State

    private val harness = HandlerHarness(IsoMediaHandler(FileFormat.Mp4))

    private val mdpm = "MDPM".toByteArray()

    private fun cameraSample(codec: VideoCodec,
                             lengthSize: Int,
                             fill: Int): ByteArray {
        val timing = message(1, byteArrayOf(0x00, 0x00, 0x00, 0x22))
        return lengthPrefixed(lengthSize,
                              accessUnitDelimiter(codec),
                              sei(codec, timing, userData(mdpmUuid, cameraMdpm)),
                              slice(codec, fill))
    }

    private fun cameraTrack(codec: VideoCodec,
                            lengthSize: Int,
                            format: String = if (codec == VideoCodec.H264) "avc1" else "hvc1"): MovieTestTrack {
        val chunks = listOf(listOf(cameraSample(codec, lengthSize, 0x41), cameraSample(codec, lengthSize, 0x42)),
                            listOf(cameraSample(codec, lengthSize, 0x43)))
        return MovieTestTrack("vide", format, chunks, sampleEntryExtras = visualEntryExtras(codec, lengthSize))
    }

    // endregion State

    // region Methods

    @Test
    fun blanksCamcorderDataInEverySample() {
        for ((codec, lengthSize) in listOf(VideoCodec.H264 to 4, VideoCodec.Hevc to 2, VideoCodec.H264 to 1)) {
            val built = MovieBuilder(listOf(cameraTrack(codec, lengthSize))).build()
            val name = "$codec/$lengthSize"

            val items = harness.inspect(built.bytes).items
            harness.assertHas(items, MetadataCategory.Location, MetadataLabel.RECORDING_DATA)
            harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, SeiTestData.CAMERA_DATE)
            harness.assertHas(items, MetadataCategory.Location, MetadataLabel.GPS, SeiTestData.CAMERA_POSITION)

            val (output, report) = harness.strip(built.bytes)
            assertEquals(name, built.bytes.size, output.size)
            assertEquals(name, -1, output.indexOf(mdpm))
            assertTrue(name, report.removed.any { it.label == MetadataLabel.RECORDING_DATA && it.byteSize == cameraMdpm.size * 3L })
            for (fill in 0x41..0x43) {
                val picture = slice(codec, fill)
                assertEquals(name, built.bytes.indexOf(picture), output.indexOf(picture))
            }
            assertOnlyUserDataChanged(built.bytes, output)
            harness.assertOnlyKeptCategoriesRemain(name, output)
            assertArrayEquals(name, output, harness.strip(output).first)
        }
    }

    @Test
    fun readsDolbyVisionAndInBandParameterSetEntries() {
        for ((codec, format) in listOf(VideoCodec.H264 to "avc3", VideoCodec.Hevc to "hev1", VideoCodec.Hevc to "dvh1")) {
            val output = harness.strip(MovieBuilder(listOf(cameraTrack(codec, 4, format))).build().bytes).first
            assertEquals(format, -1, output.indexOf(mdpm))
        }
    }

    @Test
    fun rejectsUnitsThatRunPastTheirSample() {
        val broken = MovieTestTrack("vide", "avc1", listOf(listOf(cameraSample(VideoCodec.H264, 4, 0x41).copyOf(60))),
                                    sampleEntryExtras = visualEntryExtras(VideoCodec.H264, 4))
        assertThrows(MalformedFileException::class.java) { harness.inspect(MovieBuilder(listOf(broken)).build().bytes) }
    }

    @Test
    fun cleansTheXavcFixture() {
        val input = harness.fixture("mp4-xavc.mp4")
        val items = harness.inspect(input).items
        harness.assertHas(items, MetadataCategory.Location, MetadataLabel.RECORDING_DATA)
        harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, "2026:09:12 14:30:15")
        harness.assertHas(items, MetadataCategory.Location, MetadataLabel.GPS, "48.858219, 2.294500")

        val output = harness.strip(input).first
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(mdpm))
        assertOnlyUserDataChanged(input, output)
        harness.assertOnlyKeptCategoriesRemain("mp4-xavc.mp4", output)
        assertArrayEquals(output, harness.strip(output).first)
    }

    // In `mdat`, blanked user data is 0xFF, with at most a run of escaped zeros that keeps the unit's length; nothing else may change.
    private fun assertOnlyUserDataChanged(input: ByteArray,
                                          output: ByteArray) {
        val written = setOf(0xFF.toByte(), 0x00.toByte(), 0x03.toByte())
        val mediaData = BoxReader(BinaryReader(ByteArrayChannel(input))).readTopLevel().boxes.filter { it.type == "mdat" }
        val changed = mediaData.flatMap { it.dataStart.toInt() until it.end.toInt() }.filter { input[it] != output[it] }
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { output[it] in written })
    }

    // endregion Methods
}
