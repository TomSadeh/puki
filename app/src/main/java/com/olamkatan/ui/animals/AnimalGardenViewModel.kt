package com.olamkatan.ui.animals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.AnimalDef
import com.olamkatan.data.content.FoodDef
import com.olamkatan.data.content.level1Animals
import com.olamkatan.data.content.level1Foods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnimalRoundState(
    val currentAnimal: AnimalDef = level1Animals[0],
    val foodChoices: List<FoodDef> = emptyList(),
    val roundIndex: Int = 0,
    val totalRounds: Int = 4,
    val roundComplete: Boolean = false,
    val sessionComplete: Boolean = false,
    val totalStarsEarned: Int = 0,
    val showSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class AnimalGardenViewModel(application: Application) : AndroidViewModel(application) {

    private val progressRepository = (application as OlamKatanApplication).progressRepository

    private val _state = MutableStateFlow(AnimalRoundState())
    val state: StateFlow<AnimalRoundState> = _state.asStateFlow()

    private var animals = level1Animals.shuffled().take(4)
    private var allFoods = level1Foods

    init {
        startRound(0)
    }

    private fun startRound(index: Int) {
        if (index >= animals.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            return
        }
        val animal = animals[index]
        val correctFood = allFoods.first { it.id == animal.foodId }
        val distractors = allFoods.filter { it.id != animal.foodId }.shuffled().take(2)
        val foodChoices = (listOf(correctFood) + distractors).shuffled()

        _state.value = AnimalRoundState(
            currentAnimal = animal,
            foodChoices = foodChoices,
            roundIndex = index,
            totalRounds = animals.size,
            totalStarsEarned = _state.value.totalStarsEarned,
            isLoading = false
        )
    }

    fun onFoodDropped(foodId: String): Boolean {
        val currentState = _state.value
        val isCorrect = foodId == currentState.currentAnimal.foodId
        if (isCorrect) {
            val newStars = currentState.totalStarsEarned + 1
            _state.value = currentState.copy(
                showSuccess = true,
                roundComplete = true,
                totalStarsEarned = newStars
            )
            viewModelScope.launch { progressRepository.addStars(1) }
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}
