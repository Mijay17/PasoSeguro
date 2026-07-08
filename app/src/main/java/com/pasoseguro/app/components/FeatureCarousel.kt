package com.pasoseguro.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.theme.AlertRed
import com.pasoseguro.app.ui.theme.Brand800
import com.pasoseguro.app.utils.ConfirmProgressBar
import com.pasoseguro.app.utils.LongPressConfig
import com.pasoseguro.app.utils.rememberConfirmAction
import kotlinx.coroutines.launch

/**
 * Infinite horizontal pager (7 logical slides):
 *  • Slide 0     → CarouselWelcomePage
 *  • Slides 1–6  → CarouselFeaturePage for each Feature
 *
 * Arrow buttons use a two-tap confirmation pattern for accessibility:
 *   1st tap → vibrate + TTS announcement ("Siguiente opción. Presiona de nuevo.")
 *   2nd tap → execute navigation
 *   Timeout (3 s) → cancel and reset
 */
@Composable
fun FeatureCarousel(
    pagerState: PagerState,
    onFeatureTap: (Feature) -> Unit,
    modifier: Modifier = Modifier,
    longPressConfigFor: (Feature) -> LongPressConfig? = { null },
    onSpeak: (String) -> Unit = {},
    onHaptic: () -> Unit = {},
    assistantPending: Boolean = false,
    onAssistantTap: () -> Unit = {},
) {
    val features        = Feature.entries
    val actualPageCount = features.size + 1          // 7 logical slides
    val scope           = rememberCoroutineScope()

    fun goTo(page: Int) { scope.launch { pagerState.animateScrollToPage(page) } }

    // Two-tap confirm states for left/right arrows.
    // Each tap first resets the OTHER arrow to avoid simultaneous pending state.
    val confirmLeft = rememberConfirmAction(
        pendingMessage = "Opción anterior. Presiona nuevamente para confirmar.",
        onSpeak        = onSpeak,
        onHaptic       = onHaptic,
        onConfirm      = { goTo(pagerState.currentPage - 1) },
    )
    val confirmRight = rememberConfirmAction(
        pendingMessage = "Siguiente opción. Presiona nuevamente para confirmar.",
        onSpeak        = onSpeak,
        onHaptic       = onHaptic,
        onConfirm      = { goTo(pagerState.currentPage + 1) },
    )

    fun handleLeftTap()  { confirmRight.reset(); confirmLeft.onTap() }
    fun handleRightTap() { confirmLeft.reset();  confirmRight.onTap() }

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        // ── Pager + arrow overlay ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Carrusel de funciones. Desliza o usa las flechas para explorar." },
            ) { absolutePage ->
                val page = absolutePage % actualPageCount
                if (page == 0) {
                    CarouselWelcomePage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        assistantPending = assistantPending,
                        onAssistantTap   = onAssistantTap,
                    )
                } else {
                    val feature = features[page - 1]
                    CarouselFeaturePage(
                        feature         = feature,
                        onOpenFeature   = { onFeatureTap(feature) },
                        longPressConfig = longPressConfigFor(feature),
                        modifier        = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                    )
                }
            }

            // Countdown strip at the bottom of the pager whenever an arrow is pending
            if (confirmLeft.isPending || confirmRight.isPending) {
                ConfirmProgressBar(
                    timeoutMs = 3_000L,
                    color     = AlertRed,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                )
            }

            // Left arrow — two-tap confirm, decrement absolute index
            CarouselArrowButton(
                icon        = Icons.Filled.KeyboardArrowLeft,
                contentDesc = "Opción anterior",
                isPending   = confirmLeft.isPending,
                onClick     = ::handleLeftTap,
                modifier    = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp),
            )

            // Right arrow — two-tap confirm, increment absolute index
            CarouselArrowButton(
                icon        = Icons.Filled.KeyboardArrowRight,
                contentDesc = "Siguiente opción",
                isPending   = confirmRight.isPending,
                onClick     = ::handleRightTap,
                modifier    = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Dots reflect the logical slide (0–6), not the absolute index
        PagerDots(
            pageCount   = actualPageCount,
            currentPage = pagerState.currentPage % actualPageCount,
        )
    }
}

// ── Arrow button ───────────────────────────────────────────────────────────

@Composable
private fun CarouselArrowButton(
    icon: ImageVector,
    contentDesc: String,
    isPending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick         = onClick,
        modifier        = modifier
            .size(52.dp)
            .semantics {
                contentDescription = if (isPending)
                    "Confirmar: $contentDesc. Presiona de nuevo."
                else
                    contentDesc
            },
        shape           = CircleShape,
        color           = if (isPending) AlertRed.copy(alpha = 0.10f)
                          else           MaterialTheme.colorScheme.surface,
        border          = if (isPending) BorderStroke(1.5.dp, AlertRed.copy(alpha = 0.65f))
                          else           null,
        shadowElevation = 6.dp,
        tonalElevation  = if (isPending) 0.dp else 2.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(36.dp),
                tint               = if (isPending) AlertRed
                                     else           MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Page indicator dots ────────────────────────────────────────────────────

@Composable
private fun PagerDots(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue   = if (selected) 22.dp else 8.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label         = "dot_w",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) Brand800 else Color(0xFFBBCCE0)),
            )
        }
    }
}
