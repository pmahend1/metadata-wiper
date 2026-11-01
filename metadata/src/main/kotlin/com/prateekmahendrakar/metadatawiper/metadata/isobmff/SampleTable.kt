package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

/*
 * Where one track's media data lives: a span per chunk, in chunk-table order (not necessarily
 * file order). [constantSampleSize] is set when `stsz` gives one size for every sample. Old
 * QuickTime PCM audio stores 1 there and means "one byte per frame per channel", so spans
 * computed from it can be too short; callers that zero the bytes *between* tracks check it.
 */
data class SampleTable(val chunks: List<FileSpan>,
                       val sampleCount: Long,
                       val constantSampleSize: Long?)
