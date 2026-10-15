package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box

// One `ipco` child, by its 1-based [index] as `ipma` refers to it. [subtype] is a `colr` colour type or an `auxC` URN.
data class HeifProperty(val index: Int,
                        val box: Box,
                        val subtype: String)
