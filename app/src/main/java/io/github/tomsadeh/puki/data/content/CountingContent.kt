package io.github.tomsadeh.puki.data.content

import androidx.annotation.DrawableRes
import io.github.tomsadeh.puki.R
import io.github.tomsadeh.puki.data.models.BilingualText

data class CountingRound(
    val targetNumber: Int,
    val itemName: BilingualText,
    @DrawableRes val itemIcon: Int
)

val level1CountingRounds = listOf(
    CountingRound(1, BilingualText("פרח", "flower"), R.drawable.ic_sticker_flower),
    CountingRound(2, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly),
    CountingRound(3, BilingualText("חיפושיות", "ladybugs"), R.drawable.ic_stamp_ladybug),
    CountingRound(4, BilingualText("פרחים", "flowers"), R.drawable.ic_count_hibiscus)
)

val level2CountingRounds = listOf(
    CountingRound(2, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple),
    CountingRound(3, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star),
    CountingRound(4, BilingualText("לבבות", "hearts"), R.drawable.ic_sticker_heart),
    CountingRound(5, BilingualText("דגים", "fish"), R.drawable.ic_food_fish)
)

val level3CountingRounds = listOf(
    CountingRound(5, BilingualText("ציפורים", "birds"), R.drawable.ic_count_bird),
    CountingRound(6, BilingualText("פרפרים", "butterflies"), R.drawable.ic_sticker_butterfly),
    CountingRound(7, BilingualText("פרחים", "flowers"), R.drawable.ic_sticker_flower),
    CountingRound(8, BilingualText("כוכבים", "stars"), R.drawable.ic_sticker_star),
    CountingRound(9, BilingualText("לבבות", "hearts"), R.drawable.ic_count_heart_purple),
    CountingRound(10, BilingualText("תפוחים", "apples"), R.drawable.ic_count_apple_green)
)

fun countingRoundsForLevel(level: Int): List<CountingRound> = when {
    level >= 3 -> level3CountingRounds
    level >= 2 -> level2CountingRounds
    else -> level1CountingRounds
}

