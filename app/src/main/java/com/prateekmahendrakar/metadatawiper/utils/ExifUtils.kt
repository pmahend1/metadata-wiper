package com.prateekmahendrakar.metadatawiper.utils

import androidx.exifinterface.media.ExifInterface

fun Double.formatted(): String {
    return if (this % 1.0 == 0.0) {
        this.toInt().toString()
    } else {
        this.toString()
    }
}


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
    return ExifInterface::class.java.fields.filter { it.name.startsWith("TAG_") && it.type == String::class.java }
        .map { it.get(null) as String }.toTypedArray()
}

fun getRemovableExifTags(): Array<String> {
    // This function gets all tags EXCEPT the essential ones
    val essentialTags = getEssentialExifTags()
    return getAllExifTags().filterNot { it in essentialTags }.toTypedArray()
}

fun getFormattedExifValue(exif: ExifInterface, tag: String): String {
    val value = exif.getAttribute(tag)
    val textOutput = when (tag) {
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_PIXEL_X_DIMENSION,
        ExifInterface.TAG_PIXEL_Y_DIMENSION -> "${value}px"

        ExifInterface.TAG_ORIENTATION -> when (value?.toIntOrNull()) {
            ExifInterface.ORIENTATION_NORMAL -> "Normal"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Mirror Horizontal"
            ExifInterface.ORIENTATION_ROTATE_180 -> "Rotate 180"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Mirror Vertical"
            ExifInterface.ORIENTATION_TRANSPOSE -> "Mirror horizontal and rotate 270 Clockwise"
            ExifInterface.ORIENTATION_ROTATE_90 -> "Rotate 90 Clockwise"
            ExifInterface.ORIENTATION_TRANSVERSE -> "Mirror horizontal and rotate 90 Clockwise"
            ExifInterface.ORIENTATION_ROTATE_270 -> "Rotate 270 Clockwise"
            else -> "Undefined"
        }

        ExifInterface.TAG_RESOLUTION_UNIT -> when (value?.toShortOrNull()) {
            ExifInterface.RESOLUTION_UNIT_INCHES -> "Inches"
            ExifInterface.RESOLUTION_UNIT_CENTIMETERS -> "Centimeters"
            else -> "Undefined"
        }

        ExifInterface.TAG_FLASH -> when (value?.toShortOrNull()) {
            ExifInterface.FLAG_FLASH_FIRED -> "Fired"
            ExifInterface.FLAG_FLASH_RETURN_LIGHT_NOT_DETECTED -> "Fired, return light not detected"
            ExifInterface.FLAG_FLASH_RETURN_LIGHT_DETECTED -> "Fired, return light detected"
            ExifInterface.FLAG_FLASH_MODE_COMPULSORY_FIRING -> "Compulsory firing"
            ExifInterface.FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION -> "Compulsory suppression"
            ExifInterface.FLAG_FLASH_MODE_AUTO -> "Auto"
            ExifInterface.FLAG_FLASH_NO_FLASH_FUNCTION -> "No flash function"
            ExifInterface.FLAG_FLASH_RED_EYE_SUPPORTED -> "Red eye reduction"
            else -> "Unknown"
        }

        ExifInterface.TAG_LIGHT_SOURCE -> when (value?.toShortOrNull()) {
            ExifInterface.LIGHT_SOURCE_DAYLIGHT -> "Daylight"
            ExifInterface.LIGHT_SOURCE_FLUORESCENT -> "Fluorescent"
            ExifInterface.LIGHT_SOURCE_TUNGSTEN -> "Tungsten"
            ExifInterface.LIGHT_SOURCE_FLASH -> "Flash"
            ExifInterface.LIGHT_SOURCE_FINE_WEATHER -> "Fine Weather"
            ExifInterface.LIGHT_SOURCE_CLOUDY_WEATHER -> "Cloudy"
            ExifInterface.LIGHT_SOURCE_SHADE -> "Shade"
            ExifInterface.LIGHT_SOURCE_DAYLIGHT_FLUORESCENT -> "Daylight Fluorescent"
            ExifInterface.LIGHT_SOURCE_DAY_WHITE_FLUORESCENT -> "Day white Fluorescent"
            ExifInterface.LIGHT_SOURCE_COOL_WHITE_FLUORESCENT -> "Cool white Fluorescent"
            ExifInterface.LIGHT_SOURCE_WHITE_FLUORESCENT -> "White Fluorescent"
            ExifInterface.LIGHT_SOURCE_WARM_WHITE_FLUORESCENT -> "Warm white Fluorescent"
            ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_A -> "Standard Light A"
            ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_B -> "Standard Light B"
            ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_C -> "Standard Light C"
            ExifInterface.LIGHT_SOURCE_D55 -> "D55"
            ExifInterface.LIGHT_SOURCE_D65 -> "D65"
            ExifInterface.LIGHT_SOURCE_D75 -> "D75"
            ExifInterface.LIGHT_SOURCE_D50 -> "D50"
            ExifInterface.LIGHT_SOURCE_ISO_STUDIO_TUNGSTEN -> "ISO Studio Tungsten"
            ExifInterface.LIGHT_SOURCE_OTHER -> "Other"
            ExifInterface.LIGHT_SOURCE_UNKNOWN -> "Unknown"
            else -> "Undefined"
        }

        ExifInterface.TAG_COLOR_SPACE -> when (value?.toIntOrNull()) {
            ExifInterface.COLOR_SPACE_S_RGB -> "sRGB"
            ExifInterface.COLOR_SPACE_UNCALIBRATED -> "Uncalibrated"
            else -> "Undefined"
        }

        ExifInterface.TAG_METERING_MODE -> when (value?.toShortOrNull()) {
            ExifInterface.METERING_MODE_AVERAGE -> "Average"
            ExifInterface.METERING_MODE_CENTER_WEIGHT_AVERAGE -> "Center-weighted average"
            ExifInterface.METERING_MODE_SPOT -> "Spot"
            ExifInterface.METERING_MODE_MULTI_SPOT -> "Multi-spot"
            ExifInterface.METERING_MODE_PATTERN -> "Pattern"
            ExifInterface.METERING_MODE_PARTIAL -> "Partial"
            ExifInterface.METERING_MODE_OTHER -> "Other"
            ExifInterface.METERING_MODE_UNKNOWN -> "Unknown"
            else -> "Unknown"
        }

        ExifInterface.TAG_COMPRESSION -> when (value?.toIntOrNull()) {
            ExifInterface.DATA_UNCOMPRESSED -> "Uncompressed"
            ExifInterface.DATA_HUFFMAN_COMPRESSED -> "Huffman compressed"
            ExifInterface.DATA_JPEG -> "JPEG"
            ExifInterface.DATA_JPEG_COMPRESSED -> "JPEG compressed"
            ExifInterface.DATA_DEFLATE_ZIP -> "Deflate (ZIP)"
            ExifInterface.DATA_PACK_BITS_COMPRESSED -> "PackBits compressed"
            ExifInterface.DATA_LOSSY_JPEG -> "Lossy JPEG"
            else -> "Unknown"
        }

        ExifInterface.TAG_BRIGHTNESS_VALUE -> when (val brightness = exif.getAttributeDouble(tag, 0.0)) {
            0.0 -> "Unknown"
            else -> "$brightness"
        }

        ExifInterface.TAG_Y_RESOLUTION,
        ExifInterface.TAG_X_RESOLUTION -> {
            val resolution = exif.getAttributeDouble(tag, 72.0)
            resolution.formatted()
        }

        ExifInterface.TAG_SENSING_METHOD -> when (value?.toShortOrNull()) {
            ExifInterface.SENSOR_TYPE_ONE_CHIP -> "One-chip color area sensor"
            ExifInterface.SENSOR_TYPE_TWO_CHIP -> "Two-chip color area sensor"
            ExifInterface.SENSOR_TYPE_THREE_CHIP -> "Three-chip color area sensor"
            ExifInterface.SENSOR_TYPE_COLOR_SEQUENTIAL -> "Color sequential area sensor"
            ExifInterface.SENSOR_TYPE_TRILINEAR -> "Trilinear sensor"
            ExifInterface.SENSOR_TYPE_COLOR_SEQUENTIAL_LINEAR -> "Color sequential linear sensor"
            ExifInterface.SENSOR_TYPE_NOT_DEFINED -> "Not defined"
            else -> "Not defined"
        }

        ExifInterface.TAG_WHITE_BALANCE -> when (value?.toShortOrNull()) {
            ExifInterface.WHITE_BALANCE_AUTO -> "Auto"
            ExifInterface.WHITE_BALANCE_MANUAL -> "Manual"
            else -> "Unknown"
        }

        ExifInterface.TAG_CONTRAST -> when (value?.toShortOrNull()) {
            ExifInterface.CONTRAST_SOFT -> "Soft"
            ExifInterface.CONTRAST_HARD -> "Hard"
            ExifInterface.CONTRAST_NORMAL -> "Normal"
            else -> "Normal"
        }

        ExifInterface.TAG_COMPONENTS_CONFIGURATION -> when (value) {
            "4560" -> "RGB Uncompressed"
            "1230" -> "YCbCr"
            else -> value ?: ""
        }

        ExifInterface.TAG_APERTURE_VALUE -> when (val apertureValue = exif.getAttributeDouble(tag, 0.0)) {
            apertureValue -> apertureValue.formatted()
            else -> "Unknown"
        }

        ExifInterface.TAG_DIGITAL_ZOOM_RATIO -> when (val doubleValue = exif.getAttributeDouble(tag, 0.0)) {
            doubleValue -> doubleValue.formatted()
            else -> "Unknown"
        }

        else -> {
            value ?: ""
        }
    }
    return textOutput
}
