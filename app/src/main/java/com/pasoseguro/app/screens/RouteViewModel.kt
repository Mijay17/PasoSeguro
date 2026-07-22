package com.pasoseguro.app.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.pasoseguro.app.components.LimaCityCenter
import com.pasoseguro.app.data.VibrationIntensity
import com.pasoseguro.app.routing.LocationDistanceHelper
import com.pasoseguro.app.routing.NavigationSimulationEngine
import com.pasoseguro.app.routing.RouteSimulationEngine
import com.pasoseguro.app.routing.SimulationSpeed
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.NonRepeatingPicker
import com.pasoseguro.app.utils.fetchLastLocation
import com.pasoseguro.app.utils.hasLocationPermission
import com.pasoseguro.app.voice.VoiceInteractionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Phases ─────────────────────────────────────────────────────────────────

internal enum class RoutePhase {
    SEARCH, SAVED
}

// ── Domain models ──────────────────────────────────────────────────────────

internal data class SavedDestination(
    val id: Int,
    val name: String,
    val distanceKm: Float,
    val address: String,
    val lat: Double,
    val lng: Double,
)

internal data class RouteInfo(
    val destination: SavedDestination,
    val distanceKm: Float,
    val estimatedMinutes: Int,
    val recommendation: String,
)

/** Destino favorito con su distancia real (línea recta) ya calculada y lista para mostrar. */
internal data class FavoriteDestination(
    val destination: SavedDestination,
    val distanceMeters: Float?,
    val distanceLabel: String,
)

// ── Static data ────────────────────────────────────────────────────────────

internal fun formatDist(km: Float): String =
    if (km < 1f) "${(km * 1000).toInt()} m" else "$km km"

internal val ROUTE_DESTINATIONS = listOf(
    SavedDestination(0, "Casa",                                   1.2f,  "Av. Los Pinos 234, Miraflores",           -12.1235, -77.0288),
    SavedDestination(1, "Universidad Nacional Mayor de San Marcos", 1.8f, "Av. Venezuela s/n, Cercado de Lima",       -12.0569, -77.0847),
    SavedDestination(2, "Banco BCP",                              0.45f, "Ur. Larco 1302, Miraflores",              -12.1192, -77.0311),
    SavedDestination(3, "Paradero principal",                     1.5f,  "Av. Ricardo Palma, Miraflores",           -12.1219, -77.0301),
    SavedDestination(4, "Facultad",                                2.4f, "Av. Benavides 740, Miraflores",           -12.1259, -77.0279),
)

// Distancia real desde la ubicación actual (línea recta, sin red) hacia cada
// destino favorito, ordenados de más cercano a más lejano. Sin ubicación
// conocida aún, se conserva el orden y la distancia estimada de siempre
// como resguardo temporal. RouteScreen solo consume esta lista — no calcula
// distancias por su cuenta.
// TODO(Fase 4): reemplazar LocationDistanceHelper por la distancia de ruta
// real de Google Routes API sin cambiar la forma de [FavoriteDestination].
internal fun computeFavoriteDestinations(userLocation: LatLng?): List<FavoriteDestination> =
    ROUTE_DESTINATIONS
        .map { dest ->
            val meters = userLocation?.let {
                LocationDistanceHelper.distanceMeters(it, LatLng(dest.lat, dest.lng))
            }
            FavoriteDestination(
                destination = dest,
                distanceMeters = meters,
                distanceLabel = meters?.let(LocationDistanceHelper::formatDistance) ?: formatDist(dest.distanceKm),
            )
        }
        .sortedBy { it.distanceMeters ?: (it.destination.distanceKm * 1000f) }

private fun buildRouteInfo(dest: SavedDestination): RouteInfo {
    val minutes = when (dest.id) { 0 -> 15; 1 -> 15; 2 -> 10; 3 -> 18; else -> 28 }
    return RouteInfo(
        destination      = dest,
        distanceKm       = dest.distanceKm,
        estimatedMinutes = minutes,
        recommendation   = "Ruta accesible verificada. Aceras amplias y semáforos sonoros disponibles en el trayecto.",
    )
}

// ── UI state ───────────────────────────────────────────────────────────────

