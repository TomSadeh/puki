package com.olamkatan.data.content

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText

data class CountingRound(
    val targetNumber: Int,
    val itemName: BilingualText,
    @DrawableRes val itemIcon: Int,
    val itemColor: Color
)

val level1CountingRounds = listOf(
    CountingRound(1, BilingualText("פרח", "flower"), R.drawable.ic_sticker_flower, Color(0xFFFFEE58)),
    CountingRound(2, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly, Color(0xFFAB47BC)),
    CountingRound(3, BilingualText("חיפושיות", "ladybugs"), R.drawable.ic_stamp_ladybug, Color(0xFFEF5350)),
    CountingRound(4, BilingualText("פרחים", "flowers"), R.drawable.ic_count_hibiscus, Color(0xFFEC407A))
)

val level2CountingRounds = listOf(
    CountingRound(2, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple, Color(0xFFEF5350)),
    CountingRound(3, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star, Color(0xFFFFD700)),
    CountingRound(4, BilingualText("לבבות", "hearts"), R.drawable.ic_sticker_heart, Color(0xFFE91E63)),
    CountingRound(5, BilingualText("דגים", "fish"), R.drawable.ic_food_fish, Color(0xFF42A5F5))
)

val level3CountingRounds = listOf(
    CountingRound(5, BilingualText("ציפורים", "birds"), R.drawable.ic_count_bird, Color(0xFF42A5F5)),
    CountingRound(6, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly, Color(0xFFAB47BC)),
    CountingRound(7, BilingualText("פרחים", "flowers"), R.drawable.ic_sticker_flower, Color(0xFFFFEE58)),
    CountingRound(8, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star, Color(0xFFFFD700)),
    CountingRound(9, BilingualText("לבבות", "hearts"), R.drawable.ic_count_heart_purple, Color(0xFF9C27B0)),
    CountingRound(10, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple_green, Color(0xFF66BB6A))
)

fun countingRoundsForLevel(level: Int): List<CountingRound> = when {
    level >= 3 -> level3CountingRounds
    level >= 2 -> level2CountingRounds
    else -> level1CountingRounds
}

