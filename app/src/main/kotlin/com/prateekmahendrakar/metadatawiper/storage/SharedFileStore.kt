package com.prateekmahendrakar.metadatawiper.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/*
 * Cleaned copies waiting to be shared. They're already clean, but they shouldn't pile up, so
 * the directory is cleared on start and before every new share. Each file gets its own
 * numbered subdirectory, so two files with the same name in one batch don't collide.
 */
class SharedFileStore(private val context: Context) {

    // region State

    // Must match the <cache-path> in res/xml/shared_file_paths.xml and the provider in the manifest.
    private val directory = File(context.cacheDir, "shared")
    private val authority = "${context.packageName}.fileprovider"

    // endregion State

    // region Methods

    fun clear() {
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun fileFor(index: Int,
                displayName: String): File {
        val slot = File(directory, index.toString())
        slot.mkdirs()
        return File(slot, displayName)
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    // endregion Methods
}
