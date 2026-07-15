package com.pasoseguro.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.pasoseguro.app.R
import com.pasoseguro.app.components.AssistantMicButton
import com.pasoseguro.app.components.LimaCityCenter
import com.pasoseguro.app.components.RouteGoogleMap
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.ui.theme.AlertRed
import com.pasoseguro.app.ui.theme.ContactGreen
import com.pasoseguro.app.ui.theme.ContactGreen50
import com.pasoseguro.app.ui.theme.NavBlue
import com.pasoseguro.app.ui.theme.RouteAmber
import com.pasoseguro.app.ui.theme.RouteAmberLight
import com.pasoseguro.app.utils.LOCATION_PERMISSIONS
import com.pasoseguro.app.utils.hasLocationPermission
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.VoiceInteractionState
import com.pasoseguro.app.voice.rememberAutoListenVoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Helpers ────────────────────────────────────────────────────────────────

/** Permite animar (interpolar) un LatLng con [Animatable] como si fuera un Float. */
private val LatLngToVector = TwoWayConverter<LatLng, AnimationVector2D>(
    convertToVector = { AnimationVector2D(it.latitude.toFloat(), it.longitude.toFloat()) },
    convertFromVector = { LatLng(it.v1.toDouble(), it.v2.toDouble()) },
)

private fun iconForDest(dest: SavedDestination): ImageVector = when (dest.id) {
    0    -> Icons.Filled.Home
    1    -> Icons.Filled.School
    2    -> Icons.Filled.AccountBalance
    3    -> Icons.Filled.DirectionsBus
    else -> Icons.Filled.Business
}

/**
 * Corta una polilínea en el punto correspondiente a [fraction] (0f..1f) y
 * devuelve (tramo antes, tramo después), compartiendo el punto de corte para
 * que ambos tramos queden conectados sin huecos.
 *
 * Se usa tanto para la animación de trazado progresivo (crece el tramo
 * "después" de 0 a 1) como para el color según el avance real de la
 * navegación simulada, que llega en [RouteUiState.routeProgress] (el tramo
 * "antes" se pinta como recorrido). TODO(Fase 4): alimentar [fraction] desde
 * GPS real sin cambiar esta firma.
 */
private fun splitRouteAtFraction(points: List<LatLng>, fraction: Float): Pair<List<LatLng>, List<LatLng>> {
    if (points.size < 2) return emptyList<LatLng>() to points
    val f = fraction.coerceIn(0f, 1f)
    val lastSegment = points.size - 1
    val cutIndex = (lastSegment * f).toInt().coerceIn(0, lastSegment)
    return points.take(cutIndex + 1) to points.drop(cutIndex)
}

private fun instructionIcon(instruction: String): ImageVector {
    val lower = instruction.lowercase()
    return when {
        "llegado" in lower -> Icons.Filled.CheckCircle
        "derecha" in lower -> Icons.Filled.TurnRight
        "izquierda" in lower -> Icons.Filled.TurnLeft
        "cerca" in lower    -> Icons.Filled.NearMe
        else                -> Icons.Filled.ArrowUpward
    }
}

private fun formatMeters(meters: Int): String =
    if (meters < 1000) "$meters m" else "%.1f km".format(meters / 1000f)

// ── Route screen entry point ────────────────────────────────────────────────

