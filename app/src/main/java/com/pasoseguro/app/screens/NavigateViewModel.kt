package com.pasoseguro.app.screens

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pasoseguro.app.data.TtsSpeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.random.Random

// ── Domain models ──────────────────────────────────────────────────────────

internal enum class AlertSeverity { CLEAR, CAUTION, DANGER }
internal enum class ObstacleDirection { NONE, FRONT, LEFT, RIGHT }
internal enum class VibPattern { NONE, ONE, TWO, THREE }

internal data class NavAlert(
    val id: Int,
    val title: String,
    val detail: String,
    val recommendation: String,
    val severity: AlertSeverity,
    val direction: ObstacleDirection,
    val distanceMeters: Float?,
    val speakText: String,
    val vibration: VibPattern,
)

internal val ALERT_SEQUENCE = listOf(
    NavAlert(
        id = 0, title = "Monitoreando entorno",
        detail = "Análisis de área activo",
        recommendation = "Mantenga el dispositivo hacia adelante",
        severity = AlertSeverity.CLEAR, direction = ObstacleDirection.NONE,
        distanceMeters = null,
        speakText = "Iniciando modo navegación. Monitoreando entorno.",
        vibration = VibPattern.ONE,
    ),
    NavAlert(
        id = 1, title = "Entorno despejado",
        detail = "Sin obstáculos detectados",
        recommendation = "Puede continuar con normalidad",
        severity = AlertSeverity.CLEAR, direction = ObstacleDirection.NONE,
        distanceMeters = null,
        speakText = "Entorno despejado. Puede continuar.",
        vibration = VibPattern.NONE,
    ),
    NavAlert(
        id = 2, title = "Persona detectada",
        detail = "A 3.2 metros al frente",
        recommendation = "Continúe con precaución",
        severity = AlertSeverity.CAUTION, direction = ObstacleDirection.FRONT,
        distanceMeters = 3.2f,
        speakText = "Persona detectada a 3.2 metros al frente. Continúe con precaución.",
        vibration = VibPattern.TWO,
    ),
    NavAlert(
        id = 3, title = "Poste detectado",
        detail = "A 1.5 metros a la derecha",
        recommendation = "Desvíese ligeramente hacia la izquierda",
        severity = AlertSeverity.CAUTION, direction = ObstacleDirection.RIGHT,
        distanceMeters = 1.5f,
        speakText = "Poste detectado a 1.5 metros a la derecha. Desvíese hacia la izquierda.",
        vibration = VibPattern.TWO,
    ),
    NavAlert(
        id = 4, title = "¡Escalón detectado!",
        detail = "A 0.8 metros al frente",
        recommendation = "Reduzca la velocidad inmediatamente",
        severity = AlertSeverity.DANGER, direction = ObstacleDirection.FRONT,
        distanceMeters = 0.8f,
        speakText = "Atención. Escalón detectado a 0.8 metros al frente. Reduzca la velocidad.",
        vibration = VibPattern.THREE,
    ),
    NavAlert(
        id = 5, title = "Vehículo detectado",
        detail = "A 4.0 metros a la izquierda",
        recommendation = "Mantenga su trayectoria actual",
        severity = AlertSeverity.CAUTION, direction = ObstacleDirection.LEFT,
        distanceMeters = 4.0f,
        speakText = "Vehículo detectado a 4 metros a la izquierda. Mantenga su trayectoria.",
        vibration = VibPattern.TWO,
    ),
    NavAlert(
        id = 6, title = "Sin obstáculos",
        detail = "Área libre confirmada",
        recommendation = "Camino despejado",
        severity = AlertSeverity.CLEAR, direction = ObstacleDirection.NONE,
        distanceMeters = null,
        speakText = "Sin obstáculos. Camino despejado.",
        vibration = VibPattern.NONE,
    ),
    NavAlert(
        id = 7, title = "Camino despejado",
        detail = "Navegación fluida",
        recommendation = "Puede continuar con normalidad",
        severity = AlertSeverity.CLEAR, direction = ObstacleDirection.NONE,
        distanceMeters = null,
        speakText = "Camino despejado.",
        vibration = VibPattern.NONE,
    ),
)

// ── UI state ───────────────────────────────────────────────────────────────

internal data class NavigateUiState(
    val alert: NavAlert = ALERT_SEQUENCE[0],
    val isMonitoring: Boolean = false,
)

// ── Vibration helper ───────────────────────────────────────────────────────

internal fun vibratePattern(context: Context, pattern: VibPattern) {
    if (pattern == VibPattern.NONE) return
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = when (pattern) {
            VibPattern.ONE   -> VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
            VibPattern.TWO   -> VibrationEffect.createWaveform(longArrayOf(0, 60, 110, 60), -1)
            VibPattern.THREE -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60, 80, 80), -1)
            VibPattern.NONE  -> return
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        when (pattern) {
            VibPattern.ONE   -> vibrator.vibrate(60)
            VibPattern.TWO   -> vibrator.vibrate(longArrayOf(0, 60, 110, 60), -1)
            VibPattern.THREE -> vibrator.vibrate(longArrayOf(0, 60, 80, 60, 80, 80), -1)
            VibPattern.NONE  -> {}
        }
    }
}

