package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanedFileNameTest {

    // region Methods

    @Test
    fun replacesExtensionWithDetectedFormat() {
        assertEquals("IMG_1234_cleaned.jpg", CleanedFileName.of("IMG_1234.JPEG", FileFormat.Jpeg))
        assertEquals("photo_cleaned.png", CleanedFileName.of("photo.jpg", FileFormat.Png))
    }

    @Test
    fun keepsDotsInsideTheBaseName() {
        assertEquals("my.holiday_cleaned.jpg", CleanedFileName.of("my.holiday.jpg", FileFormat.Jpeg))
    }

    @Test
    fun addsExtensionWhenNameHasNone() {
        assertEquals("photo_cleaned.jpg", CleanedFileName.of("photo", FileFormat.Jpeg))
    }

    @Test
    fun fallsBackWhenNameIsMissingOrBlank() {
        assertEquals("file_cleaned.jpg", CleanedFileName.of(null, FileFormat.Jpeg))
        assertEquals("file_cleaned.jpg", CleanedFileName.of(".jpg", FileFormat.Jpeg))
        assertEquals("file_cleaned.jpg", CleanedFileName.of("", FileFormat.Jpeg))
    }

    @Test
    fun omitsExtensionForUnknownFormat() {
        assertEquals("notes_cleaned", CleanedFileName.of("notes.bin", FileFormat.Unknown))
    }

    // endregion Methods
}
