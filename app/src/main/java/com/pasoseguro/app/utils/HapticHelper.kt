package com.pasoseguro.app.utils

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pasoseguro.app.data.TtsSpeed
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object HapticHelper {

    /** Single short pulse (48 ms). Respects [enabled] flag. */
    fun vibrate(context: Context, enabled: Boolean = true) {
        if (!enabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(48, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(48)
        }
    }
}

/**
 * Wrapper around Android TextToSpeech.
 *
 * - [enabled]: when false, [speak] is a no-op.
 * - [speechRate]: multiplier (0.5 = slow, 1.0 = normal, 1.5 = fast).
 *
 * Call [shutdown] in onDispose / onDestroy.
 */
class TtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val nextUtteranceId = AtomicInteger(0)
    private val pendingCallbacks = mutableMapOf<String, () -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    var enabled: Boolean = true
    var speechRate: Float = TtsSpeed.NORMAL.rate

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
                ready = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = runPendingCallback(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = runPendingCallback(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = runPendingCallback(utteranceId)
        })
    }

    private fun runPendingCallback(utteranceId: String?) {
        val callback = utteranceId?.let { pendingCallbacks.remove(it) } ?: return
        mainHandler.post(callback)
    }

    fun speak(text: String) = speak(text, onDone = null)

    /**
     * [onDone] se dispara cuando el motor termina de reproducir [text] — se
     * usa, por ejemplo, para arrancar el micrófono justo después de que el
     * Asistente IA diga "Te escucho." y no capte su propia voz.
     *
     * Sobrecarga aparte (en vez de un parámetro con valor por defecto) para
     * que las referencias `tts::speak` existentes, tipadas como
     * `(String) -> Unit`, sigan resolviendo sin ambigüedad.
     */
    fun speak(text: String, onDone: (() -> Unit)?) {
        if (!ready || !enabled) {
            onDone?.invoke()
            return
        }
        tts?.setSpeechRate(speechRate)
        val utteranceId = "tts-${nextUtteranceId.getAndIncrement()}"
        if (onDone != null) pendingCallbacks[utteranceId] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun setSpeed(speed: TtsSpeed) {
        speechRate = speed.rate
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pendingCallbacks.clear()
    }
}
