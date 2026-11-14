package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import java.io.ByteArrayOutputStream

/*
 * Plans the overwrite of `user_data_unregistered` SEI messages (payload type 5): AVCHD/XAVC
 * `MDPM` with the recording date and GPS, encoder settings, vendor blobs. The 16-byte UUID stays
 * and the bytes after it become 0xFF. Every other message is kept, since decoding and HDR
 * rendering depend on them (buffering period, picture timing, mastering display, T.35).
 *
 * Nothing may move, so the NAL unit keeps its raw length. 0xFF never needs an
 * emulation-prevention byte, so a payload that had some comes out shorter once re-escaped. The
 * shortfall is made up with a run of zeros inside the blanked payload, which the escaping
 * lengthens by one byte per pair. When a payload is too short for that, a unit holding nothing
 * but user data becomes an unspecified NAL type that decoders skip. Anything else fails the file.
 */
class SeiBlanker(private val userData: StreamUserData) {

    // region State

    private val ranges = mutableListOf<BlankedRange>()

    val blanked: List<BlankedRange>
        get() = ranges

    private companion object {
        const val USER_DATA_UNREGISTERED = 5
        const val UUID_SIZE = 16
        const val FILL = 0xFF.toByte()
    }

    // endregion State

    // region Methods

    fun blank(codec: VideoCodec,
              nal: NalUnit) {
        if (nal.bytes.size <= codec.headerSize) {
            throw MalformedFileException("SEI unit of ${nal.bytes.size} bytes has no payload")
        }
        val rbsp = Rbsp.unescape(nal.bytes, codec.headerSize)
        val messages = SeiReader.messages(rbsp)
        val userMessages = messages.filter { it.payloadType == USER_DATA_UNREGISTERED }
        if (userMessages.isEmpty()) {
            return
        }
        userData.noteMessage()

        val found = userMessages.filter { found(rbsp, it) }
        if (found.isEmpty()) {
            return
        }
        val raw = rewritten(codec, nal, rbsp, found) ?: unspecified(codec, nal, messages)
        record(nal, raw)
    }

    // Reports a message's data unless a previous strip already blanked it.
    private fun found(rbsp: ByteArray,
                      message: SeiMessage): Boolean {
        if (message.payloadSize < UUID_SIZE) {
            throw MalformedFileException("SEI user data of ${message.payloadSize} bytes is shorter than its UUID")
        }
        val uuid = rbsp.copyOfRange(message.payloadStart, message.payloadStart + UUID_SIZE)
        val data = rbsp.copyOfRange(message.payloadStart + UUID_SIZE, message.payloadEnd)
        if (isBlank(data)) {
            return false
        }
        userData.add(uuid, data)
        return true
    }

    // 0xFF with at most one run of zeros, which is what this class writes.
    private fun isBlank(data: ByteArray): Boolean {
        if (data.any { it != FILL && it != 0.toByte() }) {
            return false
        }
        val firstZero = data.indexOf(0)
        return firstZero < 0 || (firstZero..data.lastIndexOf(0)).all { data[it] == 0.toByte() }
    }

    // The unit with every found payload blanked, at its original raw length, or null if that length can't be kept.
    private fun rewritten(codec: VideoCodec,
                          nal: NalUnit,
                          rbsp: ByteArray,
                          found: List<SeiMessage>): ByteArray? {
        val payload = rbsp.copyOf()
        for (message in found) {
            payload.fill(FILL, message.payloadStart + UUID_SIZE, message.payloadEnd)
        }
        val raw = escaped(nal, codec, payload)
        val shortfall = nal.bytes.size - raw.size
        if (shortfall == 0) {
            return raw
        }

        // 2n + 1 zeros between 0xFF bytes take n escapes, and the 0xFF on each side keeps them from joining the bytes around.
        val widest = found.maxBy { it.payloadSize }
        val start = widest.payloadStart + UUID_SIZE
        if (shortfall < 0 || widest.payloadEnd - start < shortfall * 2 + 3) {
            return null
        }
        payload.fill(0, start + 1, start + 2 + shortfall * 2)
        val padded = escaped(nal, codec, payload)
        return if (padded.size == nal.bytes.size) padded else null
    }

    private fun unspecified(codec: VideoCodec,
                            nal: NalUnit,
                            messages: List<SeiMessage>): ByteArray {
        if (messages.any { it.payloadType != USER_DATA_UNREGISTERED }) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "SEI user data can't be blanked without moving the messages beside it")
        }
        val raw = ByteArray(nal.bytes.size) { FILL }
        nal.bytes.copyInto(raw, 0, 0, codec.headerSize)
        raw[0] = codec.unspecifiedHeaderByte(nal.bytes[0].toInt() and 0xFF)
        return raw
    }

    private fun escaped(nal: NalUnit,
                        codec: VideoCodec,
                        payload: ByteArray): ByteArray {
        val raw = ByteArrayOutputStream(nal.bytes.size)
        raw.write(nal.bytes, 0, codec.headerSize)
        Rbsp.escape(payload, raw)
        return raw.toByteArray()
    }

    // Only the bytes that changed are written, so the copy of an MP4 sample stays identical around them.
    private fun record(nal: NalUnit,
                       raw: ByteArray) {
        val first = raw.indices.firstOrNull { raw[it] != nal.bytes[it] } ?: return
        val last = raw.indices.last { raw[it] != nal.bytes[it] }
        ranges += BlankedRange.across(nal.spans(first, last + 1), raw.copyOfRange(first, last + 1))
    }

    // endregion Methods
}
