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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.wipe.WipePhase
import com.prateekmahendrakar.metadatawiper.wipe.WipeProgress

@Composable
fun WipeProgressCard(progress: WipeProgress,
                     isCancelling: Boolean,
                     onCancel: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = progress.fileName,
                 style = MaterialTheme.typography.titleSmall,
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis)
            Text(text = stringResource(id = R.string.progress_file, progress.fileIndex + 1, progress.fileCount, stringResource(id = phaseLabel(progress.phase))),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fraction = progress.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            Text(text = stringResource(id = if (progress.canCancel) R.string.progress_keep_open else R.string.progress_replacing),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCancel, enabled = progress.canCancel && isCancelling == false) {
                Text(text = stringResource(id = if (isCancelling) R.string.cancelling else R.string.cancel))
            }
        }
    }
}

private fun phaseLabel(phase: WipePhase): Int {
    return when (phase) {
        WipePhase.Reading -> R.string.progress_reading
        WipePhase.Stripping -> R.string.progress_cleaning
        WipePhase.Verifying -> R.string.progress_checking
        WipePhase.Writing -> R.string.progress_saving
    }
}
