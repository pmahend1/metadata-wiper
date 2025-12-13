package com.prateekmahendrakar.metadatawiper.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.utils.getAllExifTags
import com.prateekmahendrakar.metadatawiper.utils.getFileName
import com.prateekmahendrakar.metadatawiper.utils.getRemovableExifTags
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempFile

@Composable
fun ActionButtons(
    cleanedFiles: List<File>,
    hasRemovableExif: Boolean,
    isEnabled: Boolean,
    originalFileNames: List<String>,
    onImagesSelected: (List<Uri>, HashMap<String, String>, List<File>, Boolean, List<String>) -> Unit,
    overwriteOriginal: Boolean,
    selectedImageUris: List<Uri>
) {
    val context = LocalContext.current
    val errorProcessingFiles = stringResource(id = R.string.error_processing_files)
    val imageSavedSuccessfully = stringResource(id = R.string.image_saved_successfully)
    val imagesSavedSuccessfully = stringResource(id = R.string.images_saved_successfully)
    val failedToSaveFile = stringResource(id = R.string.failed_to_save_file)
    val failedToSaveImages = stringResource(id = R.string.failed_to_save_images)

    val openImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                try {
                    val allUris = uris
                    val allCleanedFiles = mutableListOf<File>()
                    val allOriginalFileNames = mutableListOf<String>()
                    val displayHashMap = HashMap<String, String>()
                    var hasAnyRemovableData = false

                    uris.forEach { uri ->
                        val originalTempFile = createTempFile().toFile()
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            originalTempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        val exifReader = ExifInterface(originalTempFile.absolutePath)
                        val removableTags = getRemovableExifTags()
                        val hasRemovableData =
                            removableTags.any { tag -> exifReader.getAttribute(tag) != null }
                        if (hasRemovableData) hasAnyRemovableData = true

                        val cleanedTempFile = createTempFile().toFile()
                        originalTempFile.copyTo(cleanedTempFile, overwrite = true)

                        if (hasRemovableData) {
                            val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                            for (tag in removableTags) {
                                exifWriter.setAttribute(tag, null)
                            }
                            exifWriter.saveAttributes()
                        }

                        allCleanedFiles.add(cleanedTempFile)
                        val fileName = getFileName(context, uri) ?: "cleaned_file.jpg"
                        allOriginalFileNames.add(fileName)

                        // For single image selection, populate metadata for display
                        if (uris.size == 1) {
                            val allTags = getAllExifTags()
                            for (tag in allTags) {
                                exifReader.getAttribute(tag)?.let { value -> displayHashMap[tag] = value }
                            }
                        }
                    }

                    onImagesSelected(
                        allUris,
                        displayHashMap,
                        allCleanedFiles,
                        hasAnyRemovableData,
                        allOriginalFileNames
                    )

                } catch (e: IOException) {
                    Log.e("TAG", "Error processing files", e)
                    Toast.makeText(context, errorProcessingFiles, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*"),
        onResult = { uri: Uri? ->
            uri?.let { saveUri ->
                cleanedFiles.firstOrNull()?.let { file ->
                    try {
                        context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(context, imageSavedSuccessfully, Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: IOException) {
                        Log.e("TAG", "Failed to save file", e)
                        Toast.makeText(context, failedToSaveFile, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val saveFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri: Uri? ->
            treeUri?.let {
                try {
                    cleanedFiles.zip(originalFileNames).forEach { (cleanedFile, originalName) ->
                        val dotIndex = originalName.lastIndexOf('.')
                        val newFileName = if (dotIndex != -1) {
                            originalName.substring(0, dotIndex) + "_cleaned" + originalName.substring(dotIndex)
                        } else {
                            originalName + "_cleaned"
                        }

                        // Using DocumentsContract to create file
                        val newFileUri = DocumentsContract.createDocument(context.contentResolver, it, "image/jpeg", newFileName)
                        newFileUri?.let { docUri ->
                            context.contentResolver.openOutputStream(docUri)?.use { outputStream ->
                                cleanedFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                    }
                    Toast.makeText(context, imagesSavedSuccessfully, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("TAG", "Failed to save files", e)
                    Toast.makeText(context, failedToSaveImages, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(onClick = { openImagesLauncher.launch(arrayOf("image/*")) }) {
            Text(text = stringResource(id = R.string.select_images))
        }
        Spacer(modifier = Modifier.padding(8.dp))
        if (isEnabled) {
            if (hasRemovableExif) {
                Button(onClick = {
                    if (overwriteOriginal) {
                        try {
                            selectedImageUris.zip(cleanedFiles).forEach { (uri, cleanedFile) ->
                                context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                                    cleanedFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            Toast.makeText(context, imagesSavedSuccessfully, Toast.LENGTH_SHORT).show()
                        } catch (e: IOException) {
                            Log.e("TAG", "Failed to save files", e)
                            Toast.makeText(context, failedToSaveImages, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (selectedImageUris.size == 1) {
                            val originalFileName = originalFileNames.first()
                            val newFileName = originalFileName.let {
                                val dotIndex = it.lastIndexOf('.')
                                if (dotIndex != -1) {
                                    it.substring(0, dotIndex) + "_cleaned" + it.substring(dotIndex)
                                } else {
                                    it + "_cleaned"
                                }
                            }
                            saveFileLauncher.launch(newFileName)
                        } else {
                            saveFilesLauncher.launch(null)
                        }
                    }
                }) {
                    val buttonText = if (selectedImageUris.size > 1) pluralStringResource(id = R.plurals.remove_exif_from_images, count = selectedImageUris.size, selectedImageUris.size) else stringResource(id = R.string.remove_exif_data)
                    Text(text = buttonText)
                }
            } else {
                Text(stringResource(id = R.string.no_removable_exif_data))
            }
        }
    }
}
