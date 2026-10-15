package com.olamkatan.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

val LocalDragDropState = compositionLocalOf<DragDropState> { error("No DragDropState provided") }

class DragDropState {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragPosition by mutableStateOf(Offset.Zero)
    var isDragging by mutableStateOf(false)

    private val dropZones = mutableMapOf<String, Rect>()

    fun registerDropZone(id: String, bounds: Rect) {
        dropZones[id] = bounds
    }

    fun unregisterDropZone(id: String) {
        dropZones.remove(id)
    }

    fun findDropTarget(): String? {
        if (!isDragging) return null
        return dropZones.entries.firstOrNull { (_, bounds) ->
            bounds.contains(dragPosition)
        }?.key
    }

    fun checkOverlap(threshold: Float = 0.4f): String? {
        if (!isDragging) return null
        return dropZones.entries.firstOrNull { (_, bounds) ->
            val expandedBounds = bounds.inflate(bounds.width * (1f - threshold))
            expandedBounds.contains(dragPosition)
        }?.key
    }

    fun getHoveredDropZone(): String? {
        return dropZones.entries.firstOrNull { (_, bounds) ->
            bounds.contains(dragPosition)
        }?.key
    }

    fun reset() {
        draggedItemId = null
        dragPosition = Offset.Zero
        isDragging = false
    }
}
