package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BlankedRange

/*
 * What a HEIF file holds and, for the options it was planned with, which bytes a strip
 * overwrites (sorted, non-overlapping) and where the output ends. [outputLength] is shorter than
 * the input only when removed boxes at the end of the file are cut off.
 */
data class HeifStripPlan(val items: List<MetadataItem>,
                         val warnings: List<MetadataWarning>,
                         val blanked: List<BlankedRange>,
                         val outputLength: Long)
