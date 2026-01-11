package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.prateekmahendrakar.metadatawiper.utils.getAllExifTags
import com.prateekmahendrakar.metadatawiper.utils.getFileName
import com.prateekmahendrakar.metadatawiper.utils.getFormattedExifValue
import kotlin.io.path.createTempFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageMetadataDialog(uri: Uri, onDismissRequest: () -> Unit) {
    val displayHashMap = HashMap<String, String>()
    val originalTempFile = createTempFile().toFile()
    LocalContext.current.contentResolver.openInputStream(uri)?.use { inputStream ->
        originalTempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    val exifReader = ExifInterface(originalTempFile.absolutePath)

    val allTags = getAllExifTags()
    for (tag in allTags) {
        val stringValue = getFormattedExifValue(exifReader, tag)
        displayHashMap[tag] = stringValue
    }

    ModalBottomSheet(onDismissRequest = { onDismissRequest() }) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(getFileName(LocalContext.current, uri) ?: "",
                 modifier = Modifier.fillMaxWidth(),
                 style = MaterialTheme.typography.titleSmall,
                 textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            MetadataTable(metadata = displayHashMap, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp))
        }
    }
}