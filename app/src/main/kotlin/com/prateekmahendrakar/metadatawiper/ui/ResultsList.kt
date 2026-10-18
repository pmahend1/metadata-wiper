package com.prateekmahendrakar.metadatawiper.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.wipe.CleanedResult
import com.prateekmahendrakar.metadatawiper.wipe.FailedResult
import com.prateekmahendrakar.metadatawiper.wipe.FileWipeResult
import com.prateekmahendrakar.metadatawiper.wipe.NothingToRemoveResult
import com.prateekmahendrakar.metadatawiper.wipe.UnsupportedResult
import com.prateekmahendrakar.metadatawiper.wipe.WipeFailure
import com.prateekmahendrakar.metadatawiper.wipe.WipeResult
import com.prateekmahendrakar.metadatawiper.wipe.WipeSummary

@Composable
fun ResultsList(results: List<FileWipeResult>,
                summary: WipeSummary,
                modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth(),
               contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
               verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val headline = if (summary.isCompleteSuccess) {
                stringResource(id = R.string.metadata_wiped_successfully)
            } else {
                stringResource(id = R.string.wipe_summary, summary.cleaned, summary.nothingToRemove, summary.unsupported, summary.failed)
            }
            Text(text = headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 8.dp))
        }
        // The index is part of the key because the same file can be picked twice.
        itemsIndexed(results, key = { index, wiped -> "$index:${wiped.file.uri}" }) { _, wiped ->
            ResultCard(wiped)
        }
    }
}

@Composable
private fun ResultCard(wiped: FileWipeResult) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable { isExpanded = isExpanded == false }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = wiped.file.displayName,
                     style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier
                         .weight(1f)
                         .padding(end = 8.dp))
                Text(text = stringResource(id = statusLabel(wiped.result)),
                     color = statusColor(wiped.result),
                     style = MaterialTheme.typography.labelLarge,
                     fontWeight = FontWeight.Bold)
            }
            if (isExpanded) {
                ResultDetails(wiped.result)
            }
        }
    }
}

@Composable
private fun ResultDetails(result: WipeResult) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (result) {
            is CleanedResult -> {
                ReportSection(R.string.report_removed, result.report.removed.map { describe(it) })
                ReportSection(R.string.report_kept, result.report.kept.map { describe(it) })
                val decodeWarning = stringResource(id = R.string.decode_check_skipped)
                val warnings = if (result.decodeCheckSkipped) result.report.warnings + decodeWarning else result.report.warnings
                ReportSection(R.string.report_warnings, warnings)
            }
            NothingToRemoveResult -> Text(stringResource(id = R.string.nothing_to_remove_detail))
            is UnsupportedResult -> Text(unsupportedMessage(result.format))
            is FailedResult -> Text(stringResource(id = failureMessage(result.failure)))
        }
    }
}

@Composable
private fun ReportSection(titleId: Int,
                          lines: List<String>) {
    if (lines.isEmpty()) {
        return
    }
    Text(text = stringResource(id = titleId), style = MaterialTheme.typography.titleSmall)
    lines.forEach { Text(text = "• $it", style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun describe(item: MetadataItem): String {
    val context = LocalContext.current
    val size = if (item.byteSize > 0) Formatter.formatShortFileSize(context, item.byteSize) else ""
    val category = stringResource(id = MetadataCategoryLabel.of(item.category))
    return listOf("$category: ${item.label}", item.detail, size).filter { it.isNotEmpty() }
                                                                .joinToString(" · ")
}

@Composable
private fun unsupportedMessage(format: FileFormat): String {
    if (format == FileFormat.Unknown) {
        return stringResource(id = R.string.unknown_format)
    }
    return stringResource(id = R.string.unsupported_format, format.extension.uppercase())
}

private fun statusLabel(result: WipeResult): Int {
    return when (result) {
        is CleanedResult -> R.string.status_cleaned
        NothingToRemoveResult -> R.string.status_nothing_to_remove
        is UnsupportedResult -> R.string.status_unsupported
        is FailedResult -> R.string.status_failed
    }
}

@Composable
private fun statusColor(result: WipeResult): Color {
    return when (result) {
        is CleanedResult -> MaterialTheme.colorScheme.primary
        NothingToRemoveResult -> MaterialTheme.colorScheme.onSurfaceVariant
        is UnsupportedResult, is FailedResult -> MaterialTheme.colorScheme.error
    }
}

private fun failureMessage(failure: WipeFailure): Int {
    return when (failure) {
        WipeFailure.Unreadable -> R.string.failure_unreadable
        WipeFailure.Malformed -> R.string.failure_malformed
        WipeFailure.MetadataRemained -> R.string.failure_metadata_remained
        WipeFailure.InvalidOutput -> R.string.failure_invalid_output
        WipeFailure.DecodeFailed -> R.string.failure_decode_failed
        WipeFailure.WriteFailed -> R.string.failure_write_failed
        WipeFailure.Unexpected -> R.string.failure_unexpected
    }
}
