package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R

@Composable
fun MetadataTable(metadata: Map<String, String>, modifier: Modifier = Modifier) {
    if (metadata.isEmpty()) {
        Text(stringResource(id = R.string.no_exif_data_found), modifier = modifier.padding(16.dp))
        return
    }
    val filteredEntries = metadata.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key }
    Card(
        modifier = modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp)
            ) {
                Text(
                    stringResource(id = R.string.tag),
                    modifier = modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(id = R.string.value_with_count, filteredEntries.size),
                    modifier = modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(modifier = modifier.padding(vertical = 8.dp), thickness = 2.dp)

            filteredEntries.forEachIndexed { index, (key, value) ->
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(all = 6.dp)
                ) {
                    Text(key, modifier = Modifier.weight(1f))
                    Text(value, modifier = Modifier.weight(1f))
                }
                if (index != filteredEntries.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}
