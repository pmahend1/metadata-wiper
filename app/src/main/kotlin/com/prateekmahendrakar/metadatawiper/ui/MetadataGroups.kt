package com.prateekmahendrakar.metadatawiper.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.wipe.MetadataGroup
import com.prateekmahendrakar.metadatawiper.wipe.MetadataRow
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

@Composable
fun MetadataGroups(file: SelectedFile,
                   options: StripOptions,
                   modifier: Modifier = Modifier) {
    // Never let an unreadable file look like a clean one.
    if (file.canBeCleaned == false) {
        Text(stringResource(id = R.string.metadata_unreadable), modifier = modifier.padding(16.dp))
        return
    }
    if (file.metadataGroups.isEmpty()) {
        Text(stringResource(id = R.string.no_metadata_found), modifier = modifier.padding(16.dp))
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        file.metadataGroups.forEach { group ->
            MetadataGroupCard(group = group, isKept = options.keeps(group.category))
        }
    }
}

@Composable
private fun MetadataGroupCard(group: MetadataGroup,
                              isKept: Boolean) {
    val colors = if (group.isHighRisk) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = stringResource(id = MetadataCategoryLabel.of(group.category)),
                 style = MaterialTheme.typography.titleMedium,
                 modifier = Modifier.weight(1f))
            if (isKept) {
                Text(text = stringResource(id = R.string.kept), style = MaterialTheme.typography.labelLarge)
            }
        }
        group.rows.forEach { row ->
            HorizontalDivider()
            MetadataRowItem(row)
        }
    }
}

@Composable
private fun MetadataRowItem(row: MetadataRow) {
    val context = LocalContext.current
    val size = if (row.byteSize > 0) Formatter.formatShortFileSize(context, row.byteSize) else ""
    val value = listOf(row.value, size).filter { it.isNotEmpty() }
                                       .joinToString(" · ")
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = row.label,
             fontWeight = if (row.isStructural) FontWeight.Bold else FontWeight.Normal,
             modifier = Modifier
                 .weight(1f)
                 .padding(end = 8.dp))
        Text(text = value, modifier = Modifier.weight(1f))
    }
}
