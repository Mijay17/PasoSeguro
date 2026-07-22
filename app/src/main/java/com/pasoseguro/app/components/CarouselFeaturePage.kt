package com.pasoseguro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.utils.LongPressConfig
import com.pasoseguro.app.utils.longPressActivation

/**
 * A single feature slide inside the carousel (slides 1–6).
 *
 * Toda la tarjeta es un único botón grande — ya no hay un botón "Abrir X"
 * separado en el centro. Cualquier toque (o, en modo pulsación prolongada,
 * mantener presionado 2 s) en cualquier parte de la tarjeta abre la función.
 * El área de toque real es exactamente la misma zona doble-toque de siempre
 * ([onOpenFeature] sigue siendo el mismo handler armado/confirmado por
 * [com.pasoseguro.app.utils.DoubleTapHandler]) — solo cambió qué tan grande
 * es la región que lo dispara.
 */
@Composable
fun CarouselFeaturePage(
    feature: Feature,
    onOpenFeature: () -> Unit,
    modifier: Modifier = Modifier,
    longPressConfig: LongPressConfig? = null,
) {
    var isHeld by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (isHeld) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "card_scale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (longPressConfig != null) {
                    Modifier.longPressActivation(config = longPressConfig.copy(onHeldChange = { isHeld = it }))
                } else {
                    Modifier.clickable(onClick = onOpenFeature)
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${feature.label}. ${feature.description}"
            }
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icono grande — se agrandó al liberar el espacio que antes ocupaba
        // el botón CTA independiente.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(feature.container)
                .border(3.dp, feature.tint.copy(alpha = 0.30f), CircleShape),
        ) {
            Icon(
                imageVector        = feature.icon,
                contentDescription = null,
                tint               = feature.tint,
                modifier           = Modifier.size(68.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text      = feature.label,
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text      = feature.description,
            style     = MaterialTheme.typography.bodyLarge.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 26.sp,
            ),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        // Pista visual no interactiva — indica que toda la tarjeta es
        // tocable, ya que el botón "Abrir X" independiente se eliminó.
        Text(
            text       = if (longPressConfig != null) "Mantén presionado para abrir" else "Toca dos veces para abrir",
            style      = MaterialTheme.typography.labelLarge.copy(
                color      = feature.tint,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
