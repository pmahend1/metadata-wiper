package com.prateekmahendrakar.metadatawiper.metadata.isobmff

// As stored in `iloc`: relative to the item's base offset. A length of 0 means "to the end of the data".
data class ItemExtent(val offset: Long,
                      val length: Long)
