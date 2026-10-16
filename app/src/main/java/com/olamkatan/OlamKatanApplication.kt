package com.olamkatan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.olamkatan.brain.LocalBrain
import com.olamkatan.brain.DeviceModels
import android.app.Application
import com.olamkatan.data.ProgressRepository
import com.olamkatan.data.SettingsRepository

class OlamKatanApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set
    /** Puki's brain on this device, and the models it needs (only used on capable phones). */
    val deviceModels by lazy { DeviceModels(this) }
    val localBrain by lazy { LocalBrain(deviceModels) }
    /** For work that outlives a screen (the device brain's download). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        progressRepository = ProgressRepository(this)
    }
}
