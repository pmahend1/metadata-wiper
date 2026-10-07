package com.prateekmahendrakar.metadatawiper.metadata

data class StripReport(val removed: List<MetadataItem>,
                       val kept: List<MetadataItem>,
                       val warnings: List<String>)