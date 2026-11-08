package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.wipe.CleanedFileName
import com.prateekmahendrakar.metadatawiper.wipe.OutputMimeType
import com.prateekmahendrakar.metadatawiper.wipe.WipeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(state: WipeUiState,
                 settings: PrivacySettings,
                 overwriteOriginal: Boolean,
                 onBack: () -> Unit,
                 onOverwrite: () -> Unit,
                 onSaveTo: (Uri) -> Unit,
                 onSaveToFolder: (Uri) -> Unit,
                 onShare: () -> Unit,
                 onAddFiles: (List<Uri>) -> Unit,
                 onCancelLoading: () -> Unit) {
    val files = state.files
    val firstFile = files.first()
    val options = settings.stripOptions
    val hasRemovableMetadata = state.hasRemovableMetadata(options)
    val replacesOriginals = overwriteOriginal && state.canOverwrite
    val spaceToSave = state.spaceToSave(options)
    val spaceToShare = state.spaceToShare
    val canSave = state.freeSpace >= spaceToSave
    val canShare = state.freeSpace >= spaceToShare

    // Remembered because a random name would otherwise change on every recomposition.
    val outputName = remember(firstFile, settings.randomizeFileNames) {
        CleanedFileName.forOutput(firstFile.displayName, firstFile.format, settings.randomizeFileNames)
    }

    // The contract is re-read on recomposition, so the MIME type follows the selected file.
    val saveFileLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(OutputMimeType.of(outputName, firstFile.format)),
                                                             onResult = { uri: Uri? -> uri?.let(onSaveTo) })
    val saveToFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree(),
                                                                 onResult = { uri: Uri? -> uri?.let(onSaveToFolder) })
    val addFilesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments(),
                                                             onResult = onAddFiles)

    val context = LocalContext.current
    val note = when {
        canSave == false -> spaceNote(spaceToSave, state.freeSpace)
        canShare == false -> spaceNote(spaceToShare, state.freeSpace)
        overwriteOriginal && state.canOverwrite == false -> stringResource(id = R.string.shared_files_saved_elsewhere)
        hasRemovableMetadata == false && state.uncleanableCount == 0 -> stringResource(id = R.string.no_removable_metadata)
        spaceToSave > 0 && files.any { it.format.isVideo } -> stringResource(id = R.string.space_needed, Formatter.formatShortFileSize(context, spaceToSave))
        else -> null
    }

    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text(pluralStringResource(id = R.plurals.file_count, count = files.size, files.size)) },
                      navigationIcon = {
                          IconButton(onClick = onBack, enabled = state.isBusy == false) {
                              Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = stringResource(id = R.string.back))
                          }
                      },
                      actions = {
                          TextButton(onClick = { addFilesLauncher.launch(arrayOf("image/*", "video/*")) }, enabled = state.isBusy == false) {
                              Icon(painter = painterResource(id = R.drawable.add_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                              Spacer(modifier = Modifier.width(6.dp))
                              Text(text = stringResource(id = R.string.add_more))
                          }
                      })
            val loading = state.loading
            if (loading != null) {
                LoadingCard(loading = loading,
                            isCancelling = state.isCancelling,
                            onCancel = onCancelLoading,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } else if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    },
             bottomBar = {
                 BottomActionBar(secondaryText = stringResource(id = R.string.clean_and_share),
                                 secondaryIcon = R.drawable.share_24px,
                                 secondaryEnabled = state.isBusy == false && files.any { it.canBeCleaned } && canShare,
                                 onSecondary = onShare,
                                 primaryText = stringResource(id = if (replacesOriginals) R.string.clean_and_replace else R.string.clean_and_save),
                                 primaryIcon = R.drawable.download_24px,
                                 primaryEnabled = state.isBusy == false && hasRemovableMetadata && canSave,
                                 onPrimary = {
                                     when {
                                         replacesOriginals -> onOverwrite()
                                         files.size == 1 -> saveFileLauncher.launch(outputName)
                                         else -> saveToFolderLauncher.launch(null)
                                     }
                                 },
                                 note = note)
             }) { padding ->
        if (files.size == 1) {
            SingleFileReview(file = firstFile, options = options, modifier = Modifier.padding(padding))
        } else {
            BatchReview(files = files, options = options, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun spaceNote(needed: Long,
                      available: Long): String {
    val context = LocalContext.current
    return stringResource(id = R.string.space_short, Formatter.formatShortFileSize(context, needed), Formatter.formatShortFileSize(context, available))
}
