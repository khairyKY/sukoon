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
val StateHigh = Color(0xFFC88A3E)   // amber
val StateUrgent = Color(0xFFE4574A) // coral-red
val StateLow = Color(0xFFC9564B)    // coral-red (deeper, distinguish from urgent via shape/motion too)

val NeutralWarm = Color(0xFFA39A8B)

// The brand mark's light "crescent" circle — fixed regardless of app theme, since the mark
// always sits on a dark badge (CanvasDark), matching the export's icon treatment.
val MarkHighlight = Color(0xFFEAF0EC)
