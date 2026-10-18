package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import android.util.Log
import com.prateekmahendrakar.metadatawiper.exif.ExifDetailReader
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatDetector
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandlerRegistry
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.storage.DocumentStore
import com.prateekmahendrakar.metadatawiper.storage.TempFileStore
import com.prateekmahendrakar.metadatawiper.verify.DecodeCheck
import com.prateekmahendrakar.metadatawiper.verify.DecodeVerifier
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel

/*
 * Detect → inspect → strip → verify → write, one file at a time. Blocking: callers run it on
 * an I/O dispatcher. Nothing reaches the destination until the stripped temp copy has been
 * verified, and every temp file is deleted before a call returns, whatever the outcome.
 */
class WipeFilesUseCase(private val documentStore: DocumentStore,
                       private val tempFileStore: TempFileStore,
                       private val decodeVerifier: DecodeVerifier,
                       private val handlers: FormatHandlerRegistry) {

    // region State

    private companion object {
        const val TAG = "WipeFilesUseCase"

        // Room for a minimal EXIF in any container: JPEG APP1 is 36 bytes, PNG eXIf 38, WebP EXIF 34.
        const val MINIMAL_EXIF_ALLOWANCE = 64L
    }

    // endregion State

    // region Methods

    fun inspect(uri: Uri): SelectedFile {
        val displayName = try {
            documentStore.displayName(uri)
        } catch (_: Exception) {
            null
        }
        return try {
            withSeekableSource(uri) { input ->
                val format = detect(input)
                val inspection = inspectOrNull(handlers.handlerFor(format), input)
                val groups = MetadataGrouping.of(inspection?.items.orEmpty(), readExifDetails(format, input))
                SelectedFile(uri = uri,
                             displayName = displayName ?: "",
                             format = format,
                             byteSize = input.size(),
                             inspection = inspection,
                             metadataGroups = groups)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read a selected file: ${e.javaClass.simpleName}")
            SelectedFile(uri = uri,
                         displayName = displayName ?: "",
                         format = FileFormat.Unknown,
                         byteSize = 0,
                         inspection = null,
                         metadataGroups = emptyList())
        }
    }

    fun wipe(source: Uri,
             destination: WipeDestination,
             options: StripOptions): WipeResult {
        val result = try {
            withSeekableSource(source) { input -> wipe(source, input, destination, options) }
        } catch (_: MalformedFileException) {
            FailedResult(WipeFailure.Malformed)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read a file to wipe: ${e.javaClass.simpleName}")
            FailedResult(WipeFailure.Unreadable)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected failure while wiping: ${e.javaClass.simpleName}")
            FailedResult(WipeFailure.Unexpected)
        }

        // The user created this document for the cleaned copy; an empty one would only mislead them.
        if (destination is DocumentDestination && result !is CleanedResult) {
            documentStore.delete(destination.uri)
        }
        if (destination is LocalFileDestination) {
            return completeLocalFile(source, destination.file, result)
        }
        return result
    }

    // Sharing needs every file, so one that's already clean is shared as it is.
    private fun completeLocalFile(source: Uri,
                                  file: File,
                                  result: WipeResult): WipeResult {
        if (result is CleanedResult) {
            return result
        }
        if (result !is NothingToRemoveResult) {
            file.delete()
            return result
        }
        return try {
            documentStore.copyTo(source, file)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't copy a file to share: ${e.javaClass.simpleName}")
            file.delete()
            FailedResult(WipeFailure.WriteFailed)
        }
    }

    private fun wipe(source: Uri,
                     input: SeekableByteChannel,
                     destination: WipeDestination,
                     options: StripOptions): WipeResult {
        val format = detect(input)
        val handler = handlers.handlerFor(format) ?: return UnsupportedResult(format)
        val inspection = handler.inspect(input)
        if (inspection.items.all { options.keeps(it) }) {
            return NothingToRemoveResult
        }

        return tempFileStore.withTempFile { cleaned ->
            val report = cleaned.outputStream().buffered().use { handler.strip(input, it, options) }
            val result = verify(handler, format, input, cleaned, report, options)
            if (result is CleanedResult && write(source, cleaned, destination, format) == false) {
                FailedResult(WipeFailure.WriteFailed)
            } else {
                result
            }
        }
    }

    private fun verify(handler: FormatHandler,
                       format: FileFormat,
                       input: SeekableByteChannel,
                       cleaned: File,
                       report: StripReport,
                       options: StripOptions): WipeResult {
        val allowance = if (report.kept.any { it.category == MetadataCategory.Orientation }) MINIMAL_EXIF_ALLOWANCE else 0L
        if (cleaned.length() == 0L || cleaned.length() > input.size() + allowance) {
            return FailedResult(WipeFailure.InvalidOutput)
        }

        val reinspection = try {
            FileChannel.open(cleaned.toPath()).use { handler.inspect(it) }
        } catch (_: MalformedFileException) {
            return FailedResult(WipeFailure.InvalidOutput)
        }
        if (reinspection.items.any { options.keeps(it) == false }) {
            return FailedResult(WipeFailure.MetadataRemained)
        }

        return when (decodeVerifier.verify(format, input, cleaned)) {
            DecodeCheck.Passed -> CleanedResult(report)
            DecodeCheck.Skipped -> CleanedResult(report, decodeCheckSkipped = true)
            DecodeCheck.Failed -> FailedResult(WipeFailure.DecodeFailed)
        }
    }

    private fun write(source: Uri,
                      cleaned: File,
                      destination: WipeDestination,
                      format: FileFormat): Boolean {
        var created: Uri? = null
        return try {
            when (destination) {
                OverwriteDestination -> documentStore.writeDocument(source, cleaned)
                is DocumentDestination -> documentStore.writeDocument(destination.uri, cleaned)
                is FolderDestination -> {
                    created = documentStore.createInFolder(destination.treeUri, format.mimeType, destination.displayName)
                    documentStore.writeDocument(created, cleaned)
                }
                is LocalFileDestination -> cleaned.copyTo(destination.file, overwrite = true)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write a cleaned file: ${e.javaClass.simpleName}")
            created?.let { documentStore.delete(it) }
            false
        }
    }

    private fun inspectOrNull(handler: FormatHandler?,
                              input: SeekableByteChannel): InspectionResult? {
        if (handler == null) {
            return null
        }
        return try {
            handler.inspect(input)
        } catch (_: MalformedFileException) {
            null
        }
    }

    // Display only, so a file ExifInterface can't read just shows no tags.
    private fun readExifDetails(format: FileFormat,
                                input: SeekableByteChannel): Map<String, String> {
        if (ExifDetailReader.supports(format) == false) {
            return emptyMap()
        }
        return try {
            // Not closed: closing a channel-backed stream would close the channel.
            input.position(0)
            ExifDetailReader.read(Channels.newInputStream(input))
        } catch (_: IOException) {
            emptyMap()
        }
    }

    private fun detect(input: SeekableByteChannel): FileFormat {
        val header = ByteBuffer.allocate(FormatDetector.HEADER_SIZE)
        input.position(0)
        while (header.hasRemaining()) {
            if (input.read(header) <= 0) {
                break
            }
        }
        return FormatDetector.detect(header.array().copyOf(header.position()))
    }

    /*
     * Handlers need random access. Most providers hand back a regular file, but some (cloud
     * providers, mostly) return a pipe, which can't seek; those are copied to a temp file first.
     */
    private fun <T> withSeekableSource(uri: Uri,
                                       block: (SeekableByteChannel) -> T): T {
        documentStore.openFileDescriptor(uri).use { descriptor ->
            if (descriptor.statSize >= 0) {
                return FileInputStream(descriptor.fileDescriptor).use { block(it.channel) }
            }
        }
        return tempFileStore.withTempFile { copy ->
            documentStore.copyTo(uri, copy)
            FileChannel.open(copy.toPath()).use(block)
        }
    }

    // endregion Methods
}