@Composable
fun RouteScreen(navController: NavController) {
    val vm: RouteViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val prefs = LocalUserPreferences.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voice = LocalVoiceInteractionManager.current

    LaunchedEffect(prefs.hapticEnabled) {
        vm.updateHapticEnabled(prefs.hapticEnabled)
    }
    LaunchedEffect(prefs.vibrationIntensity) {
        vm.updateVibrationIntensity(prefs.vibrationIntensity)
    }

    // ── Ubicación del usuario (Google Maps) ────────────────────────────────
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> locationPermissionGranted = results.values.any { it } }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
    }
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) vm.refreshUserLocation()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LimaCityCenter, 14f)
    }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.userLocation) {
        val loc = uiState.userLocation
        if (loc != null && !hasCenteredOnUser) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(loc, 16f)
            hasCenteredOnUser = true
        }
    }

    // Cuando la ruta simulada está lista, encuadra origen y destino para que
    // la polilínea sea realmente visible (no solo un punto fuera de cámara).
    val density = LocalDensity.current
    LaunchedEffect(uiState.routeInfo) {
        val info = uiState.routeInfo ?: return@LaunchedEffect
        val origin = uiState.userLocation ?: LimaCityCenter
        val destination = LatLng(info.destination.lat, info.destination.lng)
        val bounds = LatLngBounds.Builder().include(origin).include(destination).build()
        val paddingPx = with(density) { 96.dp.toPx().toInt() }
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), 700)
        }
    }

    // Seguimiento de cámara durante la navegación simulada: cada vez que
    // avanza a un nuevo punto, la cámara se desplaza suavemente para
    // mantener visible al usuario y el tramo de ruta que queda por delante.
    LaunchedEffect(uiState.navPosition) {
        val pos = uiState.navPosition ?: return@LaunchedEffect
        if (uiState.routeStarted) {
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, 17.5f), 1100)
            }
        }
    }

    fun onLocationTap() {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
            return
        }
        val loc = uiState.userLocation
        if (loc != null) {
            scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 16f), 600) }
            vm.onLocationRecentered()
        } else {
            vm.onLocationUnavailableYet()
            vm.refreshUserLocation()
        }
    }

    // Home button — two-tap exits RouteScreen back to HomeScreen
    var homePendingConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(homePendingConfirm) {
        if (homePendingConfirm) {
            delay(3_000L)
            homePendingConfirm = false
        }
    }

    fun onHomeTap() {
        if (homePendingConfirm) {
            navController.popBackStack()
        } else {
            homePendingConfirm = true
            vm.speakHomePrompt()
        }
    }

    // ── Asistente IA por voz — escucha automática y continua ────────────────
    // Reutiliza los íconos de micrófono que ya existían (antes solo
    // decorativos/de armado) en la barra de búsqueda y en la lista de favoritos
    // — ahora son indicadores de estado, el micrófono ya escucha solo.
    val routeVoiceContext = remember(vm) {
        ScreenVoiceContext(
            screenName = "Ruta",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords            = listOf("buscar destino", "mostrar favoritos", "mostrar destinos"),
                    onRecognized        = vm::showSavedDestinations,
                    confirmationSpeech  = null, // ya habla su propio mensaje
                ),
                ScreenVoiceCommand(
                    keywords = listOf("cancelar ruta", "cancelar navegacion", "detener ruta"),
                    onRecognized = {
                        // Lee el estado más reciente (no uno capturado al construir
                        // este contexto) — evita reconstruir el contexto en cada
                        // frame de la navegación simulada solo para mantenerlo fresco.
                        val state = vm.uiState.value
                        when {
                            state.routeStarted -> vm.onCancelNavigationTap()
                            state.routeInfo != null || state.phase == RoutePhase.SAVED -> vm.resetToSearch()
                            else -> voice.speak("No hay ningún trayecto en curso ahora mismo.")
                        }
                    },
                    confirmationSpeech = null, // cada rama ya habla su propia confirmación
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Buscar destino, Mostrar favoritos, o Cancelar ruta.",
            onActive = vm::resumeSimulatedNavigationIfNeeded,
            onInactive = vm::pauseSimulatedNavigation,
        )
    }
    val activeVoice = rememberAutoListenVoice(routeVoiceContext)
    val voiceState by activeVoice.state.collectAsState()

    AnimatedContent(
        targetState = uiState.phase,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
        modifier = Modifier.fillMaxSize(),
        label = "routePhase",
    ) { phase ->
        when (phase) {
            RoutePhase.SEARCH ->
                SearchPhaseScreen(
                    vm = vm,
                    uiState = uiState,
                    homePendingConfirm = homePendingConfirm,
                    onHomeTap = ::onHomeTap,
                    cameraPositionState = cameraPositionState,
                    onLocationTap = ::onLocationTap,
                    voiceState = voiceState,
                    onMicClick = activeVoice::requestHelp,
                )
            RoutePhase.SAVED ->
                SavedPhaseScreen(vm = vm, uiState = uiState, voiceState = voiceState, onMicClick = activeVoice::requestHelp)
        }
    }
}

