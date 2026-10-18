package com.prateekmahendrakar.metadatawiper.ui

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.wipe.MetadataGroup
import com.prateekmahendrakar.metadatawiper.wipe.MetadataRow
import com.prateekmahendrakar.metadatawiper.wipe.MetadataSections
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

@Composable
fun MetadataDetails(file: SelectedFile,
                    options: StripOptions,
                    modifier: Modifier = Modifier) {
    // Never let an unreadable file look like a clean one.
    if (file.canBeCleaned == false) {
        Text(stringResource(id = R.string.metadata_unreadable), modifier = modifier.padding(vertical = 16.dp))
        return
    }
    val sections = remember(file, options) { MetadataSections.of(file.metadataGroups, options) }
    if (sections.isEmpty) {
        Text(stringResource(id = R.string.no_metadata_found), modifier = modifier.padding(vertical = 16.dp))
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections.cards.forEach { GroupCard(it) }
        if (sections.more.isNotEmpty()) {
            MoreCard(sections.more)
        }
        if (sections.kept.isNotEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                CardHeader(icon = R.drawable.check_24px,
                           title = stringResource(id = R.string.kept_title),
                           reason = stringResource(id = R.string.kept_reason))
                Rows(sections.kept)
            }
        }
    }
}

@Composable
private fun GroupCard(group: MetadataGroup) {
    val reason = MetadataCategoryResources.reason(group.category)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(icon = MetadataCategoryResources.icon(group.category),
                   title = stringResource(id = MetadataCategoryResources.label(group.category)),
                   reason = reason?.let { stringResource(id = it) },
                   isHighRisk = group.isHighRisk)
        Rows(group.rows)
    }
}

@Composable
private fun MoreCard(rows: List<MetadataRow>) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.clickable(role = Role.Button) { isExpanded = isExpanded == false },
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                CardHeader(icon = R.drawable.photo_camera_24px,
                           title = stringResource(id = R.string.more_title),
                           reason = pluralStringResource(id = R.plurals.more_count, count = rows.size, rows.size))
            }
            Icon(painter = painterResource(id = if (isExpanded) R.drawable.expand_less_24px else R.drawable.expand_more_24px),
                 contentDescription = null,
                 tint = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(end = 14.dp))
        }
        if (isExpanded) {
            Rows(rows)
        }
    }
}

@Composable
private fun CardHeader(@DrawableRes icon: Int,
                       title: String,
                       reason: String?,
                       isHighRisk: Boolean = false) {
    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryIcon(icon, isHighRisk)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (reason != null) {
                Text(text = reason,
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isHighRisk) {
            RiskChip(stringResource(id = R.string.high_risk))
        }
    }
}

@Composable
private fun CategoryIcon(@DrawableRes icon: Int,
                         isHighRisk: Boolean) {
    val background = if (isHighRisk) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = if (isHighRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier
        .size(36.dp)
        .background(background, CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(painter = painterResource(id = icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Rows(rows: List<MetadataRow>) {
    rows.forEach { row ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MetadataRowItem(row)
    }
}

@Composable
private fun MetadataRowItem(row: MetadataRow) {
    val context = LocalContext.current
    // Raw EXIF tag names are shown as they are; they're what other tools call them.
    val readableLabel = if (row.isStructural) StructuralLabel.of(row.label) else null
    val label = readableLabel?.let { stringResource(id = it) } ?: row.label
    val readableValue = if (row.isStructural) StructuralLabel.valueFor(row.label, row.value) else null
    // A size only helps when there's no text to show, e.g. for a thumbnail or an embedded video.
    val value = when {
        readableValue != null -> stringResource(id = readableValue)
        row.value.isNotEmpty() -> row.value
        row.byteSize > 0 -> Formatter.formatShortFileSize(context, row.byteSize)
        else -> stringResource(id = R.string.value_present)
    }
    val note = if (row.isStructural) StructuralLabel.noteFor(row.label, row.value) else null

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = label,
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.weight(0.42f))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.58f))
        }
        if (note != null) {
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painter = painterResource(id = R.drawable.info_24px),
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier
                         .padding(top = 2.dp)
                         .size(14.dp))
                Text(text = stringResource(id = note),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
