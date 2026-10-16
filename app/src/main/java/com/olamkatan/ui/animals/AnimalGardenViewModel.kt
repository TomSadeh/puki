package com.olamkatan.ui.animals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.content.AnimalDef
import com.olamkatan.data.content.FoodDef
import com.olamkatan.data.content.level1Animals
import com.olamkatan.data.content.level1Foods
import com.olamkatan.data.content.level2Animals
import com.olamkatan.data.content.level3Animals
import com.olamkatan.data.content.foodsForLevel
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

    private val _state = MutableStateFlow(AnimalRoundState(isLoading = true))
    val state: StateFlow<AnimalRoundState> = _state.asStateFlow()

    private var animals = emptyList<AnimalDef>()
    private var allFoods = level1Foods
    private var level = 1
    private var mistakes = 0

    init {
        viewModelScope.launch {
            level = progressRepository.getDifficulty(ISLAND)
            // New animals join the farm ones, so old friends keep coming back
            val pool = when (level) {
                1 -> level1Animals
                2 -> level1Animals + level2Animals
                else -> level1Animals + level2Animals + level3Animals
            }
            animals = pool.shuffled().take(4)
            allFoods = foodsForLevel(level)
            startRound(0)
        }
    }

    private fun startRound(index: Int) {
        if (index >= animals.size) {
            _state.value = _state.value.copy(sessionComplete = true)
            viewModelScope.launch { progressRepository.recordSession(ISLAND, animals.size, mistakes) }
            return
        }
        val animal = animals[index]
        val correctFood = allFoods.first { it.id == animal.foodId }
        val distractors = allFoods.filter { it.id != animal.foodId }.shuffled().take(if (level == 1) 1 else 2)  // two foods at first
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
        } else {
            mistakes++
        }
        return isCorrect
    }

    fun nextRound() {
        startRound(_state.value.roundIndex + 1)
    }
}

private const val ISLAND = "animals"
