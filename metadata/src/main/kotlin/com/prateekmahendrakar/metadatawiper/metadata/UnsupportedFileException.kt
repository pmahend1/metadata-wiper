package com.prateekmahendrakar.metadatawiper.metadata

import java.io.IOException

// A well-formed file this app can't clean, as opposed to a MalformedFileException for a damaged one.
class UnsupportedFileException(val reason: UnsupportedReason,
                               message: String) : IOException(message)
