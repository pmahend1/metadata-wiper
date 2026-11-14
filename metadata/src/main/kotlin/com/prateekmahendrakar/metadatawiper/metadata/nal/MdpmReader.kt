package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.Iso6709

/*
 * Reads the recording date and position out of an AVCHD/XAVC `MDPM` block: "MDPM", an entry
 * count, then entries of a tag byte and four data bytes. Tag numbers follow exiftool's H264
 * module. Only what the report shows is decoded; the block is blanked whole either way.
 */
class MdpmReader(data: ByteArray) {

    // region State

    private val entries = mutableMapOf<Int, ByteArray>()

    val isMdpm: Boolean = data.size >= HEADER_SIZE && String(data, 0, SIGNATURE.length, Charsets.ISO_8859_1) == SIGNATURE

    val hasGps: Boolean
        get() = entries.keys.any { it in gpsTags }

    companion object {
        const val SIGNATURE = "MDPM"

        private const val HEADER_SIZE = 5
        private const val ENTRY_SIZE = 5

        // 0x18 holds the time zone, year and month, 0x19 the day and time, all in BCD.
        private const val DATE = 0x18
        private const val TIME = 0x19

        private const val LATITUDE_REF = 0xB1
        private const val LATITUDE = 0xB2
        private const val LONGITUDE_REF = 0xB5
        private const val LONGITUDE = 0xB6
        private val gpsTags = 0xB0..0xCC
    }

    // endregion State

    init {
        if (isMdpm) {
            val count = data[SIGNATURE.length].toInt() and 0xFF
            var position = HEADER_SIZE
            for (index in 0 until count) {
                if (position + ENTRY_SIZE > data.size) {
                    break
                }
                val tag = data[position].toInt() and 0xFF
                entries.putIfAbsent(tag, data.copyOfRange(position + 1, position + ENTRY_SIZE))
                position += ENTRY_SIZE
            }
        }
    }

    // region Methods

    // As EXIF writes dates ("2026:09:12 14:30:00"), without the time zone, or null if it isn't there.
    fun recordingDate(): String? {
        val date = entries[DATE] ?: return null
        val time = entries[TIME] ?: return null
        val digits = (date.drop(1) + time.toList()).map { bcd(it) ?: return null }
        return "${digits[0]}${digits[1]}:${digits[2]}:${digits[3]} ${digits[4]}:${digits[5]}:${digits[6]}"
    }

    // "latitude, longitude", or "" when there's no complete position.
    fun coordinates(): String {
        val latitude = degrees(LATITUDE_REF, LATITUDE, 'S') ?: return ""
        val longitude = degrees(LONGITUDE_REF, LONGITUDE, 'W') ?: return ""
        return Iso6709.format(latitude, longitude)
    }

    // Degrees, minutes and seconds in three consecutive tags, each a 16-bit numerator and denominator.
    private fun degrees(refTag: Int,
                        firstTag: Int,
                        negative: Char): Double? {
        val ref = entries[refTag] ?: return null
        val parts = (0 until 3).map { rational(entries[firstTag + it] ?: return null) ?: return null }
        val value = parts[0] + parts[1] / 60 + parts[2] / 3600
        return if (ref[0].toInt().toChar() == negative) -value else value
    }

    private fun rational(value: ByteArray): Double? {
        val numerator = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
        val denominator = ((value[2].toInt() and 0xFF) shl 8) or (value[3].toInt() and 0xFF)
        return if (denominator == 0) null else numerator.toDouble() / denominator
    }

    private fun bcd(byte: Byte): String? {
        val high = (byte.toInt() shr 4) and 0x0F
        val low = byte.toInt() and 0x0F
        return if (high > 9 || low > 9) null else "$high$low"
    }

    // endregion Methods
}
