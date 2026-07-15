package com.pasoseguro.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pasoseguro.app.data.VibrationIntensity

object HapticHelper {

    /**
     * Single pulse (90 ms). Respects [enabled] flag; la fuerza del pulso viene
     * de [intensity] (amplitud 90/170/255) — la duración y la cantidad de
     * pulsos no cambian, solo qué tan fuerte se siente.
     */
    fun vibrate(context: Context, enabled: Boolean = true, intensity: VibrationIntensity = VibrationIntensity.MEDIA) {
        if (!enabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(90, intensity.amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(90)
        }
    }
}
