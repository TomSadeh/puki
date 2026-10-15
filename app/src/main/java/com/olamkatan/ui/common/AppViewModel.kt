package com.olamkatan.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.olamkatan.data.ProgressRepository
import com.olamkatan.data.SettingsRepository
import com.olamkatan.data.models.AppSettings
import com.olamkatan.data.models.GameProgress
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
