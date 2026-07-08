package com.pasoseguro.app.screens

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pasoseguro.app.data.TtsSpeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.random.Random

// ── Domain models ──────────────────────────────────────────────────────────

internal enum class ExplorePhase { STARTING, ANALYZING, COMPLETE, READY }

internal enum class ExploreZone { LEFT, FRONT, RIGHT }

internal data class ExploreZoneDetail(
    val zone: ExploreZone,
    val text: String,
    val speakText: String,
)

// ── Simulated environment data ─────────────────────────────────────────────
// TODO: Replace with real ImageAnalysis + object-detection model output

private const val WELCOME_TEXT =
    "Bienvenido al modo Explorar. Gira lentamente para analizar el espacio que te rodea."

// Full environment description spoken and displayed after analysis completes
internal const val ENVIRONMENT_DISPLAY =
    "A tu izquierda hay dos sillas libres. " +
    "Al frente hay un escritorio con una persona sentada. " +
    "A tu derecha se encuentra una puerta de salida."

private const val ENVIRONMENT_SPEAK = ENVIRONMENT_DISPLAY

private const val FOLLOWUP_QUESTION =
    "¿Qué deseas hacer ahora? Puedo ayudarte a encontrar un asiento libre, " +
    "localizar una puerta o describir una zona específica."

// Per-zone detail descriptions (randomized on each query)
// TODO: Replace with directional depth estimation per camera column
internal val ZONE_DETAILS = mapOf(
    ExploreZone.LEFT to listOf(
        ExploreZoneDetail(
            zone      = ExploreZone.LEFT,
            text      = "La silla libre está a 1.5 m y sin obstáculos en el camino.",
            speakText = "La silla libre está a un metro y medio y no hay obstáculos en el camino.",
        ),
        ExploreZoneDetail(
            zone      = ExploreZone.LEFT,
            text      = "Estantería a 1 m. Paso estrecho, transitar con cuidado.",
            speakText = "Estantería en zona izquierda a un metro. Paso estrecho, transitar con cuidado.",
        ),
    ),
    ExploreZone.FRONT to listOf(
        ExploreZoneDetail(
            zone      = ExploreZone.FRONT,
            text      = "Escritorio a 2 m. Persona sentada, sin obstáculos directos.",
            speakText = "El escritorio está a dos metros al frente. Una persona sentada, sin obstáculos directos.",
        ),
        ExploreZoneDetail(
            zone      = ExploreZone.FRONT,
            text      = "Pasillo libre. 4 m hasta la siguiente pared.",
            speakText = "Pasillo libre al frente. Cuatro metros hasta la siguiente pared o mueble.",
        ),
    ),
    ExploreZone.RIGHT to listOf(
        ExploreZoneDetail(
            zone      = ExploreZone.RIGHT,
            text      = "Puerta de salida a 2.5 m. Camino despejado.",
            speakText = "La puerta de salida está a dos metros y medio a la derecha. Camino despejado.",
        ),
        ExploreZoneDetail(
            zone      = ExploreZone.RIGHT,
            text      = "Ventana grande a la derecha. Sin obstáculos inmediatos.",
            speakText = "Ventana grande a la derecha. No se detectan obstáculos inmediatos.",
        ),
    ),
)

// ── UI state ───────────────────────────────────────────────────────────────

internal data class ExploreUiState(
    val phase: ExplorePhase         = ExplorePhase.STARTING,
    val activeZone: ExploreZone?    = null,
    val zoneDetail: ExploreZoneDetail? = null,
)

// ── TTS helper (UtteranceProgressListener + Channel, same as NavigateScreen) ──

private class ExploreTtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    var enabled: Boolean  = true
    var speechRate: Float = TtsSpeed.NORMAL.rate

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)

    // Serializa speakAndAwait: la exploración automática y las respuestas del
    // Asistente IA por voz ahora pueden llamarlo al mismo tiempo — sin este
    // mutex, una cortaría a la otra (ambas usan QUEUE_FLUSH).
    private val speechMutex = Mutex()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?)  { doneChannel.trySend(Unit) }
                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) { doneChannel.trySend(Unit) }
                    override fun onError(utteranceId: String?, errorCode: Int) { doneChannel.trySend(Unit) }
                })
                ready = true
            }
        }
    }

    suspend fun speakAndAwait(text: String, fallbackMs: Long = 4000L) = speechMutex.withLock {
        if (!enabled || !ready) { delay(fallbackMs); return@withLock }
        doneChannel.tryReceive()
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        withTimeoutOrNull(30_000L) { doneChannel.receive() }
        Unit
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_PROMPT)
    }

    fun setSpeed(speed: TtsSpeed) { speechRate = speed.rate }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }

    companion object {
        private const val UTTERANCE_ID     = "explore-main"
        private const val UTTERANCE_PROMPT = "explore-prompt"
    }
}

