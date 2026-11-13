package com.prateekmahendrakar.metadatawiper.metadata.ebml

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

// Absolute offsets of one element: its ID and size, then its data up to [end] (exclusive). [depth] is 0 for the EBML header and Segment.
data class EbmlElement(val id: Long,
                       val start: Long,
                       val dataStart: Long,
                       val end: Long,
                       val depth: Int) {

    // region State

    val span: FileSpan
        get() = FileSpan(start, end)

    val dataSpan: FileSpan
        get() = FileSpan(dataStart, end)

    val dataLength: Long
        get() = end - dataStart

    // endregion State
}
