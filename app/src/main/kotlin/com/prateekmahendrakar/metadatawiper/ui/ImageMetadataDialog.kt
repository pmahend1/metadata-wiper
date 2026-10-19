package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMetadataDialog(file: SelectedFile,
                        options: StripOptions,
                        onDismissRequest: () -> Unit) {
    val itemCount = file.metadataGroups.sumOf { it.rows.size }
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(text = file.displayName, style = MaterialTheme.typography.titleMedium)
                Text(text = "${fileDetailsText(file)} · ${pluralStringResource(id = R.plurals.items_found, count = itemCount, itemCount)}",
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(16.dp))
            MetadataDetails(file = file,
                            options = options,
                            modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
