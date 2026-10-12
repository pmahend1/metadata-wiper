package com.prateekmahendrakar.metadatawiper.storage

import java.io.File

/*
 * Temporary copies of user files hold the very metadata the app removes, so they live in one
 * private directory and are deleted as soon as the work that needed them ends. Clearing the
 * directory on start catches anything a crash or process kill left behind.
 */
class TempFileStore(cacheDir: File) {

    // region State

    private val directory = File(cacheDir, "wipe")

    // endregion State

    // region Methods

    fun clear() {
        directory.listFiles()?.forEach { it.deleteRecursively() }
    }

    inline fun <T> withTempFile(block: (File) -> T): T {
        val file = create()
        try {
            return block(file)
        } finally {
            file.delete()
        }
    }

    fun create(): File {
        directory.mkdirs()
        return File.createTempFile("wipe", ".tmp", directory)
    }

    // endregion Methods
}