internal data class RouteUiState(
    val phase: RoutePhase = RoutePhase.SEARCH,
    val searchQuery: String = "",
    // Destinos favoritos con distancia real ya calculada y ordenados por
    // cercanía — ver computeFavoriteDestinations().
    val favoriteDestinations: List<FavoriteDestination> = computeFavoriteDestinations(null),
    val selectedDestination: SavedDestination? = null,
    val destPendingConfirm: SavedDestination? = null,  // first-tap destination pending
    val isCalculatingRoute: Boolean = false,
    val routeInfo: RouteInfo? = null,
    // Puntos de la polilínea simulada, calculados una sola vez cuando la ruta
    // se confirma. TODO(Fase 4): reemplazar por los puntos de Google Routes API.
    val routePoints: List<LatLng> = emptyList(),
    val startPendingConfirm: Boolean = false,
    val routeStarted: Boolean = false,  // navegación simulada activa
    val userLocation: LatLng? = null,
    // Fracción 0f..1f del recorrido ya avanzado. La avanza el
    // NavigationSimulationEngine mientras la navegación está activa, y es lo
    // que hace que el tramo recorrido de la polilínea cambie de azul a verde.
    val routeProgress: Float = 0f,
    // ── Navegación simulada (Fase 3) ────────────────────────────────────────
    val navPosition: LatLng? = null,
    val navRemainingDistanceMeters: Int = 0,
    val navRemainingMinutes: Int = 0,
    val navInstruction: String = "",
    val navArrived: Boolean = false,
    val navCancelPendingConfirm: Boolean = false,
)

// ── ViewModel ──────────────────────────────────────────────────────────────

