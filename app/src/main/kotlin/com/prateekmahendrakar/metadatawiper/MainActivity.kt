package com.prateekmahendrakar.metadatawiper

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.prateekmahendrakar.metadatawiper.share.ShareIntents
import com.prateekmahendrakar.metadatawiper.ui.MetadataGroups
import com.prateekmahendrakar.metadatawiper.ui.ResultsList
import com.prateekmahendrakar.metadatawiper.ui.SelectionGrid
import com.prateekmahendrakar.metadatawiper.ui.SettingsDialog
import com.prateekmahendrakar.metadatawiper.ui.theme.MetaDataWiperTheme
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.viewmodel.SettingsViewModel
import com.prateekmahendrakar.metadatawiper.wipe.WipeUiState
import com.prateekmahendrakar.metadatawiper.wipe.WipeViewModel

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val wipeViewModel: WipeViewModel by viewModels { WipeViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        // A recreated activity (rotation, process restore) gets the same intent back; it was already handled.
        if (savedInstanceState == null) {
            selectSharedFiles(intent)
        }

        enableEdgeToEdge()
        setContent {
            val theme by settingsViewModel.theme.collectAsState()
            val overwriteOriginal by settingsViewModel.overwriteOriginal.collectAsState()
            val privacy by settingsViewModel.privacy.collectAsState()
            val wipeState by wipeViewModel.uiState.collectAsState()
            val useDarkTheme = when (theme) {
                Theme.System -> isSystemInDarkTheme()
                Theme.Light -> false
                Theme.Dark -> true
            }

            MetaDataWiperTheme(darkTheme = useDarkTheme) {
                var showSettingsDialog by remember { mutableStateOf(false) }

                val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
                val versionName = packageInfo.versionName

                val context = LocalContext.current
                val pendingShare = wipeState.pendingShare
                val shareTitle = stringResource(id = R.string.share_chooser_title)
                LaunchedEffect(pendingShare) {
                    if (pendingShare != null) {
                        context.startActivity(ShareIntents.chooserFor(pendingShare, shareTitle))
                        wipeViewModel.onShareLaunched()
                    }
                }

                if (showSettingsDialog) {
                    SettingsDialog(currentTheme = theme,
                                   onThemeChange = { settingsViewModel.setTheme(it) },
                                   onDismiss = { showSettingsDialog = false },
                                   currentOverwriteOriginal = overwriteOriginal,
                                   onOverwriteOriginalChange = { settingsViewModel.setOverwriteOriginal(it) },
                                   privacy = privacy,
                                   onKeepColorProfileChange = { settingsViewModel.setKeepColorProfile(it) },
                                   onKeepOrientationChange = { settingsViewModel.setKeepOrientation(it) },
                                   onRandomizeFileNamesChange = { settingsViewModel.setRandomizeFileNames(it) },
                                   appVersion = versionName ?: "")
                }

                Scaffold(topBar = {
                    TopAppBar(title = {
                        Text(text = stringResource(id = R.string.app_name), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                              actions = {
                                  SettingsButton(onClick = { showSettingsDialog = true })
                              },
                              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                                                                         titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                                                         actionIconContentColor = MaterialTheme.colorScheme.onPrimary))
                }) { padding ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()) {
                        Spacer(modifier = Modifier.height(24.dp))

                        ActionButtons(state = wipeState,
                                      settings = privacy,
                                      overwriteOriginal = overwriteOriginal,
                                      onFilesSelected = { wipeViewModel.select(it) },
                                      onOverwrite = { wipeViewModel.overwriteOriginals(privacy) },
                                      onSaveTo = { wipeViewModel.saveTo(it, privacy) },
                                      onSaveToFolder = { wipeViewModel.saveToFolder(it, privacy) },
                                      onShare = { wipeViewModel.share(privacy) })

                        Spacer(modifier = Modifier.height(16.dp))

                        val selectedFiles = wipeState.files
                        if (wipeState.results.isNotEmpty()) {
                            ResultsList(results = wipeState.results,
                                        summary = wipeState.summary,
                                        modifier = Modifier.fillMaxSize())
                        } else if (selectedFiles.size == 1) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painter = rememberAsyncImagePainter(selectedFiles.first().uri),
                                      contentDescription = stringResource(id = R.string.selected_image),
                                      modifier = Modifier
                                          .fillMaxWidth()
                                          .defaultMinSize(minWidth = 250.dp, minHeight = 250.dp)
                                          .padding(horizontal = 8.dp),
                                      contentScale = ContentScale.Fit)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(selectedFiles.first().displayName, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(12.dp))
                                MetadataGroups(file = selectedFiles.first(),
                                               options = privacy.stripOptions,
                                               modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        } else if (selectedFiles.size > 1) {
                            SelectionGrid(files = selectedFiles, privacy = privacy)
                        } else {
                            Text(text = stringResource(id = R.string.select_images_prompt),
                                 style = MaterialTheme.typography.bodyLarge,
                                 textAlign = TextAlign.Justify,
                                 modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        selectSharedFiles(intent)
    }

    private fun selectSharedFiles(intent: Intent) {
        val uris = ShareIntents.urisFrom(intent)
        if (uris.isNotEmpty()) {
            wipeViewModel.selectShared(uris)
        }
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(id = R.drawable.settings_24px),
             contentDescription = stringResource(id = R.string.settings),
             tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MetaDataWiperTheme {
        Scaffold(topBar = {
            TopAppBar(title = {
                Text(text = stringResource(id = R.string.app_name), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            },
                      actions = {
                          SettingsButton(onClick = { })
                      },
                      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                                                                 titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                                                 actionIconContentColor = MaterialTheme.colorScheme.onPrimary))
        }) { padding ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                   modifier = Modifier
                       .padding(padding)
                       .fillMaxSize()
                       .verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(state = WipeUiState(),
                              settings = PrivacySettings(),
                              overwriteOriginal = false,
                              onFilesSelected = {},
                              onOverwrite = {},
                              onSaveTo = {},
                              onSaveToFolder = {},
                              onShare = {})

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = stringResource(id = R.string.select_images_prompt),
                     style = MaterialTheme.typography.bodyLarge,
                     textAlign = TextAlign.Center,
                     modifier = Modifier.padding(16.dp))
            }
        }
    }
}
