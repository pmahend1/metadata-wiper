package com.prateekmahendrakar.metadatawiper.share

object ShareMimeType {

    // region State

    private const val ANY = "*/*"

    // endregion State

    // region Methods

    // The narrowest type that covers every file: exact, then image/* or video/*, then anything.
    fun commonTo(mimeTypes: List<String>): String {
        val distinctTypes = mimeTypes.distinct()
        distinctTypes.singleOrNull()?.let { return it }
        return distinctTypes.map { it.substringBefore('/') }
                            .distinct()
                            .singleOrNull()
                            ?.let { "$it/*" }
               ?: ANY
    }

    // endregion Methods
}
