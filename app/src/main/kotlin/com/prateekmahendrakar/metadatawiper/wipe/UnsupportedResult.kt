package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason

data class UnsupportedResult(val format: FileFormat,
                             val reason: UnsupportedReason) : WipeResult
