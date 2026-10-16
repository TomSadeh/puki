package io.github.tomsadeh.puki.data.content

import androidx.compose.ui.graphics.Color
import io.github.tomsadeh.puki.data.models.BilingualText

data class ColorDef(
    val id: String,
    val name: BilingualText,
    val color: Color
)

val level1Colors = listOf(
    ColorDef("red", BilingualText("אדום", "Red"), Color(0xFFEF5350)),
    ColorDef("blue", BilingualText("כחול", "Blue"), Color(0xFF42A5F5)),
    ColorDef("yellow", BilingualText("צהוב", "Yellow"), Color(0xFFFFEE58)),
    ColorDef("green", BilingualText("ירוק", "Green"), Color(0xFF66BB6A))
)

val level2Colors = level1Colors + listOf(
    ColorDef("orange", BilingualText("כתום", "Orange"), Color(0xFFFF9800)),
    ColorDef("purple", BilingualText("סגול", "Purple"), Color(0xFF9C27B0))
)

val level3Colors = level2Colors + listOf(
    ColorDef("pink", BilingualText("ורוד", "Pink"), Color(0xFFE91E63)),
    ColorDef("brown", BilingualText("חום", "Brown"), Color(0xFF795548)),
    ColorDef("white", BilingualText("לבן", "White"), Color(0xFFFAFAFA)),
    ColorDef("black", BilingualText("שחור", "Black"), Color(0xFF424242))
)

fun colorsForLevel(level: Int): List<ColorDef> = when {
    level >= 3 -> level3Colors
    level >= 2 -> level2Colors
    else -> level1Colors
}
