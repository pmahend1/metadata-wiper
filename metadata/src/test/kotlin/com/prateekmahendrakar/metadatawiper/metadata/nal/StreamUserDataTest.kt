package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.cameraMdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpmUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.rational
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.vendorUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.x264Uuid
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUserDataTest {

    // region Methods

    // A camcorder repeats MDPM in every group of pictures; the report shows the first date and position once.
    @Test
    fun talliesRepeatedMdpmIntoOneSetOfItems() {
        val userData = StreamUserData()
        userData.add(mdpmUuid, cameraMdpm)
        userData.add(mdpmUuid, mdpm(0x18 to bytes(0x02, 0x20, 0x27, 0x01), 0x19 to bytes(0x01, 0x00, 0x00, 0x00)))

        assertEquals(listOf(MetadataItem(MetadataCategory.Location, MetadataLabel.RECORDING_DATA, byteSize = cameraMdpm.size + 15L),
                            MetadataItem(MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, SeiTestData.CAMERA_DATE),
                            MetadataItem(MetadataCategory.Location, MetadataLabel.GPS, SeiTestData.CAMERA_POSITION)),
                     userData.items())
    }

    @Test
    fun reportsMdpmWithoutAPositionAsDeviceData() {
        val userData = StreamUserData()
        userData.add(mdpmUuid, mdpm(0x70 to bytes(1, 2, 3, 4)))
        assertEquals(listOf(MetadataItem(MetadataCategory.Device, MetadataLabel.RECORDING_DATA, byteSize = 10)), userData.items())
    }

    // GPS tags with no fix: nothing to show, but the block still counts as location data.
    @Test
    fun keepsTheLocationCategoryWhenThePositionCantBeRead() {
        val userData = StreamUserData()
        userData.add(mdpmUuid, mdpm(0xB1 to "N\u0000\u0000\u0000".toByteArray(), 0xB2 to rational(0, 0)))
        assertEquals(listOf(MetadataItem(MetadataCategory.Location, MetadataLabel.RECORDING_DATA, byteSize = 15)), userData.items())
    }

    @Test
    fun namesTheEncoder() {
        val x265 = "x265 (build 217) - 4.1:[Mac OS X][clang 17.0.0][64 bit] 8bit+10bit - H.265/HEVC codec - Copyright 2013-2018 (c) Multicoreware, Inc"
        for ((uuid, text, name) in listOf(Triple(x264Uuid, SeiTestData.X264_SETTINGS, "x264 core 164 r3095 baee400"),
                                          Triple(vendorUuid, x265, "x265 (build 217)"))) {
            val userData = StreamUserData()
            userData.add(uuid, text.toByteArray())
            assertEquals(listOf(MetadataItem(MetadataCategory.Software, MetadataLabel.ENCODER_SETTINGS, name, text.length.toLong())), userData.items())
        }
    }

    @Test
    fun reportsOtherUserDataBySize() {
        val userData = StreamUserData()
        userData.add(vendorUuid, ByteArray(40) { 1 })
        userData.add(mdpmUuid, "not mdpm".toByteArray())
        assertEquals(listOf(MetadataItem(MetadataCategory.Other, MetadataLabel.STREAM_USER_DATA, byteSize = 48)), userData.items())
    }

    // endregion Methods
}
