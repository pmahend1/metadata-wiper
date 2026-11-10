package com.prateekmahendrakar.metadatawiper.wipe

import org.junit.Assert.assertEquals
import org.junit.Test

class WipeSpaceTest {

    // region State

    private val headroom = WipeSpace.neededFor(0)

    // endregion State

    // region Methods

    @Test
    fun savingOnlyNeedsRoomForTheLargestFile() {
        assertEquals(500L + headroom, WipeSpace.neededToSave(listOf(100L, 500L, 300L)))
    }

    @Test
    fun sharingNeedsRoomForEveryFile() {
        assertEquals(900L + headroom, WipeSpace.neededToShare(listOf(100L, 500L, 300L)))
    }

    @Test
    fun nothingToCleanNeedsNoSpace() {
        assertEquals(0L, WipeSpace.neededToSave(emptyList()))
        assertEquals(0L, WipeSpace.neededToShare(emptyList()))
    }

    // endregion Methods
}
