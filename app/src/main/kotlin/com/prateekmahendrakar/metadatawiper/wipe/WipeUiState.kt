package com.prateekmahendrakar.metadatawiper.wipe

data class WipeUiState(val files: List<SelectedFile> = emptyList(),
                       val isBusy: Boolean = false,
                       val summary: WipeSummary? = null) {

    // region State

    val hasRemovableMetadata: Boolean
        get() = files.any { it.hasRemovableMetadata }

    val uncleanableCount: Int
        get() = files.count { it.canBeCleaned == false }

    // endregion State
}
