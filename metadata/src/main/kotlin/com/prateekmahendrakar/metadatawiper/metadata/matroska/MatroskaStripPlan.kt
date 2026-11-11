package com.prateekmahendrakar.metadatawiper.metadata.matroska

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange

// What a Matroska file holds and which bytes a strip overwrites (sorted, non-overlapping). [outputLength] is shorter only when data after the Segment is cut off.
data class MatroskaStripPlan(val items: List<MetadataItem>,
                             val warnings: List<MetadataWarning>,
                             val blanked: List<BlankedRange>,
                             val outputLength: Long)
