package com.pasoseguro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pasoseguro.app.components.AssistantMicButton
import com.pasoseguro.app.components.BarAction
import com.pasoseguro.app.components.ProceduralBottomBar
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.LocalVoiceInteractionManager
import com.pasoseguro.app.ui.theme.*
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.VoiceInteractionState
import com.pasoseguro.app.voice.rememberAutoListenVoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val READ_NOTIFICATIONS_COUNT = 5

// ── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun AlertsScreen(navController: NavController) {
    val vm: AlertsViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    val context = LocalContext.current
    val prefs   = LocalUserPreferences.current
    val voice   = LocalVoiceInteractionManager.current
    val scope   = rememberCoroutineScope()

    val alertsVoiceContext = remember(uiState.filteredEvents) {
        ScreenVoiceContext(
            screenName = "Notificaciones",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords = listOf("leer notificaciones", "leer alertas", "leer eventos"),
                    onRecognized = {
                        scope.launch {
                            val events = uiState.filteredEvents.take(READ_NOTIFICATIONS_COUNT)
                            if (events.isEmpty()) {
                                voice.speakAndAwait("No hay notificaciones registradas.")
                            } else {
                                events.forEach { event -> voice.speakAndAwait("${event.title}. ${event.description}") }
                            }
                        }
                    },
                    confirmationSpeech = "Leyendo notificaciones.",
                ),
                ScreenVoiceCommand(
                    // Sin la palabra "repetir": ese matiz ya lo cubre el comando global Repetir;
                    // esta frase busca específicamente la última alerta registrada, no la última locución.
                    keywords = listOf("ultima alerta", "leer ultima alerta", "que paso"),
                    onRecognized = {
                        val last = uiState.filteredEvents.firstOrNull()
                        voice.speak(last?.let { "${it.title}. ${it.description}" } ?: "No hay alertas registradas todavía.")
                    },
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Leer notificaciones, o Última alerta.",
        )
    }
    val activeVoice = rememberAutoListenVoice(alertsVoiceContext)
    val voiceState by activeVoice.state.collectAsState()

    LaunchedEffect(Unit) {
        // Sin "alertas": el micrófono se arma casi al mismo tiempo que este
        // mensaje suena (ver VoiceCommand.kt).
        // flush=false: no cortar la confirmación de navegación que puede seguir sonando al entrar.
        voice.speak(
            "Bienvenido al historial de actividad. Aquí encontrarás lo registrado durante el uso de PasoSeguro.",
            flush = false,
        )
    }

    Scaffold(
        topBar = {
            AlertsTopBar(
                eventCount    = uiState.filteredEvents.size,
                recentOnly    = uiState.recentOnly,
                voiceState    = voiceState,
                onMicClick    = voice::requestHelp,
                onBackClick   = {
                    voice.speak("Volviendo a la pantalla principal")
                    navController.popBackStack()
                },
                onRecentClick = {
                    HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity)
                    val activating = !uiState.recentOnly
                    vm.toggleRecentOnly()
                    voice.speak(if (activating) "Mostrando eventos recientes." else "Mostrando todos los eventos.")
                },
            )
        },
        bottomBar = {
            // Filtros exclusivamente en la parte inferior: Todos / Navegar / Explorar.
            ProceduralBottomBar(
                left = BarAction(
                    icon           = Icons.Filled.List,
                    label          = "Todos",
                    selected       = uiState.selectedFilter == null,
                    pendingMessage = "Mostrar todos los eventos. Toca dos veces para confirmar.",
                    onConfirm      = {
                        vm.setFilter(null)
                        voice.speak("Mostrando todos los registros.")
                    },
                ),
                center = BarAction(
                    icon           = Icons.Filled.NearMe,
                    label          = "Navegar",
                    selected       = uiState.selectedFilter == EventMode.NAVIGATE,
                    pendingMessage = "Filtrar por Navegar. Toca dos veces para confirmar.",
                    onConfirm      = {
                        vm.setFilter(EventMode.NAVIGATE)
                        voice.speak("Mostrando eventos de Navegar.")
                    },
                ),
                right = BarAction(
                    icon           = Icons.Filled.Explore,
                    label          = "Explorar",
                    selected       = uiState.selectedFilter == EventMode.EXPLORE,
                    pendingMessage = "Filtrar por Explorar. Toca dos veces para confirmar.",
                    onConfirm      = {
                        vm.setFilter(EventMode.EXPLORE)
                        voice.speak("Mostrando eventos de Explorar.")
                    },
                ),
                accentColor   = AlertRed,
                onSpeak       = voice::speak,
                hapticEnabled = prefs.hapticEnabled,
                vibrationIntensity = prefs.vibrationIntensity,
                modifier      = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            SearchField(
                query   = uiState.searchQuery,
                onQuery = vm::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant,
            )
            if (uiState.filteredEvents.isEmpty()) {
                EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                )
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.filteredEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onTap = {
                                // Accesibilidad: al seleccionar un evento de la lista, describir
                                // su contenido de inmediato por voz.
                                HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity)
                                voice.speak("${event.title}. ${event.description}")
                            },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun AlertsTopBar(
    eventCount: Int,
    recentOnly: Boolean,
    voiceState: VoiceInteractionState,
    onMicClick: () -> Unit,
    onBackClick: () -> Unit,
    onRecentClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text  = "Notificaciones",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                if (eventCount > 0) {
                    Text(
                        text  = "$eventCount ${if (eventCount == 1) "evento" else "eventos"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        // Flecha de regreso — idéntica a la de Configuración: toque único e
        // inmediato, sin doble confirmación.
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector        = Icons.Filled.ArrowBackIosNew,
                    contentDescription = "Volver al inicio",
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            AssistantMicButton(
                pending = voiceState != VoiceInteractionState.Idle,
                onClick = onMicClick,
                tint    = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(
                onClick  = onRecentClick,
                modifier = Modifier.semantics {
                    contentDescription = if (recentOnly)
                        "Mostrando solo eventos recientes. Toca para ver todos."
                    else
                        "Mostrar solo eventos recientes."
                },
            ) {
                Icon(
                    imageVector        = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint               = if (recentOnly) AlertRed else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor             = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor          = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

// ── Search field ─────────────────────────────────────────────────────────────

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value          = query,
        onValueChange  = onQuery,
        placeholder    = { Text("Buscar eventos…", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon    = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon   = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        } else null,
        singleLine     = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape          = RoundedCornerShape(14.dp),
        colors         = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AlertRed,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier       = modifier.semantics {
            contentDescription = "Campo de búsqueda. Escribe para filtrar eventos."
        },
    )
}

// ── Event card ───────────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: AppEvent, onTap: () -> Unit) {
    val modeColor     = if (event.mode == EventMode.NAVIGATE) NavBlue else ScanTeal
    val severityColor = when (event.severity) {
        EventSeverity.DANGER  -> AlertRed
        EventSeverity.WARNING -> RouteAmber
        EventSeverity.INFO    -> modeColor
    }
    val modeLabel    = if (event.mode == EventMode.NAVIGATE) "NAVEGAR" else "EXPLORAR"
    val relativeTime = remember(event.timestamp) { formatRelativeTime(event.timestamp) }

    Card(
        onClick   = onTap,
        modifier  = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${event.title}. ${event.description}. Modo $modeLabel. $relativeTime."
            },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left severity accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(severityColor),
            )
            // Card body
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Mode icon circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(modeColor.copy(alpha = 0.10f)),
                ) {
                    Icon(
                        imageVector        = event.type.toIcon(),
                        contentDescription = null,
                        tint               = modeColor,
                        modifier           = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                // Title + description + timestamp
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text     = event.title,
                            style    = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSurface,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                        )
                        ModeBadge(label = modeLabel, color = modeColor)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text     = event.description,
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier           = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text  = relativeTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ── Mode badge ───────────────────────────────────────────────────────────────

@Composable
private fun ModeBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color      = color,
                fontWeight = FontWeight.Bold,
                fontSize   = 10.sp,
            ),
        )
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.NotificationsNone,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier           = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text  = "Aún no hay actividad registrada.",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "Los eventos detectados durante la navegación y exploración aparecerán aquí.",
            style = MaterialTheme.typography.bodySmall.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            ),
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "Justo ahora"
        diff < 3_600_000L   -> "Hace ${diff / 60_000} min"
        diff < 86_400_000L  -> "Hace ${diff / 3_600_000} h"
        else                -> SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale("es")).format(Date(timestamp))
    }
}

private fun EventType.toIcon(): ImageVector = when (this) {
    EventType.PERSON_DETECTED   -> Icons.Filled.DirectionsWalk
    EventType.OBSTACLE_DETECTED -> Icons.Filled.Warning
    EventType.VEHICLE_DETECTED  -> Icons.Filled.DirectionsCar
    EventType.STAIR_DETECTED    -> Icons.Filled.Stairs
    EventType.PATH_CLEAR,
    EventType.EXPLORE_COMPLETE  -> Icons.Filled.CheckCircle
    EventType.SEAT_DETECTED     -> Icons.Filled.EventSeat
    EventType.DOOR_DETECTED     -> Icons.Filled.MeetingRoom
    EventType.CROWD_DETECTED    -> Icons.Filled.Groups
    EventType.LOW_LIGHT         -> Icons.Filled.LightMode
}
