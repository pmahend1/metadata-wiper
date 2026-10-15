package com.prateekmahendrakar.metadatawiper.metadata

data class StripReport(val removed: List<MetadataItem>,
                       val kept: List<MetadataItem>,
                       val warnings: List<String>) {

    // region Methods

    companion object {

        // Orientation only counts as kept when the handler wrote it back, which it skips for the default of 1.
        fun of(items: List<MetadataItem>,
               warnings: List<String>,
               options: StripOptions,
               orientationWritten: Boolean): StripReport {
            val (kept, removed) = items.partition {
                when (it.category) {
                    MetadataCategory.ColorProfile -> options.keepColorProfile
                    MetadataCategory.Orientation -> orientationWritten
                    else -> false
                }
            }
            return StripReport(removed, kept, warnings)
        }
    }

    // endregion Methods
}
