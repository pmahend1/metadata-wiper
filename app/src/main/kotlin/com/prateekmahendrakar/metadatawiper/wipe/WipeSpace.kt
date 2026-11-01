package com.prateekmahendrakar.metadatawiper.wipe

// Every handler writes at most about the input's size, so that size is the estimate for a temp copy.
object WipeSpace {

    // region State

    private const val HEADROOM_BYTES = 64L * 1024 * 1024

    // endregion State

    // region Methods

    fun neededFor(byteSize: Long): Long = byteSize + HEADROOM_BYTES

    // Each temp copy is deleted before the next file starts, so only the largest one counts.
    fun neededToSave(byteSizes: List<Long>): Long = byteSizes.maxOrNull()?.let { neededFor(it) } ?: 0L

    // Cleaned copies stay in the cache until the share is done, so they add up.
    fun neededToShare(byteSizes: List<Long>): Long = if (byteSizes.isEmpty()) 0L else neededFor(byteSizes.sum())

    // endregion Methods
}
