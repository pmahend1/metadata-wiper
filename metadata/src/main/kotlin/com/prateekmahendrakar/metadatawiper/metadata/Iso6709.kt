package com.prateekmahendrakar.metadatawiper.metadata

import java.util.Locale

// Location text such as "+48.8582+002.2945/", which MP4, MOV and Matroska tags all use.
object Iso6709 {

    // region State

    // Degrees only. The minutes and seconds forms ISO 6709 also allows don't appear in video files.
    private val degrees = Regex("""^([+-]\d{2}(?:\.\d+)?)([+-]\d{3}(?:\.\d+)?)""")

    // endregion State

    // region Methods

    // "latitude, longitude", or "" when the text isn't a position this reads.
    fun coordinates(text: String): String {
        val match = degrees.find(text) ?: return ""
        return format(match.groupValues[1].toDouble(), match.groupValues[2].toDouble())
    }

    // Android zeroes these bytes for apps without ACCESS_MEDIA_LOCATION, so 0, 0 means "hidden", not the Gulf of Guinea.
    fun format(latitude: Double,
               longitude: Double): String {
        if (latitude == 0.0 && longitude == 0.0) {
            return ""
        }
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
    }

    // endregion Methods
}
