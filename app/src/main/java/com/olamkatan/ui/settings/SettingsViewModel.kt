package com.olamkatan.ui.settings

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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

    fun setVolumes(music: Float? = null, voice: Float? = null, sfx: Float? = null) {
        viewModelScope.launch { settingsRepository.updateVolumes(music, voice, sfx) }
    }

    fun setCompanionCode(code: String) {
        viewModelScope.launch { settingsRepository.updateCompanionCode(code) }
    }

    // ---- Puki's brain on this device ----

    private val app = application as OlamKatanApplication
    val deviceModels get() = app.deviceModels
    var downloadError by mutableStateOf<String?>(null)
        private set

    fun downloadDeviceBrain() {
        downloadError = null
        // In the app's scope, so it carries on when the parent leaves settings
        app.appScope.launch {
            try {
                app.deviceModels.download()
            } catch (e: Exception) {
                downloadError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun setBrainOnDevice(on: Boolean) {
        viewModelScope.launch { settingsRepository.updateBrainOnDevice(on) }
    }

    fun removeDeviceBrain() {
        viewModelScope.launch {
            settingsRepository.updateBrainOnDevice(false)
            runCatching { app.localBrain.close() }
            app.deviceModels.delete()
        }
    }

    fun cycleTimeLimit(current: Int) {
        val options = listOf(0, 15, 30, 45, 60)
        val nextIndex = (options.indexOf(current) + 1) % options.size
        viewModelScope.launch { settingsRepository.updateTimeLimit(options[nextIndex]) }
    }
}
