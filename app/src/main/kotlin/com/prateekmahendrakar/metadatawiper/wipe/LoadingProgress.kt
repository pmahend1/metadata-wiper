package com.prateekmahendrakar.metadatawiper.wipe

/* Reading picked files before review. fraction covers the whole batch, and is null while nothing can
   be measured. isDownloading is set once a file turns out to need copying first, which for a cloud
   file is the download. */
data class LoadingProgress(val fileCount: Int,
                           val fileName: String,
                           val fraction: Float?,
                           val isDownloading: Boolean)
