package com.endfield.pearmusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endfield.pearmusic.data.repository.SettingsRepository
import com.endfield.pearmusic.data.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val normalizeAudio: StateFlow<Boolean> = repository.normalizeAudio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val replayGain: StateFlow<Boolean> = repository.replayGain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setNormalizeAudio(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNormalizeAudio(enabled)
        }
    }

    fun setReplayGain(enabled: Boolean) {
        viewModelScope.launch {
            repository.setReplayGain(enabled)
        }
    }
}
