package com.pasoseguro.app.voice

/**
 * Estado único y compartido de todo el sistema de voz, expuesto por
 * [VoiceInteractionManager]. Reemplaza a [VoiceRecognitionState] (que solo
 * cubría el ciclo del reconocedor) para incluir también el habla del TTS y
 * la interrupción por barge-in.
 */
sealed interface VoiceInteractionState {
    data object Idle : VoiceInteractionState
    data object Listening : VoiceInteractionState
    data object Processing : VoiceInteractionState
    data object Speaking : VoiceInteractionState
    data class Interrupted(val previous: VoiceInteractionState) : VoiceInteractionState
}
