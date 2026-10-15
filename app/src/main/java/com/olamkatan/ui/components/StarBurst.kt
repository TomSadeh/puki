package com.olamkatan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StarBurst(
    show: Boolean,
    origin: Offset,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!show) return

    val progress = remember { Animatable(0f) }

    LaunchedEffect(show) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(800))
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val p = progress.value
        val starCount = 5
        val starColor = Color(0xFFFFD700)

        for (i in 0 until starCount) {
            val angle = (i * 72f + p * 30f) * Math.PI.toFloat() / 180f
            val distance = p * 120f
            val starX = origin.x + cos(angle) * distance
            val starY = origin.y + sin(angle) * distance
            val alpha = (1f - p).coerceIn(0f, 1f)
            val starSize = 12f * (1f - p * 0.5f)

            drawPath(
                path = starPath(starX, starY, starSize),
                color = starColor.copy(alpha = alpha)
            )
        }
    }
}

private fun starPath(cx: Float, cy: Float, size: Float): Path {
    val path = Path()
    val outerRadius = size
    val innerRadius = size * 0.4f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (i * 36f - 90f) * Math.PI.toFloat() / 180f
        val x = cx + cos(angle) * radius
        val y = cy + sin(angle) * radius
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
