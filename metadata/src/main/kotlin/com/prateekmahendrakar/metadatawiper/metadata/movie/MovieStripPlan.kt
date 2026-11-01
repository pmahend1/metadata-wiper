package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

/*
 * What a movie file holds and which bytes a strip overwrites (sorted, non-overlapping).
 * [outputLength] is shorter than the input only when a trailer or removed boxes at the end of
 * the file are cut off. [keptChunks] are the sample bytes that must come through unchanged.
 */
data class MovieStripPlan(val items: List<MetadataItem>,
                          val warnings: List<MetadataWarning>,
                          val blanked: List<BlankedRange>,
                          val outputLength: Long,
                          val keptChunks: List<FileSpan>)
