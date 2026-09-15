package com.endfield.pearmusic.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsRepository(private val context: Context) {
    private val THEME_MODE = stringPreferencesKey("theme_mode")
    private val NORMALIZE_AUDIO = booleanPreferencesKey("normalize_audio")
    private val REPLAY_GAIN = booleanPreferencesKey("replay_gain")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val normalizeAudio: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NORMALIZE_AUDIO] ?: true
    }

    val replayGain: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REPLAY_GAIN] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    suspend fun setNormalizeAudio(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NORMALIZE_AUDIO] = enabled
        }
    }

    suspend fun setReplayGain(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REPLAY_GAIN] = enabled
        }
    }
}
