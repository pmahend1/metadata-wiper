package com.prateekmahendrakar.metadatawiper.wipe

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat

data class UnsupportedResult(val format: FileFormat) : WipeResult
