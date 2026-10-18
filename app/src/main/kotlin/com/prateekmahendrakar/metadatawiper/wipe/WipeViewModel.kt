package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandlerRegistry
import com.prateekmahendrakar.metadatawiper.metadata.bmp.BmpHandler
import com.prateekmahendrakar.metadatawiper.metadata.gif.GifHandler
import com.prateekmahendrakar.metadatawiper.metadata.heif.HeifHandler
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegHandler
import com.prateekmahendrakar.metadatawiper.metadata.png.PngHandler
import com.prateekmahendrakar.metadatawiper.metadata.webp.WebPHandler
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.share.PendingShare
import com.prateekmahendrakar.metadatawiper.storage.DocumentStore
import com.prateekmahendrakar.metadatawiper.storage.SharedFileStore
import com.prateekmahendrakar.metadatawiper.storage.TempFileStore
import com.prateekmahendrakar.metadatawiper.verify.DecodeVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class WipeViewModel(private val wipeFiles: WipeFilesUseCase,
                    tempFileStore: TempFileStore,
                    private val sharedFileStore: SharedFileStore) : ViewModel() {

    // region State

    private val _uiState = MutableStateFlow(WipeUiState())
    val uiState: StateFlow<WipeUiState> = _uiState.asStateFlow()

    // Leftovers from a crash are cleared before any new work creates temp files.
    private val startupCleanup: Job = viewModelScope.launch(Dispatchers.IO) {
        tempFileStore.clear()
        sharedFileStore.clear()
    }

    companion object {
        private const val MIXED_IMAGE_MIME_TYPE = "image/*"

        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val tempFileStore = TempFileStore(application.cacheDir)
                val useCase = WipeFilesUseCase(DocumentStore(application.contentResolver),
                                               tempFileStore,
                                               DecodeVerifier(),
                                               FormatHandlerRegistry(listOf(JpegHandler(),
                                                                            PngHandler(),
                                                                            WebPHandler(),
                                                                            HeifHandler(FileFormat.Heif),
                                                                            HeifHandler(FileFormat.Avif),
                                                                            GifHandler(),
                                                                            BmpHandler())))
                WipeViewModel(useCase, tempFileStore, SharedFileStore(application))
            }
        }
    }

    // endregion State

    // region Methods

    fun select(uris: List<Uri>) = select(uris, canOverwrite = true)

    fun selectShared(uris: List<Uri>) = select(uris, canOverwrite = false)

    fun overwriteOriginals(settings: PrivacySettings) = wipeAll(settings) { _, _ -> OverwriteDestination }

    fun saveTo(documentUri: Uri,
               settings: PrivacySettings) = wipeAll(settings) { _, _ -> DocumentDestination(documentUri) }

    fun saveToFolder(treeUri: Uri,
                     settings: PrivacySettings) = wipeAll(settings) { _, file -> FolderDestination(treeUri, outputName(file, settings)) }

    fun share(settings: PrivacySettings) {
        runExclusively {
            sharedFileStore.clear()
            val outputs = _uiState.value.files.mapIndexed { index, file -> sharedFileStore.fileFor(index, outputName(file, settings)) }
            val results = wipeEach(settings) { index, _ -> LocalFileDestination(outputs[index]) }
            val shared = results.zip(outputs)
                                .filter { (wiped, _) -> wiped.result is CleanedResult || wiped.result is NothingToRemoveResult }
            finish(results, pendingShareOf(shared))
        }
    }

    fun onShareLaunched() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    private fun select(uris: List<Uri>,
                       canOverwrite: Boolean) {
        if (uris.isEmpty()) {
            return
        }
        runExclusively {
            val files = uris.map { wipeFiles.inspect(it) }
            _uiState.update { it.copy(files = files, canOverwrite = canOverwrite, results = emptyList()) }
        }
    }

    private fun wipeAll(settings: PrivacySettings,
                        destination: (Int, SelectedFile) -> WipeDestination) {
        runExclusively {
            finish(wipeEach(settings, destination), pendingShare = null)
        }
    }

    private fun wipeEach(settings: PrivacySettings,
                         destination: (Int, SelectedFile) -> WipeDestination): List<FileWipeResult> {
        return _uiState.value.files.mapIndexed { index, file ->
            FileWipeResult(file, wipeFiles.wipe(file.uri, destination(index, file), settings.stripOptions))
        }
    }

    // Failed files stay selected so the user can retry them.
    private fun finish(results: List<FileWipeResult>,
                       pendingShare: PendingShare?) {
        val failedFiles = results.filter { it.result is FailedResult }
                                 .map { it.file }
        _uiState.update { it.copy(files = failedFiles, results = results, pendingShare = pendingShare) }
    }

    private fun pendingShareOf(shared: List<Pair<FileWipeResult, File>>): PendingShare? {
        if (shared.isEmpty()) {
            return null
        }
        val mimeTypes = shared.map { (wiped, _) -> wiped.file.format.mimeType }.distinct()
        return PendingShare(uris = shared.map { (_, output) -> sharedFileStore.uriFor(output) },
                            mimeType = mimeTypes.singleOrNull() ?: MIXED_IMAGE_MIME_TYPE)
    }

    private fun outputName(file: SelectedFile,
                           settings: PrivacySettings): String {
        return CleanedFileName.forOutput(file.displayName, file.format, settings.randomizeFileNames)
    }

    private fun runExclusively(work: () -> Unit) {
        if (_uiState.value.isBusy) {
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                startupCleanup.join()
                work()
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    // endregion Methods
}
