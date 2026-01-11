package com.prateekmahendrakar.metadatawiper

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class MWDocumentsProvider : DocumentsProvider() {

    private companion object {
        private const val TAG = "DocumentsProvider"
        private const val ROOT = "root"

        private val defaultRootProjection = arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID,
                                                    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                                                    DocumentsContract.Root.COLUMN_TITLE,
                                                    DocumentsContract.Root.COLUMN_FLAGS,
                                                    DocumentsContract.Root.COLUMN_ICON)

        private val defaultDocumentProjection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                                        DocumentsContract.Document.COLUMN_SIZE,
                                                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                                                        DocumentsContract.Document.COLUMN_FLAGS,
                                                        DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    }

    private lateinit var mBaseDir: File

    override fun onCreate(): Boolean {
        // Initialize your base directory — for example, app’s files dir
        context?.let {
            mBaseDir = File(it.filesDir, "files")
            if (!mBaseDir.exists()) mBaseDir.mkdirs()
        }
        return true
    }

    // region === Query Roots ===
    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultRootProjection)
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT)
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "Files")
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
        row.add(DocumentsContract.Root.COLUMN_ICON, android.R.drawable.ic_menu_gallery)
        return result
    }
    // endregion

    // region === Query Document ===
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        val file = getFileForDocId(documentId)
        includeFile(result, file)
        return result
    }
    // endregion

    // region === Query Child Documents ===
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val result = MatrixCursor(projection ?: defaultDocumentProjection)
        val parent = getFileForDocId(parentDocumentId)
        parent.listFiles()?.forEach { child ->
            includeFile(result, child)
        }
        return result
    }
    // endregion

    // region === Open Document ===
    @Throws(FileNotFoundException::class)
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val isWrite = mode.contains('w')

        return if (isWrite) {
            try {
                val handler = Handler(context!!.mainLooper)
                ParcelFileDescriptor.open(file, accessMode, handler) { _ ->
                    Log.i(TAG, "File with id $documentId closed! Sync with server.")
                }
            } catch (_: IOException) {
                throw FileNotFoundException("Failed to open document $documentId ($mode)")
            }
        } else {
            ParcelFileDescriptor.open(file, accessMode)
        }
    }
    // endregion

    // region === Helper Methods ===
    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String): File {
        var target = mBaseDir
        if (docId == ROOT) return target

        val splitIndex = docId.indexOf(':', 1)
        if (splitIndex < 0) throw FileNotFoundException("Missing root for $docId")

        val path = docId.substring(splitIndex + 1)
        target = File(target, path)
        if (!target.exists()) {
            throw FileNotFoundException("Missing file for $docId at $target")
        }
        return target
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val docId = if (file == mBaseDir) ROOT else "$ROOT:${file.relativeTo(mBaseDir).path}"
        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, getMimeType(file))
        row.add(DocumentsContract.Document.COLUMN_FLAGS, if (file.isDirectory) DocumentsContract.Document.FLAG_DIR_PREFERS_GRID else 0)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
    }

    private fun getMimeType(file: File): String {
        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val ext = file.extension.lowercase()
            when (ext) {
                "txt" -> "text/plain"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> "application/octet-stream"
            }
        }
    }
}