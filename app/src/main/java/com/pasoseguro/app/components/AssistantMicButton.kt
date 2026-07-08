package com.pasoseguro.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Botón de micrófono reutilizado como punto de entrada al Asistente IA en
 * las pantallas que exponen un TopAppBar (Contactos, Notificaciones,
 * Configuración, Ruta). Alto contraste: círculo relleno del color de acento
 * mientras está armado (1er toque), transparente con ícono tintado en reposo
 * — mismo patrón de doble pulsación que el resto de PasoSeguro
 * ([pending]/[onClick] normalmente vienen de un `ConfirmActionState`
 * devuelto por `rememberVoiceAssistantTrigger`).
 */
@Composable
fun AssistantMicButton(
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (pending) tint else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    if (pending)
                        "Confirmar: Asistente IA. Presiona nuevamente para comenzar a hablar."
                    else
                        "Asistente IA por voz. Doble toque para hablar."
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = Icons.Filled.Mic,
            contentDescription = null,
            tint               = if (pending) Color.White else tint,
            modifier           = Modifier.size(22.dp),
        )
    }
}
