package com.pasoseguro.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {

    /** Single pulse (90 ms, amplitud máxima) — intensificado para que se sienta con claridad. Respects [enabled] flag. */
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
            vibrator.vibrate(VibrationEffect.createOneShot(90, 255))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(90)
        }
    }
}
