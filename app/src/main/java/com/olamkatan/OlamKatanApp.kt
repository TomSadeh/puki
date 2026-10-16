package com.olamkatan

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.olamkatan.data.models.Gender
import com.olamkatan.ui.onboarding.OnboardingScreen
import com.olamkatan.ui.common.LocalCompanion
import com.olamkatan.audio.Companion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.olamkatan.audio.MusicPlayer
import com.olamkatan.audio.Sfx
import com.olamkatan.audio.SfxPlayer
import com.olamkatan.audio.VoicePlayer
import com.olamkatan.data.ProgressRepository
import com.olamkatan.data.SettingsRepository
import com.olamkatan.data.models.Language
import com.olamkatan.navigation.OlamKatanNavGraph
import com.olamkatan.theme.OlamKatanTheme
import com.olamkatan.ui.common.AppViewModel
import com.olamkatan.ui.common.LocalAppSettings
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.common.LocalGender
import com.olamkatan.ui.common.LocalMusicPlayer
import com.olamkatan.ui.common.LocalSfxPlayer
import com.olamkatan.ui.common.LocalVoicePlayer
import com.olamkatan.ui.components.TimeLimitOverlay
import kotlinx.coroutines.delay

@Composable
fun OlamKatanApp(
    sfxPlayer: SfxPlayer,
    musicPlayer: MusicPlayer,
    voicePlayer: VoicePlayer,
    companion: Companion,
    settingsRepository: SettingsRepository,
    progressRepository: ProgressRepository
) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.Factory(settingsRepository, progressRepository)
    )
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val language = settings.language
    val layoutDirection = when (language) {
        Language.HEBREW -> LayoutDirection.Rtl
        Language.ENGLISH -> LayoutDirection.Ltr
    }

    LaunchedEffect(Unit) {
        Sfx.preloadAll(sfxPlayer)
        // Optional tracks: app/src/main/res/raw/music_hub.mp3 (and music_night.mp3 for bedtime)
        musicPlayer.playTrack("music_hub")
    }

    LaunchedEffect(settings.musicVolume, settings.voiceVolume, settings.sfxVolume) {
        musicPlayer.level = settings.musicVolume
        voicePlayer.level = settings.voiceVolume
        sfxPlayer.level = settings.sfxVolume
    }

    LaunchedEffect(settings.soundEnabled) {
        sfxPlayer.enabled = settings.soundEnabled
        voicePlayer.enabled = settings.soundEnabled
    }

    LaunchedEffect(settings.companionEnabled, settings.companionAddress, settings.companionCode, language) {
        companion.enabled = settings.companionEnabled
        companion.address = settings.companionAddress
        companion.code = settings.companionCode
        companion.language = language
    }

    LaunchedEffect(settings.childName, settings.gender) {
        companion.setChild(settings.childName, settings.gender == Gender.GIRL)
    }

    LaunchedEffect(settings.gender) {
        voicePlayer.gender = settings.gender
    }

    LaunchedEffect(settings.musicEnabled) {
        musicPlayer.setEnabled(settings.musicEnabled)
    }

    // Time limit: counts only the time the game is on screen, not while it sits in the background
    var showTimeLimit by remember { mutableStateOf(false) }
    var playedMs by remember { mutableLongStateOf(0L) }
    var onScreen by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) onScreen = true
            if (event == Lifecycle.Event.ON_STOP) onScreen = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(settings.timeLimitMinutes) { playedMs = 0L }
    LaunchedEffect(settings.timeLimitMinutes, onScreen, showTimeLimit) {
        if (settings.timeLimitMinutes <= 0 || !onScreen || showTimeLimit) return@LaunchedEffect
        while (true) {
            delay(5_000)
            playedMs += 5_000
            if (playedMs >= settings.timeLimitMinutes * 60_000L) {
                showTimeLimit = true
                break
            }
        }
    }

    OlamKatanTheme {
        CompositionLocalProvider(
            LocalLanguage provides language,
            LocalGender provides settings.gender,
            LocalAppSettings provides settings,
            LocalSfxPlayer provides sfxPlayer,
            LocalMusicPlayer provides musicPlayer,
            LocalVoicePlayer provides voicePlayer,
            LocalCompanion provides companion,
            LocalLayoutDirection provides layoutDirection
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (settings.loaded && !settings.onboardingDone) {
                    OnboardingScreen(settings)
                } else if (settings.loaded) {
                    val navController = rememberNavController()
                    OlamKatanNavGraph(navController = navController)
                }

                if (showTimeLimit) {
                    TimeLimitOverlay(
                        onDismiss = {
                            showTimeLimit = false
                            playedMs = 0L  // a new allowance starts counting
                        }
                    )
                }
            }
        }
    }
}
