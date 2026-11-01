package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan

// A file from MovieBuilder, with where each track's chunks ended up, in chunk order.
class BuiltMovie(val bytes: ByteArray,
                 val chunkSpans: List<List<FileSpan>>)
