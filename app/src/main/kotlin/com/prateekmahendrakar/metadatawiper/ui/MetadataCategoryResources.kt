package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory

object MetadataCategoryResources {

    // region Methods

    @StringRes
    fun label(category: MetadataCategory): Int {
        return when (category) {
            MetadataCategory.Location -> R.string.category_location
            MetadataCategory.People -> R.string.category_people
            MetadataCategory.Device -> R.string.category_device
            MetadataCategory.DateTime -> R.string.category_date_time
            MetadataCategory.Software -> R.string.category_software
            MetadataCategory.Thumbnail -> R.string.category_thumbnail
            MetadataCategory.EmbeddedMedia -> R.string.category_embedded_media
            MetadataCategory.Other -> R.string.category_other
            MetadataCategory.ColorProfile -> R.string.category_color_profile
            MetadataCategory.Orientation -> R.string.category_orientation
        }
    }

    // Only the categories that get their own card explain themselves.
    @StringRes
    fun reason(category: MetadataCategory): Int? {
        return when (category) {
            MetadataCategory.Location -> R.string.reason_location
            MetadataCategory.People -> R.string.reason_people
            MetadataCategory.Device -> R.string.reason_device
            MetadataCategory.DateTime -> R.string.reason_date_time
            MetadataCategory.Thumbnail -> R.string.reason_thumbnail
            MetadataCategory.EmbeddedMedia -> R.string.reason_embedded_media
            else -> null
        }
    }

    @DrawableRes
    fun icon(category: MetadataCategory): Int {
        return when (category) {
            MetadataCategory.Location -> R.drawable.location_on_24px
            MetadataCategory.People -> R.drawable.person_24px
            MetadataCategory.Device -> R.drawable.smartphone_24px
            MetadataCategory.DateTime -> R.drawable.schedule_24px
            MetadataCategory.Thumbnail -> R.drawable.crop_24px
            MetadataCategory.EmbeddedMedia -> R.drawable.movie_24px
            else -> R.drawable.photo_camera_24px
        }
    }

    // endregion Methods
}
