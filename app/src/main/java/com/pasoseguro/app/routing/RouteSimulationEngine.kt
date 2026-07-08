package com.pasoseguro.app.routing

import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Genera una polilínea simulada entre un origen y un destino que aproxima el
 * comportamiento de una ruta urbana real: avanza en cuadrícula (norte/sur,
 * este/oeste) como si siguiera avenidas y cruces, evitando diagonales y
 * tramos rectos demasiado largos.
 *
 * Es el único lugar de la app que sabe CÓMO se generan los puntos de la
 * ruta — RouteScreen y RouteViewModel solo conocen la firma
 * (origen, destino) -> lista de puntos.
 *
 * TODO(Fase 3): reemplazar el cuerpo de [generateRoute] por una llamada a
 * Google Routes API manteniendo esta misma firma; ningún otro archivo
 * debería necesitar cambios.
 */
object RouteSimulationEngine {

    private const val METERS_PER_DEG_LAT = 111_320.0
    private const val MIN_BLOCK_METERS = 40.0
    private const val MAX_BLOCK_METERS = 220.0
    private const val TARGET_BLOCK_COUNT = 10
    private const val MAX_STRAIGHT_RUN = 3

    fun generateRoute(origin: LatLng, destination: LatLng): List<LatLng> {
        val metersPerDegLng = METERS_PER_DEG_LAT * cos(Math.toRadians(origin.latitude))
        val latMeters = (destination.latitude - origin.latitude) * METERS_PER_DEG_LAT
        val lngMeters = (destination.longitude - origin.longitude) * metersPerDegLng
        val manhattanMeters = abs(latMeters) + abs(lngMeters)

        // Origen y destino prácticamente en el mismo punto — no hay cuadrícula que simular.
        if (manhattanMeters < 15.0) return listOf(origin, destination)

        val blockSize = (manhattanMeters / TARGET_BLOCK_COUNT).coerceIn(MIN_BLOCK_METERS, MAX_BLOCK_METERS)
        val latBlocks = (latMeters / blockSize).roundToInt()
        val lngBlocks = (lngMeters / blockSize).roundToInt()
        if (latBlocks == 0 && lngBlocks == 0) return listOf(origin, destination)

        val latStepDeg = if (latBlocks != 0) (destination.latitude - origin.latitude) / latBlocks else 0.0
        val lngStepDeg = if (lngBlocks != 0) (destination.longitude - origin.longitude) / lngBlocks else 0.0

        val blockPoints = manhattanBlockPath(latBlocks, lngBlocks)
            .map { (x, y) -> LatLng(origin.latitude + x * latStepDeg, origin.longitude + y * lngStepDeg) }
            .toMutableList()
        blockPoints[0] = origin
        blockPoints[blockPoints.lastIndex] = destination

        val jogLatStepDeg = blockSize / METERS_PER_DEG_LAT
        val jogLngStepDeg = blockSize / metersPerDegLng

        return breakUpLongStraightRuns(blockPoints, jogLatStepDeg, jogLngStepDeg).distinctConsecutive()
    }

    /**
     * Camino en cuadrícula entre (0,0) y (latBlocks, lngBlocks): cada paso
     * avanza exactamente un bloque en latitud O en longitud (nunca ambos a
     * la vez), distribuidos proporcionalmente como una línea de Bresenham
     * para que los giros queden repartidos de forma pareja a lo largo del
     * trayecto en vez de agrupados en un extremo.
     */
    private fun manhattanBlockPath(latBlocks: Int, lngBlocks: Int): List<Pair<Int, Int>> {
        val points = mutableListOf(0 to 0)
        var x = 0
        var y = 0
        val absLat = abs(latBlocks)
        val absLng = abs(lngBlocks)
        val signLat = if (latBlocks >= 0) 1 else -1
        val signLng = if (lngBlocks >= 0) 1 else -1

        if (absLat >= absLng) {
            var err = absLat / 2
            repeat(absLat) {
                x += signLat
                points.add(x to y)
                err -= absLng
                if (err < 0 && absLng > 0) {
                    y += signLng
                    points.add(x to y)
                    err += absLat
                }
            }
        } else {
            var err = absLng / 2
            repeat(absLng) {
                y += signLng
                points.add(x to y)
                err -= absLat
                if (err < 0 && absLat > 0) {
                    x += signLat
                    points.add(x to y)
                    err += absLng
                }
            }
        }
        return points
    }

    /**
     * Detecta tramos con más de [MAX_STRAIGHT_RUN] bloques seguidos en la
     * misma dirección — posible señal de una zona sin calles aparentes (mar,
     * parque grande, manzana) — y les agrega un pequeño desvío lateral de
     * ida y vuelta, como si la ruta rodeara el obstáculo en vez de cruzarlo
     * en línea recta.
     */
    private fun breakUpLongStraightRuns(
        points: List<LatLng>,
        jogLatStepDeg: Double,
        jogLngStepDeg: Double,
    ): List<LatLng> {
        if (points.size < MAX_STRAIGHT_RUN + 2) return points

        val result = mutableListOf(points.first())
        var runDirection: Direction? = null
        var runLength = 0

        for (i in 1 until points.size) {
            val current = points[i]
            val direction = directionBetween(points[i - 1], current)

            runLength = if (direction == runDirection) runLength + 1 else 1
            runDirection = direction

            result.add(current)

            if (runLength > MAX_STRAIGHT_RUN && direction != null && i != points.lastIndex) {
                val (jogLat, jogLng) = perpendicularJog(direction, jogLatStepDeg, jogLngStepDeg)
                result.add(LatLng(current.latitude + jogLat, current.longitude + jogLng))
                result.add(current)
                runLength = 0
            }
        }
        return result
    }

    private enum class Direction { NORTH, SOUTH, EAST, WEST }

    private fun directionBetween(a: LatLng, b: LatLng): Direction? = when {
        b.latitude > a.latitude   -> Direction.NORTH
        b.latitude < a.latitude   -> Direction.SOUTH
        b.longitude > a.longitude -> Direction.EAST
        b.longitude < a.longitude -> Direction.WEST
        else                      -> null
    }

    private fun perpendicularJog(
        direction: Direction,
        jogLatStepDeg: Double,
        jogLngStepDeg: Double,
    ): Pair<Double, Double> = when (direction) {
        Direction.NORTH, Direction.SOUTH -> 0.0 to jogLngStepDeg
        Direction.EAST, Direction.WEST   -> jogLatStepDeg to 0.0
    }

    private fun List<LatLng>.distinctConsecutive(): List<LatLng> =
        fold(mutableListOf()) { acc, point ->
            if (acc.isEmpty() || acc.last() != point) acc.add(point)
            acc
        }
}
