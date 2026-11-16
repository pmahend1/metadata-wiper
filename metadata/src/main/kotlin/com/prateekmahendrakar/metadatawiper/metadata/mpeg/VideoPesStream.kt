package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.nal.AnnexBNalSplitter

/*
 * Reassembles one video PID's PES packets into the elementary stream for [splitter]. A packet
 * that starts a unit begins with a PES header, which is skipped; it can be split across
 * packets too. Payload before the first header is the tail of a PES the file was cut into,
 * and still goes to the splitter, which ignores everything until a start code.
 */
class VideoPesStream(private val pid: Int,
                     private val splitter: AnnexBNalSplitter) {

    // region State

    private val header = ByteArray(FIXED_HEADER_SIZE)
    private var headerRead = NOT_IN_HEADER
    private var headerSkip = 0

    val isCollecting: Boolean
        get() = splitter.isCollecting

    private companion object {
        // Start code prefix, stream id, packet length, two flag bytes and the header data length.
        const val FIXED_HEADER_SIZE = 9
        const val HEADER_DATA_LENGTH_OFFSET = 8
        const val STREAM_ID_OFFSET = 3
        const val NOT_IN_HEADER = -1

        val videoStreamIds = (0xE0..0xEF).toSet() + 0xFD
    }

    // endregion State

    // region Methods

    fun feed(packet: TransportPacket) {
        var position = packet.payloadStart
        val end = packet.payloadEnd
        if (packet.startsUnit) {
            headerRead = 0
            headerSkip = 0
        }
        if (headerRead != NOT_IN_HEADER) {
            val count = minOf(FIXED_HEADER_SIZE - headerRead, end - position)
            packet.bytes.copyInto(header, headerRead, position, position + count)
            headerRead += count
            position += count
            if (headerRead < FIXED_HEADER_SIZE) {
                return
            }
            requireVideoHeader(packet.payloadFileOffset)
            headerSkip = header[HEADER_DATA_LENGTH_OFFSET].toInt() and 0xFF
            headerRead = NOT_IN_HEADER
        }
        val skipped = minOf(headerSkip, end - position)
        position += skipped
        headerSkip -= skipped
        if (position < end) {
            splitter.feed(packet.bytes, position, end, packet.payloadFileOffset + position - packet.payloadStart)
        }
    }

    fun finish() = splitter.finish()

    private fun requireVideoHeader(fileOffset: Long) {
        val hasPrefix = header[0] == 0.toByte() && header[1] == 0.toByte() && header[2] == 1.toByte()
        val streamId = header[STREAM_ID_OFFSET].toInt() and 0xFF
        if (hasPrefix == false || streamId !in videoStreamIds) {
            throw MalformedFileException("Video PID $pid has no PES header near offset $fileOffset")
        }
    }

    // endregion Methods
}
