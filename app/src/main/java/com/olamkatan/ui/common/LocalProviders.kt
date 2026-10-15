package com.olamkatan.ui.common

import com.olamkatan.audio.Companion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.olamkatan.audio.MusicPlayer
import com.olamkatan.audio.SfxPlayer
import com.olamkatan.audio.VoicePlayer
import com.olamkatan.data.models.AppSettings
import com.olamkatan.data.models.BilingualText
import com.olamkatan.data.models.Gender
import com.olamkatan.data.models.Language

val LocalLanguage = compositionLocalOf { Language.HEBREW }
val LocalGender = compositionLocalOf { Gender.GIRL }
val LocalAppSettings = compositionLocalOf { AppSettings() }
val LocalSfxPlayer = compositionLocalOf<SfxPlayer> { error("No SfxPlayer provided") }
val LocalMusicPlayer = compositionLocalOf<MusicPlayer> { error("No MusicPlayer provided") }
val LocalCompanion = compositionLocalOf<Companion?> { null }
val LocalVoicePlayer = compositionLocalOf<VoicePlayer> { error("No VoicePlayer provided") }

/** Resolves to the current language, and to the child's gender for Hebrew lines that have one. */
@Composable
fun BilingualText.localized(): String = resolve(LocalLanguage.current, LocalGender.current)
