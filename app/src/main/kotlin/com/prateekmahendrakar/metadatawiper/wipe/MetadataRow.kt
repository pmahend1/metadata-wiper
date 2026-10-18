package com.prateekmahendrakar.metadatawiper.wipe

data class MetadataRow(val label: String,
                       val value: String,
                       val byteSize: Long = 0,
                       val isStructural: Boolean = false)
