package com.pasoseguro.app.utils

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bundles callbacks for the 2-second long-press interaction mode.
 *
 * @param onMidpoint   Called at [midpointMs] while held (e.g., haptic pulse).
 * @param onComplete   Called at [holdMs] when the full hold time elapses.
 * @param onHeldChange Notifies whether the element is actively held, to drive animations.
 */
data class LongPressConfig(
    val onMidpoint: () -> Unit,
    val onComplete: () -> Unit,
    val onHeldChange: ((Boolean) -> Unit)? = null,
)

/**
 * Applies a custom 2-second long-press gesture to a composable.
 *
 * Uses [Modifier.composed] + [rememberUpdatedState] so that the underlying
 * [pointerInput] coroutine is keyed on [Unit] and NEVER restarts due to
 * recomposition (e.g., from scale animations), while always using the
 * latest callbacks.
 *
 * Timeline:
 *  0 ms          → touch down; [LongPressConfig.onHeldChange](true)
 *  [midpointMs]  → [LongPressConfig.onMidpoint]  (haptic pulse)
 *  [holdMs]      → [LongPressConfig.onComplete]   (TTS + navigate)
 *  release early → job cancelled; [LongPressConfig.onHeldChange](false)
 */
fun Modifier.longPressActivation(
    config: LongPressConfig,
    holdMs: Long = 2000L,
    midpointMs: Long = 1000L,
): Modifier = composed {
    val currentConfig by rememberUpdatedState(config)
    // rememberCoroutineScope() is the correct Compose way to get a scope for
    // launching work from a Modifier. Using `this as CoroutineScope` inside
    // pointerInput{} crashes because PointerInputScope does not implement
    // CoroutineScope in current Compose versions (ClassCastException at runtime).
    val scope = rememberCoroutineScope()

    pointerInput(Unit) {
        while (true) {
            awaitPointerEventScope {
                awaitFirstDown(requireUnconsumed = false)
            }

            var active = true
            currentConfig.onHeldChange?.invoke(true)

            val job = scope.launch {
                delay(midpointMs)
                if (active) currentConfig.onMidpoint()
                delay(holdMs - midpointMs)
                if (active) currentConfig.onComplete()
            }

            awaitPointerEventScope {
                waitForUpOrCancellation()
            }

            active = false
            currentConfig.onHeldChange?.invoke(false)
            job.cancel()
        }
    }
}
