package com.prateekmahendrakar.metadatawiper.metadata.tiff

data class TiffIfd(val entries: Map<Int, TiffEntry>,
                   val nextIfdOffset: Long)
