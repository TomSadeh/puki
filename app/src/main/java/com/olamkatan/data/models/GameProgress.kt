package com.olamkatan.data.models

data class GameProgress(
    val totalStars: Int = 0,
    val stickersEarned: List<String> = emptyList(),
    val difficultyLevels: Map<String, Int> = emptyMap(),
    val unlockedIslands: Set<String> = setOf("animals")
)
