package com.prateekmahendrakar.metadatawiper.metadata.heif

/*
 * An item for HeifBuilder. [extents] splits [data] into that many `iloc` extents. A
 * [constructionMethod] of 1 stores the data in `idat`; 2 is written but its data isn't stored.
 * A [mediaOffset] points the item at that offset from the start of `mdat`'s data (negative
 * reaches before it) instead of storing its data.
 */
class HeifTestItem(val id: Int,
                   val type: String,
                   val data: ByteArray,
                   val name: String = "",
                   val contentType: String = "",
                   val constructionMethod: Int = 0,
                   val dataReferenceIndex: Int = 0,
                   val extents: Int = 1,
                   val mediaOffset: Long? = null)
