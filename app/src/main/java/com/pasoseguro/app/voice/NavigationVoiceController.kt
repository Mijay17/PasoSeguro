package com.pasoseguro.app.voice

import androidx.navigation.NavController
import com.pasoseguro.app.navigation.Screen

/**
 * Ejecuta una [Resolution] ya interpretada por [CommandProcessor]. Reemplaza
 * a [GlobalVoiceNavigationHandler]: a diferencia de aquel (que siempre
 * esperaba a que la frase de confirmación terminara de hablarse para recién
 * actuar, vía `speakThenRun`), aquí la acción ocurre de inmediato — la
 * confirmación se habla de forma fire-and-forget y la navegación/ejecución
 * corre justo después, para que interrumpir un TTS largo con "Atrás" se
 * sienta instantáneo (el requisito central del barge-in).
 *
 * Propiedad exclusiva de [VoiceInteractionManager].
 */
internal class NavigationVoiceController(
    private val navController: NavController,
    private val tts: TextToSpeechManager,
) {
    fun execute(resolution: Resolution, screen: ScreenVoiceContext?) {
        when (resolution) {
            is Resolution.Global     -> executeGlobal(resolution.command, screen)
            is Resolution.Screen     -> executeScreen(resolution.entry)
            // Ni "no entendí" ni ningún otro aviso de error: el usuario solo
            // recibe voz cuando ejecuta un comando válido o cambia de
            // pantalla — un timeout/silencio/frase no reconocida no debe
            // sonar como un fallo de la app. La escucha sigue igual.
            is Resolution.Unresolved -> Unit
            Resolution.NoInput       -> Unit
        }
    }

    private fun executeGlobal(command: VoiceCommand, screen: ScreenVoiceContext?) {
        when (command) {
            is VoiceCommand.OpenFeature -> {
                // No se habla ninguna confirmación aquí: la pantalla destino
                // ya tiene su propio mensaje de bienvenida (ver *Screen.kt/
                // *ViewModel.kt), y hablar los dos en secuencia sonaba como
                // el mismo saludo repetido dos veces ("Aquí puedes... Aquí
                // puedes...") — se eliminó la redundancia dejando un solo
                // mensaje, el de la pantalla.
                VoiceDebugLog.d("nav: NAVEGANDO a ruta=\"${command.feature.route}\" (backStack actual=\"${navController.currentDestination?.route}\")")
                navController.navigate(command.feature.route)
            }
            VoiceCommand.GoBack -> {
                tts.speak(command.confirmationSpeech)
                VoiceDebugLog.d("nav: popBackStack() (backStack actual=\"${navController.currentDestination?.route}\")")
                navController.popBackStack()
            }
            VoiceCommand.GoHome -> {
                tts.speak(command.confirmationSpeech)
                VoiceDebugLog.d("nav: popBackStack(Home) (backStack actual=\"${navController.currentDestination?.route}\")")
                navController.popBackStack(Screen.Home.route, false)
            }
            VoiceCommand.Cancel,
            VoiceCommand.Accept -> tts.speak(command.confirmationSpeech)
            VoiceCommand.Repeat -> {
                // Re-habla el último texto dicho por la app, sin prefijo, para no
                // arriesgar un eco "Repitiendo: Repitiendo: …" en usos sucesivos.
                tts.speak(tts.lastSpokenUtterance ?: command.confirmationSpeech)
            }
            VoiceCommand.Help -> {
                val hint = screen?.helpHint
                tts.speak(if (hint != null) "${command.confirmationSpeech} $hint" else command.confirmationSpeech)
            }
            // Inalcanzable en la práctica (CommandProcessor nunca produce
            // Resolution.Global(VoiceCommand.Unknown), solo Resolution.Unresolved,
            // ya silencioso arriba) — se mantiene silencioso por la misma política.
            VoiceCommand.Unknown -> Unit
        }
    }

    private fun executeScreen(entry: ScreenVoiceCommand) {
        VoiceDebugLog.d("nav: COMANDO DE PANTALLA ejecutado keywords=${entry.keywords}")
        entry.confirmationSpeech?.let(tts::speak)
        entry.onRecognized()
    }
}
