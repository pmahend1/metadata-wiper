package com.prateekmahendrakar.metadatawiper.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.model.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(Theme.System)
    val theme: StateFlow<Theme> = _theme

    private val _overwriteOriginal = MutableStateFlow(false)
    val overwriteOriginal: StateFlow<Boolean> = _overwriteOriginal

    private val _privacy = MutableStateFlow(PrivacySettings())
    val privacy: StateFlow<PrivacySettings> = _privacy

    private companion object {
        const val KEEP_COLOR_PROFILE = "keepColorProfile"
        const val KEEP_ORIENTATION = "keepOrientation"
        const val RANDOMIZE_FILE_NAMES = "randomizeFileNames"
    }

    init {
        val savedThemeName = prefs.getString("theme", Theme.System.name) ?: Theme.System.name
        _theme.value = Theme.valueOf(savedThemeName)
        _overwriteOriginal.value = prefs.getBoolean("overwriteOriginal", false)

        val defaults = PrivacySettings()
        _privacy.value = PrivacySettings(keepColorProfile = prefs.getBoolean(KEEP_COLOR_PROFILE, defaults.keepColorProfile),
                                         keepOrientation = prefs.getBoolean(KEEP_ORIENTATION, defaults.keepOrientation),
                                         randomizeFileNames = prefs.getBoolean(RANDOMIZE_FILE_NAMES, defaults.randomizeFileNames))
    }

    fun setTheme(theme: Theme) {
        _theme.value = theme
        viewModelScope.launch {
            prefs.edit {
                putString("theme", theme.name)
            }
        }
    }

    fun setOverwriteOriginal(overwrite: Boolean) {
        _overwriteOriginal.value = overwrite
        viewModelScope.launch {
            prefs.edit {
                putBoolean("overwriteOriginal", overwrite)
            }
        }
    }

    fun setKeepColorProfile(keep: Boolean) {
        _privacy.update { it.copy(keepColorProfile = keep) }
        saveBoolean(KEEP_COLOR_PROFILE, keep)
    }

    fun setKeepOrientation(keep: Boolean) {
        _privacy.update { it.copy(keepOrientation = keep) }
        saveBoolean(KEEP_ORIENTATION, keep)
    }

    fun setRandomizeFileNames(randomize: Boolean) {
        _privacy.update { it.copy(randomizeFileNames = randomize) }
        saveBoolean(RANDOMIZE_FILE_NAMES, randomize)
    }

    private fun saveBoolean(key: String,
                            value: Boolean) {
        viewModelScope.launch {
            prefs.edit {
                putBoolean(key, value)
            }
        }
    }
}
