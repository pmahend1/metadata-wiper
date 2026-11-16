package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// Builds SEI messages, NAL units and decoder configurations for tests. Nothing here decodes as video.
object SeiTestData {

    // region State

    val mdpmUuid = hex("17ee8c60f84d11d98cd60800200c9a66")
    val x264Uuid = hex("dc45e9bde6d948b7962cd820d923eeef")
    val vendorUuid = ByteArray(16) { (it + 0x41).toByte() }

    const val X264_SETTINGS = "x264 - core 164 r3095 baee400 - H.264/MPEG-4 AVC codec - Copyleft 2003-2022 - options: cabac=1 ref=3"

    /*
     * As a camcorder writes it: 2026:09:12 14:30:15, and 48°51'29.59" N 2°17'40.2" E. The
     * zero-padded refs and small rationals hold "00 00 00" runs, so the unit needs escapes.
     */
    val cameraMdpm = mdpm(0x18 to bytes(0x02, 0x20, 0x26, 0x09),
                          0x19 to bytes(0x12, 0x14, 0x30, 0x15),
                          0xB1 to "N\u0000\u0000\u0000".toByteArray(),
                          0xB2 to rational(48, 1),
                          0xB3 to rational(51, 1),
                          0xB4 to rational(2959, 100),
                          0xB5 to "E\u0000\u0000\u0000".toByteArray(),
                          0xB6 to rational(2, 1),
                          0xB7 to rational(17, 1),
                          0xB8 to rational(402, 10),
                          0xE0 to bytes(0x01, 0x08, 0x00, 0x00))

    const val CAMERA_DATE = "2026:09:12 14:30:15"
    const val CAMERA_POSITION = "48.858219, 2.294500"

    private const val USER_DATA_UNREGISTERED = 5

    // endregion State

    // region Methods

    fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    fun mdpm(vararg entries: Pair<Int, ByteArray>): ByteArray {
        return concat("MDPM".toByteArray(), bytes(entries.size), *entries.map { bytes(it.first) + it.second }.toTypedArray())
    }

    fun rational(numerator: Int,
                 denominator: Int): ByteArray = ByteBuffer.allocate(4).putShort(numerator.toShort()).putShort(denominator.toShort()).array()

    // A type and a size, each as a run of 0xFF plus a remainder, then the payload.
    fun message(type: Int,
                payload: ByteArray): ByteArray = extended(type) + extended(payload.size) + payload

    fun userData(uuid: ByteArray,
                 data: ByteArray): ByteArray = message(USER_DATA_UNREGISTERED, uuid + data)

    fun header(codec: VideoCodec,
               type: Int): ByteArray {
        return when (codec) {
            VideoCodec.H264 -> bytes(type)
            VideoCodec.Hevc -> bytes(type shl 1, 0x01)
        }
    }

    // An SEI unit holding [messages], with the stop bit and emulation-prevention bytes.
    fun sei(codec: VideoCodec,
            vararg messages: ByteArray): ByteArray {
        val type = if (codec == VideoCodec.H264) 6 else 39
        return unit(codec, type, concat(*messages) + bytes(0x80))
    }

    // An IDR slice: arbitrary bytes standing in for a picture, with no zeros so it needs no escapes.
    fun slice(codec: VideoCodec,
              fill: Int,
              size: Int = 48): ByteArray {
        val type = if (codec == VideoCodec.H264) 5 else 19
        return header(codec, type) + ByteArray(size) { fill.toByte() }
    }

    fun accessUnitDelimiter(codec: VideoCodec): ByteArray {
        return if (codec == VideoCodec.H264) unit(codec, 9, bytes(0xF0)) else unit(codec, 35, bytes(0x50))
    }

    fun unit(codec: VideoCodec,
             type: Int,
             payload: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(header(codec, type))
        Rbsp.escape(payload, output)
        return output.toByteArray()
    }

    fun lengthPrefixed(lengthSize: Int,
                       vararg units: ByteArray): ByteArray {
        return concat(*units.map { unit ->
            val length = ByteBuffer.allocate(4).putInt(unit.size).array()
            length.copyOfRange(4 - lengthSize, 4) + unit
        }.toTypedArray())
    }

    fun annexB(vararg units: ByteArray): ByteArray = concat(*units.map { bytes(0, 0, 0, 1) + it }.toTypedArray())

    // A visual sample entry's fields after the data reference index, then the decoder configuration.
    fun visualEntryExtras(codec: VideoCodec,
                          lengthSize: Int): ByteArray {
        val config = when (codec) {
            VideoCodec.H264 -> box("avcC", bytes(0x01, 0x64, 0x00, 0x1F, 0xFC or (lengthSize - 1), 0xE0, 0x00))
            VideoCodec.Hevc -> box("hvcC", bytes(0x01, 0x01, 0x60, 0, 0, 0, 0x90, 0, 0, 0, 0, 0, 0x5D, 0xF0, 0x00, 0xFC,
                                                 0xFD, 0xF8, 0xF8, 0x00, 0x00, 0x0C or (lengthSize - 1), 0x00))
        }
        return ByteArray(70) + config
    }

    fun ByteArray.indexOf(pattern: ByteArray): Int {
        return (0..size - pattern.size).firstOrNull { start -> pattern.indices.all { this[start + it] == pattern[it] } } ?: -1
    }

    private fun extended(value: Int): ByteArray = ByteArray(value / 255) { 0xFF.toByte() } + bytes(value % 255)

    // endregion Methods
}
