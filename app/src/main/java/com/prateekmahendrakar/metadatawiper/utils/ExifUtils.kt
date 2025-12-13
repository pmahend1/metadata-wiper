package com.prateekmahendrakar.metadatawiper.utils

import androidx.exifinterface.media.ExifInterface

fun getEssentialExifTags(): Set<String> {
    return setOf(
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifInterface.TAG_SAMPLES_PER_PIXEL,
        ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_COMPRESSION,
        ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_ROWS_PER_STRIP,
        ExifInterface.TAG_STRIP_BYTE_COUNTS,
        ExifInterface.TAG_STRIP_OFFSETS,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
    )
}

fun getAllExifTags(): Array<String> {
    // This function remains to get ALL data for display purposes
    return ExifInterface::class.java.fields
        .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
        .map { it.get(null) as String }
        .toTypedArray()
}

fun getRemovableExifTags(): Array<String> {
    // This function gets all tags EXCEPT the essential ones
    val essentialTags = getEssentialExifTags()
    return getAllExifTags().filterNot { it in essentialTags }.toTypedArray()
}
