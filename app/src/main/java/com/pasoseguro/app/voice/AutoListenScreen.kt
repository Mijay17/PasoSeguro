package com.pasoseguro.app.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pasoseguro.app.ui.LocalVoiceInteractionManager

/**
 * Punto de entrada Compose para las pantallas con escucha automática y
 * continua (Navegar/Explorar/Ruta/Contactos/Notificaciones/Configuración —
 * Home queda fuera, sigue con su doble-toque vía [VoiceInteractionManager.armOneShotListen]).
 *
 * Solicita el permiso de micrófono si falta y, una vez concedido, llama
 * [VoiceInteractionManager.enterScreen] con [screenContext]; al salir de la
 * composición llama [VoiceInteractionManager.exitScreen]. Centraliza este
 * ciclo de vida para que ninguna pantalla repita el mismo boilerplate de
 * permiso + entrar/salir.
 */
@Composable
fun rememberAutoListenVoice(screenContext: ScreenVoiceContext): VoiceInteractionManager {
    val context = LocalContext.current
    val voice = LocalVoiceInteractionManager.current

    var permissionGranted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) voice.speak("No pude acceder al micrófono. Revisa los permisos de la aplicación.")
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(screenContext, permissionGranted) {
        if (permissionGranted) voice.enterScreen(screenContext)
    }
    DisposableEffect(Unit) { onDispose { voice.exitScreen() } }

    return voice
}
