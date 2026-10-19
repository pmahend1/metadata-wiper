package com.prateekmahendrakar.metadatawiper.ui

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

// EXIF stores dates as "2026:09:12 14:30:00", with no time zone; shown as written, in the reader's locale.
object ExifDateText {

    // region State

    private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
    private val exifPattern = Regex("""\d{4}:\d{2}:\d{2} \d{2}:\d{2}:\d{2}""")

    // endregion State

    // region Methods

    // Null when the value isn't an EXIF date, so callers fall back to showing it as it is.
    fun format(value: String,
               locale: Locale): String? {
        if (exifPattern.matches(value.trim()) == false) {
            return null
        }
        return try {
            val dateTime = LocalDateTime.parse(value.trim(), exifFormat)
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                             .withLocale(locale)
                             .format(dateTime)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // endregion Methods
}
