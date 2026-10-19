package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.StripReport

data class CleanedResult(val report: StripReport,
                         val decodeCheckSkipped: Boolean = false) : WipeResult
