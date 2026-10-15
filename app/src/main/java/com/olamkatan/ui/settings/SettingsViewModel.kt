package com.olamkatan.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.models.Gender
import com.olamkatan.data.models.Language
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as OlamKatanApplication).settingsRepository

    fun toggleLanguage(current: Language) {
        viewModelScope.launch {
            val next = when (current) {
                Language.HEBREW -> Language.ENGLISH
                Language.ENGLISH -> Language.HEBREW
            }
            settingsRepository.updateLanguage(next)
        }
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSound(!enabled) }
    }

    fun toggleMusic(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMusic(!enabled) }
    }

    fun toggleGender(current: Gender) {
        viewModelScope.launch {
            val next = when (current) {
                Gender.GIRL -> Gender.BOY
                Gender.BOY -> Gender.GIRL
            }
            settingsRepository.updateGender(next)
        }
    }

    fun setChildName(name: String) {
        viewModelScope.launch { settingsRepository.updateChildName(name) }
    }

    fun setLanguage(language: Language) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun setGender(gender: Gender) {
        viewModelScope.launch { settingsRepository.updateGender(gender) }
    }

    fun setCompanionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateCompanionEnabled(enabled) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    fun toggleCompanion(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateCompanionEnabled(!enabled) }
    }

    fun setCompanionAddress(address: String) {
        viewModelScope.launch { settingsRepository.updateCompanionAddress(address) }
    }

    fun setCompanionCode(code: String) {
        viewModelScope.launch { settingsRepository.updateCompanionCode(code) }
    }

    fun cycleTimeLimit(current: Int) {
        val options = listOf(0, 15, 30, 45, 60)
        val nextIndex = (options.indexOf(current) + 1) % options.size
        viewModelScope.launch { settingsRepository.updateTimeLimit(options[nextIndex]) }
    }
}
