package com.prateekmahendrakar.metadatawiper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.ui.theme.MetaDataWiperTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(isBusy: Boolean,
               onFilesSelected: (List<Uri>) -> Unit,
               onOpenSettings: () -> Unit) {
    val openFilesLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments(),
                                                              onResult = onFilesSelected)

    Scaffold(topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconBadge()
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(id = R.string.app_name))
            }
        },
                  actions = {
                      IconButton(onClick = onOpenSettings) {
                          Icon(painter = painterResource(id = R.drawable.settings_24px), contentDescription = stringResource(id = R.string.settings))
                      }
                  })
    }) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            // Centred when it fits, scrollable when large text makes it taller than the screen.
            BoxWithConstraints(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 32.dp),
                       horizontalAlignment = Alignment.CenterHorizontally,
                       verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
                    Box(modifier = Modifier
                        .size(112.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(id = R.drawable.hide_image_24px),
                             contentDescription = null,
                             tint = MaterialTheme.colorScheme.onPrimaryContainer,
                             modifier = Modifier.size(56.dp))
                    }
                    Text(text = stringResource(id = R.string.home_headline),
                         style = MaterialTheme.typography.headlineSmall,
                         textAlign = TextAlign.Center)
                    Text(text = stringResource(id = R.string.home_body),
                         style = MaterialTheme.typography.bodyLarge,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center)
                    Button(onClick = { openFilesLauncher.launch(arrayOf("image/*")) },
                           enabled = isBusy == false,
                           modifier = Modifier
                               .padding(top = 16.dp)
                               .height(56.dp)) {
                        Icon(painter = painterResource(id = R.drawable.image_24px), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = stringResource(id = R.string.choose_images), style = MaterialTheme.typography.titleMedium)
                    }
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.width(160.dp))
                    }
                    Text(text = stringResource(id = R.string.choose_several_hint),
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MetaDataWiperTheme {
        HomeScreen(isBusy = false, onFilesSelected = {}, onOpenSettings = {})
    }
}
