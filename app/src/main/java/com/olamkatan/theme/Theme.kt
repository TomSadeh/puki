package com.olamkatan.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val OlamKatanColorScheme = lightColorScheme(
    primary = SkyBlue,
    secondary = GrassGreen,
    tertiary = SunYellow,
    background = CloudWhite,
    surface = CloudWhite,
    onPrimary = EarthBrown,
    onSecondary = CloudWhite,
    onBackground = EarthBrown,
    onSurface = EarthBrown
)

@Composable
fun OlamKatanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OlamKatanColorScheme,
        typography = OlamKatanTypography,
        content = content
    )
}
