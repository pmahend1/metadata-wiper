package com.prateekmahendrakar.metadatawiper.metadata.tiff

import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem

data class ExifInspection(val items: List<MetadataItem>,
                          val orientation: Int?,
                          val isDamaged: Boolean)
