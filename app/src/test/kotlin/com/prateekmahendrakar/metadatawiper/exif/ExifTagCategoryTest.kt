package com.prateekmahendrakar.metadatawiper.exif

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ExifTagCategoryTest {

    // region Methods

    @Test
    fun everyGpsTagIsLocation() {
        listOf("GPSLatitude", "GPSLongitude", "GPSProcessingMethod", "GPSAreaInformation").forEach {
            assertEquals(it, MetadataCategory.Location, ExifTagCategory.of(it))
        }
    }

    @Test
    fun identifyingTagsMatchTheirCategories() {
        assertEquals(MetadataCategory.Device, ExifTagCategory.of("Make"))
        assertEquals(MetadataCategory.Device, ExifTagCategory.of("BodySerialNumber"))
        assertEquals(MetadataCategory.Device, ExifTagCategory.of("LensSerialNumber"))
        assertEquals(MetadataCategory.People, ExifTagCategory.of("CameraOwnerName"))
        assertEquals(MetadataCategory.People, ExifTagCategory.of("Artist"))
        assertEquals(MetadataCategory.DateTime, ExifTagCategory.of("DateTimeOriginal"))
        assertEquals(MetadataCategory.Software, ExifTagCategory.of("Software"))
        assertEquals(MetadataCategory.Orientation, ExifTagCategory.of("Orientation"))
        assertEquals(MetadataCategory.Thumbnail, ExifTagCategory.of("JPEGInterchangeFormat"))
    }

    @Test
    fun cameraSettingsAreOther() {
        assertEquals(MetadataCategory.Other, ExifTagCategory.of("FNumber"))
        assertEquals(MetadataCategory.Other, ExifTagCategory.of("ExposureTime"))
    }

    // endregion Methods
}
