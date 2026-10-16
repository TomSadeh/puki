package com.olamkatan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DragTarget(
    itemId: String,
    modifier: Modifier = Modifier,
    onDragEnd: (dropTargetId: String?) -> Unit = {},
    onDragStart: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    val scope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var isDragging by remember { mutableStateOf(false) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                rootPosition = coords.positionInRoot()
            }
            // The touch handling sits before the movement: measured on the part that stays put,
            // finger movement is exact (inside the moving part, each move would count for less)
            .pointerInput(itemId) {
                detectDragGestures(
                    onDragStart = {
                        onDragStart()
                        isDragging = true
                        dragDropState.draggedItemId = itemId
                        dragDropState.isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Where the item is going now (the snap below happens a frame later)
                        val next = offsetAnim.value + Offset(dragAmount.x, dragAmount.y)
                        scope.launch { offsetAnim.snapTo(next) }
                        dragDropState.dragPosition = rootPosition + next +
                            Offset(size.width / 2f, size.height / 2f)
                    },
                    onDragEnd = {
                        isDragging = false
                        val dropTarget = dragDropState.findDropTarget()
                        dragDropState.reset()
                        onDragEnd(dropTarget)
                        scope.launch {
                            offsetAnim.animateTo(
                                Offset.Zero,
                                animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f)
                            )
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        dragDropState.reset()
                        scope.launch {
                            offsetAnim.animateTo(
                                Offset.Zero,
                                animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f)
                            )
                        }
                    }
                )
            }
            .absoluteOffset {
                IntOffset(
                    offsetAnim.value.x.roundToInt(),
                    offsetAnim.value.y.roundToInt()
                )
            }
            // After the touch handling: inside a scaled layer, finger movement is measured scaled
            // down, and the item would fall behind the finger the farther it's dragged
            .graphicsLayer {
                // Lifted items just grow; a shadow here would be a square box around round things
                scaleX = if (isDragging) 1.12f else 1f
                scaleY = if (isDragging) 1.12f else 1f
            }
    ) {
        content()
    }
}
