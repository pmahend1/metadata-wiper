package com.prateekmahendrakar.metadatawiper.metadata.tiff

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem

data class TiffSummary(val orientation: Int?,
                       val items: List<MetadataItem>)
