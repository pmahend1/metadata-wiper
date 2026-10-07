package com.prateekmahendrakar.metadatawiper.metadata

data class MetadataItem(val category: MetadataCategory,
                        val label: String,
                        val detail: String = "",
                        val byteSize: Long = 0)
