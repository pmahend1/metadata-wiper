package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory

data class MetadataGroup(val category: MetadataCategory,
                         val rows: List<MetadataRow>) {

    // region State

    val isHighRisk: Boolean
        get() = MetadataGrouping.isHighRisk(category)

    // endregion State
}
