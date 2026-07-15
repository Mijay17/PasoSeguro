package com.pasoseguro.app.data

enum class InteractionMode {
    DOUBLE_TAP,  // Two consecutive taps to activate
    LONG_PRESS,  // Hold 2 seconds to activate
}

enum class TtsSpeed(val rate: Float) {
    SLOW(0.5f),
    NORMAL(1.0f),
    FAST(1.5f),
}

enum class ConfirmationPrompt {
    PRESS_AGAIN,      // "Toca dos veces en la pantalla para confirmar."
    HOLD_TWO_SECONDS, // "Mantén presionado durante dos segundos para confirmar."
}

data class UserPreferences(
    val interactionMode: InteractionMode = InteractionMode.DOUBLE_TAP,
    val ttsEnabled: Boolean = true,
    val ttsSpeed: TtsSpeed = TtsSpeed.NORMAL,
    val confirmationPrompt: ConfirmationPrompt = ConfirmationPrompt.PRESS_AGAIN,
    val hapticEnabled: Boolean = true,
    val highContrast: Boolean = false,
    val largeFont: Boolean = false,
) {
    companion object {
        val Default = UserPreferences()
    }
}
