package com.olamkatan.data.models

data class AppSettings(
    val language: Language = Language.HEBREW,
    val gender: Gender = Gender.GIRL,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val timeLimitMinutes: Int = 0, // 0 = no limit
    val companionEnabled: Boolean = true,
    val companionAddress: String = DEFAULT_COMPANION_ADDRESS
)

/** The PC running brain/doorbell.py, e.g. "192.168.1.20:8790". Empty until a parent sets it. */
const val DEFAULT_COMPANION_ADDRESS = ""

enum class Language {
    HEBREW, ENGLISH
}

enum class Gender {
    BOY, GIRL
}
