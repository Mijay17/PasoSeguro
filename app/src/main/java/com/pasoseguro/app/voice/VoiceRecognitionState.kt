package com.pasoseguro.app.voice

/** Estado interno del ciclo de escucha — hoy solo lo consume [VoiceCommandManager]. */
sealed interface VoiceRecognitionState {
    data object Idle : VoiceRecognitionState
    data object Listening : VoiceRecognitionState
    data object Processing : VoiceRecognitionState
    data class Error(val message: String) : VoiceRecognitionState
}
