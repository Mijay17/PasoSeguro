package com.pasoseguro.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class PreferencesRepository private constructor(context: Context) {

    private val store = context.applicationContext.dataStore

    companion object {
        private val KEY_INTERACTION_MODE   = stringPreferencesKey("interaction_mode")
        private val KEY_TTS_ENABLED        = booleanPreferencesKey("tts_enabled")
        private val KEY_TTS_SPEED          = stringPreferencesKey("tts_speed")
        private val KEY_HAPTIC_ENABLED     = booleanPreferencesKey("haptic_enabled")
        private val KEY_VIBRATION_INTENSITY = stringPreferencesKey("vibration_intensity")
        private val KEY_HIGH_CONTRAST      = booleanPreferencesKey("high_contrast")
        private val KEY_LARGE_FONT         = booleanPreferencesKey("large_font")

        @Volatile private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context).also { INSTANCE = it }
            }
    }

    val userPreferences: Flow<UserPreferences> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            UserPreferences(
                interactionMode = prefs[KEY_INTERACTION_MODE]
                    ?.let { runCatching { InteractionMode.valueOf(it) }.getOrNull() }
                    ?: InteractionMode.DOUBLE_TAP,
                ttsEnabled = prefs[KEY_TTS_ENABLED] ?: true,
                ttsSpeed = prefs[KEY_TTS_SPEED]
                    ?.let { runCatching { TtsSpeed.valueOf(it) }.getOrNull() }
                    ?: TtsSpeed.NORMAL,
                hapticEnabled = prefs[KEY_HAPTIC_ENABLED] ?: true,
                vibrationIntensity = prefs[KEY_VIBRATION_INTENSITY]
                    ?.let { runCatching { VibrationIntensity.valueOf(it) }.getOrNull() }
                    ?: VibrationIntensity.MEDIA,
                highContrast  = prefs[KEY_HIGH_CONTRAST]  ?: false,
                largeFont     = prefs[KEY_LARGE_FONT]     ?: false,
            )
        }

    suspend fun save(prefs: UserPreferences) {
        store.edit { p ->
            p[KEY_INTERACTION_MODE]    = prefs.interactionMode.name
            p[KEY_TTS_ENABLED]         = prefs.ttsEnabled
            p[KEY_TTS_SPEED]           = prefs.ttsSpeed.name
            p[KEY_HAPTIC_ENABLED]      = prefs.hapticEnabled
            p[KEY_VIBRATION_INTENSITY] = prefs.vibrationIntensity.name
            p[KEY_HIGH_CONTRAST]       = prefs.highContrast
            p[KEY_LARGE_FONT]          = prefs.largeFont
        }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }
}
