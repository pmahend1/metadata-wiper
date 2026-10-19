package com.prateekmahendrakar.metadatawiper.share

import android.net.Uri

data class PendingShare(val uris: List<Uri>,
                        val mimeType: String)
