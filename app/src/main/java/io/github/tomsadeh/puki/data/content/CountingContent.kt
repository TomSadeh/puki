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


/** The words that count as saying [n] out loud (with the digit, as Whisper may write it). */
fun numberWords(n: Int, english: Boolean): List<String> {
    val words = if (english) NUMBERS_EN.getOrNull(n - 1).orEmpty() else NUMBERS_HE.getOrNull(n - 1).orEmpty()
    return listOf("$n") + words
}

private val NUMBERS_HE = listOf(
    listOf("אחת", "אחד"), listOf("שתיים", "שתים", "שניים", "שנים"), listOf("שלוש", "שלושה"),
    listOf("ארבע", "ארבעה"), listOf("חמש", "חמישה", "חמשה"), listOf("שש", "שישה", "ששה"),
    listOf("שבע", "שבעה"), listOf("שמונה"), listOf("תשע", "תשעה"), listOf("עשר", "עשרה")
)
private val NUMBERS_EN = listOf(
    listOf("one"), listOf("two"), listOf("three"), listOf("four"), listOf("five"),
    listOf("six"), listOf("seven"), listOf("eight"), listOf("nine"), listOf("ten")
)
