package com.prateekmahendrakar.metadatawiper.metadata.tiff

// valueOffset is relative to the start of the TIFF data, whether the value is inline or not.
data class TiffEntry(val tag: Int,
                     val type: Int,
                     val count: Long,
                     val valueOffset: Long,
                     val valueSize: Long)
