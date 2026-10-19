package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.share.PendingShare

// canOverwrite is false for files shared in from another app, which usually grant read access only.
data class WipeUiState(val files: List<SelectedFile> = emptyList(),
                       val canOverwrite: Boolean = true,
                       val isBusy: Boolean = false,
                       val results: List<FileWipeResult> = emptyList(),
                       val lastAction: WipeAction? = null,
                       val pendingShare: PendingShare? = null) {

    // region State

    val uncleanableCount: Int
        get() = files.count { it.canBeCleaned == false }

    val summary: WipeSummary
        get() = WipeSummary.of(results.map { it.result })

    // Files with nothing to remove are shared as they are, so they count as shareable too.
    val hasShareableResults: Boolean
        get() = results.any { it.result is CleanedResult || it.result is NothingToRemoveResult }

    // endregion State

    // region Methods

    fun hasRemovableMetadata(options: StripOptions): Boolean = files.any { it.hasRemovableMetadata(options) }

    // endregion Methods
}
