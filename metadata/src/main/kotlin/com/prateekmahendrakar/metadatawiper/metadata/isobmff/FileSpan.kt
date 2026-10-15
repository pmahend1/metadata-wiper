package com.prateekmahendrakar.metadatawiper.metadata.isobmff

// Absolute file offsets; [end] is exclusive.
data class FileSpan(val start: Long,
                    val end: Long) {

    // region Methods

    val length: Long
        get() = end - start

    fun overlaps(other: FileSpan): Boolean = start < other.end && other.start < end

    fun contains(other: FileSpan): Boolean = start <= other.start && other.end <= end

    // endregion Methods
}
