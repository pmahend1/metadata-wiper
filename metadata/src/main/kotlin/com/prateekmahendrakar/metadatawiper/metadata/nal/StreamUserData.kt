package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel

/*
 * Tallies the SEI user data found in a video stream into a few report items. Camcorders repeat
 * `MDPM` in every group of pictures, so a long clip holds thousands of copies; the report shows
 * one item per kind, with the date and position of the first copy that has them.
 */
class StreamUserData {

    // region State

    // Set by any user data message, even one already blanked, so a scan that samples the start knows to go on.
    var hasMessages = false
        private set

    private var recordingBytes = 0L
    private var hasRecordingGps = false
    private var recordingDate: String? = null
    private var coordinates = ""
    private var encoderBytes = 0L
    private var encoder = ""
    private var otherBytes = 0L

    private companion object {
        val mdpmUuid = uuid("17ee8c60f84d11d98cd60800200c9a66")
        val x264Uuid = uuid("dc45e9bde6d948b7962cd820d923eeef")
        const val X265_PREFIX = "x265 "
        const val SEGMENT_SEPARATOR = " - "
        const val CORE_SEGMENT = "core "
        const val MAX_ENCODER_NAME = 48

        fun uuid(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    // endregion State

    // region Methods

    fun noteMessage() {
        hasMessages = true
    }

    // [data] is the payload after the 16-byte UUID, which is what gets blanked.
    fun add(uuid: ByteArray,
            data: ByteArray) {
        val mdpm = MdpmReader(data)
        when {
            uuid.contentEquals(mdpmUuid) && mdpm.isMdpm -> addRecording(mdpm, data.size)
            uuid.contentEquals(x264Uuid) || text(data).startsWith(X265_PREFIX) -> addEncoder(text(data), data.size)
            else -> otherBytes += data.size
        }
    }

    fun items(): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()
        if (recordingBytes > 0) {
            val category = if (hasRecordingGps) MetadataCategory.Location else MetadataCategory.Device
            items += MetadataItem(category, MetadataLabel.RECORDING_DATA, byteSize = recordingBytes)
        }
        recordingDate?.let { items += MetadataItem(MetadataCategory.DateTime, MetadataLabel.DATE_TIME_ORIGINAL, it) }
        if (coordinates.isNotEmpty()) {
            items += MetadataItem(MetadataCategory.Location, MetadataLabel.GPS, coordinates)
        }
        if (encoderBytes > 0) {
            items += MetadataItem(MetadataCategory.Software, MetadataLabel.ENCODER_SETTINGS, encoder, encoderBytes)
        }
        if (otherBytes > 0) {
            items += MetadataItem(MetadataCategory.Other, MetadataLabel.STREAM_USER_DATA, byteSize = otherBytes)
        }
        return items
    }

    private fun addRecording(mdpm: MdpmReader,
                             size: Int) {
        recordingBytes += size
        hasRecordingGps = hasRecordingGps || mdpm.hasGps
        recordingDate = recordingDate ?: mdpm.recordingDate()
        coordinates = coordinates.ifEmpty { mdpm.coordinates() }
    }

    // x264 writes "x264 - core 164 r3095 baee400 - H.264/MPEG-4 AVC codec - … - options: …"; the name and build are enough.
    private fun addEncoder(text: String,
                           size: Int) {
        encoderBytes += size
        if (encoder.isEmpty()) {
            val segments = text.split(SEGMENT_SEPARATOR)
            val name = if (segments.size > 1 && segments[1].startsWith(CORE_SEGMENT)) "${segments[0]} ${segments[1]}" else segments[0]
            encoder = name.take(MAX_ENCODER_NAME)
        }
    }

    private fun text(data: ByteArray): String = String(data, Charsets.ISO_8859_1).trimEnd('\u0000')

    // endregion Methods
}
