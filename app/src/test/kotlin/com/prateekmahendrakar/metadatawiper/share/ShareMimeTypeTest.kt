package com.prateekmahendrakar.metadatawiper.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareMimeTypeTest {

    // region Methods

    @Test
    fun usesTheExactTypeWhenThereIsOnlyOne() {
        assertEquals("video/mp4", ShareMimeType.commonTo(listOf("video/mp4", "video/mp4")))
    }

    @Test
    fun widensToTheFamilyWhenTheFormatsDiffer() {
        assertEquals("image/*", ShareMimeType.commonTo(listOf("image/jpeg", "image/png")))
        assertEquals("video/*", ShareMimeType.commonTo(listOf("video/mp4", "video/quicktime")))
    }

    @Test
    fun widensToAnythingWhenTheFamiliesDiffer() {
        assertEquals("*/*", ShareMimeType.commonTo(listOf("image/jpeg", "video/mp4")))
    }

    // endregion Methods
}
