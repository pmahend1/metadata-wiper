package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.share.PendingShare

// canOverwrite is false for files shared in from another app, which usually grant read access only.
data class WipeUiState(val files: List<SelectedFile> = emptyList(),
                       val canOverwrite: Boolean = true,
                       val isBusy: Boolean = false,
                       val results: List<FileWipeResult> = emptyList(),
                       val pendingShare: PendingShare? = null) {

    // region State

    val uncleanableCount: Int
        get() = files.count { it.canBeCleaned == false }

    val summary: WipeSummary
        get() = WipeSummary.of(results.map { it.result })

    // endregion State

    // region Methods

    fun hasRemovableMetadata(options: StripOptions): Boolean = files.any { it.hasRemovableMetadata(options) }

    // endregion Methods
}
