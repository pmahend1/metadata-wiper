package com.prateekmahendrakar.metadatawiper.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prateekmahendrakar.metadatawiper.model.Theme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(Theme.System)
    val theme: StateFlow<Theme> = _theme

    private val _overwriteOriginal = MutableStateFlow(false)
    val overwriteOriginal: StateFlow<Boolean> = _overwriteOriginal

    init {
        val savedThemeName = prefs.getString("theme", Theme.System.name) ?: Theme.System.name
        _theme.value = Theme.valueOf(savedThemeName)
        _overwriteOriginal.value = prefs.getBoolean("overwriteOriginal", false)
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
}
