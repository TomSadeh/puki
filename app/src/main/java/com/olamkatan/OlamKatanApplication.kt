package com.olamkatan

import android.app.Application
import com.olamkatan.data.ProgressRepository
import com.olamkatan.data.SettingsRepository

class OlamKatanApplication : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        progressRepository = ProgressRepository(this)
    }
}
