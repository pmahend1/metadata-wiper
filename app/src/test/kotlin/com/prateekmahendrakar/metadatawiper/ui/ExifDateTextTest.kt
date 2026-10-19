package com.prateekmahendrakar.metadatawiper.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExifDateTextTest {

    // region Methods

    @Test
    fun formatsForTheLocale() {
        val english = ExifDateText.format("2026:09:12 14:30:00", Locale.UK)
        val spanish = ExifDateText.format("2026:09:12 14:30:00", Locale.forLanguageTag("es-ES"))
        assertTrue(english, english!!.contains("2026") && english.contains("Sept") && english.contains("14:30"))
        assertTrue(spanish, spanish!!.contains("2026") && spanish.contains("sept") && spanish.contains("14:30"))
    }

    @Test
    fun leavesOtherValuesAlone() {
        assertNull(ExifDateText.format("Fixture Camera Co", Locale.UK))
        assertNull(ExifDateText.format("2026-09-12T14:30:00", Locale.UK))
        assertNull(ExifDateText.format("    :  :     :  :  ", Locale.UK))
    }

    @Test
    fun rejectsImpossibleDates() {
        assertNull(ExifDateText.format("2026:13:45 25:61:00", Locale.UK))
    }

    // endregion Methods
}
