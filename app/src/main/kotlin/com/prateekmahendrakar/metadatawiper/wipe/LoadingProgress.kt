package com.prateekmahendrakar.metadatawiper.wipe

// Reading picked files before review. fraction covers the whole batch, and is null while nothing can be measured.
data class LoadingProgress(val fileCount: Int,
                           val fileName: String,
                           val fraction: Float?)
