package io.github.tomsadeh.puki.theme

import io.github.tomsadeh.puki.ui.components.SquishIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PukiColorScheme = lightColorScheme(
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
fun PukiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PukiColorScheme,
        typography = PukiTypography
    ) {
        // Things squish when pressed, instead of a ripple rectangle
        CompositionLocalProvider(LocalIndication provides SquishIndication, content = content)
    }
}
