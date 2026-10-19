package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataSectionsTest {

    // region Methods

    @Test
    fun cardsShowFindingsAndRawTagsFoldIntoMore() {
        val groups = MetadataGrouping.of(listOf(MetadataItem(MetadataCategory.Location, "GPS", "37.77, -122.42")),
                                         mapOf("GPSLatitude" to "37/1,46/1,0/1"))
        val sections = MetadataSections.of(groups, StripOptions())
        assertEquals(listOf("GPS"), sections.cards.single().rows.map { it.label })
        assertEquals(listOf("GPSLatitude"), sections.more.map { it.label })
    }

    @Test
    fun cardKeepsRawTagsWhenThereIsNoFinding() {
        val groups = MetadataGrouping.of(emptyList(), mapOf("DateTimeOriginal" to "2026:09:12 14:30:00"))
        val sections = MetadataSections.of(groups, StripOptions())
        assertEquals(MetadataCategory.DateTime, sections.cards.single().category)
        assertEquals(emptyList<MetadataRow>(), sections.more)
    }

    @Test
    fun lowInterestCategoriesFoldIntoMore() {
        val groups = MetadataGrouping.of(listOf(MetadataItem(MetadataCategory.Software, "Software", "Editor 2.0")),
                                         mapOf("FNumber" to "1.8"))
        val sections = MetadataSections.of(groups, StripOptions())
        assertEquals(emptyList<MetadataGroup>(), sections.cards)
        assertEquals(listOf("Software", "FNumber"), sections.more.map { it.label })
    }

    @Test
    fun keptCategoriesFollowTheOptions() {
        val items = listOf(MetadataItem(MetadataCategory.ColorProfile, "ICC profile"),
                           MetadataItem(MetadataCategory.Orientation, "Orientation", "6"))
        val groups = MetadataGrouping.of(items, mapOf("Orientation" to "Rotate 90 Clockwise"))

        val keepingBoth = MetadataSections.of(groups, StripOptions())
        assertEquals(listOf("ICC profile", "Orientation"), keepingBoth.kept.map { it.label })

        val keepingNone = MetadataSections.of(groups, StripOptions(keepColorProfile = false, keepOrientation = false))
        assertEquals(emptyList<MetadataRow>(), keepingNone.kept)
        assertEquals(listOf("ICC profile", "Orientation", "Orientation"), keepingNone.more.map { it.label })
    }

    // Everything found has to appear somewhere, or a file would look cleaner than it is.
    @Test
    fun everyRemovableRowIsShown() {
        val items = MetadataCategory.entries.map { MetadataItem(it, it.name) }
        val exif = mapOf("GPSLatitude" to "1", "Make" to "Phone", "FNumber" to "1.8", "DateTime" to "2026")
        val groups = MetadataGrouping.of(items, exif)
        val sections = MetadataSections.of(groups, StripOptions(keepColorProfile = false, keepOrientation = false))
        val shown = sections.cards.flatMap { it.rows } + sections.more + sections.kept
        assertEquals(groups.flatMap { it.rows }.toSet(), shown.toSet())
    }

    // endregion Methods
}
