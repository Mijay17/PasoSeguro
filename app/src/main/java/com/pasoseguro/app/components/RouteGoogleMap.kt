package com.pasoseguro.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberMarkerState

/** Punto de referencia mientras no se conoce la ubicación real del usuario. */
val LimaCityCenter: LatLng = LatLng(-12.0463731, -77.0311907)

/** Azul de marca de PasoSeguro para el marcador de ubicación actual. */
private val UserMarkerBlue = Color(0xFF1565C0)

/**
 * Mapa base reutilizado por la sección Ruta. Centraliza la configuración de
 * Google Maps Compose y el marcador de ubicación actual; el marcador de
 * destino y la polilínea (hoy simulada) se agregan desde el llamador a
 * través de [extraContent], sin duplicar esta configuración base.
 *
 * TODO(Fase 3): cuando se integre Google Routes API, [extraContent] sigue
 * siendo el punto de extensión — solo cambia el origen de los puntos.
 */
@Composable
fun RouteGoogleMap(
    cameraPositionState: CameraPositionState,
    userLocation: LatLng?,
    modifier: Modifier = Modifier,
    extraContent: @Composable @GoogleMapComposable () -> Unit = {},
) {
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
        ),
        contentDescription = "Mapa. Ubicación actual centrada en el mapa.",
    ) {
        if (userLocation != null) {
            MarkerComposable(
                userLocation.toString(),
                state = rememberMarkerState(key = userLocation.toString(), position = userLocation),
                contentDescription = "Tu ubicación actual",
                title = "Tu ubicación actual",
            ) {
                UserLocationMarkerIcon()
            }
        }
        extraContent()
    }
}

/** Marcador accesible de alto contraste (azul/blanco) para la ubicación actual. */
@Composable
private fun UserLocationMarkerIcon() {
    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(UserMarkerBlue),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
