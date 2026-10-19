package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import kotlin.random.Random

// The extension comes from the detected format, not the original name, which may be wrong or missing.
object CleanedFileName {

    // region State

    private const val SUFFIX = "_cleaned"
    private const val FALLBACK_BASE_NAME = "file"
    private const val RANDOM_NAME_LENGTH = 12
    private const val HEX_DIGITS = "0123456789abcdef"

    // endregion State

    // region Methods

    // Names like IMG_20260912_143000 reveal when a photo was taken, so the user can ask for a random one.
    fun forOutput(originalName: String?,
                  format: FileFormat,
                  randomize: Boolean): String {
        return if (randomize) random(format) else of(originalName, format)
    }

    fun of(originalName: String?,
           format: FileFormat): String {
        val baseName = originalName?.substringBeforeLast('.')
                                   ?.takeIf { it.isNotBlank() }
                       ?: FALLBACK_BASE_NAME
        return withExtension(baseName + SUFFIX, format)
    }

    fun random(format: FileFormat,
               random: Random = Random.Default): String {
        val baseName = (1..RANDOM_NAME_LENGTH).map { HEX_DIGITS[random.nextInt(HEX_DIGITS.length)] }
                                              .joinToString("")
        return withExtension(baseName, format)
    }

    private fun withExtension(baseName: String,
                              format: FileFormat): String {
        if (format.extension.isEmpty()) {
            return baseName
        }
        return "$baseName.${format.extension}"
    }

    // endregion Methods
}
