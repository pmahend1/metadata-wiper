package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BlankedRange

// What a HEIF file holds and, for the options it was planned with, which bytes a strip overwrites (sorted, non-overlapping).
data class HeifStripPlan(val items: List<MetadataItem>,
                         val warnings: List<String>,
                         val blanked: List<BlankedRange>)
