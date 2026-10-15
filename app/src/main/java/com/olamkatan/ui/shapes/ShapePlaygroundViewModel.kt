package com.olamkatan.ui.shapes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.ShapeDef
import com.olamkatan.data.content.level1Shapes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShapeRoundState(
    val targetShape: ShapeDef = level1Shapes[0],
    val shapeChoices: List<ShapeDef> = level1Shapes,
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val roundComplete: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val isLoading: Boolean = false
)

class ShapePlaygroundViewModel(application: Application) : AndroidViewModel(application) {

    private val progressRepository = (application as OlamKatanApplication).progressRepository
    private var allShapes = level1Shapes
    private var rounds = allShapes.shuffled().take(4)

    private val _state = MutableStateFlow(ShapeRoundState())
    val state: StateFlow<ShapeRoundState> = _state.asStateFlow()

    init {
        startRound(0)
    }

    private fun startRound(index: Int) {
        if (index >= rounds.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            return
        }
        _state.value = ShapeRoundState(
            targetShape = rounds[index],
            shapeChoices = allShapes.shuffled().take(4),
            roundIndex = index,
            totalRounds = rounds.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    fun onShapeDropped(shapeId: String): Boolean {
        val isCorrect = shapeId == _state.value.targetShape.id
        if (isCorrect) {
            val newStars = _state.value.totalStarsEarned + 1
            _state.value = _state.value.copy(roundComplete = true, totalStarsEarned = newStars)
            viewModelScope.launch { progressRepository.addStars(1) }
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}
