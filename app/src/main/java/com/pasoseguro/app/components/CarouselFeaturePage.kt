package com.pasoseguro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.theme.TextOnDark
import com.pasoseguro.app.utils.LongPressConfig
import com.pasoseguro.app.utils.longPressActivation

/**
 * A single feature slide inside the carousel (slides 1–6).
 *
 * When [longPressConfig] is null, the CTA button uses a standard click ([onOpenFeature]).
 * When [longPressConfig] is non-null, it uses the 2-second hold gesture instead.
 */
@Composable
fun CarouselFeaturePage(
    feature: Feature,
    onOpenFeature: () -> Unit,
    modifier: Modifier = Modifier,
    longPressConfig: LongPressConfig? = null,
) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Large icon circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(feature.container)
                .border(3.dp, feature.tint.copy(alpha = 0.30f), CircleShape),
        ) {
            Icon(
                imageVector        = feature.icon,
                contentDescription = null,
                tint               = feature.tint,
                modifier           = Modifier.size(50.dp),
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

        Spacer(Modifier.height(28.dp))

        FeatureCTAButton(
            feature          = feature,
            onClick          = onOpenFeature,
            longPressConfig  = longPressConfig,
        )
    }
}

// ── CTA button ────────────────────────────────────────────────────────────

@Composable
private fun FeatureCTAButton(
    feature: Feature,
    onClick: () -> Unit,
    longPressConfig: LongPressConfig?,
) {
    if (longPressConfig != null) {
        // In long-press mode: custom Box with the 2s gesture. Scale tracks held state.
        var isHeld by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue   = if (isHeld) 0.95f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label         = "cta_scale",
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .background(feature.tint)
                .longPressActivation(
                    config = longPressConfig.copy(onHeldChange = { isHeld = it }),
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = feature.icon,
                    contentDescription = null,
                    tint               = TextOnDark,
                    modifier           = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Abrir ${feature.label}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = TextOnDark,
                )
            }
        }
    } else {
        Button(
            onClick  = onClick,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = feature.tint,
                contentColor   = TextOnDark,
            ),
        ) {
            Icon(
                imageVector        = feature.icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Abrir ${feature.label}",
                fontWeight = FontWeight.SemiBold,
                fontSize   = 15.sp,
            )
        }
    }
}
