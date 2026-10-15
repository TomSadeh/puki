package com.olamkatan.ui.colors

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.ColorDef
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
    private var rounds = allColors.shuffled().take(4)

    private val _state = MutableStateFlow(ColorRoundState())
    val state: StateFlow<ColorRoundState> = _state.asStateFlow()

    init {
        startRound(0)
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            return
        }
        val choiceCount = 4
        _state.value = ColorRoundState(
            targetColor = rounds[index],
            colorChoices = allColors.shuffled().take(choiceCount),
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    fun onColorTapped(colorId: String): Boolean {
        val isCorrect = colorId == _state.value.targetColor.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(showSuccess = true, totalStarsEarned = newStars)
            viewModelScope.launch { progressRepository.addStars(1) }
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}
