package com.olamkatan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** For grown-ups only: [onUnlocked] runs after a [holdDurationMs] hold, while a ring fills up. */
@Composable
fun LongPressGate(
    holdDurationMs: Long = 2000L,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val unlocked by rememberUpdatedState(onUnlocked)  // the latest callback, not the first one
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(100),
        label = "hold_progress"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holdJob = scope.launch {
                            val steps = 15
                            for (i in 1..steps) {
                                delay(holdDurationMs / steps)
                                holdProgress = i.toFloat() / steps
                            }
                            unlocked()
                            holdProgress = 0f
                        }
                        tryAwaitRelease()
                        holdJob?.cancel()
                        holdProgress = 0f
                    }
                )
            }
            .drawBehind {
                if (animatedProgress > 0f) {
                    drawArc(
                        color = Color(0xFF4CAF50),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(width = 4f)
                    )
                }
            }
    ) {
        content()
    }
}
