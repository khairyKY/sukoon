package com.sukoon.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ponytail: system default FontFamily for all roles below; the brand export references Hanken
// Grotesk (Latin UI), IBM Plex Sans Arabic (RTL), and Newsreader/Amiri (the serif used for the
// hero glucose number and emotional/onboarding headlines — confirmed against the shipped Home
// screens, screens 5a/8e/8f). Swap in real font files under res/font/ once added; upgrade path
// is just changing these FontFamily values, nothing structural.
val UiFontFamily = FontFamily.Default
val HeadlineSerifFontFamily = FontFamily.Serif

// Kept for places that do want tabular alignment (e.g. a future logbook table); the Home
// screen's hero number does NOT use this — see HeadlineSerifFontFamily above.
val NumeralFontFamily = FontFamily.Monospace

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = HeadlineSerifFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 96.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)
