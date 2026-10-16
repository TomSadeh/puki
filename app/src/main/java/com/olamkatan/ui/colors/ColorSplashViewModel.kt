package com.olamkatan.ui.colors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.ColorDef
import com.olamkatan.data.content.colorsForLevel
import com.olamkatan.data.content.level1Colors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ColorRoundState(
    val targetColor: ColorDef = level1Colors[0],
    val colorChoices: List<ColorDef> = level1Colors,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val showSuccess: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
)

class ColorSplashViewModel(application: Application) : AndroidViewModel(application) {

    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private var allColors = level1Colors
    private var rounds = emptyList<ColorDef>()
    private var level = 1
    private var mistakes = 0

    private val _state = MutableStateFlow(ColorRoundState(isLoading = true))
    val state: StateFlow<ColorRoundState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            level = progressRepository.getDifficulty(ISLAND)
            allColors = colorsForLevel(level)  // later levels add colors to the first four
            rounds = allColors.shuffled().take(4)
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            viewModelScope.launch { progressRepository.recordSession(ISLAND, rounds.size, mistakes) }
            return
        }
        val target = rounds[index]
        val choiceCount = if (level == 1) 3 else 4
        _state.value = ColorRoundState(
            targetColor = target,
            colorChoices = (listOf(target) + allColors.filter { it.id != target.id }.shuffled().take(choiceCount - 1)).shuffled(),
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    /** True if right, false if not, null while the round is already won (a toddler's second tap). */
    fun onColorTapped(colorId: String): Boolean? {
        if (_state.value.showSuccess || _state.value.sessionComplete) return null
        val isCorrect = colorId == _state.value.targetColor.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(showSuccess = true, totalStarsEarned = newStars)
            viewModelScope.launch { progressRepository.addStars(1) }
        } else {
            mistakes++
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

private const val ISLAND = "colors"
