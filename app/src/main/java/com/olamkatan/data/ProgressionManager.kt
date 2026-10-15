package com.olamkatan.data

import com.olamkatan.ui.hub.allIslands

object ProgressionManager {

    fun starsToUnlock(islandId: String): Int {
        return allIslands.find { it.id == islandId }?.starsToUnlock ?: Int.MAX_VALUE
    }

    fun getUnlockableIslands(
        currentStars: Int,
        alreadyUnlocked: Set<String>
    ): Set<String> {
        return allIslands
            .filter { it.starsToUnlock <= currentStars && it.id !in alreadyUnlocked }
            .map { it.id }
            .toSet()
    }

    fun shouldIncreaseDifficulty(consecutiveSuccesses: Int): Boolean {
        return consecutiveSuccesses >= 3
    }
}
