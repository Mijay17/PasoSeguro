package com.pasoseguro.app.voice

import android.content.Context
import androidx.navigation.NavController
import com.pasoseguro.app.data.TtsSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Único punto de contacto de toda la app con la interacción por voz.
 * Construye y posee en privado a [SpeechRecognitionManager],
 * [TextToSpeechManager], [CommandProcessor] y [NavigationVoiceController] —
 * ninguna pantalla ni ViewModel debe instanciarlos ni referenciarlos
 * directamente, solo llamar a los métodos públicos de esta clase (vía
 * `LocalVoiceInteractionManager.current` desde Compose, o
 * `VoiceInteractionManager.getInstance(application)` desde un ViewModel —
 * mismo singleton en ambos casos).
 *
 * Aquí vive la única regla del barge-in: ante cada resultado reconocido, si
 * el TTS está hablando se lo interrumpe (`tts.stop()`) ANTES de resolver y
 * ejecutar el comando — así "Atrás"/"Inicio"/"Cancelar"/"Repetir" se sienten
 * instantáneos incluso a mitad de una locución larga.
 */
class VoiceInteractionManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tts = TextToSpeechManager(appContext)
    private val recognition = SpeechRecognitionManager(
        context       = appContext,
        scope         = scope,
        onResult      = ::onRecognized,
        onStateChange = { _recognitionState.value = it },
    )
    private var navigation: NavigationVoiceController? = null
    private var activeScreen: ScreenVoiceContext? = null

    private val _recognitionState = MutableStateFlow<VoiceInteractionState>(VoiceInteractionState.Idle)
    private val _state = MutableStateFlow<VoiceInteractionState>(VoiceInteractionState.Idle)
    val state: StateFlow<VoiceInteractionState> = _state.asStateFlow()

    init {
        // El estado visible da prioridad a "Speaking": el reconocedor puede
        // seguir "Listening" por debajo mientras el TTS habla (así es como
        // funciona el barge-in), pero eso no debe confundir a la UI.
        scope.launch {
            combine(tts.isSpeaking, _recognitionState) { speaking, recState ->
                if (speaking) VoiceInteractionState.Speaking else recState
            }.collect { _state.value = it }
        }
    }

    /** Enlaza el NavController activo — se llama una sola vez al arrancar la Activity. */
    fun bind(navController: NavController) {
        navigation = NavigationVoiceController(navController, tts)
    }

    /** Sincroniza las preferencias de usuario (TTS on/off, velocidad) con el único motor de la app. */
    fun updateTtsSettings(enabled: Boolean, speed: TtsSpeed) {
        tts.enabled = enabled
        tts.setSpeed(speed)
    }

    /** Registra los comandos de la pantalla activa y arranca (o mantiene) la escucha continua. */
    fun enterScreen(context: ScreenVoiceContext) {
        VoiceDebugLog.d("manager: ENTER SCREEN \"${context.screenName}\" (screen anterior=\"${activeScreen?.screenName}\")")
        activeScreen = context
        recognition.startContinuousListening()
    }

    /** Detiene la escucha continua — se llama al salir de una de las pantallas con voz automática. */
    fun exitScreen() {
        VoiceDebugLog.d("manager: EXIT SCREEN \"${activeScreen?.screenName}\"")
        activeScreen = null
        recognition.stopContinuousListening()
    }

    /** Flujo de un solo disparo para Home: habla "Te escucho." y recién entonces escucha una vez. */
    fun armOneShotListen(onArmed: () -> Unit = {}) {
        scope.launch {
            tts.speakAndAwait("Te escucho.")
            onArmed()
            recognition.startOneShot()
        }
    }

    fun speak(text: String, flush: Boolean = true) = tts.speak(text, flush)

    suspend fun speakAndAwait(text: String, fallbackMs: Long = 3500L, flush: Boolean = true) =
        tts.speakAndAwait(text, fallbackMs, flush)

    fun stopSpeaking() = tts.stop()

    /**
     * Atajo manual equivalente a decir "Ayuda": interrumpe el TTS si estaba
     * hablando y ejecuta el mismo camino que seguiría ese comando reconocido
     * por voz (incluye el `helpHint` de la pantalla activa). Usado por
     * [com.pasoseguro.app.components.AssistantMicButton] como toque de
     * respaldo descubrible, ya que el micrófono ya escucha automáticamente.
     */
    fun requestHelp() {
        tts.stop()
        navigation?.execute(Resolution.Global(VoiceCommand.Help), activeScreen)
    }

    private fun onRecognized(rawText: String) {
        val wasSpeaking = tts.isSpeaking.value
        VoiceDebugLog.d(
            "manager: onRecognized texto=\"$rawText\" wasSpeaking=$wasSpeaking " +
                "estadoActual=${_state.value} pantallaActiva=\"${activeScreen?.screenName}\""
        )
        val lastSpoken = tts.lastSpokenUtterance
        if (wasSpeaking) {
            _state.value = VoiceInteractionState.Interrupted(_state.value)
            tts.stop()
        }
        val resolution = CommandProcessor.interpret(rawText, activeScreen, strict = wasSpeaking, lastSpoken = lastSpoken)
        VoiceDebugLog.d("manager: resolution=$resolution (strict=$wasSpeaking, lastSpoken=\"$lastSpoken\")")
        navigation?.execute(resolution, activeScreen)
    }

    companion object {
        @Volatile private var INSTANCE: VoiceInteractionManager? = null

        fun getInstance(context: Context): VoiceInteractionManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceInteractionManager(context).also { INSTANCE = it }
            }
    }
}
