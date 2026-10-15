package com.olamkatan.ui.hub

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.navigation.Routes
import com.olamkatan.theme.CoralPink
import com.olamkatan.theme.EarthBrown
import com.olamkatan.theme.GrassGreen
import com.olamkatan.theme.SoftPurple
import com.olamkatan.theme.SunYellow
import com.olamkatan.theme.WarmOrange

data class IslandDef(
    val id: String,
    val name: BilingualText,
    val route: String,
    val color: Color,
    val emoji: String,
    @DrawableRes val drawableRes: Int = 0,
    val starsToUnlock: Int,
    val fractionalX: Float,
    val fractionalY: Float
)

val allIslands = listOf(
    IslandDef(
        id = "animals",
        name = BilingualText("גן החיות", "Animal Garden"),
        route = Routes.ANIMALS,
        color = GrassGreen,
        emoji = "\uD83D\uDC04",
        drawableRes = R.drawable.ic_island_animals,
        starsToUnlock = 0,
        fractionalX = 0.14f,
        fractionalY = 0.50f
    ),
    IslandDef(
        id = "colors",
        name = BilingualText("צבעים", "Color Splash"),
        route = Routes.COLORS,
        color = CoralPink,
        emoji = "\uD83C\uDFA8",
        drawableRes = R.drawable.ic_island_colors,
        starsToUnlock = 4,
        fractionalX = 0.33f,
        fractionalY = 0.42f
    ),
    IslandDef(
        id = "shapes",
        name = BilingualText("צורות", "Shapes"),
        route = Routes.SHAPES,
        color = SoftPurple,
        emoji = "\uD83D\uDD37",
        drawableRes = R.drawable.ic_island_shapes,
        starsToUnlock = 10,
        fractionalX = 0.52f,
        fractionalY = 0.53f
    ),
    IslandDef(
        id = "counting",
        name = BilingualText("ספירה", "Counting"),
        route = Routes.COUNTING,
        color = SunYellow,
        emoji = "\uD83C\uDF3B",
        drawableRes = R.drawable.ic_island_counting,
        starsToUnlock = 18,
        fractionalX = 0.71f,
        fractionalY = 0.43f
    ),
    IslandDef(
        id = "music",
        name = BilingualText("מוזיקה", "Music"),
        route = Routes.MUSIC,
        color = WarmOrange,
        emoji = "\uD83C\uDFB5",
        drawableRes = R.drawable.ic_island_music,
        starsToUnlock = 40,
        fractionalX = 0.87f,
        fractionalY = 0.63f
    ),
    IslandDef(
        id = "words",
        name = BilingualText("מילים", "Words"),
        route = Routes.WORDS,
        color = CoralPink,
        emoji = "\uD83D\uDCD6",
        drawableRes = R.drawable.ic_island_words,
        starsToUnlock = 60,
        fractionalX = 0.66f,
        fractionalY = 0.80f
    ),
    IslandDef(
        id = "myday",
        name = BilingualText("היום שלי", "My Day"),
        route = Routes.MY_DAY,
        color = EarthBrown,
        emoji = "\uD83C\uDFE0",
        drawableRes = R.drawable.ic_island_myday,
        starsToUnlock = 25,
        fractionalX = 0.37f,
        fractionalY = 0.80f
    )
)
