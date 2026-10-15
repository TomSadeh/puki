package com.olamkatan.ui.hub.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin

/** Fraction of the screen height where the sky meets the sea. */
const val HORIZON = 0.30f

@Composable
fun HubBackground() {
    val transition = rememberInfiniteTransition(label = "hub_bg")
    val cloudOffset by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(70000, easing = LinearEasing), RepeatMode.Restart),
        label = "cloud_drift"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase"
    )
    val sunSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart),
        label = "sun_spin"
    )
    val boatX by transition.animateFloat(
        initialValue = 1.1f,
        targetValue = -0.1f,
        animationSpec = infiniteRepeatable(tween(90000, easing = LinearEasing), RepeatMode.Restart),
        label = "boat"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * HORIZON

        // Sky
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF64C8F5), Color(0xFFB3E5FC)),
                endY = horizon
            ),
            size = Size(w, horizon + 2f)
        )

        drawSun(Offset(w * 0.80f, h * 0.13f), h * 0.065f, sunSpin)

        drawCloud(w * cloudOffset, h * 0.10f, w * 0.045f)
        drawCloud(w * ((cloudOffset + 0.55f) % 1.3f - 0.15f), h * 0.19f, w * 0.035f)

        // Faraway islands on the horizon
        drawFarIsland(w * 0.08f, horizon, w * 0.07f, h * 0.025f)
        drawFarIsland(w * 0.58f, horizon, w * 0.05f, h * 0.018f)

        // Sea
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF4FC3F7), Color(0xFF29B6F6), Color(0xFF0397D6)),
                startY = horizon,
                endY = h
            ),
            topLeft = Offset(0f, horizon),
            size = Size(w, h - horizon)
        )
        // Bright line where sky meets sea
        drawRect(Color(0x66FFFFFF), Offset(0f, horizon), Size(w, h * 0.006f))

        drawBoat(w * boatX, horizon + h * 0.035f, h * 0.035f, wavePhase)

        // Little wave arcs drifting sideways, bigger towards the viewer
        val rows = 7
        for (row in 0 until rows) {
            val t = (row + 0.5f) / rows
            val y = horizon + (h - horizon) * t
            val waveW = w * (0.018f + 0.022f * t)
            val gap = waveW * 5f
            val drift = (wavePhase + row * 0.37f) % 1f * gap * (if (row % 2 == 0) 1 else -1)
            val bob = sin((wavePhase + row * 0.2f) * 2 * PI).toFloat() * h * 0.003f
            var x = -2 * gap + drift + (row * 0.43f % 1f) * gap
            while (x < w + gap) {
                drawWave(x, y + bob, waveW, Color.White.copy(alpha = 0.18f + 0.22f * t))
                x += gap
            }
        }

        // Twinkling sparkles on the water
        val sparkles = listOf(0.15f to 0.38f, 0.45f to 0.34f, 0.9f to 0.45f, 0.25f to 0.66f, 0.8f to 0.9f, 0.55f to 0.95f)
        sparkles.forEachIndexed { i, (sx, sy) ->
            val a = (sin((wavePhase + i * 0.29f) * 2 * PI).toFloat() + 1f) / 2f
            drawCircle(Color.White.copy(alpha = 0.7f * a), radius = h * 0.005f * (0.5f + a), center = Offset(w * sx, h * sy))
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, spin: Float) {
    drawCircle(Color(0x33FFE082), radius * 1.9f, center)
    rotate(spin, center) {
        for (i in 0 until 12) {
            rotate(i * 30f, center) {
                drawLine(
                    Color(0xFFFFCA28),
                    Offset(center.x, center.y - radius * 1.25f),
                    Offset(center.x, center.y - radius * 1.6f),
                    strokeWidth = radius * 0.16f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
    drawCircle(Color(0xFFFFD54F), radius, center)
    drawCircle(Color(0xFFFFE082), radius * 0.65f, center + Offset(-radius * 0.15f, -radius * 0.15f))
}

private fun DrawScope.drawCloud(x: Float, y: Float, r: Float) {
    val c = Color(0xEEFFFFFF)
    drawCircle(c, r, Offset(x, y))
    drawCircle(c, r * 0.8f, Offset(x - r * 0.9f, y + r * 0.25f))
    drawCircle(c, r * 0.75f, Offset(x + r * 0.95f, y + r * 0.25f))
    drawRoundRect(
        c,
        topLeft = Offset(x - r * 1.6f, y + r * 0.2f),
        size = Size(r * 3.2f, r * 0.8f),
        cornerRadius = CornerRadius(r * 0.4f)
    )
}

private fun DrawScope.drawFarIsland(cx: Float, horizon: Float, halfW: Float, height: Float) {
    val p = Path().apply {
        moveTo(cx - halfW, horizon + 1f)
        cubicTo(cx - halfW * 0.6f, horizon - height, cx + halfW * 0.4f, horizon - height * 1.2f, cx + halfW, horizon + 1f)
        close()
    }
    drawPath(p, Color(0xFF7CB9A8))
}

private fun DrawScope.drawBoat(x: Float, y: Float, s: Float, phase: Float) {
    val rock = sin(phase * 2 * PI).toFloat() * 4f
    rotate(rock, Offset(x, y)) {
        val hull = Path().apply {
            moveTo(x - s * 1.1f, y)
            lineTo(x + s * 1.1f, y)
            lineTo(x + s * 0.75f, y + s * 0.45f)
            lineTo(x - s * 0.75f, y + s * 0.45f)
            close()
        }
        drawPath(hull, Color(0xFFE57373))
        drawLine(Color(0xFF6D4C41), Offset(x, y), Offset(x, y - s * 1.6f), strokeWidth = s * 0.08f)
        val sail = Path().apply {
            moveTo(x + s * 0.06f, y - s * 1.55f)
            lineTo(x + s * 0.9f, y - s * 0.15f)
            lineTo(x + s * 0.06f, y - s * 0.15f)
            close()
        }
        drawPath(sail, Color.White)
        val jib = Path().apply {
            moveTo(x - s * 0.06f, y - s * 1.3f)
            lineTo(x - s * 0.7f, y - s * 0.15f)
            lineTo(x - s * 0.06f, y - s * 0.15f)
            close()
        }
        drawPath(jib, Color(0xFFFFF59D))
    }
}

private fun DrawScope.drawWave(x: Float, y: Float, width: Float, color: Color) {
    val p = Path().apply {
        moveTo(x, y)
        quadraticBezierTo(x + width * 0.25f, y - width * 0.3f, x + width * 0.5f, y)
        quadraticBezierTo(x + width * 0.75f, y - width * 0.3f, x + width, y)
    }
    drawPath(p, color, style = Stroke(width = width * 0.12f, cap = StrokeCap.Round))
}
