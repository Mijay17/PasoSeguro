package com.pasoseguro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasoseguro.app.utils.LongPressConfig
import com.pasoseguro.app.utils.longPressActivation

/**
 * Large circular button for the top and bottom perimeter bars.
 *
 * When [longPressConfig] is null: standard click interaction (double-tap mode).
 * When [longPressConfig] is non-null: 2-second hold interaction (long-press mode).
 *
 * Minimum touch target: 54 dp (above the 48 dp a11y guideline).
 */
@Composable
fun PerimeterButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    container: Color,
    accessibilityLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    longPressConfig: LongPressConfig? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // In long-press mode, track held state for scale animation
    var isHeld by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (pressed || isHeld) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "perim_scale",
    )

    val gestureModifier = if (longPressConfig != null) {
        Modifier.longPressActivation(
            config = longPressConfig.copy(
                onHeldChange = { isHeld = it },
            ),
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication        = null,
            onClick           = onClick,
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = accessibilityLabel }
            .then(gestureModifier),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(container)
                .border(2.dp, tint.copy(alpha = 0.40f), CircleShape),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = tint,
                modifier           = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = tint,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
        )
    }
}
