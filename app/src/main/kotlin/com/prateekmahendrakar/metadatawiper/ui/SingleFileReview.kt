package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.wipe.MetadataGrouping
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleFileReview(file: SelectedFile,
                     options: StripOptions,
                     modifier: Modifier = Modifier) {
    val highRiskCategories = file.removableCategories(options).filter { MetadataGrouping.isHighRisk(it) }
                                                             .sortedBy { MetadataGrouping.riskRank(it) }
    Column(modifier = modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
           verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = rememberAsyncImagePainter(file.uri),
                  contentDescription = stringResource(id = R.string.selected_image),
                  contentScale = ContentScale.Crop,
                  modifier = Modifier
                      .size(96.dp)
                      .clip(RoundedCornerShape(16.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = file.displayName, style = MaterialTheme.typography.titleMedium)
                Text(text = fileDetailsText(file),
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    highRiskCategories.forEach { category ->
                        RiskChip(text = stringResource(id = MetadataCategoryResources.label(category)),
                                 icon = MetadataCategoryResources.icon(category))
                    }
                }
            }
        }
        MetadataDetails(file = file, options = options)
    }
}
