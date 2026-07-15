package com.pasoseguro.app.voice

/**
 * Un comando de voz propio de una pantalla — el punto de extensión por el
 * cual cada pantalla "registra automáticamente qué comandos acepta" sin que
 * [CommandProcessor] ni [NavigationVoiceController] conozcan nada específico
 * de esa pantalla.
 *
 * [confirmationSpeech] es opcional: cuando el propio [onRecognized] ya habla
 * su confirmación (p. ej. `stopMonitoringForConfirm()`, que pregunta "¿Desea
 * volver al inicio?"), se deja en null para no duplicar el habla.
 */
data class ScreenVoiceCommand(
    val keywords: List<String>,
    val onRecognized: () -> Unit,
    val confirmationSpeech: String? = null,
)

/**
 * El conjunto de comandos que acepta la pantalla actualmente activa, más un
 * [helpHint] opcional que se suma a la respuesta del comando global "Ayuda".
 * Cada pantalla construye la suya y la registra con
 * [VoiceInteractionManager.enterScreen] al entrar.
 */
data class ScreenVoiceContext(
    val screenName: String,
    val commands: List<ScreenVoiceCommand> = emptyList(),
    val helpHint: String? = null,
)
