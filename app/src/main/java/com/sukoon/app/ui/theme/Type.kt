package com.sukoon.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ponytail: system default FontFamily for all three roles below; the brand export references
// Hanken Grotesk (Latin UI), IBM Plex Sans Arabic (RTL), and JetBrains Mono (tabular glucose
// numerals — matters for PLAN.md §5's "digits don't jump" requirement). Swap in real font
// files under res/font/ once they're added; upgrade path is just changing these FontFamily
// values, nothing structural.
val UiFontFamily = FontFamily.Default
val NumeralFontFamily = FontFamily.Monospace

// ponytail: system serif placeholder for the Newsreader (EN) / Amiri (AR) headline pairing used
// on emotional/onboarding moments (e.g. the disclaimer gate). Same upgrade path as above.
val HeadlineSerifFontFamily = FontFamily.Serif

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NumeralFontFamily,
        fontWeight = FontWeight.Medium,
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
