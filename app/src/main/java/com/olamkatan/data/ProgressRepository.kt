package com.olamkatan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.olamkatan.data.models.GameProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressRepository(private val context: Context) {

    private object Keys {
        val TOTAL_STARS = intPreferencesKey("total_stars")
        val STICKERS = stringSetPreferencesKey("stickers_earned")
        val UNLOCKED_ISLANDS = stringSetPreferencesKey("unlocked_islands")
    }

    private fun difficultyKey(island: String) = intPreferencesKey("difficulty_$island")

    val progress: Flow<GameProgress> = context.progressDataStore.data.map { prefs ->
        GameProgress(
            totalStars = prefs[Keys.TOTAL_STARS] ?: 0,
            stickersEarned = (prefs[Keys.STICKERS] ?: emptySet()).toList(),
            unlockedIslands = prefs[Keys.UNLOCKED_ISLANDS] ?: setOf("animals")
        )
    }

    suspend fun addStars(count: Int) {
        context.progressDataStore.edit { prefs ->
            val current = prefs[Keys.TOTAL_STARS] ?: 0
            prefs[Keys.TOTAL_STARS] = current + count
        }
    }

    suspend fun addSticker(stickerId: String) {
        context.progressDataStore.edit { prefs ->
            val current = prefs[Keys.STICKERS] ?: emptySet()
            prefs[Keys.STICKERS] = current + stickerId
        }
    }

    suspend fun unlockIsland(islandId: String) {
        context.progressDataStore.edit { prefs ->
            val current = prefs[Keys.UNLOCKED_ISLANDS] ?: setOf("animals")
            prefs[Keys.UNLOCKED_ISLANDS] = current + islandId
        }
    }

    suspend fun getDifficulty(island: String): Int {
        val prefs = context.progressDataStore.data.first()
        return prefs[difficultyKey(island)] ?: 1
    }

    suspend fun incrementDifficulty(island: String) {
        context.progressDataStore.edit { prefs ->
            val current = prefs[difficultyKey(island)] ?: 1
            prefs[difficultyKey(island)] = current + 1
        }
    }

    suspend fun checkAndUnlockIslands(): Set<String> {
        var newUnlocks = emptySet<String>()
        context.progressDataStore.edit { prefs ->
            val totalStars = prefs[Keys.TOTAL_STARS] ?: 0
            val unlocked = prefs[Keys.UNLOCKED_ISLANDS] ?: setOf("animals")
            val toUnlock = ProgressionManager.getUnlockableIslands(totalStars, unlocked)
            if (toUnlock.isNotEmpty()) {
                prefs[Keys.UNLOCKED_ISLANDS] = unlocked + toUnlock
                newUnlocks = toUnlock
            }
        }
        return newUnlocks
    }
}
