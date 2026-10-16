package com.olamkatan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.olamkatan.data.models.GameProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

class ProgressRepository(private val context: Context) {

    companion object {
        const val MAX_LEVEL = 3
    }

    private object Keys {
        val TOTAL_STARS = intPreferencesKey("total_stars")
        val STICKERS = stringSetPreferencesKey("stickers_earned")
    }

    private fun difficultyKey(island: String) = intPreferencesKey("difficulty_$island")
    private fun streakKey(island: String) = intPreferencesKey("clean_streak_$island")

    val progress: Flow<GameProgress> = context.progressDataStore.data.map { prefs ->
        GameProgress(
            totalStars = prefs[Keys.TOTAL_STARS] ?: 0,
            stickersEarned = (prefs[Keys.STICKERS] ?: emptySet()).toList()
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


    /** A game's level, 1 (easiest) to [MAX_LEVEL]. */
    suspend fun getDifficulty(island: String): Int {
        val prefs = context.progressDataStore.data.first()
        return (prefs[difficultyKey(island)] ?: 1).coerceIn(1, MAX_LEVEL)
    }

    /**
     * After a session of [rounds] rounds with [mistakes] wrong answers: two easy sessions in a
     * row move the game up a level, and a hard one moves it back down, so it grows with the
     * child without getting stuck too hard. Returns the new level.
     */
    suspend fun recordSession(island: String, rounds: Int, mistakes: Int): Int {
        var level = 1
        context.progressDataStore.edit { prefs ->
            level = (prefs[difficultyKey(island)] ?: 1).coerceIn(1, MAX_LEVEL)
            var streak = prefs[streakKey(island)] ?: 0
            when {
                mistakes <= rounds / 2 -> {  // easy: at most one slip every other round
                    streak++
                    if (streak >= 2 && level < MAX_LEVEL) { level++; streak = 0 }
                }
                mistakes >= rounds * 2 -> { streak = 0; if (level > 1) level-- }  // two misses a round
                else -> streak = 0
            }
            prefs[difficultyKey(island)] = level
            prefs[streakKey(island)] = streak
        }
        return level
    }

}
