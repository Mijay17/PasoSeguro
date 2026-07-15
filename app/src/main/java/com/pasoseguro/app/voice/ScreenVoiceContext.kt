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
 *
 * [onActive]/[onInactive] son para pantallas con trabajo de fondo propio
 * (Navegar/Ruta/Explorar): se disparan cuando esta pantalla pasa a ser (o
 * deja de ser) la activa — comparando por [screenName], no por identidad del
 * objeto, así que una recomposición que reconstruye este `ScreenVoiceContext`
 * sin cambiar de pantalla no los vuelve a disparar. Ver
 * [VoiceInteractionManager.enterScreen]/[VoiceInteractionManager.exitScreen].
 */
data class ScreenVoiceContext(
    val screenName: String,
    val commands: List<ScreenVoiceCommand> = emptyList(),
    val helpHint: String? = null,
    val onActive: () -> Unit = {},
    val onInactive: () -> Unit = {},
)
