package io.github.tomsadeh.puki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import io.github.tomsadeh.puki.brain.LocalBrain
import io.github.tomsadeh.puki.brain.DeviceModels
import android.app.Application
import io.github.tomsadeh.puki.data.ProgressRepository
import io.github.tomsadeh.puki.data.SettingsRepository

class PukiApplication : Application() {
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
