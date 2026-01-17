package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import com.prateekmahendrakar.metadatawiper.utils.getFormattedExifValue
import com.prateekmahendrakar.metadatawiper.utils.getRemovableExifTags
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempFile

@Composable
fun ActionButtons(selectedImageUris: List<Uri>,
                  hasRemovableExif: Boolean,
                  isEnabled: Boolean,
                  onImagesSelected: (List<Uri>, HashMap<String, String>, Boolean) -> Unit,
                  overwriteOriginal: Boolean,
                  onSuccess: () -> Unit) {
    val context = LocalContext.current
    val errorProcessingFiles = stringResource(id = R.string.error_processing_files)
    val metadataWipeSuccess = stringResource(id = R.string.metadata_wiped_successfully)
    val failedToSaveFile = stringResource(id = R.string.failed_to_save_file)
    val failedToSaveImages = stringResource(id = R.string.failed_to_save_images)

    val openImagesLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments(), onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                try {
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

                        val hasRemovableData = getRemovableExifTags().any { tag ->
                            exifReader.getAttribute(tag) != null
                        }
                        if (hasRemovableData) {
                            hasAnyRemovableData = true
                        }

                        // For single image selection, populate metadata for display
                        if (uris.size == 1) {
                            val allTags = getAllExifTags()
                            for (tag in allTags) {
                                val stringValue = getFormattedExifValue(exifReader, tag)
                                displayHashMap[tag] = stringValue
                            }
                        }

                    }

                    onImagesSelected(uris, displayHashMap, hasAnyRemovableData)

                } catch (e: IOException) {
                    Log.e("TAG", "Error processing files", e)
                    Toast.makeText(context, errorProcessingFiles, Toast.LENGTH_SHORT).show()
                }
            }
        })

    //Single
    val saveFileLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("image/*"), onResult = { uri: Uri? ->
        uri?.let { saveUri ->
            selectedImageUris.firstOrNull()?.let { selectedImageUri ->
                try {
                    val cleanedTempFile = createTempFile().toFile()
                    context.contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
                        cleanedTempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (hasRemovableExif) {
                        val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                        for (tag in getRemovableExifTags()) {
                            exifWriter.setAttribute(tag, null)
                        }
                        exifWriter.saveAttributes()
                    }

                    context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                        cleanedTempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Toast.makeText(context, metadataWipeSuccess, Toast.LENGTH_SHORT).show()
                    onSuccess()
                } catch (e: IOException) {
                    Log.e("TAG", "Failed to save file", e)
                    Toast.makeText(context, failedToSaveFile, Toast.LENGTH_SHORT).show()
                }
            }
        }
    })

    //Multiple
    val saveFilesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree(), onResult = { treeUri: Uri? ->
        treeUri?.let { safeAccessFolderUri ->
            try {
                val allCleanedFiles: MutableList<File> = mutableListOf()
                val allOriginalFileNames: MutableList<String> = mutableListOf()

                selectedImageUris.forEach { selectedImageUri ->
                    val cleanedTempFile = createTempFile().toFile()
                    context.contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
                        cleanedTempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (hasRemovableExif) {
                        val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                        for (tag in getRemovableExifTags()) {
                            exifWriter.setAttribute(tag, null)
                        }
                        exifWriter.saveAttributes()
                    }
                    allCleanedFiles.add(cleanedTempFile)
                    val fileName = getFileName(context, selectedImageUri) ?: "cleaned_file.jpg"
                    allOriginalFileNames.add(fileName)
                }

                allCleanedFiles.zip(allOriginalFileNames).forEach { (cleanedFile, originalName) ->
                    val dotIndex = originalName.lastIndexOf('.')
                    val newFileName = when {
                        dotIndex != -1 -> originalName.substring(0, dotIndex) + "_cleaned" + originalName.substring(dotIndex)
                        else -> originalName + "_cleaned"
                    }
                    // Using DocumentsContract to create file
                    val docId = DocumentsContract.getTreeDocumentId(safeAccessFolderUri)
                    val dirUri = DocumentsContract.buildDocumentUriUsingTree(safeAccessFolderUri, docId)
                    val newFileUri = DocumentsContract.createDocument(context.contentResolver, dirUri, "image/jpeg", newFileName)
                    newFileUri?.let { docUri ->
                        context.contentResolver.openOutputStream(docUri)?.use { outputStream ->
                            cleanedFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
                Toast.makeText(context, metadataWipeSuccess, Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                Log.e("TAG", "Failed to save files", e)
                Toast.makeText(context, failedToSaveImages, Toast.LENGTH_SHORT).show()
            }
        }
    })

    ///UI
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FilledTonalButton(shape = MaterialTheme.shapes.large, onClick = { openImagesLauncher.launch(arrayOf("image/*")) }) {
            Text(text = stringResource(id = R.string.select_images), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.padding(8.dp))
        if (isEnabled) {
            if (hasRemovableExif) {
                Button(shape = MaterialTheme.shapes.large, onClick = {
                    if (overwriteOriginal) {
                        try {
                            val allCleanedFiles: MutableList<File> = mutableListOf()
                            val allOriginalFileNames: MutableList<String> = mutableListOf()

                            selectedImageUris.forEach { selectedImageUri ->
                                val cleanedTempFile = createTempFile().toFile()
                                context.contentResolver.openInputStream(selectedImageUri)?.use { inputStream ->
                                    cleanedTempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                if (hasRemovableExif) {
                                    val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                                    for (tag in getRemovableExifTags()) {
                                        exifWriter.setAttribute(tag, null)
                                    }
                                    exifWriter.saveAttributes()
                                }
                                allCleanedFiles.add(cleanedTempFile)
                                val fileName = getFileName(context, selectedImageUri) ?: "cleaned_file.jpg"
                                allOriginalFileNames.add(fileName)
                            }
                            selectedImageUris.zip(allCleanedFiles).forEach { (uri, cleanedFile) ->
                                context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                                    cleanedFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            }
                            Toast.makeText(context, metadataWipeSuccess, Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } catch (e: IOException) {
                            Log.e("TAG", "Failed to save files", e)
                            Toast.makeText(context, failedToSaveImages, Toast.LENGTH_SHORT).show()
                        }
                    } else if (selectedImageUris.size == 1) {
                        val originalName = getFileName(context, selectedImageUris.first())
                        if (originalName != null) {

                            val dotIndex = originalName.lastIndexOf('.')
                            val newFileName = if (dotIndex != -1) {
                                originalName.take(dotIndex) + "_cleaned" + originalName.substring(dotIndex)
                            } else {
                                originalName + "_cleaned"
                            }
                            saveFileLauncher.launch(newFileName)
                        }
                    } else if (selectedImageUris.size > 1) {
                        saveFilesLauncher.launch(null)
                    }
                }) {
                    val buttonText = if (selectedImageUris.size > 1) {
                        pluralStringResource(id = R.plurals.remove_exif_from_images, count = selectedImageUris.size, selectedImageUris.size)
                    } else {
                        stringResource(id = R.string.remove_exif_data)
                    }
                    Text(text = buttonText, style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Text(stringResource(id = R.string.no_removable_exif_data))
            }
        }
    }
}
