package com.pasoseguro.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasoseguro.app.R

/**
 * Slide 0 of the carousel: logo from res/drawable/ic_logo.png, app name,
 * welcome text and swipe hint. Uses MaterialTheme colours for high-contrast support.
 */
@Composable
fun CarouselWelcomePage(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Logo from drawable resource
        Image(
            painter            = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Logo de PasoSeguro",
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.size(110.dp),
        )

        Spacer(Modifier.height(16.dp))

        // App name
        Text(
            text      = "PasoSeguro",
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.onSurface,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text      = "Navegación asistida con IA",
            style     = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text      = "Bienvenido a PasoSeguro",
            style     = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Tu asistente de navegación para movilidad segura e independiente.",
            style     = MaterialTheme.typography.bodyMedium.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Accessibility hint badge
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.secondary,
                modifier           = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "Compatible con lector de pantalla",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text      = "← Desliza para explorar las funciones →",
            style     = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
            textAlign = TextAlign.Center,
        )
    }
}
