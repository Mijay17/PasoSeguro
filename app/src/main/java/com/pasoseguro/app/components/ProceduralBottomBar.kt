package com.pasoseguro.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pasoseguro.app.ui.theme.TextOnDark
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.rememberConfirmAction

/**
 * One slot of [ProceduralBottomBar]: icon, label, the message spoken on the
 * first (arming) tap, and the action run once the second tap confirms it.
 */
data class BarAction(
    val icon: ImageVector,
    val label: String,
    val pendingMessage: String,
    val selected: Boolean = false,
    val onConfirm: () -> Unit,
)

/**
 * Floating bottom navigation bar shared by Contactos, Historial de Actividad
 * y Configuración. Fixed left / center / right layout so the position of each
 * action never changes between screens (procedural memory for low-vision users).
 * Every button uses the app-wide two-tap confirm pattern (see [rememberConfirmAction]).
 */
@Composable
fun ProceduralBottomBar(
    left: BarAction,
    center: BarAction,
    right: BarAction,
    accentColor: Color,
    onSpeak: (String) -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(left, center, right).forEach { action ->
                    ProceduralBarButton(
                        action = action,
                        accentColor = accentColor,
                        onSpeak = onSpeak,
                        hapticEnabled = hapticEnabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProceduralBarButton(
    action: BarAction,
    accentColor: Color,
    onSpeak: (String) -> Unit,
    hapticEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val confirm = rememberConfirmAction(
        pendingMessage = action.pendingMessage,
        onSpeak = onSpeak,
        onHaptic = { HapticHelper.vibrate(context, hapticEnabled) },
        onConfirm = action.onConfirm,
    )

    val scale by animateFloatAsState(
        targetValue = if (confirm.isPending) 0.90f else 1f,
        animationSpec = tween(200),
        label = "barButtonScale",
    )

    val background = when {
        confirm.isPending -> accentColor.copy(alpha = 0.75f)
        action.selected -> accentColor
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = if (confirm.isPending || action.selected) TextOnDark else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (action.selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    val description = buildString {
        append(action.label)
        if (action.selected) append(", activo")
        if (confirm.isPending) append(". Presiona nuevamente para confirmar.")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clickable(onClick = confirm::onTap)
            .semantics { contentDescription = description }
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(60.dp * scale)
                .shadow(if (confirm.isPending) 8.dp else 2.dp, CircleShape)
                .background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (action.selected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