internal class RouteViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext

    // Único motor de voz de toda la app — ver misma nota en NavigateViewModel.
    private val voice = VoiceInteractionManager.getInstance(appContext)

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private var navJob: Job? = null
    private var hapticEnabled = true
    private var vibrationIntensity = VibrationIntensity.MEDIA
    private var navPaused = false
    private val navigationEngine = NavigationSimulationEngine()

    // Velocidad de la navegación simulada — constante por ahora (facilita
    // pruebas y demostraciones); TODO(Fase 4): exponer desde ConfigScreen.
    private var simulationSpeed = SimulationSpeed.NORMAL

    // Variaciones del Asistente IA para no repetir siempre el mismo mensaje.
    private val calculatingMessages = NonRepeatingPicker(
        listOf(
            "Destino confirmado. Estoy calculando la mejor ruta para llegar de forma segura.",
            "Perfecto. Un momento mientras preparo la ruta más adecuada hacia tu destino.",
            "He recibido tu destino. Estoy analizando el recorrido para ofrecerte una navegación segura.",
        ),
    )
    private val routeReadyMessages = NonRepeatingPicker(
        listOf(
            "Ruta calculada correctamente. Puedes iniciar la navegación cuando lo desees.",
            "Todo está listo. He preparado una ruta segura hacia tu destino.",
            "La ruta está lista. Presiona Iniciar ruta cuando estés preparado.",
        ),
    )
    private val navStartMessages = NonRepeatingPicker(
        listOf(
            "La navegación ha comenzado. Te acompañaré hasta tu destino.",
            "Navegación iniciada. Estaré contigo durante todo el recorrido.",
        ),
    )
    private val navArrivalMessages = NonRepeatingPicker(
        listOf(
            "Has llegado a tu destino. Espero haberte ayudado durante el recorrido.",
            "Has llegado a tu destino. Fue un gusto acompañarte en este recorrido.",
        ),
    )

    init {
        viewModelScope.launch {
            delay(500L)
            // Sin "Ruta": el micrófono se arma casi al mismo tiempo que este
            // mensaje suena (ver VoiceCommand.kt). flush=false: no cortar la
            // confirmación de navegación que puede seguir sonando al entrar.
            voice.speak(
                "Modo activado. Puedes seleccionar un destino favorito o indicarlo mediante un comando de voz.",
                flush = false,
            )
        }
    }

    // ── Navigation within the app ─────────────────────────────────────────

    /** Pulso háptico previo a cualquier confirmación hablada — ver [HapticHelper]. */
    private fun vibrate() {
        if (hapticEnabled) HapticHelper.vibrate(appContext, true, vibrationIntensity)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showSavedDestinations() {
        _uiState.update { it.copy(phase = RoutePhase.SAVED, destPendingConfirm = null) }
        vibrate()
        voice.speak("Mis destinos guardados. Toca un destino para seleccionarlo.")
    }

    // Two-tap destination selection — permanece en el mapa (fase SEARCH):
    // muestra el overlay "Calculando ruta..." y luego la tarjeta con la ruta.
    fun onDestinationTap(dest: SavedDestination) {
        val pending = _uiState.value.destPendingConfirm
        if (pending?.id == dest.id) {
            // Second tap — confirm and start calculation
            navJob?.cancel()
            _uiState.update {
                it.copy(
                    destPendingConfirm = null,
                    selectedDestination = dest,
                    phase = RoutePhase.SEARCH,
                    isCalculatingRoute = true,
                    routeInfo = null,
                    routePoints = emptyList(),
                    startPendingConfirm = false,
                    routeStarted = false,
                    routeProgress = 0f,
                    navPosition = null,
                    navRemainingDistanceMeters = 0,
                    navRemainingMinutes = 0,
                    navInstruction = "",
                    navArrived = false,
                    navCancelPendingConfirm = false,
                )
            }
            voice.speak(calculatingMessages.next())
            viewModelScope.launch {
                delay((2_000L..3_000L).random())
                val info = buildRouteInfo(dest)
                val origin = _uiState.value.userLocation ?: LimaCityCenter
                val destination = LatLng(dest.lat, dest.lng)
                val points = RouteSimulationEngine.generateRoute(origin, destination)
                _uiState.update {
                    it.copy(routeInfo = info, routePoints = points, isCalculatingRoute = false)
                }
                // El mensaje de "ruta lista" se reproduce desde la UI
                // (announceRouteReady) recién cuando la cámara ya encuadró el
                // trayecto y la polilínea terminó de dibujarse — no antes.
            }
        } else {
            // First tap — announce and arm
            _uiState.update { it.copy(destPendingConfirm = dest) }
            vibrate()
            voice.speak("Destino seleccionado: ${dest.name}. Toca dos veces para calcular el trayecto.")
            viewModelScope.launch {
                delay(3_000L)
                if (_uiState.value.destPendingConfirm?.id == dest.id) {
                    _uiState.update { it.copy(destPendingConfirm = null) }
                }
            }
        }
    }

    // Se llama desde la UI (RouteScreen) una vez que la cámara encuadró el
    // trayecto y la polilínea terminó su animación de dibujado — es el paso
    // final de la secuencia: cámara -> polilínea -> tarjeta -> Asistente IA.
    fun announceRouteReady() {
        voice.speak(routeReadyMessages.next())
    }

    // Two-tap "Iniciar ruta" button — el segundo toque arranca la navegación
    // simulada sobre el mismo mapa (sin cambiar de pantalla).
    fun onStartRouteTap() {
        if (_uiState.value.startPendingConfirm) {
            _uiState.update { it.copy(startPendingConfirm = false, routeStarted = true) }
            voice.speak(navStartMessages.next())
            startSimulatedNavigation()
        } else {
            _uiState.update { it.copy(startPendingConfirm = true) }
            vibrate()
            voice.speak("Has seleccionado iniciar la navegación. Toca dos veces para comenzar.")
            viewModelScope.launch {
                delay(3_000L)
                if (_uiState.value.startPendingConfirm) {
                    _uiState.update { it.copy(startPendingConfirm = false) }
                }
            }
        }
    }

    // Two-tap "Cancelar navegación" button — visible mientras la navegación
    // simulada está activa. Mismo patrón (armar + confirmar) que el resto de
    // acciones de doble pulsación de esta pantalla.
    fun onCancelNavigationTap() {
        if (_uiState.value.navCancelPendingConfirm) {
            navJob?.cancel()
            val lastLocation = _uiState.value.userLocation
            _uiState.update {
                RouteUiState(userLocation = lastLocation, favoriteDestinations = computeFavoriteDestinations(lastLocation))
            }
            viewModelScope.launch {
                delay(200L)
                voice.speak("Navegación cancelada. Indica tu destino.")
            }
        } else {
            _uiState.update { it.copy(navCancelPendingConfirm = true) }
            vibrate()
            voice.speak("La navegación será cancelada. Toca dos veces para confirmar.")
            viewModelScope.launch {
                delay(3_000L)
                if (_uiState.value.navCancelPendingConfirm) {
                    _uiState.update { it.copy(navCancelPendingConfirm = false) }
                }
            }
        }
    }

    fun speakHomePrompt() {
        // Sin "inicio": el micrófono sigue escuchando mientras esta frase suena.
        voice.speak("¿Deseas salir de este modo? Toca dos veces para confirmar.")
    }

    fun resetToSearch() {
        navJob?.cancel()
        _uiState.update {
            RouteUiState(userLocation = it.userLocation, favoriteDestinations = it.favoriteDestinations)
        }
        vibrate()
        viewModelScope.launch {
            delay(200L)
            voice.speak("Indica tu destino.")
        }
    }

    // ── Ubicación (Google Maps) ────────────────────────────────────────────
    //
    // Solo obtiene la última ubicación conocida una vez (no hace seguimiento
    // continuo todavía). El seguimiento en tiempo real y el cálculo de rutas
    // llegarán con la navegación asistida (Routes API).

    fun refreshUserLocation() {
        if (!hasLocationPermission(appContext)) return
        fetchLastLocation(appContext) { latLng ->
            if (latLng != null) {
                _uiState.update {
                    it.copy(userLocation = latLng, favoriteDestinations = computeFavoriteDestinations(latLng))
                }
            }
        }
    }

    fun onLocationRecentered() {
        voice.speak("Mapa centrado en tu ubicación actual.")
    }

    fun onLocationUnavailableYet() {
        voice.speak("Buscando tu ubicación actual.")
    }

    // ── Navegación simulada ─────────────────────────────────────────────────
    //
    // Consume el Flow<NavigationFrame> de NavigationSimulationEngine y lo
    // traduce a RouteUiState. RouteScreen solo observa este estado — no sabe
    // cómo se genera la posición, la distancia ni las instrucciones.
    // TODO(Fase 4): esta función es el único lugar que cambiaría al pasar de
    // simulación a GPS real (otro motor, misma forma de consumo).

    private fun startSimulatedNavigation() {
        navJob?.cancel()
        val routePoints = _uiState.value.routePoints
        val info = _uiState.value.routeInfo ?: return
        navJob = viewModelScope.launch {
            navigationEngine
                .navigate(
                    routePoints = routePoints,
                    totalDistanceMeters = (info.distanceKm * 1000).toInt(),
                    totalMinutes = info.estimatedMinutes,
                    speed = simulationSpeed,
                )
                .collect { frame ->
                    _uiState.update {
                        it.copy(
                            navPosition = frame.position,
                            routeProgress = frame.progress,
                            navRemainingDistanceMeters = frame.remainingDistanceMeters,
                            navRemainingMinutes = frame.remainingMinutes,
                            navInstruction = frame.instruction,
                            navArrived = frame.arrived,
                        )
                    }
                    if (hapticEnabled) {
                        vibratePattern(appContext, if (frame.arrived) VibPattern.ONE else VibPattern.TWO, vibrationIntensity)
                    }
                    voice.speak(frame.instruction)
                    if (frame.arrived) {
                        delay(300L)
                        voice.speak(navArrivalMessages.next())
                        delay(4_500L)
                        _uiState.update { RouteUiState(userLocation = it.userLocation) }
                    }
                }
        }
    }

    /**
     * Pausa silenciosa al dejar de ser la pantalla activa (navegación a otra
     * pantalla, sin destruir este ViewModel) — corta la narración de la
     * navegación simulada en curso, si la hay. Idempotente.
     */
    fun pauseSimulatedNavigation() {
        if (navPaused) return
        navPaused = true
        navJob?.cancel()
        voice.stopSpeaking()
    }

    /**
     * Reanuda la navegación simulada tras [pauseSimulatedNavigation], solo si
     * había una en curso y no había llegado ya a destino. No guarda el
     * progreso exacto: reinicia el trayecto simulado desde el punto de partida.
     */
    fun resumeSimulatedNavigationIfNeeded() {
        navPaused = false
        if (!_uiState.value.routeStarted || _uiState.value.navArrived) return
        startSimulatedNavigation()
    }

    // ── Prefs sync ─────────────────────────────────────────────────────────

    fun updateHapticEnabled(enabled: Boolean) {
        hapticEnabled = enabled
    }

    fun updateVibrationIntensity(intensity: VibrationIntensity) {
        vibrationIntensity = intensity
    }

    /** Cambia el ritmo de la próxima navegación simulada (no afecta una ya en curso). */
    fun updateSimulationSpeed(speed: SimulationSpeed) {
        simulationSpeed = speed
    }

    override fun onCleared() {
        navJob?.cancel()
    }
}
