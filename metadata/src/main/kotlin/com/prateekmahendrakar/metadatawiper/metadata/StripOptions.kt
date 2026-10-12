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

    // endregion Methods
}
