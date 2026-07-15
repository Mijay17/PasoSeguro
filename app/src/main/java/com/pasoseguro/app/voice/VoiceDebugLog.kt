package com.pasoseguro.app.voice

import android.util.Log

/**
 * Logging TEMPORAL para diagnosticar el ciclo de auto-reconocimiento del TTS
 * y la caída de la escucha continua (Fase 1 de estabilización, ver
 * conversación). Filtrar logcat por el tag "VoiceDebug".
 *
 * Borrar este archivo y todas las llamadas a [VoiceDebugLog.d] una vez
 * confirmada y corregida la causa raíz — no es parte de la arquitectura
 * final.
 */
internal object VoiceDebugLog {
    private const val TAG = "VoiceDebug"

    fun d(event: String) {
        Log.d(TAG, "[t=${System.currentTimeMillis()}] $event")
    }
}
