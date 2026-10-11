package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import org.junit.Assert.assertEquals
import org.junit.Test

class WipeSummaryTest {

    // region Methods

    @Test
    fun countsEachOutcome() {
        val results = listOf(CleanedResult(StripReport(emptyList(), emptyList(), emptyList())),
                             CleanedResult(StripReport(emptyList(), emptyList(), emptyList())),
                             NothingToRemoveResult,
                             UnsupportedResult(FileFormat.Png),
                             FailedResult(WipeFailure.WriteFailed))
        assertEquals(WipeSummary(cleaned = 2, nothingToRemove = 1, unsupported = 1, failed = 1), WipeSummary.of(results))
    }

    @Test
    fun isCompleteSuccessOnlyWhenEveryFileWasCleaned() {
        val cleaned = CleanedResult(StripReport(emptyList(), emptyList(), emptyList()))
        assertEquals(true, WipeSummary.of(listOf(cleaned)).isCompleteSuccess)
        assertEquals(false, WipeSummary.of(listOf(cleaned, NothingToRemoveResult)).isCompleteSuccess)
        assertEquals(false, WipeSummary.of(emptyList()).isCompleteSuccess)
    }

    // endregion Methods
}
