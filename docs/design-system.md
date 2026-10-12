# Sukoon design system

Source of truth is the code in `app/src/main/java/com/sukoon/app/ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`, `Motion.kt`). This file is a readable index of it; the brand export lives in `_design-export/`. Screens: see [design-screens.md](design-screens.md).

## Principles
- Calm first: still water, stillness by default, motion only for moments that matter.
- Never rely on hue alone for glucose state. Pair color with an icon or text (PLAN.md §5).
- Light and dark are both first-class; dark text tones are lifted to keep 4.5:1 on the dark canvas.

## Color

| Token | Light | Dark | Use |
|---|---|---|---|
| Canvas | `#F4F1EA` | `#1E2B26` | page background |
| Surface | `#FBF9F4` | `#10201B` | cards, sheets |
| OnCanvas | `#2B2822` | `#E4DFD4` | primary text |
| SageDeep | `#2E5C4A` | `#82BBA0` | text, links, primary |
| Sage | `#3E7A63` | `#3E7A63` | secondary accent |
| SageLight | `#82BBA0` | `#82BBA0` | accent on dark |
| SageMist | `#CFE0D6` | `#26453A` | wash behind pills and icons |
| StateHigh (amber) | `#C88A3E` | same | high glucose |
| StateLow / StateUrgent | `#C9564B` | same | low and urgent: one red, severity comes from copy, banner and button |
| NeutralWarm | `#A39A8B` | same | eyebrows, uppercase captions |
| TextMuted | `#8A8275` | `#9C968A` | status labels |
| CaptionMuted | `#5E6B64` | `#A6B1AA` | captions, secondary text |
| PlaceholderMuted | `#C1B7A6` | `#56635C` | "- - -" stand-ins |
| PillHigh bg / text | `#F1E2CC` / `#8A5A1E` | `#3A3122` / `#E5B877` | high pill |
| PillLow bg / text | `#F0DAD5` / `#A23F35` | `#3D2623` / `#F0A097` | low pill |
| PillNeutral bg | `#E7E2D8` | `#2C3934` | in-range pill |
| MarkHighlight | `#EAF0EC` | same | brand crescent; the mark always sits on a `CanvasDark` badge |

Dark-aware tokens read `LocalDarkTheme`, which `SukoonTheme` provides. Material3 `primary` = SageDeep (light) / SageLight (dark); `error` = StateUrgent.

## Typography
- **Hanken Grotesk**: all interface text (every Material style is mapped to it). Overrides: headlineMedium 24sp SemiBold, bodyLarge 16sp Normal, labelLarge 14sp Medium.
- **Newsreader**: hero glucose number (Light, optical size 72) and headlines (other weights, optical size 24).
- Both are bundled variable fonts under the SIL OFL (`assets/fonts-license.txt`), Latin only. Arabic falls back to the phone's Arabic font glyph by glyph.

## Motion
- Durations (ms): INSTANT 90 · QUICK 160 · BASE 240 · CALM 360 · SLOW 560 · DRAW 900 · LOOP 2400.
- Easing: Standard `(0.4, 0, 0.2, 1)` · Out `(0, 0, 0.2, 1)` · In `(0.4, 0, 1, 1)` · Calm `(0.22, 1, 0.36, 1)` (the signature curve).
- Slow in, quick out. Loops only for live or urgent states.
- `staggerIn`: first 8 items rise 14dp and fade, 70 ms apart, on first appearance.
- `pressScale`: dips to 96% while pressed.
- Reduced motion (system "Remove animations") shows end states; presses fall back to 85% alpha.
