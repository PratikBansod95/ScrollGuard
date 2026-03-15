package com.scrollguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = DeepSea,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Ink,
    tertiary = Reef,
    background = Sand,
    surface = Cream,
    surfaceVariant = Mist,
    onSurface = Ink,
    onSurfaceVariant = Slate,
)

private val DarkColors = darkColorScheme(
    primary = Reef,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF1A1407),
    tertiary = Coral,
    background = Night,
    surface = Coal,
    surfaceVariant = Graphite,
    onSurface = Cloud,
    onSurfaceVariant = Smoke,
)

@Composable
fun ScrollGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
