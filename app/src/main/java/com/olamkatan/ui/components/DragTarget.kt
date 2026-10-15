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
            .absoluteOffset {
                IntOffset(
                    offsetAnim.value.x.roundToInt(),
                    offsetAnim.value.y.roundToInt()
                )
            }
            .graphicsLayer {
                scaleX = if (isDragging) 1.1f else 1f
                scaleY = if (isDragging) 1.1f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
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
                        scope.launch {
                            offsetAnim.snapTo(offsetAnim.value + Offset(dragAmount.x, dragAmount.y))
                        }
                        dragDropState.dragPosition = rootPosition + offsetAnim.value +
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
    ) {
        content()
    }
}
