package com.pasoseguro.app.voice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ventana de escucha por voz sin botón — pensada para pantallas como
 * Navegar y Explorar, que no tienen UI tradicional donde colocar un ícono de
 * micrófono. Cada vez que el Asistente IA termina de hablar (bienvenida,
 * una indicación), la pantalla llama a [listenBriefly] y durante
 * [windowMs] el usuario puede decir "Volver" o "Inicio" sin tocar nada.
 * Se resuelve con el mismo [GlobalVoiceNavigationHandler] que usan las
 * pantallas con botón — mismo comportamiento, sin duplicar lógica.
 */
class ContextualVoiceListener internal constructor(
    private val context: Context,
    private val voiceManager: VoiceCommandManager,
    private val scope: CoroutineScope,
    private val windowMs: Long,
) {
    private var windowJob: Job? = null

    /** No hace nada si falta el permiso de micrófono — no interrumpe con un diálogo de permiso sin que el usuario lo haya pedido. */
    fun listenBriefly() {
        if (!hasRecordAudioPermission(context)) return
        windowJob?.cancel()
        voiceManager.startListening()
        windowJob = scope.launch {
            delay(windowMs)
            voiceManager.stopListening()
        }
    }
}

@Composable
fun rememberContextualVoiceListener(
    navController: NavController,
    speakThenRun: (text: String, onSpoken: () -> Unit) -> Unit,
    windowMs: Long = 3_000L,
): ContextualVoiceListener {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val globalVoice = remember(navController, speakThenRun) {
        GlobalVoiceNavigationHandler(navController, speakThenRun)
    }
    val voiceManager = rememberVoiceCommandManager(onCommand = globalVoice::handle)
    return remember(voiceManager) {
        ContextualVoiceListener(context, voiceManager, scope, windowMs)
    }
}
