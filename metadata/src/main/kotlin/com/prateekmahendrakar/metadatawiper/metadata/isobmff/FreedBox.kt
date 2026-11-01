package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

object FreedBox {

    // region State

    private const val TYPE_OFFSET = 4L
    private const val TYPE_SIZE = 4L

    private val freeType = "free".toByteArray(Charsets.US_ASCII)

    // endregion State

    // region Methods

    // The same box as `free`, zeroed after its size fields, including a `uuid` box's extended type.
    fun ranges(box: Box): List<BlankedRange> {
        return listOf(BlankedRange(FileSpan(box.start + TYPE_OFFSET, box.start + TYPE_OFFSET + TYPE_SIZE), freeType),
                      BlankedRange(FileSpan(box.headerEnd, box.end)))
    }

    // endregion Methods
}
