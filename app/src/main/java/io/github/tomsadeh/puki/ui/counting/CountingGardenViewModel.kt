package io.github.tomsadeh.puki.ui.counting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.PukiApplication
import io.github.tomsadeh.puki.ui.common.GameSession
import io.github.tomsadeh.puki.data.content.CountingRound
import io.github.tomsadeh.puki.data.content.countingRoundsForLevel
import io.github.tomsadeh.puki.data.content.level1CountingRounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CountingState(
    val currentRound: CountingRound = level1CountingRounds[0],
    val currentCount: Int = 0,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val roundComplete: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
)

class CountingGardenViewModel(application: Application) : AndroidViewModel(application) {

    private val session = GameSession(ISLAND, (application as PukiApplication).progressRepository, viewModelScope)
    private var rounds = emptyList<CountingRound>()

    private val _state = MutableStateFlow(CountingState(isLoading = true))
    val state: StateFlow<CountingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val level = session.level()
            // Bigger numbers at higher levels, with one easier round from the level below
            rounds = if (level == 1) level1CountingRounds.shuffled().take(4)
                else (countingRoundsForLevel(level).shuffled().take(3) + countingRoundsForLevel(level - 1).shuffled().take(1)).shuffled()
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            session.finish(rounds.size)
            return
        }
        _state.value = CountingState(
            currentRound = rounds[index],
            currentCount = 0,
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /** Returns the new count (1-based), or -1 if round is complete. */
    /** The new count, -1 when that completed the round, or 0 if the round was already done. */
    fun onItemTapped(): Int {
        val current = _state.value
        if (current.roundComplete) return 0
        val newCount = current.currentCount + 1
        val isComplete = newCount >= current.currentRound.targetNumber

        _state.value = current.copy(
            currentCount = newCount,
            roundComplete = isComplete,
            totalStarsEarned = if (isComplete) current.totalStarsEarned + 1 else current.totalStarsEarned
        )

        if (isComplete) {
            session.star()
        }

        return if (isComplete) -1 else newCount
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

private const val ISLAND = "counting"
