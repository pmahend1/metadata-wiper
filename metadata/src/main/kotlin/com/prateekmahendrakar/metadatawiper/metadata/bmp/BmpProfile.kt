package com.prateekmahendrakar.metadatawiper.metadata.bmp

// The color profile a BITMAPV5HEADER points at: an ICC file path when linked, the ICC data itself when embedded.
data class BmpProfile(val isLinked: Boolean,
                      val start: Long,
                      val length: Long)
