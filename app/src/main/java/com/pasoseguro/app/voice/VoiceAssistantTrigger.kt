package com.pasoseguro.app.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.pasoseguro.app.utils.ConfirmActionState
import com.pasoseguro.app.utils.rememberConfirmAction

/**
 * Punto de entrada reutilizable al Asistente IA por voz para cualquier
 * pantalla con un botón dedicado (logo de Home, ícono de micrófono en un
 * TopAppBar, etc.). Centraliza en un solo lugar lo que, de no existir esto,
 * se repetiría en cada pantalla: el mismo patrón de doble pulsación del
 * resto de PasoSeguro (1er toque anuncia el asistente, 2do toque dice "Te
 * escucho." y arranca el micrófono), el permiso de RECORD_AUDIO, y la
 * resolución del comando reconocido vía [GlobalVoiceNavigationHandler].
 *
 * [speakThenRun] delega la síntesis de voz al motor TTS propio de la
 * pantalla que lo usa — este helper solo decide cuándo pedir que se hable y
 * cuándo, una vez terminado el audio, ejecutar la navegación resultante.
 */
@Composable
fun rememberVoiceAssistantTrigger(
    navController: NavController,
    onSpeak: (String) -> Unit,
    onHaptic: () -> Unit,
    speakThenRun: (text: String, onSpoken: () -> Unit) -> Unit,
): ConfirmActionState {
    val context = LocalContext.current

    val globalVoice = remember(navController, speakThenRun) {
        GlobalVoiceNavigationHandler(navController, speakThenRun)
    }
    val voiceManager = rememberVoiceCommandManager(onCommand = globalVoice::handle)

    var micPermissionGranted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var listenAfterPermission by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermissionGranted = granted
        if (granted && listenAfterPermission) {
            speakThenRun("Te escucho.") { voiceManager.startListening() }
        } else if (!granted) {
            onSpeak("No pude acceder al micrófono. Revisa los permisos de la aplicación.")
        }
        listenAfterPermission = false
    }

    fun beginListening() {
        if (!micPermissionGranted) {
            listenAfterPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        speakThenRun("Te escucho.") { voiceManager.startListening() }
    }

    return rememberConfirmAction(
        pendingMessage = "Asistente IA. Presiona nuevamente para comenzar a hablar.",
        onSpeak        = onSpeak,
        onHaptic       = onHaptic,
        onConfirm      = ::beginListening,
    )
}
