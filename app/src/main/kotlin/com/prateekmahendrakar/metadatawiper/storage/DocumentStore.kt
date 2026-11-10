package com.prateekmahendrakar.metadatawiper.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

// Storage Access Framework reads and writes. Every URI here came from a SAF picker.
class DocumentStore(private val contentResolver: ContentResolver) {

    // region State

    private companion object {
        // Plain "w" isn't guaranteed to truncate, which would leave the tail of the original behind.
        const val TRUNCATING_WRITE_MODE = "wt"
        const val COPY_BUFFER_SIZE = 1024 * 1024
    }

    // endregion State

    // region Methods

    fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return null
    }

    // What the provider reports, which for a cloud file is known before any of it is downloaded.
    fun size(uri: Uri): Long? {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.isNull(sizeIndex) == false) {
                    return cursor.getLong(sizeIndex)
                }
            }
        }
        return null
    }

    fun openFileDescriptor(uri: Uri): ParcelFileDescriptor {
        return contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("No descriptor for document")
    }

    fun openInputStream(uri: Uri): InputStream {
        return contentResolver.openInputStream(uri) ?: throw FileNotFoundException("No input stream for document")
    }

    fun copyTo(uri: Uri,
               destination: File,
               wrapOutput: (OutputStream) -> OutputStream = { it }) {
        openInputStream(uri).use { input ->
            wrapOutput(destination.outputStream()).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        }
    }

    // Used both to overwrite an original and to fill a document the user just created.
    fun writeDocument(uri: Uri,
                      source: File,
                      wrapOutput: (OutputStream) -> OutputStream = { it }) {
        write(contentResolver.openOutputStream(uri, TRUNCATING_WRITE_MODE), source, wrapOutput)
    }

    fun createInFolder(treeUri: Uri,
                       mimeType: String,
                       displayName: String): Uri {
        val folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return DocumentsContract.createDocument(contentResolver, folderUri, mimeType, displayName)
               ?: throw FileNotFoundException("Could not create document")
    }

    // Best effort: used to remove a document we created but couldn't fill.
    fun delete(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
        }
    }

    private fun write(output: OutputStream?,
                      source: File,
                      wrapOutput: (OutputStream) -> OutputStream) {
        if (output == null) {
            throw FileNotFoundException("No output stream for document")
        }
        wrapOutput(output).use { stream ->
            source.inputStream().use { it.copyTo(stream, COPY_BUFFER_SIZE) }
        }
    }

    // endregion Methods
}
