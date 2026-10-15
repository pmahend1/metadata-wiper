package com.prateekmahendrakar.metadatawiper.metadata.heif

// One `iref` entry, such as `thmb` from a thumbnail to its image or `cdsc` from metadata to the image it describes.
data class HeifReference(val type: String,
                         val fromId: Long,
                         val toIds: List<Long>)
