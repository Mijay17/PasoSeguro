package com.pasoseguro.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

/** Permisos de ubicación usados por Ruta (y por la futura navegación asistida). */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Última ubicación conocida vía FusedLocationProviderClient.
 * Llamar solo cuando [hasLocationPermission] devuelve true.
 */
fun fetchLastLocation(context: Context, onResult: (LatLng?) -> Unit) {
    if (!hasLocationPermission(context)) {
        onResult(null)
        return
    }
    LocationServices.getFusedLocationProviderClient(context).lastLocation
        .addOnSuccessListener { location -> onResult(location?.let { LatLng(it.latitude, it.longitude) }) }
        .addOnFailureListener { onResult(null) }
}
