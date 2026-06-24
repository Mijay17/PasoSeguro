package com.pasoseguro.app.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ── Domain models ──────────────────────────────────────────────────────────
// No Compose/UI dependencies — these types are Room-ready.
// TODO: Annotate AppEvent with @Entity and move to a :domain module for
//       full separation; replace SIMULATED_EVENTS with an EventRepository.

enum class EventMode     { NAVIGATE, EXPLORE }
enum class EventSeverity { INFO, WARNING, DANGER }
enum class EventType {
    PERSON_DETECTED,
    OBSTACLE_DETECTED,
    VEHICLE_DETECTED,
    STAIR_DETECTED,
    PATH_CLEAR,
    EXPLORE_COMPLETE,
    SEAT_DETECTED,
    DOOR_DETECTED,
    CROWD_DETECTED,
    LOW_LIGHT,
}

data class AppEvent(
    val id: Int,
    val type: EventType,
    val title: String,
    val description: String,
    val mode: EventMode,
    val severity: EventSeverity,
    val timestamp: Long,
)

// ── UI state ───────────────────────────────────────────────────────────────

data class AlertsUiState(
    val filteredEvents: List<AppEvent> = SIMULATED_EVENTS,
    val selectedFilter: EventMode?     = null,
    val searchQuery: String            = "",
)

// ── ViewModel ──────────────────────────────────────────────────────────────

class AlertsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun setFilter(mode: EventMode?) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = mode,
                filteredEvents = applyFilters(state.searchQuery, mode),
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery    = query,
                filteredEvents = applyFilters(query, state.selectedFilter),
            )
        }
    }

    private fun applyFilters(query: String, mode: EventMode?): List<AppEvent> =
        SIMULATED_EVENTS
            .filter { mode == null || it.mode == mode }
            .filter {
                query.isBlank() ||
                it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
}

// ── Simulated event history (sorted newest → oldest) ───────────────────────

private val BASE = System.currentTimeMillis()
private fun minsAgo(n: Int) = BASE - n * 60_000L
private fun hrsAgo(n: Int)  = BASE - n * 3_600_000L

internal val SIMULATED_EVENTS: List<AppEvent> = listOf(
    AppEvent(
        id = 0, type = EventType.PERSON_DETECTED,
        title       = "Persona detectada",
        description = "Persona detectada a 2.3 metros al frente.",
        mode = EventMode.NAVIGATE, severity = EventSeverity.WARNING,
        timestamp = minsAgo(3),
    ),
    AppEvent(
        id = 1, type = EventType.OBSTACLE_DETECTED,
        title       = "Poste detectado",
        description = "Poste detectado a la derecha. Se recomendó desviarse ligeramente.",
        mode = EventMode.NAVIGATE, severity = EventSeverity.WARNING,
        timestamp = minsAgo(18),
    ),
    AppEvent(
        id = 2, type = EventType.EXPLORE_COMPLETE,
        title       = "Exploración completada",
        description = "Exploración completada con éxito. Se mapearon tres zonas del espacio.",
        mode = EventMode.EXPLORE, severity = EventSeverity.INFO,
        timestamp = minsAgo(35),
    ),
    AppEvent(
        id = 3, type = EventType.SEAT_DETECTED,
        title       = "Asientos libres detectados",
        description = "Se identificaron dos asientos libres a la izquierda.",
        mode = EventMode.EXPLORE, severity = EventSeverity.INFO,
        timestamp = minsAgo(36),
    ),
    AppEvent(
        id = 4, type = EventType.DOOR_DETECTED,
        title       = "Puerta de salida localizada",
        description = "Puerta de salida localizada en el lado derecho.",
        mode = EventMode.EXPLORE, severity = EventSeverity.INFO,
        timestamp = minsAgo(38),
    ),
    AppEvent(
        id = 5, type = EventType.CROWD_DETECTED,
        title       = "Espacio muy concurrido",
        description = "Espacio muy concurrido. Se detectaron varias personas. Proceda con precaución.",
        mode = EventMode.EXPLORE, severity = EventSeverity.WARNING,
        timestamp = hrsAgo(2),
    ),
    AppEvent(
        id = 6, type = EventType.LOW_LIGHT,
        title       = "Iluminación insuficiente",
        description = "Iluminación insuficiente para la exploración. Considere activar la linterna.",
        mode = EventMode.EXPLORE, severity = EventSeverity.DANGER,
        timestamp = hrsAgo(3),
    ),
    AppEvent(
        id = 7, type = EventType.STAIR_DETECTED,
        title       = "Escalón detectado",
        description = "Escalón detectado a 0.8 metros al frente. Se recomendó reducir la velocidad.",
        mode = EventMode.NAVIGATE, severity = EventSeverity.DANGER,
        timestamp = hrsAgo(4),
    ),
    AppEvent(
        id = 8, type = EventType.VEHICLE_DETECTED,
        title       = "Vehículo detectado",
        description = "Vehículo detectado a 4 metros a la izquierda. Mantenga su trayectoria.",
        mode = EventMode.NAVIGATE, severity = EventSeverity.WARNING,
        timestamp = hrsAgo(5),
    ),
    AppEvent(
        id = 9, type = EventType.PATH_CLEAR,
        title       = "Camino despejado",
        description = "Navegación fluida. Camino completamente despejado al frente.",
        mode = EventMode.NAVIGATE, severity = EventSeverity.INFO,
        timestamp = hrsAgo(6),
    ),
).sortedByDescending { it.timestamp }
