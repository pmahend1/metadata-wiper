package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.wipe.MetadataGrouping
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

/*
 * The metadata sheet is hosted here, once, rather than inside a grid cell: a sheet owned by a
 * lazy item is torn down and recreated whenever the grid recomposes that item, which can make
 * it jump.
 */
@Composable
fun BatchReview(files: List<SelectedFile>,
                options: StripOptions,
                modifier: Modifier = Modifier) {
    var inspectedFile by remember { mutableStateOf<SelectedFile?>(null) }
    val hasLocation = files.any { MetadataCategory.Location in it.removableCategories(options) }
    val hasUncleanable = files.any { it.canBeCleaned == false }

    LazyVerticalGrid(columns = GridCells.Fixed(3),
                     modifier = modifier.fillMaxSize(),
                     contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                     horizontalArrangement = Arrangement.spacedBy(6.dp),
                     verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            BatchSummary(files, options)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(text = stringResource(id = R.string.tap_to_inspect),
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 modifier = Modifier.padding(vertical = 8.dp))
        }
        // The index is part of the key because the same file can be picked twice.
        itemsIndexed(files, key = { index, file -> "$index:${file.uri}" }) { _, file ->
            Thumbnail(file = file,
                      hasLocation = MetadataCategory.Location in file.removableCategories(options),
                      onClick = { inspectedFile = file })
        }
        if (hasLocation || hasUncleanable) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (hasLocation) {
                        LegendEntry(MaterialTheme.colorScheme.error, stringResource(id = R.string.legend_location))
                    }
                    if (hasUncleanable) {
                        LegendEntry(MaterialTheme.colorScheme.inverseSurface, stringResource(id = R.string.legend_unsupported))
                    }
                }
            }
        }
    }

    inspectedFile?.let { file ->
        ImageMetadataDialog(file = file,
                            options = options,
                            onDismissRequest = { inspectedFile = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchSummary(files: List<SelectedFile>,
                         options: StripOptions) {
    val cleanable = files.count { it.canBeCleaned }
    val uncleanable = files.size - cleanable
    val categoryCounts = files.flatMap { it.removableCategories(options) }
                              .groupingBy { it }
                              .eachCount()
                              .toList()
                              .sortedBy { (category, _) -> MetadataGrouping.riskRank(category) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (categoryCounts.isEmpty()) {
                Text(text = stringResource(id = R.string.no_removable_metadata), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(text = pluralStringResource(id = R.plurals.images_can_be_cleaned, count = cleanable, cleanable),
                     style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryCounts.forEach { (category, count) ->
                        RiskChip(text = stringResource(id = R.string.category_count, stringResource(id = MetadataCategoryResources.label(category)), count),
                                 isHighRisk = MetadataGrouping.isHighRisk(category),
                                 icon = MetadataCategoryResources.icon(category))
                    }
                }
            }
            if (uncleanable > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painter = painterResource(id = R.drawable.info_24px),
                         contentDescription = null,
                         tint = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.size(16.dp))
                    Text(text = pluralStringResource(id = R.plurals.files_cannot_be_cleaned, count = uncleanable, uncleanable),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: SelectedFile,
                      hasLocation: Boolean,
                      onClick: () -> Unit) {
    Box(modifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .clickable(onClick = onClick)) {
        Image(painter = rememberAsyncImagePainter(file.uri),
              contentDescription = file.displayName,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize())
        if (file.canBeCleaned == false) {
            Badge(R.drawable.block_24px, MaterialTheme.colorScheme.inverseSurface, MaterialTheme.colorScheme.inverseOnSurface)
        } else if (hasLocation) {
            Badge(R.drawable.location_on_24px, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Badge(@DrawableRes icon: Int,
                  background: Color,
                  tint: Color) {
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(6.dp),
        contentAlignment = Alignment.TopEnd) {
        Box(modifier = Modifier
            .size(26.dp)
            .shadow(2.dp, CircleShape)
            .background(background, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(id = icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LegendEntry(color: Color,
                        text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
