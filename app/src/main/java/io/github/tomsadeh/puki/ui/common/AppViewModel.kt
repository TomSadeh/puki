package io.github.tomsadeh.puki.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.tomsadeh.puki.data.ProgressRepository
import io.github.tomsadeh.puki.data.SettingsRepository
import io.github.tomsadeh.puki.data.models.AppSettings
import io.github.tomsadeh.puki.data.models.GameProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppViewModel(
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val progress: StateFlow<GameProgress> = progressRepository.progress
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameProgress())

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val progressRepository: ProgressRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(settingsRepository, progressRepository) as T
        }
    }
}
