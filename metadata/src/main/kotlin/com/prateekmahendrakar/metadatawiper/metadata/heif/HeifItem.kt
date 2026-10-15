package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.FileSpan

// One `infe` entry. [name] spans the item name's bytes, without its terminator; [contentType] is only set for `mime` items.
data class HeifItem(val id: Long,
                    val type: String,
                    val name: FileSpan,
                    val contentType: String,
                    val contentEncoding: String)
