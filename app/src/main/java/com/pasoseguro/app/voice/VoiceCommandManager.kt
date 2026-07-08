package com.pasoseguro.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun hasRecordAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Envuelve el reconocimiento de voz nativo de Android (`SpeechRecognizer`) y
 * entrega únicamente el [VoiceCommand] ya interpretado por
 * [VoiceCommandParser]. Las pantallas nunca tocan `SpeechRecognizer`
 * directamente — solo reciben la intención resultante a través de
 * [onCommand], igual que [VoiceCommandParser] es el único lugar que conoce
 * las palabras clave.
 *
 * TODO(Fase futura): si se reemplaza el reconocimiento por lotes por streaming
 * o el parser por un modelo de lenguaje, esta clase es el único punto que
 * debería cambiar — su contrato con las pantallas ([onCommand]) se mantiene.
 */
class VoiceCommandManager(
    private val context: Context,
    private val onCommand: (VoiceCommand) -> Unit,
    private val onStateChange: (VoiceRecognitionState) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!hasRecordAudioPermission(context)) {
            onStateChange(VoiceRecognitionState.Error("Falta el permiso de micrófono."))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStateChange(VoiceRecognitionState.Error("Reconocimiento de voz no disponible en este dispositivo."))
            onCommand(VoiceCommand.Unknown)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(buildRecognizerIntent())
        }
        onStateChange(VoiceRecognitionState.Listening)
    }

    fun cancel() {
        recognizer?.cancel()
        onStateChange(VoiceRecognitionState.Idle)
    }

    /**
     * Cierra la ventana de escucha ya iniciada, conservando lo que el motor
     * alcanzó a reconocer hasta ahora (dispara [onCommand] con ese
     * resultado, a diferencia de [cancel] que descarta todo). Se usa para
     * las ventanas de escucha contextual de duración fija (p. ej. los 3 s
     * tras cada indicación en Navegar/Explorar).
     */
    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            onStateChange(VoiceRecognitionState.Processing)
        }

        override fun onError(error: Int) {
            onStateChange(VoiceRecognitionState.Idle)
            onCommand(VoiceCommand.Unknown)
        }

        override fun onResults(results: Bundle?) {
            val recognizedText = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            onStateChange(VoiceRecognitionState.Idle)
            onCommand(VoiceCommandParser.parse(recognizedText))
        }
    }
}

@Composable
fun rememberVoiceCommandManager(
    onCommand: (VoiceCommand) -> Unit,
    onStateChange: (VoiceRecognitionState) -> Unit = {},
): VoiceCommandManager {
    val context = LocalContext.current
    val manager = remember { VoiceCommandManager(context, onCommand, onStateChange) }
    DisposableEffect(Unit) { onDispose { manager.destroy() } }
    return manager
}
