package com.pasoseguro.app.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pasoseguro.app.data.UserPreferences
import com.pasoseguro.app.navigation.Feature

/**
 * Encapsulates the accessible two-tap interaction:
 *
 *  • 1st tap → vibrate (if hapticEnabled) + speak feature name + confirmation prompt.
 *  • 2nd tap on the SAME feature within [windowMs] → open it.
 *  • Different feature → re-arm on the new one.
 *  • Window expired → next tap is treated as 1st tap.
 */
class DoubleTapHandler(
    private val context: Context,
    private val onSpeak: (String) -> Unit,
    private val prefs: UserPreferences,
    private val windowMs: Long = 2500L,
    private val onOpen: (Feature) -> Unit,
) {
    private var armedRoute: String? = null
    private var armedAt: Long = 0L

    fun onTap(feature: Feature) {
        val now = System.currentTimeMillis()
        val isSecondTap = feature.route == armedRoute && (now - armedAt) <= windowMs

        if (isSecondTap) {
            armedRoute = null
            onOpen(feature)
        } else {
            HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity)
            // Este handler solo se usa en modo DOUBLE_TAP (ver HomeScreen.longPressConfigFor),
            // así que el gesto a confirmar siempre es el mismo.
            onSpeak("${feature.ttsText}. ${feature.description} Toca dos veces en la pantalla para confirmar.")
            armedRoute = feature.route
            armedAt = now
        }
    }
}

@Composable
fun rememberDoubleTapHandler(
    onSpeak: (String) -> Unit,
    prefs: UserPreferences,
    onOpen: (Feature) -> Unit,
): DoubleTapHandler {
    val context = LocalContext.current
    return remember(prefs) {
        DoubleTapHandler(context = context, onSpeak = onSpeak, prefs = prefs, onOpen = onOpen)
    }
}
