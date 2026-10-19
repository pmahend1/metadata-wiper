package com.prateekmahendrakar.metadatawiper.ui

import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuralLabelTest {

    // region Methods

    // An unmapped label would show in English whatever the user's language.
    @Test
    fun everyHandlerLabelIsTranslated() {
        val missing = MetadataLabel.all.filter { StructuralLabel.of(it) == null }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun fileOwnKeywordsAreShownAsTheyAre() {
        assertNull(StructuralLabel.of("Camera rig notes"))
    }

    @Test
    fun orientationValuesAreReadable() {
        assertEquals(R.string.orientation_rotated_90, StructuralLabel.valueFor(MetadataLabel.ORIENTATION, "6"))
        assertNull(StructuralLabel.valueFor(MetadataLabel.ORIENTATION, "9"))
        assertNull(StructuralLabel.valueFor(MetadataLabel.MAKE, "6"))
    }

    @Test
    fun unreadableGpsGetsANote() {
        assertEquals(R.string.note_gps_hidden, StructuralLabel.noteFor(MetadataLabel.GPS, ""))
        assertNull(StructuralLabel.noteFor(MetadataLabel.GPS, "48.858217, 2.294500"))
    }

    // endregion Methods
}
