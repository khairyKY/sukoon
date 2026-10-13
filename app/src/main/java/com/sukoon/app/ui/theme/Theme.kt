package com.sukoon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private fun darkColors(t: ColorTheme) = darkColorScheme(
    primary = t.light,
    onPrimary = t.surfaceDark,
    secondary = t.accent,
    background = t.canvasDark,
    onBackground = OnCanvasDark,
    surface = t.surfaceDark,
    onSurface = OnCanvasDark,
    error = StateUrgent,
)

private fun lightColors(t: ColorTheme) = lightColorScheme(
    primary = t.deep,
    onPrimary = t.surfaceLight,
    secondary = t.accent,
    background = t.canvasLight,
    onBackground = OnCanvasLight,
    surface = t.surfaceLight,
    onSurface = OnCanvasLight,
    error = StateUrgent,
)

@Composable
fun SukoonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val theme = Themes.current
    val colors = androidx.compose.runtime.remember(theme, darkTheme) { if (darkTheme) darkColors(theme) else lightColors(theme) }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
