package com.olamkatan.ui.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.models.GameProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val progressRepository = (application as OlamKatanApplication).progressRepository

    val progress: StateFlow<GameProgress> = progressRepository.progress
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameProgress())

    private val _newlyUnlocked = MutableStateFlow<Set<String>>(emptySet())
    val newlyUnlocked: StateFlow<Set<String>> = _newlyUnlocked.asStateFlow()

    init {
        checkForUnlocks()
    }

    fun checkForUnlocks() {
        viewModelScope.launch {
            val unlocked = progressRepository.checkAndUnlockIslands()
            if (unlocked.isNotEmpty()) {
                _newlyUnlocked.value = unlocked
            }
        }
    }

    fun dismissUnlockCeremony() {
        _newlyUnlocked.value = emptySet()
    }

    fun isIslandUnlocked(islandId: String, totalStars: Int): Boolean {
        val island = allIslands.find { it.id == islandId } ?: return false
        return totalStars >= island.starsToUnlock
    }
}
