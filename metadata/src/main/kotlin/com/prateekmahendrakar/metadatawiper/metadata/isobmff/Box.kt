package com.prateekmahendrakar.metadatawiper.metadata.isobmff

/*
 * One ISO BMFF box as absolute file offsets. [headerEnd] follows the size and type fields,
 * including a 64-bit size if there is one; [dataStart] also skips a `uuid` box's extended
 * type. [end] is exclusive, so [start, end) is the whole box.
 */
data class Box(val type: String,
               val start: Long,
               val headerEnd: Long,
               val dataStart: Long,
               val end: Long)
