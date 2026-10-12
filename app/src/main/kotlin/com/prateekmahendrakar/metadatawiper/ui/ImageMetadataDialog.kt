package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMetadataDialog(file: SelectedFile, onDismissRequest: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { onDismissRequest() }) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(file.displayName,
                 modifier = Modifier.fillMaxWidth(),
                 style = MaterialTheme.typography.titleSmall,
                 textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            MetadataTable(metadata = file.exifDetails, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp))
        }
    }
}
