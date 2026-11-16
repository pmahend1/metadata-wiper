package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.nal.AnnexBNalSplitter
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiBlanker
import com.prateekmahendrakar.metadatawiper.metadata.nal.StreamUserData
import com.prateekmahendrakar.metadatawiper.metadata.nal.VideoCodec
import java.util.Locale

/*
 * Works out what a transport stream (TS, M2TS, AVCHD .MTS) holds. The container has no metadata
 * worth the name; camcorders write the recording date, time and GPS into SEI user data in the
 * H.264 or HEVC stream, every group of pictures. That data is blanked where it is, so nothing
 * moves. Finding it means reading the whole video stream, so the scan samples the first few
 * megabytes and only goes on through the file when they hold user data.
 *
 * Other video codecs carry their own user data this doesn't read (MPEG-2 `user_data`), so a
 * stream of one fails the file. Audio and subtitles are kept. Streams of a type this doesn't
 * know are kept with a warning, and so is KLV, which drones use for telemetry.
 */
class TransportStreamPlanner(private val reader: BinaryReader) {

    // region State

    private val userData = StreamUserData()
    private val blanker = SeiBlanker(userData)

    private val parsedVideo = mapOf(0x1B to VideoCodec.H264, 0x20 to VideoCodec.H264, 0x24 to VideoCodec.Hevc)

    // MPEG-1/2 and MPEG-4 Part 2 video, VC-1, AVS, Dirac, VVC and auxiliary or SVC video.
    private val unparsedVideo = setOf(0x01, 0x02, 0x10, 0x1E, 0x1F, 0x33, 0x42, 0xD1, 0xEA)

    // Audio (MPEG, AAC, LPCM, AC-3, DTS, TrueHD), subtitles and Blu-ray graphics, private PES and SCTE-35 cues.
    private val keptTypes = setOf(0x03, 0x04, 0x06, 0x0F, 0x11, 0x1C, 0x2D, 0x2E, 0x80, 0x81, 0x82, 0x83, 0x84,
                                  0x85, 0x86, 0x87, 0x90, 0x91, 0x92, 0xA1, 0xA2)

    private companion object {
        const val SAMPLE_SIZE = 4L * 1024 * 1024
        const val KLV = "KLV"
    }

    // endregion State

    // region Methods

    fun plan(): TransportStreamPlan {
        val packets = TransportPacketReader(reader)
        val streams = ProgramMapReader(packets).read()

        val unparsed = streams.firstOrNull { it.streamType in unparsedVideo }
        if (unparsed != null) {
            throw UnsupportedFileException(UnsupportedReason.InStreamMetadata, "Stream type ${hex(unparsed.streamType)} can hold user data that isn't read")
        }
        val video = streams.filter { it.streamType in parsedVideo }.associate { stream ->
            val codec = parsedVideo.getValue(stream.streamType)
            stream.pid to VideoPesStream(stream.pid, AnnexBNalSplitter(codec) { blanker.blank(codec, it) })
        }
        scan(packets, video)

        val warnings = mutableListOf<MetadataWarning>()
        val unknown = streams.filter { it.isKlv || (it.streamType !in keptTypes && it.streamType !in parsedVideo) }
        if (unknown.isNotEmpty()) {
            warnings += MetadataWarning(MetadataWarningKind.UnknownTracksKept, unknown.map { if (it.isKlv) KLV else hex(it.streamType) }.distinct())
        }
        return TransportStreamPlan(userData.items(), warnings, blanker.blanked.sortedBy { it.span.start })
    }

    private fun scan(packets: TransportPacketReader,
                     video: Map<Int, VideoPesStream>) {
        var stoppedEarly = false
        packets.read { packet ->
            val stream = video[packet.pid]
            if (stream != null && packet.hasPayload) {
                if (packet.isScrambled) {
                    throw UnsupportedFileException(UnsupportedReason.Encrypted, "Video PID ${packet.pid} is scrambled")
                }
                stream.feed(packet)
            }
            stoppedEarly = packet.payloadFileOffset >= SAMPLE_SIZE && userData.hasMessages == false && video.values.none { it.isCollecting }
            stoppedEarly == false
        }
        if (stoppedEarly == false) {
            video.values.forEach { it.finish() }
        }
    }

    private fun hex(streamType: Int): String = String.format(Locale.ROOT, "0x%02X", streamType)

    // endregion Methods
}
