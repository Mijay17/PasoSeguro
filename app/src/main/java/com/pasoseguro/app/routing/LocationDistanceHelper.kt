package com.pasoseguro.app.routing

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlin.math.roundToInt

/**
 * Distancia geográfica en línea recta entre dos puntos (Android
 * `Location.distanceBetween`, sin llamadas de red).
 *
 * Es el único lugar de la app que calcula distancias hacia destinos —
 * RouteViewModel lo consume para ordenar y anotar los destinos favoritos.
 *
 * TODO(Fase 4): reemplazar [distanceMeters] por la distancia de ruta real de
 * Google Routes API manteniendo esta misma firma.
 */
object LocationDistanceHelper {

    fun distanceMeters(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0]
    }

    /** "280 m" / "950 m" por debajo de 1 km, "1.3 km" / "2.8 km" desde 1 km. */
    fun formatDistance(meters: Float): String =
        if (meters < 1000f) "${meters.roundToInt()} m"
        else "%.1f km".format(meters / 1000f)
}
