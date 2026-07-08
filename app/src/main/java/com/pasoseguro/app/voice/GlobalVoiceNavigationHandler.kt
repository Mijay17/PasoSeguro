package com.pasoseguro.app.voice

import androidx.navigation.NavController
import com.pasoseguro.app.navigation.Screen

/**
 * Único lugar que decide qué hacer con un [VoiceCommand] ya interpretado —
 * usado por igual desde HomeScreen y desde cualquier otra pantalla principal
 * (Navegar, Explorar, Ruta, Contactos, Notificaciones, Configuración), para
 * que "Volver", "Ir al inicio", "Cancelar" y "Abrir X" se comporten
 * exactamente igual sin importar dónde se invoquen.
 *
 * [speakThenRun] recibe la frase de confirmación y una acción; quien lo usa
 * decide CÓMO se reproduce el audio (cada pantalla ya tiene su propio motor
 * TTS) pero es este handler el que garantiza que la acción (navegar) ocurre
 * recién cuando el TTS terminó de hablar — nunca antes, para que un mensaje
 * nunca corte al anterior ni se corte por la navegación.
 */
class GlobalVoiceNavigationHandler(
    private val navController: NavController,
    private val speakThenRun: (text: String, onSpoken: () -> Unit) -> Unit,
) {
    fun handle(command: VoiceCommand) {
        val action: () -> Unit = when (command) {
            is VoiceCommand.OpenFeature -> ({ navController.navigate(command.feature.route) })
            VoiceCommand.GoBack         -> ({ navController.popBackStack() })
            VoiceCommand.GoHome         -> ({ navController.popBackStack(Screen.Home.route, false) })
            VoiceCommand.Cancel,
            VoiceCommand.Accept,
            VoiceCommand.Unknown        -> ({ /* solo la confirmación hablada, sin navegación */ })
        }
        speakThenRun(command.confirmationSpeech, action)
    }
}
