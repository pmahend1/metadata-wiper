package com.prateekmahendrakar.metadatawiper.exif

import androidx.exifinterface.media.ExifInterface
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory

object ExifTagCategory {

    // region State

    private const val GPS_PREFIX = "GPS"

    private val tagCategories = mapOf(ExifInterface.TAG_MAKE to MetadataCategory.Device,
                                      ExifInterface.TAG_MODEL to MetadataCategory.Device,
                                      ExifInterface.TAG_BODY_SERIAL_NUMBER to MetadataCategory.Device,
                                      ExifInterface.TAG_LENS_MAKE to MetadataCategory.Device,
                                      ExifInterface.TAG_LENS_MODEL to MetadataCategory.Device,
                                      ExifInterface.TAG_LENS_SERIAL_NUMBER to MetadataCategory.Device,
                                      ExifInterface.TAG_IMAGE_UNIQUE_ID to MetadataCategory.Device,
                                      ExifInterface.TAG_MAKER_NOTE to MetadataCategory.Device,
                                      ExifInterface.TAG_CAMERA_OWNER_NAME to MetadataCategory.People,
                                      ExifInterface.TAG_ARTIST to MetadataCategory.People,
                                      ExifInterface.TAG_COPYRIGHT to MetadataCategory.People,
                                      ExifInterface.TAG_DATETIME to MetadataCategory.DateTime,
                                      ExifInterface.TAG_DATETIME_ORIGINAL to MetadataCategory.DateTime,
                                      ExifInterface.TAG_DATETIME_DIGITIZED to MetadataCategory.DateTime,
                                      ExifInterface.TAG_OFFSET_TIME to MetadataCategory.DateTime,
                                      ExifInterface.TAG_OFFSET_TIME_ORIGINAL to MetadataCategory.DateTime,
                                      ExifInterface.TAG_OFFSET_TIME_DIGITIZED to MetadataCategory.DateTime,
                                      ExifInterface.TAG_SUBSEC_TIME to MetadataCategory.DateTime,
                                      ExifInterface.TAG_SUBSEC_TIME_ORIGINAL to MetadataCategory.DateTime,
                                      ExifInterface.TAG_SUBSEC_TIME_DIGITIZED to MetadataCategory.DateTime,
                                      ExifInterface.TAG_SOFTWARE to MetadataCategory.Software,
                                      ExifInterface.TAG_ORIENTATION to MetadataCategory.Orientation,
                                      ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT to MetadataCategory.Thumbnail,
                                      ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH to MetadataCategory.Thumbnail,
                                      ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH to MetadataCategory.Thumbnail,
                                      ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH to MetadataCategory.Thumbnail)

    // endregion State

    // region Methods

    // A prefix match, so GPSProcessingMethod and GPSAreaInformation, which can name a place, count too.
    fun of(tag: String): MetadataCategory {
        if (tag.startsWith(GPS_PREFIX)) {
            return MetadataCategory.Location
        }
        return tagCategories[tag] ?: MetadataCategory.Other
    }

    // endregion Methods
}
