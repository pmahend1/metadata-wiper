package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind

object WarningText {

    // region Methods

    // Strings for the Unknown… kinds take the unrecognised types as their one argument.
    @StringRes
    fun of(kind: MetadataWarningKind): Int {
        return when (kind) {
            MetadataWarningKind.DamagedExif -> R.string.warning_damaged_exif
            MetadataWarningKind.ThumbnailKept -> R.string.warning_thumbnail_kept
            MetadataWarningKind.AuxiliaryImagesKept -> R.string.warning_auxiliary_kept
            MetadataWarningKind.SequenceTimestampsKept -> R.string.warning_sequence_kept
            MetadataWarningKind.UnknownBoxesKept -> R.string.warning_unknown_boxes
            MetadataWarningKind.UnknownItemTypesKept -> R.string.warning_unknown_items
            MetadataWarningKind.UnknownPropertiesKept -> R.string.warning_unknown_properties
        }
    }

    // endregion Methods
}
