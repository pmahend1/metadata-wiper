package com.prateekmahendrakar.metadatawiper.metadata

data class StripOptions(val keepColorProfile: Boolean = true,
                        val keepOrientation: Boolean = true) {

    // region Methods

    // Anything these options don't keep must be gone from a stripped file, which is what verification checks.
    fun keeps(item: MetadataItem): Boolean {
        return when (item.category) {
            MetadataCategory.ColorProfile -> keepColorProfile
            MetadataCategory.Orientation -> keepOrientation
            else -> false
        }
    }

    // Orientation 1 is the default, so a minimal EXIF is only worth writing for the other seven.
    fun orientationToWrite(found: Int?): Int? {
        if (keepOrientation == false || found == null || found !in 2..8) {
            return null
        }
        return found
    }

    // endregion Methods
}
