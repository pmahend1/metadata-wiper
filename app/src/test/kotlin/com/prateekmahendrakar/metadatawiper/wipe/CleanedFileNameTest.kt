package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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

    @Test
    fun randomNameUsesHexDigitsAndDetectedExtension() {
        val name = CleanedFileName.random(FileFormat.Png, Random(42))
        assertTrue(name, Regex("[0-9a-f]{12}\\.png").matches(name))
    }

    @Test
    fun randomNameDiffersBetweenCalls() {
        val random = Random(7)
        assertNotEquals(CleanedFileName.random(FileFormat.Jpeg, random), CleanedFileName.random(FileFormat.Jpeg, random))
    }

    @Test
    fun forOutputDropsTheOriginalNameWhenRandomized() {
        assertEquals("IMG_20260912_143000_cleaned.jpg", CleanedFileName.forOutput("IMG_20260912_143000.jpg", FileFormat.Jpeg, randomize = false))
        assertFalse(CleanedFileName.forOutput("IMG_20260912_143000.jpg", FileFormat.Jpeg, randomize = true).contains("2026"))
    }

    // endregion Methods
}
