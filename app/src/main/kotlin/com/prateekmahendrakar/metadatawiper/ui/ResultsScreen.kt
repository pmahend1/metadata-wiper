package com.prateekmahendrakar.metadatawiper.ui

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.wipe.CleanedResult
import com.prateekmahendrakar.metadatawiper.wipe.FailedResult
import com.prateekmahendrakar.metadatawiper.wipe.FileWipeResult
import com.prateekmahendrakar.metadatawiper.wipe.MetadataGrouping
import com.prateekmahendrakar.metadatawiper.wipe.NothingToRemoveResult
import com.prateekmahendrakar.metadatawiper.wipe.UnsupportedResult
import com.prateekmahendrakar.metadatawiper.wipe.WipeAction
import com.prateekmahendrakar.metadatawiper.wipe.WipeFailure
import com.prateekmahendrakar.metadatawiper.wipe.WipeResult
import com.prateekmahendrakar.metadatawiper.wipe.WipeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: WipeUiState,
                  onShare: () -> Unit,
                  onDone: () -> Unit) {
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text(stringResource(id = R.string.results_title)) })
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    },
             bottomBar = {
                 BottomActionBar(secondaryText = stringResource(id = R.string.share),
                                 secondaryIcon = R.drawable.share_24px,
                                 secondaryEnabled = state.isBusy == false && state.hasShareableResults,
                                 onSecondary = onShare,
                                 primaryText = stringResource(id = R.string.done),
                                 primaryIcon = null,
                                 primaryEnabled = state.isBusy == false,
                                 onPrimary = onDone)
             }) { padding ->
        LazyColumn(modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
                   contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                   verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ResultsHeader(state)
            }
            // The index is part of the key because the same file can be picked twice.
            itemsIndexed(state.results, key = { index, wiped -> "$index:${wiped.file.uri}" }) { _, wiped ->
                ResultCard(wiped)
            }
        }
    }
}

@Composable
private fun ResultsHeader(state: WipeUiState) {
    val summary = state.summary
    val headline = if (summary.cleaned > 0) {
        pluralStringResource(id = R.plurals.results_cleaned, count = summary.cleaned, summary.cleaned)
    } else {
        stringResource(id = R.string.results_none_cleaned)
    }
    val skipped = summary.unsupported + summary.failed
    val action = when (state.lastAction) {
        WipeAction.Saved -> R.plurals.results_saved
        WipeAction.Replaced -> R.string.results_replaced
        WipeAction.Shared -> R.string.results_shared
        null -> null
    }
    val actionText = when {
        summary.cleaned == 0 || action == null -> null
        state.lastAction == WipeAction.Saved -> pluralStringResource(id = action, count = summary.cleaned)
        else -> stringResource(id = action)
    }
    val details = listOfNotNull(actionText,
                                if (summary.nothingToRemove > 0) pluralStringResource(id = R.plurals.results_already_clean,
                                                                                     count = summary.nothingToRemove,
                                                                                     summary.nothingToRemove) else null,
                                if (skipped > 0) pluralStringResource(id = R.plurals.results_skipped, count = skipped, skipped) else null)

    Row(modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = R.drawable.check_24px),
                 contentDescription = null,
                 tint = MaterialTheme.colorScheme.onPrimaryContainer,
                 modifier = Modifier.size(28.dp))
        }
        Column {
            Text(text = headline, style = MaterialTheme.typography.titleLarge)
            if (details.isNotEmpty()) {
                Text(text = details.joinToString(" · "),
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultCard(wiped: FileWipeResult) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val result = wiped.result
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { isExpanded = isExpanded == false }
            .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painter = rememberAsyncImagePainter(wiped.file.uri),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = wiped.file.displayName, style = MaterialTheme.typography.titleSmall)
                Text(text = if (result is NothingToRemoveResult) fileDetailsText(wiped.file) else resultSummary(result),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(result)
        }
        if (isExpanded) {
            Column(modifier = Modifier.padding(start = 68.dp, end = 14.dp, bottom = 14.dp),
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultDetails(result)
            }
        }
    }
}

@Composable
private fun ResultDetails(result: WipeResult) {
    when (result) {
        is CleanedResult -> {
            ReportSection(R.string.report_removed, result.report.removed)
            ReportSection(R.string.report_kept, result.report.kept)
            val decodeWarning = stringResource(id = R.string.decode_check_skipped)
            val warnings = if (result.decodeCheckSkipped) result.report.warnings + decodeWarning else result.report.warnings
            if (warnings.isNotEmpty()) {
                SectionTitle(R.string.report_warnings)
                warnings.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        NothingToRemoveResult, is UnsupportedResult, is FailedResult -> Text(text = resultSummary(result), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportSection(titleId: Int,
                          items: List<MetadataItem>) {
    if (items.isEmpty()) {
        return
    }
    SectionTitle(titleId)
    items.groupBy { it.category }
         .toSortedMap(compareBy { MetadataGrouping.riskRank(it) })
         .forEach { (category, categoryItems) ->
             Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                 Text(text = stringResource(id = MetadataCategoryResources.label(category)),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.width(96.dp))
                 Column {
                     categoryItems.forEach { Text(text = reportLine(it), style = MaterialTheme.typography.bodyMedium) }
                 }
             }
         }
}

@Composable
private fun reportLine(item: MetadataItem): String {
    val label = StructuralLabel.of(item.label)?.let { stringResource(id = it) } ?: item.label
    val readableValue = StructuralLabel.valueFor(item.label, item.detail)?.let { stringResource(id = it) }
    val detail = when {
        readableValue != null -> readableValue
        item.detail.isEmpty() && item.byteSize > 0 -> Formatter.formatShortFileSize(LocalContext.current, item.byteSize)
        else -> item.detail
    }
    return listOf(label, detail).filter { it.isNotEmpty() }
                                .joinToString(" · ")
}

@Composable
private fun SectionTitle(titleId: Int) {
    Text(text = stringResource(id = titleId).uppercase(),
         style = MaterialTheme.typography.labelMedium,
         color = MaterialTheme.colorScheme.onSurfaceVariant,
         modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun StatusPill(result: WipeResult) {
    val (container, content) = when (result) {
        is CleanedResult -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        is FailedResult -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        NothingToRemoveResult, is UnsupportedResult -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(stringResource(id = statusLabel(result)), container, content)
}

@Composable
private fun StatusPill(text: String,
                       container: Color,
                       content: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = container, contentColor = content) {
        Text(text = text,
             style = MaterialTheme.typography.labelMedium,
             modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun resultSummary(result: WipeResult): String {
    return when (result) {
        is CleanedResult -> pluralStringResource(id = R.plurals.removed_count, count = result.report.removed.size, result.report.removed.size)
        NothingToRemoveResult -> stringResource(id = R.string.nothing_to_remove_detail)
        is UnsupportedResult -> unsupportedMessage(result.format)
        is FailedResult -> stringResource(id = failureMessage(result.failure))
    }
}

@Composable
private fun unsupportedMessage(format: FileFormat): String {
    if (format == FileFormat.Unknown) {
        return stringResource(id = R.string.unknown_format)
    }
    return stringResource(id = R.string.unsupported_format, formatName(format))
}

private fun statusLabel(result: WipeResult): Int {
    return when (result) {
        is CleanedResult -> R.string.status_cleaned
        NothingToRemoveResult -> R.string.status_nothing_to_remove
        is UnsupportedResult -> R.string.status_unsupported
        is FailedResult -> R.string.status_failed
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
