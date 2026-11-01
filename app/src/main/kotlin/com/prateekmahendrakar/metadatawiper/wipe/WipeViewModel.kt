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
import com.prateekmahendrakar.metadatawiper.metadata.movie.IsoMediaHandler
import com.prateekmahendrakar.metadatawiper.metadata.png.PngHandler
import com.prateekmahendrakar.metadatawiper.metadata.webp.WebPHandler
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.share.PendingShare
import com.prateekmahendrakar.metadatawiper.share.ShareMimeType
import com.prateekmahendrakar.metadatawiper.storage.DocumentStore
import com.prateekmahendrakar.metadatawiper.storage.SharedFileStore
import com.prateekmahendrakar.metadatawiper.storage.TempFileStore
import com.prateekmahendrakar.metadatawiper.verify.DecodeVerifier
import com.prateekmahendrakar.metadatawiper.verify.VideoDecodeVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class WipeViewModel(private val wipeFiles: WipeFilesUseCase,
                    private val tempFileStore: TempFileStore,
                    private val sharedFileStore: SharedFileStore) : ViewModel() {

    // region State

    private val _uiState = MutableStateFlow(WipeUiState())
    val uiState: StateFlow<WipeUiState> = _uiState.asStateFlow()

    private val isCancelRequested = AtomicBoolean(false)

    // Leftovers from a crash are cleared before any new work creates temp files.
    private val startupCleanup: Job = viewModelScope.launch(Dispatchers.IO) {
        tempFileStore.clear()
        sharedFileStore.clear()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val tempFileStore = TempFileStore(application.cacheDir)
                val useCase = WipeFilesUseCase(DocumentStore(application.contentResolver),
                                               tempFileStore,
                                               DecodeVerifier(),
                                               VideoDecodeVerifier(),
                                               FormatHandlerRegistry(listOf(JpegHandler(),
                                                                            PngHandler(),
                                                                            WebPHandler(),
                                                                            HeifHandler(FileFormat.Heif),
                                                                            HeifHandler(FileFormat.Avif),
                                                                            GifHandler(),
                                                                            BmpHandler(),
                                                                            IsoMediaHandler(FileFormat.Mp4),
                                                                            IsoMediaHandler(FileFormat.QuickTime),
                                                                            IsoMediaHandler(FileFormat.ThreeGpp))))
                WipeViewModel(useCase, tempFileStore, SharedFileStore(application))
            }
        }
    }

    // endregion State

    // region Methods

    fun select(uris: List<Uri>) = select(uris, canOverwrite = true)

    fun selectShared(uris: List<Uri>) = select(uris, canOverwrite = false)

    fun overwriteOriginals(settings: PrivacySettings) = wipeAll(settings, WipeAction.Replaced) { _, _ -> OverwriteDestination }

    fun saveTo(documentUri: Uri,
               settings: PrivacySettings) = wipeAll(settings, WipeAction.Saved) { _, _ -> DocumentDestination(documentUri) }

    fun saveToFolder(treeUri: Uri,
                     settings: PrivacySettings) = wipeAll(settings, WipeAction.Saved) { _, file -> FolderDestination(treeUri, outputName(file, settings)) }

    fun share(settings: PrivacySettings) {
        runExclusively {
            val (results, pendingShare) = shareFiles(_uiState.value.files, settings, ::showResult)
            finish(results, if (wasCancelled(results)) null else WipeAction.Shared, pendingShare)
        }
    }

    // Cleans the originals again rather than keeping copies around; the output is the same either way.
    fun shareCleaned(settings: PrivacySettings) {
        runExclusively {
            val files = _uiState.value.results.filter { it.result is CleanedResult || it.result is NothingToRemoveResult }
                                              .map { it.file }
            val (_, pendingShare) = shareFiles(files, settings) { }
            _uiState.update { it.copy(pendingShare = pendingShare) }
        }
    }

    fun onShareLaunched() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    fun cancel() {
        isCancelRequested.set(true)
        _uiState.update { it.copy(isCancelling = true) }
    }

    fun clear() {
        if (_uiState.value.isBusy) {
            return
        }
        _uiState.update { WipeUiState() }
    }

    // A wipe's thread blocks on file I/O and never sees the scope's cancellation by itself.
    override fun onCleared() {
        isCancelRequested.set(true)
    }

    private fun select(uris: List<Uri>,
                       canOverwrite: Boolean) {
        if (uris.isEmpty()) {
            return
        }
        runExclusively {
            val files = uris.map { wipeFiles.inspect(it) }
            val freeSpace = tempFileStore.usableSpace()
            _uiState.update { WipeUiState(files = files, canOverwrite = canOverwrite, isBusy = it.isBusy, freeSpace = freeSpace) }
        }
    }

    private fun wipeAll(settings: PrivacySettings,
                        action: WipeAction,
                        destination: (Int, SelectedFile) -> WipeDestination) {
        runExclusively {
            finish(wipeEach(_uiState.value.files, settings, ::showResult, destination), action, pendingShare = null)
        }
    }

    private fun shareFiles(files: List<SelectedFile>,
                           settings: PrivacySettings,
                           onWiped: (FileWipeResult) -> Unit): Pair<List<FileWipeResult>, PendingShare?> {
        sharedFileStore.clear()
        val outputs = files.mapIndexed { index, file -> sharedFileStore.fileFor(index, outputName(file, settings)) }
        val results = wipeEach(files, settings, onWiped) { index, _ -> LocalFileDestination(outputs[index]) }

        // Cancelling means the user no longer wants these sent anywhere.
        if (wasCancelled(results)) {
            sharedFileStore.clear()
            return results to null
        }
        val shared = results.zip(outputs)
                            .filter { (wiped, _) -> wiped.result is CleanedResult || wiped.result is NothingToRemoveResult }
        return results to pendingShareOf(shared)
    }

    private fun wipeEach(files: List<SelectedFile>,
                         settings: PrivacySettings,
                         onWiped: (FileWipeResult) -> Unit,
                         destination: (Int, SelectedFile) -> WipeDestination): List<FileWipeResult> {
        isCancelRequested.set(false)
        return files.mapIndexed { index, file ->
            val target = destination(index, file)
            val report = { phase: WipePhase, fraction: Float? ->
                val progress = WipeProgress(fileIndex = index,
                                            fileCount = files.size,
                                            fileName = file.displayName,
                                            phase = phase,
                                            fraction = fraction,
                                            canCancel = target != OverwriteDestination || phase != WipePhase.Writing)
                _uiState.update { it.copy(progress = progress) }
            }
            report(WipePhase.Stripping, null)
            val monitor = WipeMonitor(isCancelled = { isCancelRequested.get() }, onProgress = report)
            FileWipeResult(file, wipeFiles.wipe(file, target, settings.stripOptions, monitor)).also(onWiped)
        }
    }

    private fun showResult(wiped: FileWipeResult) {
        _uiState.update { it.copy(results = it.results + wiped) }
    }

    private fun wasCancelled(results: List<FileWipeResult>): Boolean {
        return results.any { (it.result as? FailedResult)?.failure == WipeFailure.Cancelled }
    }

    // Failed files stay selected so the user can retry them.
    private fun finish(results: List<FileWipeResult>,
                       action: WipeAction?,
                       pendingShare: PendingShare?) {
        val failedFiles = results.filter { it.result is FailedResult }
                                 .map { it.file }
        _uiState.update { it.copy(files = failedFiles, results = results, lastAction = action, pendingShare = pendingShare) }
    }

    private fun pendingShareOf(shared: List<Pair<FileWipeResult, File>>): PendingShare? {
        if (shared.isEmpty()) {
            return null
        }
        return PendingShare(uris = shared.map { (_, output) -> sharedFileStore.uriFor(output) },
                            mimeType = ShareMimeType.commonTo(shared.map { (wiped, _) -> wiped.file.format.mimeType }))
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
                _uiState.update { it.copy(isBusy = false, progress = null, isCancelling = false) }
            }
        }
    }

    // endregion Methods
}
