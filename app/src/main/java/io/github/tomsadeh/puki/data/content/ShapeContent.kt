package io.github.tomsadeh.puki.data.content

import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.data.models.BilingualText

data class ShapeDef(
    val id: String,
    val name: BilingualText,
    val color: Color,
    val sides: Int
)

val level1Shapes = listOf(
    ShapeDef("circle", BilingualText("עיגול", "Circle"), Color(0xFFEF5350), 0),
    ShapeDef("square", BilingualText("ריבוע", "Square"), Color(0xFF42A5F5), 4),
    ShapeDef("triangle", BilingualText("משולש", "Triangle"), Color(0xFFFFEE58), 3),
    ShapeDef("star", BilingualText("כוכב", "Star"), Color(0xFFAB47BC), 5)
)

val level2Shapes = level1Shapes + listOf(
    ShapeDef("heart", BilingualText("לב", "Heart"), Color(0xFFE91E63), 0),
    ShapeDef("diamond", BilingualText("מעוין", "Diamond"), Color(0xFF00BCD4), 4),
    ShapeDef("hexagon", BilingualText("משושה", "Hexagon"), Color(0xFFFF9800), 6)
)

val level3Shapes = level2Shapes + listOf(
    ShapeDef("pentagon", BilingualText("מחומש", "Pentagon"), Color(0xFF4CAF50), 5),
    ShapeDef("octagon", BilingualText("מתומן", "Octagon"), Color(0xFF607D8B), 8)
)

fun shapesForLevel(level: Int): List<ShapeDef> = when {
    level >= 3 -> level3Shapes
    level >= 2 -> level2Shapes
    else -> level1Shapes
}
