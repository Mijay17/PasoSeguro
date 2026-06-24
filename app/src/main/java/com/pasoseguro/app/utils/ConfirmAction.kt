package com.pasoseguro.app.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── State ──────────────────────────────────────────────────────────────────
//
// Manages the two-tap confirmation pattern used for back buttons and carousel
// arrows throughout the app:
//   • 1st tap → vibrate + speak pendingMessage + arm (isPending = true)
//   • 2nd tap within timeoutMs → cancel timer + execute onConfirm
//   • Timeout → reset isPending without executing the action
//
// The mutable vars (pendingMessage, onSpeak, onHaptic, onConfirm) are written
// by rememberConfirmAction on every composition via SideEffect so that captures
// like `prefs.hapticEnabled` are always fresh even after settings changes.

class ConfirmActionState internal constructor(
    val timeoutMs: Long,
    private val scope: CoroutineScope,
) {
    var isPending by mutableStateOf(false)
        private set

    // Written by rememberConfirmAction on each composition — do not write externally.
    var pendingMessage: String       = ""
    var onSpeak:  (String) -> Unit   = {}
    var onHaptic: () -> Unit         = {}
    var onConfirm: () -> Unit        = {}

    private var job: Job? = null

    fun onTap() {
        if (isPending) {
            job?.cancel()
            isPending = false
            onConfirm()
        } else {
            onHaptic()
            onSpeak(pendingMessage)
            isPending = true
            job?.cancel()
            job = scope.launch {
                delay(timeoutMs)
                isPending = false
            }
        }
    }

    fun reset() {
        job?.cancel()
        isPending = false
    }
}

// ── Factory ────────────────────────────────────────────────────────────────

@Composable
fun rememberConfirmAction(
    pendingMessage: String,
    timeoutMs: Long = 3_000L,
    onSpeak:   (String) -> Unit,
    onHaptic:  () -> Unit,
    onConfirm: () -> Unit,
): ConfirmActionState {
    val scope = rememberCoroutineScope()
    val state = remember(timeoutMs) { ConfirmActionState(timeoutMs, scope) }
    // Keep lambdas fresh on every recomposition so closures like
    // { HapticHelper.vibrate(ctx, prefs.hapticEnabled) } always read current prefs.
    SideEffect {
        state.pendingMessage = pendingMessage
        state.onSpeak        = onSpeak
        state.onHaptic       = onHaptic
        state.onConfirm      = onConfirm
    }
    return state
}

// ── Progress bar ───────────────────────────────────────────────────────────
//
// Animates from full (1f) to empty (0f) over timeoutMs when first composed.
// Removing it from the composition (when isPending becomes false) cancels
// the animation automatically via Compose's structured concurrency.

@Composable
fun ConfirmProgressBar(timeoutMs: Long, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue   = 0f,
            animationSpec = tween(durationMillis = timeoutMs.toInt(), easing = LinearEasing),
        )
    }
    LinearProgressIndicator(
        progress   = { progress.value },
        modifier   = modifier,
        color      = color,
        trackColor = color.copy(alpha = 0.15f),
        strokeCap  = StrokeCap.Square,
    )
}
