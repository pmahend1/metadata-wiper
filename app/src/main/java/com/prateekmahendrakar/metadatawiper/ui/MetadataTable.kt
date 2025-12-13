package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun MetadataTable(metadata: Map<String, String>) {
    if (metadata.isEmpty()) {
        Text(stringResource(id = R.string.no_exif_data_found), modifier = Modifier.padding(16.dp))
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(id = R.string.tag), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(stringResource(id = R.string.value_with_count, metadata.size), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            metadata.entries.forEach { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(key, modifier = Modifier.weight(1f))
                    Text(value, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
        }
    }
}
