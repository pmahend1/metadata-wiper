package com.prateekmahendrakar.metadatawiper.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object ShareIntents {

    // region Methods

    fun urisFrom(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
    }

    // The ClipData carries the read grant; EXTRA_STREAM alone doesn't grant anything on every API level.
    fun chooserFor(share: PendingShare,
                   title: String): Intent {
        val send = if (share.uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, share.uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(share.uris))
        }
        send.type = share.mimeType
        send.clipData = ClipData.newRawUri(null, share.uris.first()).apply {
            share.uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    // endregion Methods
}
