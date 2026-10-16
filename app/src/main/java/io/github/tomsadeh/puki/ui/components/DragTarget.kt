package io.github.tomsadeh.puki.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Something the child can drag onto a [DropZone]. With [tapToZone], a plain tap works too: the
 * piece flies to that zone by itself, as if dragged there (two-year-olds tap far more than they
 * drag). Right or wrong is still the game's call, through [onDragEnd].
 */
@Composable
fun DragTarget(
    itemId: String,
    modifier: Modifier = Modifier,
    onDragEnd: (dropTargetId: String?) -> Unit = {},
    onDragStart: () -> Unit = {},
    tapToZone: String? = null,
    content: @Composable () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    val scope = rememberCoroutineScope()
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var isDragging by remember { mutableStateOf(false) }
    // How far the finger has moved the piece; folded into [offsetAnim] when it lets go
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var pieceSize by remember { mutableStateOf(Size.Zero) }
    val flyTo by rememberUpdatedState(tapToZone)
    val dropped by rememberUpdatedState(onDragEnd)
    val started by rememberUpdatedState(onDragStart)
    val goHome: () -> Unit = {
        val delta = dragDelta
        scope.launch {
            offsetAnim.snapTo(offsetAnim.value + delta)
            dragDelta = Offset.Zero
            offsetAnim.animateTo(Offset.Zero, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                rootPosition = coords.positionInRoot()
                pieceSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            // The touch handling sits before the movement: measured on the part that stays put,
            // finger movement is exact (inside the moving part, each move would count for less)
            .pointerInput(itemId) {
                detectDragGestures(
                    onDragStart = {
                        started()
                        isDragging = true
                        dragDropState.draggedItemId = itemId
                        dragDropState.draggedSize = pieceSize
                        dragDropState.isDragging = true
                        scope.launch { offsetAnim.stop() }  // grabbed while flying home: stay put
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDelta += dragAmount
                        dragDropState.dragPosition = rootPosition + offsetAnim.value + dragDelta +
                            Offset(size.width / 2f, size.height / 2f)
                    },
                    onDragEnd = {
                        isDragging = false
                        val dropTarget = dragDropState.findDropTarget()
                        dragDropState.reset()
                        dropped(dropTarget)
                        goHome()
                    },
                    onDragCancel = {
                        isDragging = false
                        dragDropState.reset()
                        goHome()
                    }
                )
            }
            .pointerInput(itemId) {
                detectTapGestures(onTap = {
                    val zone = flyTo ?: return@detectTapGestures
                    val target = dragDropState.zoneCenter(zone) ?: return@detectTapGestures
                    if (isDragging || dragDropState.isDragging) return@detectTapGestures
                    val start = rootPosition + Offset(pieceSize.width / 2f, pieceSize.height / 2f)
                    started()
                    isDragging = true
                    dragDropState.draggedItemId = itemId
                    dragDropState.draggedSize = pieceSize
                    dragDropState.isDragging = true
                    scope.launch {
                        offsetAnim.animateTo(target - start, tween(450, easing = FastOutSlowInEasing))
                        dragDropState.dragPosition = target
                        val dropTarget = dragDropState.findDropTarget()
                        isDragging = false
                        dragDropState.reset()
                        dropped(dropTarget)
                        goHome()  // a wrong piece bounces back; a right one usually disappears first
                    }
                })
            }
            .absoluteOffset {
                val at = offsetAnim.value + dragDelta
                IntOffset(at.x.roundToInt(), at.y.roundToInt())
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
