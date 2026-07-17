package com.sukoon.app.ui.theme

import androidx.compose.ui.graphics.Color

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
val SageDeep = Color(0xFF2E5C4A)
val Sage = Color(0xFF3E7A63)
val SageLight = Color(0xFF82BBA0)
val SageMist = Color(0xFFCFE0D6)

// Glucose-state colors — dual-encode with icon/text too, never rely on hue alone (PLAN.md §5).
val StateHigh = Color(0xFFC88A3E) // amber
val StateLow = Color(0xFFC9564B)  // coral-red

// Confirmed against the shipped Home screens (5b/7c, 8f): Low and Urgent use the SAME red —
// severity is distinguished by banner/copy/button choice, not a second shade. Kept as a
// separate name (not just an alias) since alarm-engine work may want them to diverge later.
val StateUrgent = StateLow

val NeutralWarm = Color(0xFFA39A8B)  // muted label text (eyebrows, uppercase captions)
val TextMuted = Color(0xFF8A8275)    // slightly stronger secondary text (status labels)
val CaptionMuted = Color(0xFF5E6B64) // cooler caption/body-secondary text
val PlaceholderMuted = Color(0xFFC1B7A6) // "- - -" / "- -" stand-ins for missing data

// Pill backgrounds — each pairs with a matching text tone, confirmed per-state from the export.
val PillHighBg = Color(0xFFF1E2CC)
val PillHighText = Color(0xFF8A5A1E)
val PillLowBg = Color(0xFFF0DAD5)
val PillLowText = Color(0xFFA23F35)
val PillNeutralBg = Color(0xFFE7E2D8)

// The brand mark's light "crescent" circle — fixed regardless of app theme, since the mark
// always sits on a dark badge (CanvasDark), matching the export's icon treatment.
val MarkHighlight = Color(0xFFEAF0EC)
