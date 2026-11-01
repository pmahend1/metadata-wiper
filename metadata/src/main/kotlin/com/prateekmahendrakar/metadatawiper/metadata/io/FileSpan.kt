package com.prateekmahendrakar.metadatawiper.metadata.io

// Absolute file offsets; [end] is exclusive.
data class FileSpan(val start: Long,
                    val end: Long) {

    // region State

    val length: Long
        get() = end - start

    companion object {

        // Sorted by start, with overlapping and touching spans joined into one.
        fun merged(spans: List<FileSpan>): List<FileSpan> {
            val joined = mutableListOf<FileSpan>()
            for (span in spans.sortedBy { it.start }) {
                val last = joined.lastOrNull()
                if (last != null && span.start <= last.end) {
                    joined[joined.lastIndex] = FileSpan(last.start, maxOf(last.end, span.end))
                } else {
                    joined += span
                }
            }
            return joined
        }

        // The first of [sorted] (by start) that any of [others] overlaps. A sweep, because a long video has a million chunks.
        fun firstOverlap(sorted: List<FileSpan>,
                         others: List<FileSpan>): FileSpan? {
            val othersByStart = others.sortedBy { it.start }
            var next = 0
            var reach = 0L
            for (span in sorted) {
                while (next < othersByStart.size && othersByStart[next].start < span.end) {
                    reach = maxOf(reach, othersByStart[next].end)
                    next++
                }
                if (reach > span.start) {
                    return span
                }
            }
            return null
        }
    }

    // endregion State

    // region Methods

    fun overlaps(other: FileSpan): Boolean = start < other.end && other.start < end

    fun contains(other: FileSpan): Boolean = start <= other.start && other.end <= end

    // endregion Methods
}
