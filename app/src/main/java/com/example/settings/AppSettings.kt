package com.example.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "cool_voice_settings")

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sampleRate: Int = 44100,
    val antiFeedbackEnabled: Boolean = true,
    val defaultVolume: Float = 1.0f
)

class AppSettings(private val context: Context) {

    companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SAMPLE_RATE = intPreferencesKey("sample_rate")
        val KEY_ANTI_FEEDBACK = booleanPreferencesKey("anti_feedback")
        val KEY_DEFAULT_VOLUME = floatPreferencesKey("default_volume")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val themeString = preferences[KEY_THEME] ?: ThemeMode.SYSTEM.name
        val theme = try {
            ThemeMode.valueOf(themeString)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
        val rate = preferences[KEY_SAMPLE_RATE] ?: 44100
        val antiFeedback = preferences[KEY_ANTI_FEEDBACK] ?: true
        val vol = preferences[KEY_DEFAULT_VOLUME] ?: 1.0f

        UserSettings(
            themeMode = theme,
            sampleRate = rate,
            antiFeedbackEnabled = antiFeedback,
            defaultVolume = vol
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = mode.name
        }
    }

    suspend fun setSampleRate(rate: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAMPLE_RATE] = rate
        }
    }

    suspend fun setAntiFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ANTI_FEEDBACK] = enabled
        }
    }

    suspend fun setDefaultVolume(vol: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_VOLUME] = vol
        }
    }
}
