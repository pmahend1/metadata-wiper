package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedCopy
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.cameraMdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.indexOf
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpmUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.message
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.sei
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.userData
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.vendorUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.x264Uuid
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SeiBlankerTest {

    // region State

    private val bufferingPeriod = message(0, bytes(0x8F, 0x00, 0x00, 0x01, 0x40))
    private val pictureTiming = message(1, bytes(0x00, 0x00, 0x00, 0x22, 0x10))

    // Escaped as 00 00 03 01 00 00 03 01: too short to make up two escapes with a run of zeros.
    private val denseData = bytes(0x00, 0x00, 0x01, 0x00, 0x00, 0x01)

    private companion object {
        const val UNIT_OFFSET = 100
    }

    // endregion State

    // region Methods

    @Test
    fun blanksEncoderSettingsAndKeepsTheUuid() {
        val input = sei(VideoCodec.H264, userData(x264Uuid, SeiTestData.X264_SETTINGS.toByteArray()))
        val (output, items) = blank(VideoCodec.H264, input)

        assertEquals(input.size, output.size)
        val message = SeiReader.messages(Rbsp.unescape(output, 1)).single()
        val payload = Rbsp.unescape(output, 1).copyOfRange(message.payloadStart, message.payloadEnd)
        assertArrayEquals(x264Uuid, payload.copyOf(16))
        assertTrue(payload.drop(16).all { it == 0xFF.toByte() })
        assertEquals(listOf(MetadataItem(MetadataCategory.Software, MetadataLabel.ENCODER_SETTINGS, "x264 core 164 r3095 baee400", SeiTestData.X264_SETTINGS.length.toLong())), items)
    }

    @Test
    fun blanksEscapedMdpmBesideOtherMessagesWithoutMovingThem() {
        val input = sei(VideoCodec.H264, bufferingPeriod, pictureTiming, userData(mdpmUuid, cameraMdpm))
        assertTrue("Fixture must need escapes", input.size > Rbsp.unescape(input, 1).size + 1)
        val (output, items) = blank(VideoCodec.H264, input)

        assertEquals(input.size, output.size)
        assertEquals(input[0], output[0])
        val before = Rbsp.unescape(input, 1)
        val after = Rbsp.unescape(output, 1)
        val messages = SeiReader.messages(after)
        assertEquals(listOf(0, 1, 5), messages.map { it.payloadType })
        assertArrayEquals(before.copyOf(messages[2].payloadStart + 16), after.copyOf(messages[2].payloadStart + 16))
        assertEquals(-1, output.indexOf("MDPM".toByteArray()))

        assertHas(items, MetadataCategory.Location, MetadataLabel.RECORDING_DATA, "")
        assertHas(items, MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, SeiTestData.CAMERA_DATE)
        assertHas(items, MetadataCategory.Location, MetadataLabel.GPS, SeiTestData.CAMERA_POSITION)
    }

    @Test
    fun blanksEveryUserDataMessageInAUnit() {
        val input = sei(VideoCodec.Hevc, userData(vendorUuid, "serial 0042".toByteArray()), pictureTiming, userData(mdpmUuid, cameraMdpm))
        val (output, items) = blank(VideoCodec.Hevc, input)

        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf("serial".toByteArray()))
        assertEquals(-1, output.indexOf("MDPM".toByteArray()))
        assertHas(items, MetadataCategory.Other, MetadataLabel.STREAM_USER_DATA, "")
        assertHas(items, MetadataCategory.Location, MetadataLabel.RECORDING_DATA, "")
    }

    @Test
    fun makesAUnitOfOnlyUserDataUnspecifiedWhenItCantBeRewritten() {
        for ((codec, header) in listOf(VideoCodec.H264 to bytes(0x00), VideoCodec.Hevc to bytes(0x60, 0x01))) {
            val input = sei(codec, userData(vendorUuid, denseData))
            val (output, items) = blank(codec, input)

            assertEquals(input.size, output.size)
            assertArrayEquals(header, output.copyOf(codec.headerSize))
            assertTrue(output.drop(codec.headerSize).all { it == 0xFF.toByte() })
            assertTrue(codec.isSei(output[0].toInt() and 0xFF) == false)
            assertEquals(1, items.size)
        }
    }

    @Test
    fun refusesUserDataThatCantBeBlankedWithoutMovingOtherMessages() {
        val input = sei(VideoCodec.H264, pictureTiming, userData(vendorUuid, denseData))
        val error = assertThrows(UnsupportedFileException::class.java) { blank(VideoCodec.H264, input) }
        assertEquals(UnsupportedReason.NotYetSupported, error.reason)
    }

    @Test
    fun leavesBlankedUserDataAsItIs() {
        val input = sei(VideoCodec.H264, bufferingPeriod, userData(mdpmUuid, cameraMdpm), userData(x264Uuid, "x264 - core 1".toByteArray()))
        val once = blank(VideoCodec.H264, input).first

        val userData = StreamUserData()
        val blanker = SeiBlanker(userData)
        blanker.blank(VideoCodec.H264, NalUnit(once, listOf(FileSpan(0, once.size.toLong()))))
        assertEquals(emptyList<Any>(), blanker.blanked)
        assertEquals(emptyList<MetadataItem>(), userData.items())
        assertTrue(userData.hasMessages)
    }

    @Test
    fun ignoresUnitsWithoutUserData() {
        val userData = StreamUserData()
        val blanker = SeiBlanker(userData)
        val input = sei(VideoCodec.H264, bufferingPeriod, pictureTiming)
        blanker.blank(VideoCodec.H264, NalUnit(input, listOf(FileSpan(0, input.size.toLong()))))
        assertEquals(emptyList<Any>(), blanker.blanked)
        assertTrue(userData.hasMessages == false)
    }

    @Test
    fun rejectsUserDataShorterThanItsUuid() {
        assertThrows(MalformedFileException::class.java) { blank(VideoCodec.H264, sei(VideoCodec.H264, message(5, ByteArray(10) { 1 }))) }
        assertThrows(MalformedFileException::class.java) { blank(VideoCodec.Hevc, bytes(0x4E, 0x01)) }
    }

    // Blanks [unit] where it sits in a file, through the same copy a strip uses, and returns the unit as written.
    private fun blank(codec: VideoCodec,
                      unit: ByteArray): Pair<ByteArray, List<MetadataItem>> {
        val file = ByteArray(UNIT_OFFSET) { 0x55 } + unit + ByteArray(8) { 0x55 }
        val userData = StreamUserData()
        val blanker = SeiBlanker(userData)
        blanker.blank(codec, NalUnit(unit, listOf(FileSpan(UNIT_OFFSET.toLong(), UNIT_OFFSET.toLong() + unit.size))))

        val output = ByteArrayOutputStream()
        BlankedCopy.write(BinaryReader(ByteArrayChannel(file)), output, blanker.blanked.sortedBy { it.span.start })
        val written = output.toByteArray()
        assertEquals(file.size, written.size)
        assertArrayEquals(file.copyOf(UNIT_OFFSET), written.copyOf(UNIT_OFFSET))
        return written.copyOfRange(UNIT_OFFSET, UNIT_OFFSET + unit.size) to userData.items()
    }

    private fun assertHas(items: List<MetadataItem>,
                           category: MetadataCategory,
                           label: String,
                           detail: String) {
        assertTrue("Missing $label=$detail in $items", items.any { it.category == category && it.label == label && it.detail == detail })
    }

    // endregion Methods
}