// ── Bottom card states ───────────────────────────────────────────────────

private sealed interface BottomCardState {
    data object Waiting : BottomCardState
    data object Drawing : BottomCardState
    data class Ready(val info: RouteInfo) : BottomCardState
    data class Navigating(val info: RouteInfo) : BottomCardState
    data object Arrived : BottomCardState
}

// ── Phase 1 — Search ───────────────────────────────────────────────────────

@Composable
private fun SearchPhaseScreen(
    vm: RouteViewModel,
    uiState: RouteUiState,
    homePendingConfirm: Boolean,
    onHomeTap: () -> Unit,
    cameraPositionState: CameraPositionState,
    onLocationTap: () -> Unit,
    voiceState: VoiceInteractionState,
    onMicClick: () -> Unit,
) {
    val destination = uiState.selectedDestination
    val routeInfo = uiState.routeInfo
    val destLatLng = destination?.let { LatLng(it.lat, it.lng) }
    // La ViewModel es la única que sabe cómo se generan estos puntos
    // (RouteSimulationEngine hoy, Google Routes API en el futuro) — esta
    // pantalla solo los observa.
    val routePoints = uiState.routePoints

    // Animación de trazado: al aparecer la ruta, la polilínea se dibuja
    // progresivamente y solo al terminar se revela la tarjeta inferior.
    var routeDrawFraction by remember { mutableStateOf(0f) }
    LaunchedEffect(routeInfo) {
        routeDrawFraction = 0f
        if (routeInfo != null) {
            delay(450L)
            animate(0f, 1f, animationSpec = tween(900, easing = FastOutSlowInEasing)) { value, _ ->
                routeDrawFraction = value
            }
        }
    }
    val routeReady = routeInfo != null && routeDrawFraction >= 1f

    // Último paso de la secuencia de aparición: cámara -> polilínea -> tarjeta
    // -> mensaje del Asistente IA (no todo a la vez).
    LaunchedEffect(routeReady) {
        if (routeReady) vm.announceRouteReady()
    }

    // Tramo recorrido (verde) vs. pendiente (azul) — routeProgress lo avanza
    // NavigationSimulationEngine mientras la navegación simulada está activa.
    val (traveledPoints, pendingPointsFull) = remember(routePoints, uiState.routeProgress) {
        splitRouteAtFraction(routePoints, uiState.routeProgress)
    }
    val visiblePendingPoints = remember(pendingPointsFull, routeDrawFraction) {
        splitRouteAtFraction(pendingPointsFull, routeDrawFraction).first
    }

    // Marcador azul: durante la navegación simulada sigue la posición emitida
    // por el motor, interpolando suavemente entre puntos (sin saltos). Fuera
    // de navegación, refleja directamente la ubicación real del usuario.
    val markerAnimatable = remember {
        Animatable(uiState.userLocation ?: LimaCityCenter, LatLngToVector)
    }
    LaunchedEffect(uiState.navPosition) {
        val target = uiState.navPosition ?: return@LaunchedEffect
        markerAnimatable.animateTo(target, animationSpec = tween(3_800, easing = LinearEasing))
    }
    LaunchedEffect(uiState.userLocation, uiState.routeStarted) {
        val loc = uiState.userLocation
        if (!uiState.routeStarted && loc != null) {
            markerAnimatable.snapTo(loc)
        }
    }
    val displayedUserLocation = if (uiState.routeStarted) markerAnimatable.value else uiState.userLocation

    Box(Modifier.fillMaxSize()) {
        RouteGoogleMap(
            cameraPositionState = cameraPositionState,
            userLocation = displayedUserLocation,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Destino + polilínea simulada — solo una vez calculada la ruta.
            // TODO(Fase 4): sustituir por la polilínea real de Google Routes API.
            if (destLatLng != null && routeInfo != null) {
                Marker(
                    state = rememberMarkerState(key = "dest-${destination.id}", position = destLatLng),
                    title = destination.name,
                    snippet = destination.address,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                )
            }
            if (traveledPoints.size >= 2) {
                Polyline(
                    points = traveledPoints,
                    color = ContactGreen,
                    width = 10f,
                )
            }
            if (visiblePendingPoints.size >= 2) {
                Polyline(
                    points = visiblePendingPoints,
                    color = NavBlue,
                    width = 10f,
                )
            }
        }

        // Search bar overlaid at the top
        SearchBarCard(
            query = uiState.searchQuery,
            onQueryChange = vm::onSearchQueryChange,
            onOpenDestinations = vm::showSavedDestinations,
            voiceState = voiceState,
            onMicClick = onMicClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        // Location centering FAB (right, mid-map)
        Surface(
            onClick = onLocationTap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 80.dp)
                .semantics { contentDescription = "Centrar mapa en mi ubicación" },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = null,
                tint = RouteAmber,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
            )
        }

        // Contextual bottom card (esperando destino / ruta lista) + barra fija
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            val cardState = when {
                routeInfo == null      -> BottomCardState.Waiting
                !routeReady             -> BottomCardState.Drawing
                uiState.navArrived      -> BottomCardState.Arrived
                uiState.routeStarted    -> BottomCardState.Navigating(routeInfo)
                else                    -> BottomCardState.Ready(routeInfo)
            }
            AnimatedContent(
                targetState = cardState,
                transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(160)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                label = "routeBottomCard",
            ) { state ->
                when (state) {
                    BottomCardState.Waiting -> WaitingForDestinationCard()
                    // Ruta calculada pero aún dibujándose en el mapa — sin tarjeta,
                    // para que la atención quede en la animación de la polilínea.
                    BottomCardState.Drawing -> Spacer(Modifier.height(1.dp))
                    is BottomCardState.Ready -> RouteInfoCard(
                        info = state.info,
                        startPendingConfirm = uiState.startPendingConfirm,
                        onStartTap = vm::onStartRouteTap,
                    )
                    is BottomCardState.Navigating -> NavigationActiveCard(
                        instruction = uiState.navInstruction,
                        remainingDistanceMeters = uiState.navRemainingDistanceMeters,
                        remainingMinutes = uiState.navRemainingMinutes,
                        progress = uiState.routeProgress,
                        cancelPendingConfirm = uiState.navCancelPendingConfirm,
                        onCancelTap = vm::onCancelNavigationTap,
                    )
                    BottomCardState.Arrived -> ArrivedCard()
                }
            }

            BottomRouteActionBar(
                homePendingConfirm = homePendingConfirm,
                onHomeTap = onHomeTap,
                onLocationClick = onLocationTap,
                onDestinationsClick = vm::showSavedDestinations,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Overlay "Calculando ruta..." — por encima de todo lo demás.
        if (uiState.isCalculatingRoute) {
            CalculatingRouteOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

// ── Contextual bottom cards ─────────────────────────────────────────────────

@Composable
private fun WaitingForDestinationCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Esperando un destino. Selecciona un destino favorito o realiza una búsqueda."
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(RouteAmberLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Explore,
                    contentDescription = null,
                    tint = RouteAmber,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Esperando un destino.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Selecciona un destino favorito o realiza una búsqueda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RouteReadyBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = ContactGreen50,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = ContactGreen,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "Ruta lista",
                style = MaterialTheme.typography.labelSmall,
                color = ContactGreen,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RouteInfoCard(
    info: RouteInfo,
    startPendingConfirm: Boolean,
    onStartTap: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Ruta lista. Ruta hacia ${info.destination.name}. " +
                    "${formatDist(info.distanceKm)}, ${info.estimatedMinutes} minutos caminando."
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            RouteReadyBadge()

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(RouteAmberLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        iconForDest(info.destination),
                        contentDescription = null,
                        tint = RouteAmber,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Destino",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        info.destination.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteStatChip(Icons.Filled.Straighten, formatDist(info.distanceKm))
                RouteStatChip(Icons.Filled.Schedule, "${info.estimatedMinutes} min caminando")
                RouteStatChip(Icons.Filled.Accessible, "Accesible")
            }

            Spacer(Modifier.height(18.dp))

            StartRouteButton(
                pendingConfirm = startPendingConfirm,
                onTap = onStartTap,
            )
        }
    }
}

@Composable
private fun StartRouteButton(
    pendingConfirm: Boolean,
    onTap: () -> Unit,
) {
    val label = if (pendingConfirm) "Confirmar" else "Iniciar ruta"
    val description = if (pendingConfirm) {
        "Confirmar inicio de navegación. Toca dos veces para comenzar."
    } else {
        "Iniciar ruta. Doble pulsación para confirmar."
    }
    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pendingConfirm) RouteAmber.copy(alpha = 0.75f) else RouteAmber,
        ),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun NavigationActiveCard(
    instruction: String,
    remainingDistanceMeters: Int,
    remainingMinutes: Int,
    progress: Float,
    cancelPendingConfirm: Boolean,
    onCancelTap: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Navegando. $instruction " +
                    "Distancia restante: ${formatMeters(remainingDistanceMeters)}. " +
                    "Tiempo estimado: $remainingMinutes minutos."
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = ContactGreen,
                trackColor = NavBlue.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(RouteAmberLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        instructionIcon(instruction),
                        contentDescription = null,
                        tint = RouteAmber,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    instruction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteStatChip(Icons.Filled.Straighten, "${formatMeters(remainingDistanceMeters)} restantes")
                RouteStatChip(Icons.Filled.Schedule, "$remainingMinutes min estimados")
            }

            Spacer(Modifier.height(18.dp))

            CancelNavigationButton(pendingConfirm = cancelPendingConfirm, onTap = onCancelTap)
        }
    }
}

