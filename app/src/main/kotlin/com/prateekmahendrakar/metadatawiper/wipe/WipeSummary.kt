package com.prateekmahendrakar.metadatawiper.wipe

data class WipeSummary(val cleaned: Int,
                       val nothingToRemove: Int,
                       val unsupported: Int,
                       val failed: Int) {

    // region State

    val isCompleteSuccess: Boolean
        get() = cleaned > 0 && nothingToRemove == 0 && unsupported == 0 && failed == 0

    // endregion State

    companion object {

        // region Methods

        fun of(results: List<WipeResult>): WipeSummary {
            return WipeSummary(cleaned = results.count { it is CleanedResult },
                               nothingToRemove = results.count { it is NothingToRemoveResult },
                               unsupported = results.count { it is UnsupportedResult },
                               failed = results.count { it is FailedResult })
        }

        // endregion Methods
    }
}
