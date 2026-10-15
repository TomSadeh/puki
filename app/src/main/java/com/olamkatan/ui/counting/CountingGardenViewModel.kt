package com.olamkatan.ui.counting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.CountingRound
import com.olamkatan.data.content.level1CountingRounds
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

    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private var rounds = level1CountingRounds.shuffled().take(4)

    private val _state = MutableStateFlow(CountingState())
    val state: StateFlow<CountingState> = _state.asStateFlow()

    init {
        startRound(0)
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
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
    fun onItemTapped(): Int {
        val current = _state.value
        val newCount = current.currentCount + 1
        val isComplete = newCount >= current.currentRound.targetNumber

        _state.value = current.copy(
            currentCount = newCount,
            roundComplete = isComplete,
            totalStarsEarned = if (isComplete) current.totalStarsEarned + 1 else current.totalStarsEarned
        )

        if (isComplete) {
            viewModelScope.launch { progressRepository.addStars(1) }
        }

        return if (isComplete) -1 else newCount
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}
