package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind

object WarningText {

    // region Methods

    // Strings for the Unknown… kinds take the unrecognised types as their one argument, and UnreferencedDataKept its size.
    @StringRes
    fun of(kind: MetadataWarningKind): Int {
        return when (kind) {
            MetadataWarningKind.DamagedExif -> R.string.warning_damaged_exif
            MetadataWarningKind.ThumbnailKept -> R.string.warning_thumbnail_kept
            MetadataWarningKind.AuxiliaryImagesKept -> R.string.warning_auxiliary_kept
            MetadataWarningKind.SubtitleTracksKept -> R.string.warning_subtitles_kept
            MetadataWarningKind.ChaptersKept -> R.string.warning_chapters_kept
            MetadataWarningKind.InStreamMetadataNotChecked -> R.string.warning_in_stream_not_checked
            MetadataWarningKind.UnreferencedDataKept -> R.string.warning_unreferenced_kept
            MetadataWarningKind.UnknownBoxesKept -> R.string.warning_unknown_boxes
            MetadataWarningKind.UnknownItemTypesKept -> R.string.warning_unknown_items
            MetadataWarningKind.UnknownPropertiesKept -> R.string.warning_unknown_properties
            MetadataWarningKind.UnknownTracksKept -> R.string.warning_unknown_tracks
        }
    }

    // endregion Methods
}
