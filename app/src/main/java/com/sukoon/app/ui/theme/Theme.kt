package com.sukoon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = SageLight,
    onPrimary = SurfaceDark,
    secondary = Sage,
    background = CanvasDark,
    onBackground = OnCanvasDark,
    surface = SurfaceDark,
    onSurface = OnCanvasDark,
    error = StateUrgent,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5C4A), // SageDeep's light tone
    onPrimary = SurfaceLight,
    secondary = Sage,
    background = CanvasLight,
    onBackground = OnCanvasLight,
    surface = SurfaceLight,
    onSurface = OnCanvasLight,
    error = StateUrgent,
)

@Composable
fun SukoonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
