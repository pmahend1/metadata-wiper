package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.wipe.LoadingProgress

// One file is named; a batch is counted, because its names would flick past too fast to read.
@Composable
fun LoadingCard(loading: LoadingProgress,
                isCancelling: Boolean,
                onCancel: () -> Unit,
                modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = if (loading.fileCount == 1) {
                loading.fileName
            } else {
                pluralStringResource(id = R.plurals.loading_files, count = loading.fileCount, loading.fileCount)
            }
            Text(text = title,
                 style = MaterialTheme.typography.titleSmall,
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis)
            val fraction = loading.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            // Only once a file is being copied; a local file opens too fast for the hint to mean anything.
            if (loading.isDownloading) {
                Text(text = stringResource(id = R.string.loading_hint),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onCancel, enabled = isCancelling == false) {
                Text(text = stringResource(id = if (isCancelling) R.string.cancelling else R.string.cancel))
            }
        }
    }
}
