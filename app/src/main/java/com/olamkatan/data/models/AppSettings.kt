package com.olamkatan.data.models

data class AppSettings(
    val language: Language = Language.HEBREW,
    val gender: Gender = Gender.GIRL,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    /** Volumes, 0..1: music, voices (recorded lines and Puki), sound effects. */
    val musicVolume: Float = 1f,
    val voiceVolume: Float = 1f,
    val sfxVolume: Float = 1f,
    val timeLimitMinutes: Int = 0, // 0 = no limit
    val childName: String = "",
    val onboardingDone: Boolean = false,
    /** False until the stored settings have been read (so the welcome screen doesn't flash). */
    val loaded: Boolean = false,
    val companionEnabled: Boolean = true,
    val companionAddress: String = DEFAULT_COMPANION_ADDRESS,
    /** Shown by brain/doorbell.py on the PC; without it the PC won't answer. */
    val companionCode: String = ""
)

/** The PC running brain/doorbell.py, e.g. "192.168.1.20:8790". Empty until a parent sets it. */
const val DEFAULT_COMPANION_ADDRESS = ""

enum class Language {
    HEBREW, ENGLISH
}

enum class Gender {
    BOY, GIRL
}
