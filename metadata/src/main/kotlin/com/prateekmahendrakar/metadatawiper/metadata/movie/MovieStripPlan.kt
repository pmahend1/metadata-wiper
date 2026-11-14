package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

/*
 * What a movie file holds and which bytes a strip overwrites (sorted, non-overlapping).
 * [blanked] never touches a sample; [sampleBlanked] is SEI user data inside kept video samples,
 * so a container that checks its own ranges against sample data checks only the first list.
 * [outputLength] is shorter than the input only when a trailer or removed boxes at the end of
 * the file are cut off. [keptChunks] are the sample bytes that must come through unchanged,
 * apart from [sampleBlanked].
 */
data class MovieStripPlan(val items: List<MetadataItem>,
                          val warnings: List<MetadataWarning>,
                          val blanked: List<BlankedRange>,
                          val sampleBlanked: List<BlankedRange>,
                          val outputLength: Long,
                          val keptChunks: List<FileSpan>) {

    // region State

    val allBlanked: List<BlankedRange>
        get() = (blanked + sampleBlanked).sortedBy { it.span.start }

    // endregion State
}
