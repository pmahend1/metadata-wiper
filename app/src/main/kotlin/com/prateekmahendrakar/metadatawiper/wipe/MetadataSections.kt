package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions

/*
 * How the metadata view lays out a file's groups. Each category users care about gets a card
 * showing the handlers' readable findings; the raw EXIF tags behind them, and everything of
 * low interest, fold into one collapsed list so nothing is hidden. Kept items get their own card.
 */
data class MetadataSections(val cards: List<MetadataGroup>,
                            val more: List<MetadataRow>,
                            val kept: List<MetadataRow>) {

    // region State

    val isEmpty: Boolean
        get() = cards.isEmpty() && more.isEmpty() && kept.isEmpty()

    // endregion State

    companion object {

        // region State

        private val cardCategories = setOf(MetadataCategory.Location,
                                           MetadataCategory.People,
                                           MetadataCategory.Device,
                                           MetadataCategory.DateTime,
                                           MetadataCategory.Thumbnail,
                                           MetadataCategory.EmbeddedMedia)

        // endregion State

        // region Methods

        fun of(groups: List<MetadataGroup>,
               options: StripOptions): MetadataSections {
            val cards = mutableListOf<MetadataGroup>()
            val more = mutableListOf<MetadataRow>()
            val kept = mutableListOf<MetadataRow>()
            for (group in groups) {
                val structural = group.rows.filter { it.isStructural }
                when {
                    // The raw tag repeats what the finding already says, and it isn't removed, so it's left out.
                    options.keeps(group.category) -> kept += structural.ifEmpty { group.rows }
                    group.category in cardCategories && structural.isEmpty() -> cards += group
                    group.category in cardCategories -> {
                        cards += MetadataGroup(group.category, structural)
                        more += group.rows.filter { it.isStructural == false }
                    }
                    else -> more += group.rows
                }
            }
            return MetadataSections(cards, more, kept)
        }

        // endregion Methods
    }
}
