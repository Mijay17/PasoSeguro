package com.pasoseguro.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Programmatic version of the PasoSeguro logo (running figure with cane).
 * Replace with Image(painterResource(R.drawable.ic_logo)) once the real
 * asset is placed in res/drawable/.
 */
@Composable
fun PasoSeguroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    primary: Color = Color(0xFF1A3A6B),
    accent: Color  = Color(0xFF2D5FA6),
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // ── Head ──────────────────────────────────────────────────────────
        drawCircle(color = primary, radius = w * 0.085f,
            center = Offset(w * 0.58f, h * 0.13f))

        // ── Body ──────────────────────────────────────────────────────────
        val body = Path().apply {
            moveTo(w * 0.58f, h * 0.23f)
            cubicTo(w * 0.61f, h * 0.34f, w * 0.67f, h * 0.41f, w * 0.72f, h * 0.49f)
            lineTo(w * 0.63f, h * 0.49f)
            cubicTo(w * 0.57f, h * 0.41f, w * 0.53f, h * 0.33f, w * 0.53f, h * 0.23f)
            close()
        }
        drawPath(body, primary)

        // ── Left arm (forward / cane side) ────────────────────────────────
        val arm = Path().apply {
            moveTo(w * 0.56f, h * 0.27f)
            cubicTo(w * 0.46f, h * 0.25f, w * 0.34f, h * 0.24f, w * 0.26f, h * 0.32f)
            lineTo(w * 0.29f, h * 0.37f)
            cubicTo(w * 0.36f, h * 0.30f, w * 0.46f, h * 0.31f, w * 0.56f, h * 0.33f)
            close()
        }
        drawPath(arm, accent)

        // ── Right leg (forward) ───────────────────────────────────────────
        val legR = Path().apply {
            moveTo(w * 0.65f, h * 0.50f)
            cubicTo(w * 0.67f, h * 0.62f, w * 0.63f, h * 0.74f, w * 0.57f, h * 0.86f)
            lineTo(w * 0.52f, h * 0.84f)
            cubicTo(w * 0.58f, h * 0.72f, w * 0.62f, h * 0.60f, w * 0.60f, h * 0.50f)
            close()
        }
        drawPath(legR, primary)

        // ── Left leg (back / trailing) ─────────────────────────────────────
        val legL = Path().apply {
            moveTo(w * 0.52f, h * 0.51f)
            cubicTo(w * 0.44f, h * 0.62f, w * 0.38f, h * 0.73f, w * 0.33f, h * 0.85f)
            lineTo(w * 0.38f, h * 0.88f)
            cubicTo(w * 0.43f, h * 0.76f, w * 0.49f, h * 0.65f, w * 0.57f, h * 0.52f)
            close()
        }
        drawPath(legL, accent)

        // ── Cane (guide loop) ─────────────────────────────────────────────
        val cane = Path().apply {
            moveTo(w * 0.26f, h * 0.32f)
            cubicTo(w * 0.13f, h * 0.42f, w * 0.10f, h * 0.60f, w * 0.17f, h * 0.73f)
            cubicTo(w * 0.23f, h * 0.82f, w * 0.33f, h * 0.84f, w * 0.40f, h * 0.79f)
        }
        drawPath(
            path = cane,
            color = primary,
            style = Stroke(width = w * 0.042f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
