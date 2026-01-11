package com.prateekmahendrakar.metadatawiper

import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.model.Theme
import com.prateekmahendrakar.metadatawiper.ui.ActionButtons
import com.prateekmahendrakar.metadatawiper.ui.ImageMetadataDialog
import com.prateekmahendrakar.metadatawiper.ui.MetadataTable
import com.prateekmahendrakar.metadatawiper.ui.SettingsDialog
import com.prateekmahendrakar.metadatawiper.ui.theme.MetaDataWiperTheme
import com.prateekmahendrakar.metadatawiper.utils.getFileName
import com.prateekmahendrakar.metadatawiper.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        enableEdgeToEdge()
        setContent {
            val theme by settingsViewModel.theme.collectAsState()
            val overwriteOriginal by settingsViewModel.overwriteOriginal.collectAsState()
            val useDarkTheme = when (theme) {
                Theme.System -> isSystemInDarkTheme()
                Theme.Light -> false
                Theme.Dark -> true
            }

            MetaDataWiperTheme(darkTheme = useDarkTheme) {
                var metadataMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var hasRemovableExifData by remember { mutableStateOf(false) }
                var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
                var cleanedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
                var originalFileNames by remember { mutableStateOf<List<String>>(emptyList()) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName

                if (showSettingsDialog) {
                    SettingsDialog(
                        currentTheme = theme,
                        onThemeChange = { settingsViewModel.setTheme(it) },
                        onDismiss = { showSettingsDialog = false },
                        currentOverwriteOriginal = overwriteOriginal,
                        onOverwriteOriginalChange = { settingsViewModel.setOverwriteOriginal(it) },
                        appVersion = versionName ?: ""
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            actions = {
                                SettingsButton(onClick = { showSettingsDialog = true })
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Action Buttons ---
                        ActionButtons(
                            cleanedFiles = cleanedFiles,
                            hasRemovableExif = hasRemovableExifData,
                            isEnabled = selectedImageUris.isNotEmpty(),
                            originalFileNames = originalFileNames,
                            onImagesSelected = { uris, metadata, files, hasRemovable, names ->
                                selectedImageUris = uris
                                cleanedFiles = files
                                metadataMap = metadata
                                hasRemovableExifData = hasRemovable
                                originalFileNames = names
                            },
                            overwriteOriginal = overwriteOriginal,
                            selectedImageUris = selectedImageUris
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Content Area ---
                        if (selectedImageUris.size == 1) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedImageUris.first()),
                                    contentDescription = stringResource(id = R.string.selected_image),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minWidth = 250.dp, minHeight = 250.dp)
                                        .padding(horizontal = 8.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    getFileName(LocalContext.current, selectedImageUris.first()) ?: "",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MetadataTable(
                                    metadata = metadataMap,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        } else if (selectedImageUris.size > 1) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(selectedImageUris) { uri ->
                                    var isImageMetadataModalVisible by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = { isImageMetadataModalVisible = !isImageMetadataModalVisible },
                                        colors = ButtonDefaults.buttonColors(Color.Transparent)
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(uri),
                                            contentDescription = stringResource(id = R.string.selected_image),
                                            modifier = Modifier.aspectRatio(1f),
                                            contentScale = ContentScale.Fit,
                                        )
                                        if (isImageMetadataModalVisible) {
                                            ImageMetadataDialog(uri) {
                                                isImageMetadataModalVisible = false
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(id = R.string.select_images_prompt),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(id = R.drawable.settings_24px),
            contentDescription = stringResource(id = R.string.settings),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MetaDataWiperTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    actions = {
                        SettingsButton(onClick = { })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    cleanedFiles = emptyList(),
                    hasRemovableExif = false,
                    isEnabled = false,
                    originalFileNames = emptyList(),
                    onImagesSelected = { _, _, _, _, _ -> },
                    overwriteOriginal = false,
                    selectedImageUris = emptyList()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.select_images_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
