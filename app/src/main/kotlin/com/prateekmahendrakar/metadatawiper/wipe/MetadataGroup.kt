package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory

data class MetadataGroup(val category: MetadataCategory,
                         val rows: List<MetadataRow>) {

    // region State

    // The categories that most directly identify where someone was, who they are, or what they own.
    val isHighRisk: Boolean
        get() = category == MetadataCategory.Location || category == MetadataCategory.People || category == MetadataCategory.Device

    // endregion State
}
