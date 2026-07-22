package com.pasoseguro.app.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pasoseguro.app.data.VibrationIntensity
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.voice.VoiceInteractionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

// Sin "Explorar"/"exploración": el micrófono se arma casi al mismo tiempo
// que este mensaje suena (ver VoiceCommand.kt).
private const val WELCOME_TEXT =
    "Modo activado. Gira lentamente para analizar el espacio que te rodea."

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

// ── ViewModel ──────────────────────────────────────────────────────────────

internal class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext

    // Único motor de voz de toda la app — ver misma nota en NavigateViewModel.
    private val voice = VoiceInteractionManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var wasBackgrounded = false
    private var hapticEnabled = true
    private var vibrationIntensity = VibrationIntensity.MEDIA

    init {
        startExploration()
    }

    /** Pulso háptico previo a cualquier confirmación hablada — ver [HapticHelper]. */
    private fun vibrate() {
        if (hapticEnabled) HapticHelper.vibrate(appContext, true, vibrationIntensity)
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
            // flush=false en toda la secuencia: la primera evita cortar la
            // confirmación de navegación que puede seguir sonando al entrar;
            // las siguientes ya se esperan una a una, así que no cambia nada
            // para ellas — se mantiene por consistencia de la secuencia.
            voice.speakAndAwait(WELCOME_TEXT, flush = false)

            _uiState.update { it.copy(phase = ExplorePhase.ANALYZING) }
            delay(Random.nextLong(5_000L, 8_001L))  // TODO: replace with real analysis time

            _uiState.update { it.copy(phase = ExplorePhase.COMPLETE) }
            voice.speakAndAwait(ENVIRONMENT_SPEAK, flush = false)

            delay(600L)
            _uiState.update { it.copy(phase = ExplorePhase.READY) }
            voice.speakAndAwait(FOLLOWUP_QUESTION, flush = false)
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
        vibrate()
        activeJob = viewModelScope.launch {
            voice.speakAndAwait(detail.speakText)
            delay(1_000L)
            _uiState.update { it.copy(activeZone = null, zoneDetail = null) }
            voice.speak(FOLLOWUP_QUESTION)
        }
    }

    /** Restarts the full exploration from scratch — comando de voz "Explorar nuevamente". */
    fun restartExploration() { startExploration() }

    /**
     * Re-habla la descripción del entorno ya analizado sin repetir el loop
     * STARTING→ANALYZING — comando de voz "Describir entorno". No hace nada
     * si la exploración todavía no llegó a COMPLETE/READY (nada que describir).
     */
    fun describeEnvironmentAgain() {
        if (_uiState.value.phase != ExplorePhase.COMPLETE && _uiState.value.phase != ExplorePhase.READY) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch { voice.speakAndAwait(ENVIRONMENT_SPEAK) }
    }

    /**
     * Halts any running TTS and speaks the exit confirmation prompt.
     * Called on the first tap of the close button (two-tap exit pattern).
     */
    fun stopExplorationForConfirm() {
        activeJob?.cancel()
        activeJob = null
        vibrate()
        // Sin "inicio": el micrófono sigue escuchando mientras esta frase suena
        // y "inicio" es palabra gatillo del comando global Inicio.
        voice.speak("¿Deseas salir de este modo? Presiona dos veces para confirmar.")
    }

    /**
     * Pausa silenciosa al dejar de ser la pantalla activa (navegación a otra
     * pantalla, sin destruir este ViewModel) — a diferencia de
     * [stopExplorationForConfirm], no habla ningún mensaje. Idempotente.
     */
    fun pauseExploration() {
        if (wasBackgrounded) return
        wasBackgrounded = true
        activeJob?.cancel()
        activeJob = null
        voice.stopSpeaking()
    }

    /**
     * Reanuda tras [pauseExploration] — no-op si nunca se pausó (evita forzar
     * la fase a READY en la primera entrada, que es lo que hace
     * [resumeExploration] incondicionalmente).
     */
    fun resumeIfBackgrounded() {
        if (!wasBackgrounded) return
        wasBackgrounded = false
        resumeExploration()
    }

    /**
     * Resumes after the user cancels the exit confirmation.
     * If analysis was in progress, jumps to READY to avoid replaying the wait.
     */
    fun resumeExploration() {
        val phase = _uiState.value.phase
        if (phase == ExplorePhase.READY) {
            voice.speak("Continuando modo exploración.")
            return
        }
        // If interrupted during ANALYZING or COMPLETE, skip to READY
        _uiState.update { it.copy(phase = ExplorePhase.READY, activeZone = null, zoneDetail = null) }
        activeJob = viewModelScope.launch {
            voice.speak("Exploración lista. Puedes explorar las zonas.")
        }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }

    fun updateVibrationIntensity(intensity: VibrationIntensity) {
        vibrationIntensity = intensity
    }

    override fun onCleared() {
        activeJob?.cancel()
    }
}