// ── ViewModel ──────────────────────────────────────────────────────────────

internal class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val ttsHelper           = ExploreTtsHelper(appContext)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Emite cada vez que el Asistente IA termina de hablar una indicación —
    // ExploreScreen lo usa para abrir una breve ventana de escucha por voz
    // (sin botón) sin acoplar el reconocimiento a esta ViewModel.
    private val _speechFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val speechFinished: SharedFlow<Unit> = _speechFinished.asSharedFlow()

    private var activeJob: Job? = null

    init {
        startExploration()
    }

    // ── Exploration flow ───────────────────────────────────────────────────
    //
    // Sequence:
    //   STARTING  → speak welcome message
    //   ANALYZING → visual progress for 5–8 s (simulates camera sweep)
    //   COMPLETE  → speak environment description
    //   READY     → speak follow-up question, zone buttons become active
    //
    // TODO: In ANALYZING, bind CameraX ImageAnalysis + run ML model here.
    //       Replace the fixed delay with real inference over captured frames.
    //       Feed model output into ENVIRONMENT_SPEAK/DISPLAY constants.

    private fun startExploration() {
        activeJob?.cancel()
        _uiState.update { ExploreUiState(phase = ExplorePhase.STARTING) }
        activeJob = viewModelScope.launch {
            delay(300L)
            ttsHelper.speakAndAwait(WELCOME_TEXT)
            _speechFinished.tryEmit(Unit)

            _uiState.update { it.copy(phase = ExplorePhase.ANALYZING) }
            delay(Random.nextLong(5_000L, 8_001L))  // TODO: replace with real analysis time

            _uiState.update { it.copy(phase = ExplorePhase.COMPLETE) }
            ttsHelper.speakAndAwait(ENVIRONMENT_SPEAK)
            _speechFinished.tryEmit(Unit)

            delay(600L)
            _uiState.update { it.copy(phase = ExplorePhase.READY) }
            ttsHelper.speakAndAwait(FOLLOWUP_QUESTION)
            _speechFinished.tryEmit(Unit)
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Speaks a detailed description for [zone] and then re-prompts the user.
     * Only active when in the READY phase.
     *
     * TODO: Replace ZONE_DETAILS lookup with directional depth estimation:
     *   - Divide camera frame into left/center/right columns
     *   - Run distance model per column, identify nearest object
     *   - Generate description from model output
     */
    fun requestZoneDetail(zone: ExploreZone) {
        if (_uiState.value.phase != ExplorePhase.READY) return
        val detail = ZONE_DETAILS[zone]?.random() ?: return
        activeJob?.cancel()
        _uiState.update { it.copy(activeZone = zone, zoneDetail = detail) }
        activeJob = viewModelScope.launch {
            ttsHelper.speakAndAwait(detail.speakText)
            _speechFinished.tryEmit(Unit)
            delay(1_000L)
            _uiState.update { it.copy(activeZone = null, zoneDetail = null) }
            ttsHelper.speak(FOLLOWUP_QUESTION)
        }
    }

    /** Para el Asistente IA por voz: habla [text] y solo al terminar ejecuta [onDone]. */
    fun speakThenRun(text: String, onDone: () -> Unit) {
        viewModelScope.launch {
            ttsHelper.speakAndAwait(text)
            onDone()
        }
    }

    /** Restarts the full exploration from scratch. */
    fun restartExploration() { startExploration() }

    /**
     * Halts any running TTS and speaks the exit confirmation prompt.
     * Called on the first tap of the close button (two-tap exit pattern).
     */
    fun stopExplorationForConfirm() {
        activeJob?.cancel()
        activeJob = null
        ttsHelper.speak("¿Desea volver al inicio? Presione dos veces para confirmar.")
    }

    /**
     * Resumes after the user cancels the exit confirmation.
     * If analysis was in progress, jumps to READY to avoid replaying the wait.
     */
    fun resumeExploration() {
        val phase = _uiState.value.phase
        if (phase == ExplorePhase.READY) {
            ttsHelper.speak("Continuando modo exploración.")
            return
        }
        // If interrupted during ANALYZING or COMPLETE, skip to READY
        _uiState.update { it.copy(phase = ExplorePhase.READY, activeZone = null, zoneDetail = null) }
        activeJob = viewModelScope.launch {
            ttsHelper.speak("Exploración lista. Puedes explorar las zonas.")
        }
    }

    fun updateTtsSettings(enabled: Boolean, speed: TtsSpeed) {
        ttsHelper.enabled = enabled
        ttsHelper.setSpeed(speed)
    }

    fun updateHapticEnabled(enabled: Boolean) { /* reserved */ }

    override fun onCleared() {
        activeJob?.cancel()
        ttsHelper.shutdown()
    }
}
