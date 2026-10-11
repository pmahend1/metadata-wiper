package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri

// A document the user has already created with the system "save as" picker.
data class DocumentDestination(val uri: Uri) : WipeDestination
