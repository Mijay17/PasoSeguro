package com.pasoseguro.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.pasoseguro.app.voice.VoiceInteractionManager

/**
 * Provisto una vez en MainActivity (mismo patrón que [LocalUserPreferences]),
 * consumido por cualquier pantalla vía `LocalVoiceInteractionManager.current`
 * en vez de crear/pasar el gestor de voz explícitamente.
 */
val LocalVoiceInteractionManager = staticCompositionLocalOf<VoiceInteractionManager> {
    error("LocalVoiceInteractionManager no fue provisto — ¿falta el CompositionLocalProvider en MainActivity?")
}
