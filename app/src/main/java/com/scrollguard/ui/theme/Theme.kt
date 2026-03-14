package com.scrollguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = DeepSea,
    secondary = Amber,
    tertiary = Reef,
    background = Sand,
    surface = Color.White,
)

@Composable
fun ScrollGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
