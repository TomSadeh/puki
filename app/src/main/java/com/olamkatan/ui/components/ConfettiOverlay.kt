package com.olamkatan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float,
    var size: Float,
    var color: Color
)

private val confettiColors = listOf(
    Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
    Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA),
    Color(0xFFFCBF49), Color(0xFF2EC4B6)
)

@Composable
fun ConfettiOverlay(
    show: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!show) return

    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var frameTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(show) {
        // Initialize particles
        particles = List(60) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2
            val speed = Random.nextFloat() * 800f + 200f
            Particle(
                x = 0.5f,
                y = 0.3f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 400f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 360f - 180f,
                alpha = 1f,
                size = Random.nextFloat() * 12f + 6f,
                color = confettiColors.random()
            )
        }

        val startTime = System.nanoTime()
        var lastFrame = startTime

        while (true) {
            awaitFrame()
            val now = System.nanoTime()
            val dt = (now - lastFrame) / 1_000_000_000f
            lastFrame = now
            val elapsed = (now - startTime) / 1_000_000_000f

            if (elapsed > 2.5f) {
                onFinished()
                break
            }

            particles = particles.map { p ->
                p.copy(
                    x = p.x + p.vx * dt / 1000f,
                    y = p.y + p.vy * dt / 1000f,
                    vy = p.vy + 600f * dt,
                    rotation = p.rotation + p.rotationSpeed * dt,
                    alpha = (1f - (elapsed / 2.5f)).coerceIn(0f, 1f)
                )
            }
            frameTime = now
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            if (p.alpha > 0.01f) {
                rotate(p.rotation, pivot = Offset(p.x * w, p.y * h)) {
                    drawRect(
                        color = p.color.copy(alpha = p.alpha),
                        topLeft = Offset(p.x * w - p.size / 2, p.y * h - p.size / 2),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
