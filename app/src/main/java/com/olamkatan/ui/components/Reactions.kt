package com.olamkatan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Lets a game make a character or item react: a happy jump for a right answer,
 * munching when an animal eats, and a gentle side-to-side "nope" for a wrong one.
 * Attach with [Modifier.reaction] and trigger from a coroutine scope.
 */
class ReactionState {
    internal val lift = Animatable(0f)     // 0 = resting, 1 = top of the jump
    internal val squash = Animatable(1f)   // vertical scale, width compensates
    internal val sway = Animatable(0f)     // -1..1 sideways shake
    internal val tilt = Animatable(0f)     // degrees

    suspend fun celebrate() = coroutineScope {
        repeat(2) {
            squash.animateTo(0.82f, tween(90))
            launch { squash.animateTo(1.12f, tween(160)) }
            launch { tilt.animateTo(if (it == 0) -8f else 8f, tween(200)) }
            lift.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
            launch { squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
            lift.animateTo(0f, tween(220))
        }
        squash.animateTo(0.9f, tween(70))
        launch { tilt.animateTo(0f, spring(dampingRatio = 0.4f)) }
        squash.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 400f))
    }

    suspend fun munch() {
        repeat(3) {
            squash.animateTo(0.9f, tween(110))
            squash.animateTo(1.05f, tween(110))
        }
        squash.animateTo(1f, spring(dampingRatio = 0.4f))
    }

    suspend fun shake() {
        sway.animateTo(
            0f,
            keyframes {
                durationMillis = 480
                -1f at 60
                1f at 160
                -0.8f at 260
                0.6f at 350
                -0.3f at 420
            }
        )
    }
}

@Composable
fun rememberReactionState(vararg keys: Any?): ReactionState = remember(*keys) { ReactionState() }

/** Applies [state]'s jumps, squashes and shakes. Squashing pivots on the bottom edge, like standing on the ground. */
fun Modifier.reaction(state: ReactionState, jumpHeight: Dp = 48.dp, shakeDistance: Dp = 16.dp): Modifier =
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        translationY = -state.lift.value * jumpHeight.toPx()
        translationX = state.sway.value * shakeDistance.toPx()
        scaleY = state.squash.value
        scaleX = 2f - state.squash.value
        rotationZ = state.tilt.value + state.sway.value * 4f
    }

/** A slow, gentle breathing loop so characters look alive while waiting. */
fun Modifier.idleBreathing(periodMillis: Int = 2400): Modifier = composed {
    val breath by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        scaleY = 1f + 0.035f * breath
        scaleX = 1f - 0.015f * breath
    }
}