@Composable
private fun CancelNavigationButton(pendingConfirm: Boolean, onTap: () -> Unit) {
    val label = if (pendingConfirm) "Confirmar cancelación" else "Cancelar navegación"
    val description = if (pendingConfirm) {
        "Confirmar: cancelar navegación. Toca dos veces para confirmar."
    } else {
        "Cancelar navegación. Doble pulsación para confirmar."
    }
    OutlinedButton(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
        border = BorderStroke(1.5.dp, if (pendingConfirm) AlertRed else AlertRed.copy(alpha = 0.5f)),
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun ArrivedCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Has llegado a tu destino." },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(ContactGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                "Has llegado a tu destino.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ContactGreen,
            )
        }
    }
}

// ── Calculating overlay ──────────────────────────────────────────────────────

@Composable
private fun CalculatingRouteOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f))
            .semantics { contentDescription = "Calculando ruta. Un momento por favor." },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = RouteAmber, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Calculando ruta...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SearchBarCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenDestinations: () -> Unit,
    voiceState: VoiceInteractionState,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenDestinations() },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "¿A dónde quieres ir?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            Spacer(Modifier.width(4.dp))
            AssistantMicButton(
                pending  = voiceState != VoiceInteractionState.Idle,
                onClick  = onMicClick,
                tint     = RouteAmber,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun BottomRouteActionBar(
    homePendingConfirm: Boolean,
    onHomeTap: () -> Unit,
    onLocationClick: () -> Unit,
    onDestinationsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floating white card above the system navigation bar
    Box(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            ) {
                // Left — Ubicación, cerca del borde izquierdo
                LabeledCircleButton(
                    icon = Icons.Filled.MyLocation,
                    label = "Ubicación",
                    onClick = onLocationClick,
                    size = 60.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp),
                )

                // Right — Favoritos, cerca del borde derecho
                LabeledCircleButton(
                    icon = Icons.Filled.Bookmark,
                    label = "Favoritos",
                    onClick = onDestinationsClick,
                    size = 60.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp),
                )

                // Center — PasoSeguro home (two-tap, slightly larger), perfectamente centrado
                val scale by animateFloatAsState(
                    targetValue = if (homePendingConfirm) 0.90f else 1f,
                    animationSpec = tween(200),
                    label = "homeScale",
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp * scale)
                            .shadow(if (homePendingConfirm) 6.dp else 3.dp, CircleShape)
                            .background(
                                if (homePendingConfirm) RouteAmber.copy(alpha = 0.75f) else RouteAmber,
                                CircleShape,
                            )
                            .clickable(onClick = onHomeTap)
                            .semantics {
                                contentDescription =
                                    if (homePendingConfirm) "Confirmar: volver al inicio. Botón PasoSeguro."
                                    else "Volver al inicio. Botón principal PasoSeguro. Doble pulsación para confirmar."
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                    Text(
                        text = if (homePendingConfirm) "Confirmar" else "PasoSeguro",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (homePendingConfirm) FontWeight.Bold else FontWeight.Normal,
                        color = if (homePendingConfirm) RouteAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Normal: fondo blanco, borde e icono naranja. Activo (presionado):
    // fondo naranja, icono blanco. Transición suave entre ambos.
    val backgroundColor by animateColorAsState(
        targetValue = if (pressed) RouteAmber else Color.White,
        animationSpec = tween(180),
        label = "circleBtnBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (pressed) Color.White else RouteAmber,
        animationSpec = tween(180),
        label = "circleBtnIcon",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(3.dp, CircleShape)
                .background(backgroundColor, CircleShape)
                .border(1.5.dp, RouteAmber, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics { contentDescription = "$label. Botón de acción." },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Phase 2 — Saved destinations ──────────────────────────────────────────

@Composable
private fun SavedPhaseScreen(
    vm: RouteViewModel,
    uiState: RouteUiState,
    voiceState: VoiceInteractionState,
    onMicClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::resetToSearch) {
                Icon(Icons.Filled.ArrowBack, "Volver a búsqueda", tint = RouteAmber)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "Mis destinos guardados",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Selecciona un destino para planificar tu ruta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(uiState.favoriteDestinations, key = { it.destination.id }) { fav ->
                DestinationCard(
                    dest = fav.destination,
                    distanceLabel = fav.distanceLabel,
                    isPending = uiState.destPendingConfirm?.id == fav.destination.id,
                    onClick = { vm.onDestinationTap(fav.destination) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                onClick = onMicClick,
                modifier = Modifier
                    .size(60.dp)
                    .semantics {
                        contentDescription =
                            if (voiceState is VoiceInteractionState.Speaking)
                                "Asistente IA hablando. Toca para interrumpir y pedir ayuda."
                            else
                                "Asistente IA por voz. El micrófono ya está escuchando."
                    },
                shape = CircleShape,
                color = if (voiceState != VoiceInteractionState.Idle) RouteAmber.copy(alpha = 0.75f) else RouteAmber,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (voiceState != VoiceInteractionState.Idle) "Escuchando" else "Asistente IA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DestinationCard(
    dest: SavedDestination,
    distanceLabel: String,
    isPending: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPending) RouteAmberLight else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "destBg",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics {
                contentDescription =
                    if (isPending) "Confirmar: ${dest.name}. Toca nuevamente para calcular la ruta."
                    else "${dest.name}. $distanceLabel. ${dest.address}"
            },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = if (isPending) 4.dp else 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(if (isPending) RouteAmber else RouteAmberLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconForDest(dest),
                    contentDescription = null,
                    tint = if (isPending) Color.White else RouteAmber,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    dest.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPending) RouteAmber else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    dest.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPending) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Toca dos veces para calcular la ruta",
                        style = MaterialTheme.typography.labelSmall,
                        color = RouteAmber,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = if (isPending) RouteAmber else RouteAmberLight,
            ) {
                Text(
                    distanceLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPending) Color.White else RouteAmber,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RouteStatChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = RouteAmberLight,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = RouteAmber, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = RouteAmber, fontWeight = FontWeight.SemiBold)
        }
    }
}