// ── TTS with UtteranceProgressListener ─────────────────────────────────────
//
// Each call to speakAndAwait() blocks (suspends) until onDone fires,
// guaranteeing that no message is cut off by the next one.

private class NavigateTtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    var enabled: Boolean = true
    var speechRate: Float = TtsSpeed.NORMAL.rate

    // Receives Unit when the TTS engine finishes the current utterance.
    // CONFLATED: only one pending signal is kept — protects against stale signals.
    private val doneChannel = Channel<Unit>(Channel.CONFLATED)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        doneChannel.trySend(Unit)
                    }

                    // Required abstract override (deprecated in API 21+, still mandatory)
                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        doneChannel.trySend(Unit) // treat error as completion
                    }

                    // API 21+ non-abstract override for modern engines
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        doneChannel.trySend(Unit)
                    }
                })
                ready = true
            }
        }
    }

    /**
     * Speaks [text] and suspends until the engine fires onDone (speech is complete).
     * Falls back to a fixed [fallbackMs] delay when TTS is disabled or not yet ready.
     * A 30-second safety timeout prevents infinite suspension if the engine hangs.
     */
    suspend fun speakAndAwait(text: String, fallbackMs: Long = 3500L) {
        if (!enabled || !ready) {
            delay(fallbackMs)
            return
        }
        // Drain any stale signal left by a previous utterance
        doneChannel.tryReceive()
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_MAIN)
        withTimeoutOrNull(30_000L) { doneChannel.receive() }
    }

    /**
     * Fire-and-forget speak for UI prompts (e.g., exit confirmation question).
     * Does NOT block — use speakAndAwait for sequenced alerts.
     */
    fun speak(text: String) {
        if (!ready) return
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_DIALOG)
    }

    fun setSpeed(speed: TtsSpeed) { speechRate = speed.rate }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val UTTERANCE_MAIN   = "nav-main"
        private const val UTTERANCE_DIALOG = "nav-dialog"
    }
}

// ── ViewModel ──────────────────────────────────────────────────────────────

internal class NavigateViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val ttsHelper = NavigateTtsHelper(appContext)

    private val _uiState = MutableStateFlow(NavigateUiState())
    val uiState: StateFlow<NavigateUiState> = _uiState.asStateFlow()

    private var monitoringJob: Job? = null
    private var alertIndex = 0
    private var hapticEnabled = true

    init {
        // Give the TTS engine ~300 ms to initialize before the first alert.
        // If it isn't ready in time, speakAndAwait() falls back to a fixed delay
        // and the engine will be ready for subsequent alerts.
        viewModelScope.launch {
            delay(300L)
            startMonitoringLoop()
        }
    }

    // ── Private monitoring loop ────────────────────────────────────────────
    //
    // Timing contract per alert:
    //   1. Update UI (bounding box + card change instantly)
    //   2. speakAndAwait → suspends until TTS finishes the FULL message
    //   3. Vibrate (synchronized, plays after speech ends)
    //   4. Silence gap: 3–4 s of breathing room
    //   5. Advance index → repeat

    private fun startMonitoringLoop() {
        monitoringJob?.cancel()
        _uiState.update { it.copy(isMonitoring = true) }

        monitoringJob = viewModelScope.launch {
            while (isActive) {
                val alert = ALERT_SEQUENCE[alertIndex]
                _uiState.update { it.copy(alert = alert) }

                val fallback = when (alert.severity) {
                    AlertSeverity.DANGER  -> 4000L
                    AlertSeverity.CAUTION -> 4000L
                    AlertSeverity.CLEAR   -> 3500L
                }
                ttsHelper.speakAndAwait(alert.speakText, fallback)

                if (!isActive) break

                if (hapticEnabled) vibratePattern(appContext, alert.vibration)

                // Silence gap — gives the user time to process before the next alert
                delay(Random.nextLong(3000L, 4001L))

                alertIndex = (alertIndex + 1) % ALERT_SEQUENCE.size
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Halts the monitoring loop and speaks the voice confirmation prompt.
     * Called on the FIRST tap of the close button so the user hears the warning
     * before the second tap actually closes the screen.
     */
    fun stopMonitoringForConfirm() {
        monitoringJob?.cancel()
        monitoringJob = null
        _uiState.update { it.copy(isMonitoring = false) }
        ttsHelper.speak("¿Desea volver al inicio? Presione dos veces para confirmar.")
    }

    /**
     * Resumes the monitoring loop from the last alert index.
     * Safe to call even when already monitoring (no-op in that case).
     */
    fun resumeMonitoring() {
        if (_uiState.value.isMonitoring) return
        startMonitoringLoop()
    }

    /** Sync TTS prefs from ConfigScreen when they change. */
    fun updateTtsSettings(enabled: Boolean, speed: TtsSpeed) {
        ttsHelper.enabled = enabled
        ttsHelper.setSpeed(speed)
    }

    /** Sync haptic pref from ConfigScreen when it changes. */
    fun updateHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }

    override fun onCleared() {
        monitoringJob?.cancel()
        ttsHelper.shutdown()
    }
}
