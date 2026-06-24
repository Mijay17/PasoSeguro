package com.pasoseguro.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.pasoseguro.app.data.TtsSpeed
import java.util.Locale

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

    var enabled: Boolean = true
    var speechRate: Float = TtsSpeed.NORMAL.rate

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!ready || !enabled) return
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun setSpeed(speed: TtsSpeed) {
        speechRate = speed.rate
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
