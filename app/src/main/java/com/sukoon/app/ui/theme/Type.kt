@file:OptIn(ExperimentalTextApi::class)

package com.sukoon.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sukoon.app.R

// The design's fonts, bundled (SIL Open Font License, see assets/fonts-license.txt): Hanken Grotesk
// for the interface, Newsreader for the hero number and the headlines. Both are variable fonts, so
// one file gives every weight. They carry Latin only: Arabic text falls back to the phone's own
// Arabic font, glyph by glyph.

private fun hanken(weight: FontWeight) =
    Font(R.font.hanken_grotesk, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

// ponytail: Newsreader's optical size is fixed per weight, not per text size — Light is only used
// for the big glucose numbers (display cut, opsz 72), the rest for headlines (opsz 24). Split into
// two families if a light headline or a heavy hero number ever appears.
private fun newsreader(weight: FontWeight) = Font(
    R.font.newsreader,
    weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", if (weight == FontWeight.Light) 72f else 24f),
    ),
)

private val WEIGHTS = listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

val UiFontFamily = FontFamily(WEIGHTS.map(::hanken))
val HeadlineSerifFontFamily = FontFamily(WEIGHTS.map(::newsreader))

private val base = Typography()
private fun TextStyle.ui() = copy(fontFamily = UiFontFamily)

// Every Material style in Hanken Grotesk, so buttons, fields and menus match the rest of the app.
val Typography = Typography(
    // Material draws the time picker's hours and minutes in displayLarge: Material's size, the UI face.
    // (The hero number sets its own serif and size.)
    displayLarge = base.displayLarge.ui(),
    displayMedium = base.displayMedium.ui(),
    displaySmall = base.displaySmall.ui(),
    headlineLarge = base.headlineLarge.ui(),
    headlineMedium = base.headlineMedium.copy(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    headlineSmall = base.headlineSmall.ui(),
    titleLarge = base.titleLarge.ui(),
    titleMedium = base.titleMedium.ui(),
    titleSmall = base.titleSmall.ui(),
    bodyLarge = base.bodyLarge.copy(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = base.bodyMedium.ui(),
    bodySmall = base.bodySmall.ui(),
    labelLarge = base.labelLarge.copy(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = base.labelMedium.ui(),
    labelSmall = base.labelSmall.ui(),
)
