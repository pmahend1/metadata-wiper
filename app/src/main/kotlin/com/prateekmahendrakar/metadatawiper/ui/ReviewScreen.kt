package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.wipe.CleanedFileName
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
                 onShare: () -> Unit) {
    val files = state.files
    val firstFile = files.first()
    val options = settings.stripOptions
    val hasRemovableMetadata = state.hasRemovableMetadata(options)
    val replacesOriginals = overwriteOriginal && state.canOverwrite

    // The contract is re-read on recomposition, so the MIME type follows the selected file's format.
    val saveFileLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(firstFile.format.mimeType),
                                                             onResult = { uri: Uri? -> uri?.let(onSaveTo) })
    val saveToFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree(),
                                                                 onResult = { uri: Uri? -> uri?.let(onSaveToFolder) })

    val note = when {
        overwriteOriginal && state.canOverwrite == false -> stringResource(id = R.string.shared_files_saved_elsewhere)
        hasRemovableMetadata == false && state.uncleanableCount == 0 -> stringResource(id = R.string.no_removable_metadata)
        else -> null
    }

    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text(pluralStringResource(id = R.plurals.image_count, count = files.size, files.size)) },
                      navigationIcon = {
                          IconButton(onClick = onBack, enabled = state.isBusy == false) {
                              Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = stringResource(id = R.string.back))
                          }
                      })
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    },
             bottomBar = {
                 BottomActionBar(secondaryText = stringResource(id = R.string.clean_and_share),
                                 secondaryIcon = R.drawable.share_24px,
                                 secondaryEnabled = state.isBusy == false && files.any { it.canBeCleaned },
                                 onSecondary = onShare,
                                 primaryText = stringResource(id = if (replacesOriginals) R.string.clean_and_replace else R.string.clean_and_save),
                                 primaryIcon = R.drawable.download_24px,
                                 primaryEnabled = state.isBusy == false && hasRemovableMetadata,
                                 onPrimary = {
                                     when {
                                         replacesOriginals -> onOverwrite()
                                         files.size == 1 -> saveFileLauncher.launch(CleanedFileName.forOutput(firstFile.displayName,
                                                                                                              firstFile.format,
                                                                                                              settings.randomizeFileNames))
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
