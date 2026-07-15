package com.pasoseguro.app.screens

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pasoseguro.app.voice.VoiceInteractionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
        // Sin "Navegar"/"navegación": el micrófono se arma casi al mismo tiempo
        // que este mensaje suena (ver VoiceCommand.kt).
        speakText = "Modo activado. La cámara está activa. Comenzando el monitoreo del entorno.",
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

// ── ViewModel ──────────────────────────────────────────────────────────────

internal class NavigateViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext

    // Único motor de voz de toda la app — ya no un TTS propio de esta
    // pantalla. El mismo singleton sirve tanto a las alertas guionadas de
    // este loop como a las respuestas del Asistente IA; su `speakAndAwait`
    // ya serializa ambos productores (ver TextToSpeechManager).
    private val voice = VoiceInteractionManager.getInstance(appContext)

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
                // flush=false: en la primera vuelta (bienvenida) evita cortar la
                // confirmación de navegación que puede seguir sonando al entrar;
                // en las vueltas siguientes no cambia nada, porque cada alerta ya
                // se espera (speakAndAwait) antes de que empiece la próxima.
                voice.speakAndAwait(alert.speakText, fallback, flush = false)

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
        // Sin "inicio": el micrófono sigue escuchando mientras esta frase suena
        // y "inicio" es palabra gatillo del comando global Inicio.
        voice.speak("¿Deseas salir de este modo? Presiona dos veces para confirmar.")
    }

    /**
     * Resumes the monitoring loop from the last alert index.
     * Safe to call even when already monitoring (no-op in that case) — usada
     * también por el comando de voz "Iniciar navegación"/"Reanudar navegación".
     */
    fun resumeMonitoring() {
        if (_uiState.value.isMonitoring) return
        startMonitoringLoop()
    }

    /** Sync haptic pref from ConfigScreen when it changes. */
    fun updateHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }

    override fun onCleared() {
        monitoringJob?.cancel()
    }
}
