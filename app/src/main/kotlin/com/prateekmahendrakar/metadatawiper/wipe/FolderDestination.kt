package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri

data class FolderDestination(val treeUri: Uri,
                             val displayName: String) : WipeDestination
