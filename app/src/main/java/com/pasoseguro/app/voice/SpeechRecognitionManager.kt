package com.pasoseguro.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun hasRecordAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Captura de audio→texto — únicamente eso. No sabe qué significa el texto
 * reconocido (responsabilidad de [CommandProcessor]) ni qué hacer con el
 * resultado (responsabilidad de [VoiceInteractionManager]). Propiedad
 * exclusiva de [VoiceInteractionManager]; ninguna pantalla ni ViewModel debe
 * instanciar esta clase directamente.
 *
 * Android no ofrece un modo de dictado continuo real: cada sesión de
 * [SpeechRecognizer] entrega un solo resultado (o un error) y termina. Para
 * aproximar "siempre escuchando" mientras el usuario permanece en una
 * pantalla, [startContinuousListening] reinicia la sesión automáticamente
 * después de cada resultado/error esperado (silencio), y aplica backoff
 * exponencial ante errores duros repetidos para no agotar la batería en un
 * loop de reintentos.
 */
internal class SpeechRecognitionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (text: String) -> Unit,
    private val onStateChange: (VoiceInteractionState) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var restartJob: Job? = null
    private var consecutiveHardErrors = 0

    /** Escucha continua: se reinicia sola tras cada resultado/silencio hasta [stopContinuousListening]. */
    fun startContinuousListening() {
        if (continuous) return
        continuous = true
        consecutiveHardErrors = 0
        armListening()
    }

    fun stopContinuousListening() {
        continuous = false
        restartJob?.cancel()
        teardown()
    }

    /** Un solo ciclo escuchar→resultado, sin reinicio automático — usado por el doble-toque de Home. */
    fun startOneShot() {
        continuous = false
        consecutiveHardErrors = 0
        armListening()
    }

    private fun teardown() {
        if (recognizer != null) VoiceDebugLog.d("recognizer: TEARDOWN (destroy previous instance)")
        recognizer?.setRecognitionListener(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun armListening() {
        if (!hasRecordAudioPermission(context)) {
            VoiceDebugLog.d("recognizer: NO PERMISSION — abortando armListening (continuous=$continuous)")
            onStateChange(VoiceInteractionState.Idle)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            VoiceDebugLog.d("recognizer: NOT AVAILABLE — abortando armListening (continuous=$continuous)")
            onStateChange(VoiceInteractionState.Idle)
            return
        }
        teardown()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(buildRecognizerIntent())
        }
        VoiceDebugLog.d("recognizer: START listening (continuous=$continuous, hardErrors=$consecutiveHardErrors)")
        onStateChange(VoiceInteractionState.Listening)
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    /** Reprograma el siguiente ciclo de escucha fuera del callback actual del reconocedor (evita reentrancia). */
    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (continuous) armListening()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            VoiceDebugLog.d("recognizer: END of speech capture (procesando…)")
            onStateChange(VoiceInteractionState.Processing)
        }

        override fun onError(error: Int) {
            when (error) {
                // Silencio / nada reconocido — esperable en modo siempre-activo, no cuenta como fallo.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    VoiceDebugLog.d("recognizer: ERROR $error (silencio/timeout, esperado) — restart en ${RESTART_DELAY_MS}ms")
                    consecutiveHardErrors = 0
                    if (continuous) scheduleRestart(RESTART_DELAY_MS) else onStateChange(VoiceInteractionState.Idle)
                }
                // Error real (audio/red/cliente/ocupado) — backoff exponencial con tope.
                else -> {
                    consecutiveHardErrors++
                    if (continuous && consecutiveHardErrors <= MAX_HARD_ERRORS) {
                        val backoff = (BASE_BACKOFF_MS shl (consecutiveHardErrors - 1)).coerceAtMost(MAX_BACKOFF_MS)
                        VoiceDebugLog.d("recognizer: ERROR DURO $error (intento $consecutiveHardErrors/$MAX_HARD_ERRORS) — restart en ${backoff}ms")
                        scheduleRestart(backoff)
                    } else {
                        VoiceDebugLog.d("recognizer: ERROR DURO $error — LÍMITE ALCANZADO, escucha continua DETENIDA")
                        continuous = false
                        onStateChange(VoiceInteractionState.Idle)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveHardErrors = 0
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            VoiceDebugLog.d("recognizer: RESULT texto=\"$text\" (continuous=$continuous)")
            if (text.isNotBlank()) onResult(text)
            if (continuous) scheduleRestart(RESTART_DELAY_MS) else onStateChange(VoiceInteractionState.Idle)
        }
    }

    companion object {
        private const val RESTART_DELAY_MS = 120L
        private const val BASE_BACKOFF_MS = 300L
        private const val MAX_BACKOFF_MS = 3_000L
        private const val MAX_HARD_ERRORS = 5
    }
}
