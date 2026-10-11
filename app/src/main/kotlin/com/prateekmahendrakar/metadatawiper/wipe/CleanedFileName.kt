package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat

// The extension comes from the detected format, not the original name, which may be wrong or missing.
object CleanedFileName {

    // region State

    private const val SUFFIX = "_cleaned"
    private const val FALLBACK_BASE_NAME = "file"

    // endregion State

    // region Methods

    fun of(originalName: String?,
           format: FileFormat): String {
        val baseName = originalName?.substringBeforeLast('.')
                                   ?.takeIf { it.isNotBlank() }
                       ?: FALLBACK_BASE_NAME
        if (format.extension.isEmpty()) {
            return baseName + SUFFIX
        }
        return "$baseName$SUFFIX.${format.extension}"
    }

    // endregion Methods
}
