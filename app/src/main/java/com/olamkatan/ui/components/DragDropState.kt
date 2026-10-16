package com.olamkatan.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

val LocalDragDropState = compositionLocalOf<DragDropState> { error("No DragDropState provided") }

/** A piece counts as dropped on a zone it covers at least this much of (as a share of the piece). */
private const val MIN_OVERLAP = 0.3f

class DragDropState {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragPosition by mutableStateOf(Offset.Zero)  // the dragged piece's center
    var draggedSize by mutableStateOf(Size.Zero)
    var isDragging by mutableStateOf(false)

    private val dropZones = mutableMapOf<String, Rect>()

    fun registerDropZone(id: String, bounds: Rect) {
        dropZones[id] = bounds
    }

    fun unregisterDropZone(id: String) {
        dropZones.remove(id)
    }

    /** Where to fly a tapped piece to. */
    fun zoneCenter(id: String): Offset? = dropZones[id]?.center

    fun findDropTarget(): String? = if (isDragging) zoneUnderPiece() else null

    fun getHoveredDropZone(): String? = zoneUnderPiece()

    /**
     * The zone under the piece's center; failing that, the zone it covers most, if it covers
     * enough of it (small hands rarely land a piece dead center).
     */
    private fun zoneUnderPiece(): String? {
        dropZones.entries.firstOrNull { (_, bounds) -> bounds.contains(dragPosition) }?.let { return it.key }
        if (draggedSize == Size.Zero) return null
        val piece = Rect(dragPosition - Offset(draggedSize.width / 2, draggedSize.height / 2), draggedSize)
        val area = piece.width * piece.height
        return dropZones.entries
            .map { (id, bounds) ->
                val o = piece.intersect(bounds)
                id to if (o.width > 0 && o.height > 0) o.width * o.height / area else 0f
            }
            .filter { it.second >= MIN_OVERLAP }
            .maxByOrNull { it.second }
            ?.first
    }

    fun reset() {
        draggedItemId = null
        dragPosition = Offset.Zero
        draggedSize = Size.Zero
        isDragging = false
    }
}
