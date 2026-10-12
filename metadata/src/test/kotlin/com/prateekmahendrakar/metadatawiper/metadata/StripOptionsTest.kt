package com.prateekmahendrakar.metadatawiper.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class StripOptionsTest {

    // region Methods

    @Test
    fun keepsOnlyColorProfileAndOrientationByDefault() {
        val options = StripOptions()
        val kept = MetadataCategory.entries.filter { options.keeps(MetadataItem(it, "item")) }
        assertEquals(listOf(MetadataCategory.ColorProfile, MetadataCategory.Orientation), kept)
    }

    @Test
    fun keepsNothingWhenBothOptionsAreOff() {
        val options = StripOptions(keepColorProfile = false, keepOrientation = false)
        val kept = MetadataCategory.entries.filter { options.keeps(MetadataItem(it, "item")) }
        assertEquals(emptyList<MetadataCategory>(), kept)
    }

    // endregion Methods
}
