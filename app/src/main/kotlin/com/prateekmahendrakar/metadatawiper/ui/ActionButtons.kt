package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.wipe.CleanedFileName
import com.prateekmahendrakar.metadatawiper.wipe.WipeUiState

@Composable
fun ActionButtons(state: WipeUiState,
                  overwriteOriginal: Boolean,
                  onFilesSelected: (List<Uri>) -> Unit,
                  onOverwrite: () -> Unit,
                  onSaveTo: (Uri) -> Unit,
                  onSaveToFolder: (Uri) -> Unit) {
    val files = state.files
    val firstFile = files.firstOrNull()

    val openFilesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments(),
                                                              onResult = onFilesSelected)

    // The contract is re-read on recomposition, so the MIME type follows the selected file's format.
    val saveFileLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(firstFile?.format?.mimeType ?: "image/*"),
                                                             onResult = { uri: Uri? -> uri?.let(onSaveTo) })

    val saveToFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree(),
                                                                 onResult = { uri: Uri? -> uri?.let(onSaveToFolder) })

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FilledTonalButton(shape = MaterialTheme.shapes.large,
                          enabled = state.isBusy == false,
                          onClick = { openFilesLauncher.launch(arrayOf("image/*")) }) {
            Text(text = stringResource(id = R.string.select_images), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.padding(8.dp))
        if (state.isBusy) {
            LinearProgressIndicator(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.padding(8.dp))
        }
        if (state.hasRemovableMetadata) {
            Button(shape = MaterialTheme.shapes.large,
                   enabled = state.isBusy == false,
                   onClick = {
                       when {
                           overwriteOriginal -> onOverwrite()
                           files.size == 1 && firstFile != null -> saveFileLauncher.launch(CleanedFileName.of(firstFile.displayName, firstFile.format))
                           else -> saveToFolderLauncher.launch(null)
                       }
                   }) {
                val buttonText = if (files.size > 1) {
                    pluralStringResource(id = R.plurals.remove_exif_from_images, count = files.size, files.size)
                } else {
                    stringResource(id = R.string.remove_exif_data)
                }
                Text(text = buttonText, style = MaterialTheme.typography.titleLarge)
            }
        } else if (files.isNotEmpty() && state.uncleanableCount == 0) {
            Text(stringResource(id = R.string.no_removable_exif_data))
        }

        // Never let an unsupported or damaged file look as though it will be cleaned.
        if (state.uncleanableCount > 0) {
            Text(text = pluralStringResource(id = R.plurals.files_cannot_be_cleaned, count = state.uncleanableCount, state.uncleanableCount),
                 textAlign = TextAlign.Center,
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}
