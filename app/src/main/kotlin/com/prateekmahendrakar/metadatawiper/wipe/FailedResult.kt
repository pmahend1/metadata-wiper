package com.prateekmahendrakar.metadatawiper.wipe

// bytesNeeded is only set for WipeFailure.InsufficientSpace.
data class FailedResult(val failure: WipeFailure,
                        val bytesNeeded: Long = 0L) : WipeResult
