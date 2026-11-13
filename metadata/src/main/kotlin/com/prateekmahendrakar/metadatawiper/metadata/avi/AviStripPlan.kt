package com.prateekmahendrakar.metadatawiper.metadata.avi

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange

// What an AVI holds and which bytes a strip overwrites (sorted, non-overlapping). [outputLength] is shorter only when data after the last RIFF is cut off.
data class AviStripPlan(val items: List<MetadataItem>,
                        val warnings: List<MetadataWarning>,
                        val blanked: List<BlankedRange>,
                        val outputLength: Long)
