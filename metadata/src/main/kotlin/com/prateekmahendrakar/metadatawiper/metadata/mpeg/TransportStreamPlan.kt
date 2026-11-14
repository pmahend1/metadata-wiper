package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange

// What a transport stream holds and which bytes a strip overwrites (sorted, non-overlapping). Nothing is cut.
data class TransportStreamPlan(val items: List<MetadataItem>,
                               val warnings: List<MetadataWarning>,
                               val blanked: List<BlankedRange>)
