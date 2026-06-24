package com.pasoseguro.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pasoseguro.app.ui.theme.*

/**
 * Single source of truth for every feature in PasoSeguro.
 * Both the perimeter buttons and the carousel read from this list.
 */
enum class Feature(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
    val contentDescription: String,
    /** Text spoken aloud by TTS when the user taps this feature. */
    val ttsText: String,
) {
    NAVIGATE(
        route              = "navigate",
        label              = "Navegar",
        description        = "Guía de voz paso a paso para desplazarte con seguridad.",
        icon               = Icons.Filled.NearMe,
        tint               = NavBlue,
        container          = NavBlueLight,
        contentDescription = "Botón de navegación asistida",
        ttsText            = "Navegar",
    ),
    SCAN(
        route              = "scan",
        label              = "Escanear",
        description        = "Analiza el entorno a tu alrededor usando la cámara del dispositivo.",
        icon               = Icons.Filled.DocumentScanner,
        tint               = ScanTeal,
        container          = ScanTealLight,
        contentDescription = "Botón de escaneo del entorno",
        ttsText            = "Escanear",
    ),
    ROUTE(
        route              = "route",
        label              = "Ruta",
        description        = "Selecciona un destino guardado e inicia la navegación guiada.",
        icon               = Icons.Filled.MyLocation,
        tint               = RouteAmber,
        container          = RouteAmberLight,
        contentDescription = "Botón de gestión de rutas",
        ttsText            = "Ruta",
    ),
    CONTACTS(
        route              = "contacts",
        label              = "Contactos",
        description        = "Personas de confianza que reciben tus alertas de emergencia.",
        icon               = Icons.Filled.Contacts,
        tint               = ContactGreen,
        container          = ContactGreen50,
        contentDescription = "Botón de contactos de confianza",
        ttsText            = "Contactos",
    ),
    ALERTS(
        route              = "alerts",
        label              = "Notificaciones",
        description        = "Detecta y alerta sobre obstáculos en tiempo real mediante la cámara.",
        icon               = Icons.Filled.NotificationsActive,
        tint               = AlertRed,
        container          = AlertRedLight,
        contentDescription = "Botón de notificaciones y alertas de obstáculos",
        ttsText            = "Notificaciones",
    ),
    CONFIG(
        route              = "config",
        label              = "Configuración",
        description        = "Ajusta el modo de alerta, preferencias de voz y vibración.",
        icon               = Icons.Filled.Tune,
        tint               = ConfigSlate,
        container          = ConfigSlate50,
        contentDescription = "Botón de configuración",
        ttsText            = "Configuración",
    ),
}
