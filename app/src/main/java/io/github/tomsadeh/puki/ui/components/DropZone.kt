package io.github.tomsadeh.puki.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

@Composable
fun DropZone(
    zoneId: String,
    modifier: Modifier = Modifier,
    highlightColor: Color = Color(0xFF4CAF50),
    content: @Composable () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    // Derived, so the zone recomposes when the piece enters or leaves it, not on every finger move
    val isHovered by remember(zoneId) {
        derivedStateOf { dragDropState.isDragging && dragDropState.getHoveredDropZone() == zoneId }
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.5f else 0f,
        animationSpec = tween(200),
        label = "glow"
    )

    DisposableEffect(zoneId) {
        onDispose {
            dragDropState.unregisterDropZone(zoneId)
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                dragDropState.registerDropZone(zoneId, coords.boundsInRoot())
            }
            .drawBehind {
                if (glowAlpha > 0f) {
                    // A soft round glow under whatever is hovering, no hard edges
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(highlightColor.copy(alpha = glowAlpha), Color.Transparent),
                            center = center, radius = size.maxDimension * 0.7f
                        ),
                        radius = size.maxDimension * 0.7f
                    )
                }
            }
    ) {
        content()
    }
}
