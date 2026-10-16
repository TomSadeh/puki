package com.olamkatan.ui.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olamkatan.OlamKatanApplication
import com.olamkatan.data.models.GameProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HubViewModel(application: Application) : AndroidViewModel(application) {

    private val progressRepository = (application as OlamKatanApplication).progressRepository

    val progress: StateFlow<GameProgress> = progressRepository.progress
        .stateIn(viewModelScope, SharingStarted.Eagerly, GameProgress())
}
