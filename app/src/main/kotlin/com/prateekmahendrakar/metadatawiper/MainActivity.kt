package com.prateekmahendrakar.metadatawiper

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.prateekmahendrakar.metadatawiper.model.Theme
import com.prateekmahendrakar.metadatawiper.share.ShareIntents
import com.prateekmahendrakar.metadatawiper.ui.HomeScreen
import com.prateekmahendrakar.metadatawiper.ui.ResultsScreen
import com.prateekmahendrakar.metadatawiper.ui.ReviewScreen
import com.prateekmahendrakar.metadatawiper.ui.SettingsScreen
import com.prateekmahendrakar.metadatawiper.ui.theme.MetaDataWiperTheme
import com.prateekmahendrakar.metadatawiper.viewmodel.SettingsViewModel
import com.prateekmahendrakar.metadatawiper.wipe.WipeViewModel

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

            // Bar icons follow the app's theme, which can differ from the system's.
            DisposableEffect(useDarkTheme) {
                val barStyle = if (useDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                onDispose { }
            }

            MetaDataWiperTheme(darkTheme = useDarkTheme) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val versionName = remember { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }

                val context = LocalContext.current
                val pendingShare = wipeState.pendingShare
                val shareTitle = stringResource(id = R.string.share_chooser_title)
                LaunchedEffect(pendingShare) {
                    if (pendingShare != null) {
                        context.startActivity(ShareIntents.chooserFor(pendingShare, shareTitle))
                        wipeViewModel.onShareLaunched()
                    }
                }

                when {
                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(theme = theme,
                                       onThemeChange = { settingsViewModel.setTheme(it) },
                                       overwriteOriginal = overwriteOriginal,
                                       onOverwriteOriginalChange = { settingsViewModel.setOverwriteOriginal(it) },
                                       privacy = privacy,
                                       onKeepColorProfileChange = { settingsViewModel.setKeepColorProfile(it) },
                                       onKeepOrientationChange = { settingsViewModel.setKeepOrientation(it) },
                                       onRandomizeFileNamesChange = { settingsViewModel.setRandomizeFileNames(it) },
                                       appVersion = versionName,
                                       onBack = { showSettings = false })
                    }

                    wipeState.results.isNotEmpty() -> {
                        BackHandler { wipeViewModel.clear() }
                        ResultsScreen(state = wipeState,
                                      onShare = { wipeViewModel.shareCleaned(privacy) },
                                      onDone = { wipeViewModel.clear() })
                    }

                    wipeState.files.isNotEmpty() -> {
                        BackHandler { wipeViewModel.clear() }
                        ReviewScreen(state = wipeState,
                                     settings = privacy,
                                     overwriteOriginal = overwriteOriginal,
                                     onBack = { wipeViewModel.clear() },
                                     onOverwrite = { wipeViewModel.overwriteOriginals(privacy) },
                                     onSaveTo = { wipeViewModel.saveTo(it, privacy) },
                                     onSaveToFolder = { wipeViewModel.saveToFolder(it, privacy) },
                                     onShare = { wipeViewModel.share(privacy) })
                    }

                    else -> HomeScreen(isBusy = wipeState.isBusy,
                                       onFilesSelected = { wipeViewModel.select(it) },
                                       onOpenSettings = { showSettings = true })
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
