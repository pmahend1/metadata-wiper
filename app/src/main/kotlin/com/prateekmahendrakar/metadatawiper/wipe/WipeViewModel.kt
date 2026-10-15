package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandlerRegistry
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.bmp.BmpHandler
import com.prateekmahendrakar.metadatawiper.metadata.gif.GifHandler
import com.prateekmahendrakar.metadatawiper.metadata.heif.HeifHandler
import com.prateekmahendrakar.metadatawiper.metadata.jpeg.JpegHandler
import com.prateekmahendrakar.metadatawiper.metadata.png.PngHandler
import com.prateekmahendrakar.metadatawiper.metadata.webp.WebPHandler
import com.prateekmahendrakar.metadatawiper.storage.DocumentStore
import com.prateekmahendrakar.metadatawiper.storage.TempFileStore
import com.prateekmahendrakar.metadatawiper.verify.DecodeVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WipeViewModel(private val wipeFiles: WipeFilesUseCase,
                    tempFileStore: TempFileStore) : ViewModel() {

    // region State

    private val _uiState = MutableStateFlow(WipeUiState())
    val uiState: StateFlow<WipeUiState> = _uiState.asStateFlow()

    // Fixed until the Privacy settings exist.
    private val options = StripOptions()

    // Leftovers from a crash are cleared before any new work creates temp files.
    private val startupCleanup: Job = viewModelScope.launch(Dispatchers.IO) { tempFileStore.clear() }

    companion object {
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
                WipeViewModel(useCase, tempFileStore)
            }
        }
    }

    // endregion State

    // region Methods

    fun select(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        runExclusively {
            val files = uris.map { wipeFiles.inspect(it, options) }
            _uiState.update { it.copy(files = files) }
        }
    }

    fun overwriteOriginals() = wipeAll { OverwriteDestination }

    fun saveTo(documentUri: Uri) = wipeAll { DocumentDestination(documentUri) }

    fun saveToFolder(treeUri: Uri) = wipeAll { FolderDestination(treeUri) }

    fun onSummaryShown() {
        _uiState.update { it.copy(summary = null) }
    }

    private fun wipeAll(destination: () -> WipeDestination) {
        runExclusively {
            val files = _uiState.value.files
            val results = files.map { wipeFiles.wipe(it.uri, destination(), options) }
            // Failed files stay selected so the user can retry them.
            val failedFiles = files.zip(results)
                                   .filter { (_, result) -> result is FailedResult }
                                   .map { (file, _) -> file }
            _uiState.update { it.copy(files = failedFiles, summary = WipeSummary.of(results)) }
        }
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
