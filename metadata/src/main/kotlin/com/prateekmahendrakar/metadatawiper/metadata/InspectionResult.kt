package com.prateekmahendrakar.metadatawiper.metadata

data class InspectionResult(val format: FileFormat,
                            val items: List<MetadataItem>,
                            val warnings: List<String> = emptyList())
