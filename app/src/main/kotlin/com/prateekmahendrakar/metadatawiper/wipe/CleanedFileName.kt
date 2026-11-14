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

    // Other names for a format. An M4V is an MP4, but people expect it to stay an M4V, and a camcorder's .MTS an .mts.
    private val extensionAliases = mapOf(FileFormat.Mp4 to setOf("m4v"),
                                         FileFormat.MpegTs to setOf("m2ts", "mts", "m2t"))

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
        return withExtension(baseName + SUFFIX, extensionFor(originalName, format))
    }

    fun random(format: FileFormat,
               random: Random = Random.Default): String {
        val baseName = (1..RANDOM_NAME_LENGTH).map { HEX_DIGITS[random.nextInt(HEX_DIGITS.length)] }
                                              .joinToString("")
        return withExtension(baseName, format.extension)
    }

    private fun extensionFor(originalName: String?,
                             format: FileFormat): String {
        val originalExtension = originalName?.substringAfterLast('.', "")?.lowercase()
        return if (originalExtension in extensionAliases[format].orEmpty()) originalExtension.orEmpty() else format.extension
    }

    private fun withExtension(baseName: String,
                              extension: String): String {
        if (extension.isEmpty()) {
            return baseName
        }
        return "$baseName.$extension"
    }

    // endregion Methods
}
