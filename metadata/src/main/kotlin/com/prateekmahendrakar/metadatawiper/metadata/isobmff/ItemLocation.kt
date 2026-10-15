package com.prateekmahendrakar.metadatawiper.metadata.isobmff

/*
 * One `iloc` entry. Construction method 0 places the extents in this file, 1 inside the `idat`
 * box, and 2 inside other items. A non-zero data reference index means the data lives in
 * another file entirely.
 */
data class ItemLocation(val itemId: Long,
                        val constructionMethod: Int,
                        val dataReferenceIndex: Int,
                        val baseOffset: Long,
                        val extents: List<ItemExtent>) {

    // region State

    companion object {
        const val FILE_OFFSET = 0
        const val IDAT_OFFSET = 1
        const val ITEM_OFFSET = 2
    }

    // endregion State
}
