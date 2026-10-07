package com.sukoon.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Pulled from the dominant swatches in the shipped brand export
// (_design-export/Sukoon Brand Directions.dc.html). That file explores several directions,
// so this is a best-effort starter set from what's clearly dominant by usage count —
// confirm/adjust against the final chosen direction when the Home screen gets implemented.

// Dark theme — still-water canvas
val CanvasDark = Color(0xFF1E2B26)
val SurfaceDark = Color(0xFF10201B)
val OnCanvasDark = Color(0xFFE4DFD4)

// Light theme — warm neutral canvas
val CanvasLight = Color(0xFFF4F1EA)
val SurfaceLight = Color(0xFFFBF9F4)
val OnCanvasLight = Color(0xFF2B2822)

// Calm / accent — the "ripple" sage-teal family
/** Deep sage for text and links; in the dark theme it lifts to the light sage so it stays readable. */
val SageDeep: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF82BBA0) else Color(0xFF2E5C4A)
val Sage = Color(0xFF3E7A63)
val SageLight = Color(0xFF82BBA0)
/** A sage wash behind pills and icons; a deep sage tint on the dark canvas. */
val SageMist: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF26453A) else Color(0xFFCFE0D6)

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
val PillNeutralBg: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF2C3934) else Color(0xFFE7E2D8)

/** True inside a dark [SukoonTheme]: the tokens above pick their dark tone from it. */
val LocalDarkTheme = staticCompositionLocalOf { false }

// The brand mark's light "crescent" circle — fixed regardless of app theme, since the mark
// always sits on a dark badge (CanvasDark), matching the export's icon treatment.
val MarkHighlight = Color(0xFFEAF0EC)
