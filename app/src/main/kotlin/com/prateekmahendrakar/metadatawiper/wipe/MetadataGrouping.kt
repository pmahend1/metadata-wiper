package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.exif.ExifTagCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem

object MetadataGrouping {

    // region State

    private val riskOrder = listOf(MetadataCategory.Location,
                                   MetadataCategory.People,
                                   MetadataCategory.Device,
                                   MetadataCategory.DateTime,
                                   MetadataCategory.Software,
                                   MetadataCategory.Thumbnail,
                                   MetadataCategory.EmbeddedMedia,
                                   MetadataCategory.Other,
                                   MetadataCategory.ColorProfile,
                                   MetadataCategory.Orientation)

    // The categories that most directly identify where someone was, who they are, or what they own.
    private val highRiskCategories = setOf(MetadataCategory.Location, MetadataCategory.People, MetadataCategory.Device)

    // endregion State

    // region Methods

    fun isHighRisk(category: MetadataCategory): Boolean = category in highRiskCategories

    fun riskRank(category: MetadataCategory): Int = riskOrder.indexOf(category)

    // Structural findings come first in each group: they name the block, the tags below give its contents.
    fun of(items: List<MetadataItem>,
           exifDetails: Map<String, String>): List<MetadataGroup> {
        val structuralRows = items.map { it.category to MetadataRow(it.label, it.detail, it.byteSize, isStructural = true) }
        val exifRows = exifDetails.entries.sortedBy { it.key }
                                          .map { (tag, value) -> ExifTagCategory.of(tag) to MetadataRow(tag, value) }
        val rowsByCategory = (structuralRows + exifRows).groupBy({ it.first }, { it.second })
        return riskOrder.mapNotNull { category ->
            rowsByCategory[category]?.let { MetadataGroup(category, it) }
        }
    }

    // endregion Methods
}
