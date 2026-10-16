package com.olamkatan.theme

import com.olamkatan.ui.components.SquishIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
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
        typography = OlamKatanTypography
    ) {
        // Things squish when pressed, instead of a ripple rectangle
        CompositionLocalProvider(LocalIndication provides SquishIndication, content = content)
    }
}
