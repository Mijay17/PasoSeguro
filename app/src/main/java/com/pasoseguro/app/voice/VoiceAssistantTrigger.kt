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
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.utils.ConfirmActionState
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.rememberConfirmAction

/**
 * Punto de entrada al Asistente IA para Home — el único lugar de la app que
 * conserva el patrón de doble toque (1er toque anuncia el asistente, 2do
 * toque dice "Te escucho." y escucha una vez), a diferencia de la escucha
 * continua y automática de las otras 6 pantallas principales. Ya no crea su
 * propio `SpeechRecognizer`/`TextToSpeech` — delega enteramente en el
 * [VoiceInteractionManager] centralizado vía [VoiceInteractionManager.armOneShotListen],
 * la misma instancia que usa el resto de la app.
 */
@Composable
fun rememberVoiceAssistantTrigger(): ConfirmActionState {
    val context = LocalContext.current
    val prefs = LocalUserPreferences.current
    val voice = LocalVoiceInteractionManager.current

    var micPermissionGranted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    var listenAfterPermission by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermissionGranted = granted
        if (granted && listenAfterPermission) {
            voice.armOneShotListen()
        } else if (!granted) {
            voice.speak("No pude acceder al micrófono. Revisa los permisos de la aplicación.")
        }
        listenAfterPermission = false
    }

    fun beginListening() {
        if (!micPermissionGranted) {
            listenAfterPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voice.armOneShotListen()
    }

    return rememberConfirmAction(
        pendingMessage = "Asistente IA. Presiona nuevamente para comenzar a hablar.",
        onSpeak        = voice::speak,
        onHaptic       = { HapticHelper.vibrate(context, prefs.hapticEnabled) },
        onConfirm      = ::beginListening,
    )
}
