package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataGroupingTest {

    // region Methods

    @Test
    fun groupsFollowRiskOrder() {
        val items = listOf(MetadataItem(MetadataCategory.ColorProfile, "ICC profile"),
                           MetadataItem(MetadataCategory.DateTime, "DateTime"),
                           MetadataItem(MetadataCategory.Location, "GPS"),
                           MetadataItem(MetadataCategory.Device, "Make"),
                           MetadataItem(MetadataCategory.People, "Artist"))
        val categories = MetadataGrouping.of(items, emptyMap()).map { it.category }
        assertEquals(listOf(MetadataCategory.Location,
                            MetadataCategory.People,
                            MetadataCategory.Device,
                            MetadataCategory.DateTime,
                            MetadataCategory.ColorProfile),
                     categories)
    }

    // A category missing from the order would vanish from the view and make a file look cleaner than it is.
    @Test
    fun everyCategoryIsShown() {
        val items = MetadataCategory.entries.map { MetadataItem(it, it.name) }
        assertEquals(MetadataCategory.entries.toSet(), MetadataGrouping.of(items, emptyMap()).map { it.category }.toSet())
    }

    @Test
    fun structuralFindingsPrecedeExifTagsInTheirGroup() {
        val items = listOf(MetadataItem(MetadataCategory.Location, "GPS", "37.77, -122.42"))
        val exif = mapOf("GPSLongitude" to "122/1,25/1,0/1", "GPSLatitude" to "37/1,46/1,0/1")
        val location = MetadataGrouping.of(items, exif).single()
        assertEquals(listOf("GPS", "GPSLatitude", "GPSLongitude"), location.rows.map { it.label })
        assertEquals(listOf(true, false, false), location.rows.map { it.isStructural })
    }

    @Test
    fun onlyLocationPeopleAndDeviceAreHighRisk() {
        val highRisk = MetadataCategory.entries.filter { MetadataGroup(it, emptyList()).isHighRisk }
        assertEquals(listOf(MetadataCategory.Location, MetadataCategory.Device, MetadataCategory.People), highRisk)
    }

    @Test
    fun nothingFoundMeansNoGroups() {
        assertEquals(emptyList<MetadataGroup>(), MetadataGrouping.of(emptyList(), emptyMap()))
    }

    // endregion Methods
}
