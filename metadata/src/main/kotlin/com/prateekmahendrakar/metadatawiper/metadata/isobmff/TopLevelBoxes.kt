package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

// The boxes at the top of a file, and the bytes after the last one when they don't form a box (vendor trailers).
data class TopLevelBoxes(val boxes: List<Box>,
                         val trailing: FileSpan?)
