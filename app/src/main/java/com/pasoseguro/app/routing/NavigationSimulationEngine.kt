package com.pasoseguro.app.routing

import com.google.android.gms.maps.model.LatLng
import com.pasoseguro.app.utils.NonRepeatingPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Un "fotograma" de la navegación simulada: dónde está el usuario en ese
 * instante, cuánto lleva avanzado, cuánto le falta y qué debería escuchar.
 */
data class NavigationFrame(
    val position: LatLng,
    val progress: Float,
    val remainingDistanceMeters: Int,
    val remainingMinutes: Int,
    val instruction: String,
    val arrived: Boolean,
)

/**
 * Velocidad de la navegación simulada: controla el intervalo entre cada
 * punto emitido por [NavigationSimulationEngine] (y, por lo tanto, el ritmo
 * de las instrucciones por voz que lo acompañan). Pensado para facilitar
 * pruebas y demostraciones del prototipo — no afecta la lógica de cálculo
 * de progreso, distancia ni instrucciones, solo su cadencia.
 *
 * TODO(Fase 4): exponer esta elección en ConfigScreen para que el usuario
 * la controle; hoy vive como una constante dentro de RouteViewModel.
 */
enum class SimulationSpeed(val stepIntervalMs: Long) {
    SLOW(5_000L),
    NORMAL(3_000L),
    FAST(1_000L),
}

/**
 * Motor de navegación asistida simulada.
 *
 * Avanza sobre una polilínea ya calculada (hoy por [RouteSimulationEngine],
 * mañana por Google Routes API) emitiendo un [NavigationFrame] al ritmo que
 * marque [SimulationSpeed] con la siguiente posición, el progreso, la
 * distancia y el tiempo restante, y una instrucción. No conoce Compose ni
 * Android UI — quien lo usa (RouteViewModel) traduce estos fotogramas a
 * estado observable; RouteScreen solo observa ese estado.
 *
 * TODO(Fase 4): reemplazar el cuerpo de [navigate] por un motor basado en
 * ubicación GPS real. La forma de consumirlo (Flow<NavigationFrame>) y esta
 * interfaz pública pueden mantenerse igual.
 */
class NavigationSimulationEngine {

    fun navigate(
        routePoints: List<LatLng>,
        totalDistanceMeters: Int,
        totalMinutes: Int,
        speed: SimulationSpeed = SimulationSpeed.NORMAL,
    ): Flow<NavigationFrame> = flow {
        if (routePoints.size < 2) return@flow

        // Frescos por cada navegación: "no repetir la misma frase consecutiva"
        // solo debe valer dentro de un mismo recorrido.
        val progressMessages = NonRepeatingPicker(PROGRESS_INSTRUCTIONS)
        val nearArrivalMessages = NonRepeatingPicker(NEAR_ARRIVAL_INSTRUCTIONS)
        val lastIndex = routePoints.lastIndex

        for (i in 1..lastIndex) {
            delay(speed.stepIntervalMs)

            val progress = i / lastIndex.toFloat()
            val remainingFraction = (1f - progress).coerceIn(0f, 1f)
            val arrived = i == lastIndex

            val instruction = when {
                arrived -> ARRIVAL_INSTRUCTION
                remainingFraction <= NEAR_ARRIVAL_THRESHOLD -> nearArrivalMessages.next()
                else -> progressMessages.next()
            }

            emit(
                NavigationFrame(
                    position = routePoints[i],
                    progress = progress,
                    remainingDistanceMeters = (totalDistanceMeters * remainingFraction).roundToInt(),
                    remainingMinutes = if (arrived) {
                        0
                    } else {
                        ceil(totalMinutes * remainingFraction).toInt().coerceAtLeast(1)
                    },
                    instruction = instruction,
                    arrived = arrived,
                ),
            )
        }
    }

    private companion object {
        const val NEAR_ARRIVAL_THRESHOLD = 0.15f
        const val ARRIVAL_INSTRUCTION = "Has llegado a tu destino."

        val PROGRESS_INSTRUCTIONS = listOf(
            "Continúa recto aproximadamente cien metros.",
            "Gira ligeramente hacia la derecha.",
            "Gira ligeramente hacia la izquierda.",
            "Continúa por la acera con precaución.",
            "En unos metros encontrarás un cruce peatonal.",
            "Vas por buen camino, continúa así.",
            "Mantén el rumbo actual.",
        )
        val NEAR_ARRIVAL_INSTRUCTIONS = listOf(
            "El destino está muy cerca.",
            "Ya casi llegas a tu destino.",
        )
    }
}
