package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory

object MetadataCategoryLabel {

    // region Methods

    @StringRes
    fun of(category: MetadataCategory): Int {
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

    // endregion Methods
}
