package com.sukoon.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Pulled from the dominant swatches in the shipped brand export
// (_design-export/Sukoon Brand Directions.dc.html). That file explores several directions,
// so this is a best-effort starter set from what's clearly dominant by usage count —
// confirm/adjust against the final chosen direction when the Home screen gets implemented.

/**
 * You → Appearance → Colour (design: the "Sukoon themes and long-acting" canvas). A theme fills the
 * accent slot (the tokens still named Sage*, the original theme) and tints the canvases; type,
 * spacing and motion stay. Glucose colours never change: [InRange], [StateHigh], [StateLow]. Every
 * accent keeps white text at 5:1 or better and its deep/light tones at 4.5:1 on their canvas.
 */
enum class ColorTheme(
    val accent: Color, val light: Color, val deep: Color, val mist: Color, val mistDark: Color,
    val canvasDark: Color, val surfaceDark: Color, val canvasLight: Color, val surfaceLight: Color,
    val neutral: Color, val neutralDark: Color,
) {
    SAGE(Color(0xFF3E7A63), Color(0xFF82BBA0), Color(0xFF2E5C4A), Color(0xFFCFE0D6), Color(0xFF26453A), Color(0xFF1E2B26), Color(0xFF10201B), Color(0xFFF4F1EA), Color(0xFFFBF9F4), Color(0xFFE7E2D8), Color(0xFF2C3934)),
    // A cool, blue-leaning pink, kept well away from the low red (and its coral pill).
    ROSE(Color(0xFFA44C78), Color(0xFFE3A3C4), Color(0xFF7C3459), Color(0xFFF0D9E7), Color(0xFF4A2B3C), Color(0xFF2A1E25), Color(0xFF1B1218), Color(0xFFF7F0F2), Color(0xFFFCF8F9), Color(0xFFECE1E5), Color(0xFF3A2C33)),
    TIDE(Color(0xFF3A6E93), Color(0xFF8DB9D9), Color(0xFF29506E), Color(0xFFD3E3EF), Color(0xFF243A4D), Color(0xFF1B2530), Color(0xFF111922), Color(0xFFF1F3F4), Color(0xFFF9FAFB), Color(0xFFE1E6EA), Color(0xFF2A3540)),
    DUSK(Color(0xFF675CA6), Color(0xFFB0A8E0), Color(0xFF4B4285), Color(0xFFE0DCF2), Color(0xFF332E50), Color(0xFF211F2D), Color(0xFF15141E), Color(0xFFF3F2F6), Color(0xFFFAF9FC), Color(0xFFE4E2EC), Color(0xFF2E2C3C)),
    LAGOON(Color(0xFF237A85), Color(0xFF7CC3CC), Color(0xFF1B5C64), Color(0xFFCBE5E8), Color(0xFF21434A), Color(0xFF1B282B), Color(0xFF10191C), Color(0xFFF1F4F3), Color(0xFFF9FBFA), Color(0xFFE1E8E7), Color(0xFF2A3638)),
    SLATE(Color(0xFF56687A), Color(0xFFA7B8C8), Color(0xFF3E4D5C), Color(0xFFDCE3E9), Color(0xFF2F3A44), Color(0xFF1F2328), Color(0xFF14171B), Color(0xFFF2F3F4), Color(0xFFFAFAFB), Color(0xFFE3E5E8), Color(0xFF2D3238)),
}

/**
 * The theme in force. Snapshot state, so every token below follows a change wherever it's read:
 * in composition, in a draw block, in a widget. ponytail: one global, not a CompositionLocal, so
 * the ~300 existing token reads (many in draw lambdas) needed no change.
 */
object Themes {
    var current by mutableStateOf(ColorTheme.SAGE)
}

// Dark theme — still-water canvas
val CanvasDark: Color get() = Themes.current.canvasDark
val SurfaceDark: Color get() = Themes.current.surfaceDark
val OnCanvasDark = Color(0xFFE4DFD4)

// Light theme — warm neutral canvas
val CanvasLight: Color get() = Themes.current.canvasLight
val SurfaceLight: Color get() = Themes.current.surfaceLight
val OnCanvasLight = Color(0xFF2B2822)

// The accent, named after the original sage theme.
/** Deep accent for text and links; in the dark theme it lifts to the light accent so it stays readable. */
val SageDeep: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Themes.current.light else Themes.current.deep
val Sage: Color get() = Themes.current.accent
val SageLight: Color get() = Themes.current.light
/** An accent wash behind pills and icons; a deep tint on the dark canvas. */
val SageMist: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Themes.current.mistDark else Themes.current.mist

// Glucose in range: the same green in every theme, like high and low.
val InRange = Color(0xFF3E7A63)
val InRangeLight = Color(0xFF82BBA0)
val InRangeDeep: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF82BBA0) else Color(0xFF2E5C4A)
val InRangeMist: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF26453A) else Color(0xFFCFE0D6)

// Glucose-state colors — dual-encode with icon/text too, never rely on hue alone (PLAN.md §5).
val StateHigh = Color(0xFFC88A3E) // amber
val StateLow = Color(0xFFC9564B)  // coral-red

// Confirmed against the shipped Home screens (5b/7c, 8f): Low and Urgent use the SAME red —
// severity is distinguished by banner/copy/button choice, not a second shade. Kept as a
// separate name (not just an alias) since alarm-engine work may want them to diverge later.
val StateUrgent = StateLow

val NeutralWarm = Color(0xFFA39A8B)  // muted label text (eyebrows, uppercase captions)
// Muted text tones, each with a dark-theme tone that keeps 4.5:1 on the dark canvas.
val TextMuted: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF9C968A) else Color(0xFF8A8275) // status labels
val CaptionMuted: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFA6B1AA) else Color(0xFF5E6B64) // captions, secondary text
val PlaceholderMuted: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF56635C) else Color(0xFFC1B7A6) // "- - -" stand-ins

// Pill backgrounds — each pairs with a matching text tone, confirmed per-state from the export.
// Dark tones: the same hue as a low tint on the canvas, text lifted to stay readable.
val PillHighBg: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF3A3122) else Color(0xFFF1E2CC)
val PillHighText: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFE5B877) else Color(0xFF8A5A1E)
val PillLowBg: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF3D2623) else Color(0xFFF0DAD5)
val PillLowText: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFF0A097) else Color(0xFFA23F35)
val PillNeutralBg: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Themes.current.neutralDark else Themes.current.neutral

/** True inside a dark [SukoonTheme]: the tokens above pick their dark tone from it. */
val LocalDarkTheme = staticCompositionLocalOf { false }

// The brand mark's light "crescent" circle — fixed regardless of app theme, since the mark
// always sits on a dark badge (CanvasDark), matching the export's icon treatment.
val MarkHighlight = Color(0xFFEAF0EC)
