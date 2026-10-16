# Motion

How Sukoon moves, and more often how it holds still. The tokens and helpers live in
`app/src/main/java/com/sukoon/app/ui/theme/Motion.kt`; the original brand spec with live demos is
`_design-export/Sukoon Motion Spec.dc.html`. This file is the working spec: what each pattern is,
where it is used, and what it does when the phone's "Remove animations" is on.

## Principles

1. **Calm.** "Sukoon" means calm. Motion is soft: opacity plus at most 12 dp of travel, 180 to 420 ms
   for everyday changes. Nothing bounces, springs, or overshoots.
2. **Purposeful.** Motion explains a change: something arrived, something moved, something was
   chosen. If it explains nothing, it doesn't move.
3. **Never in the way of safety.** Alarms, a low, an urgent low and the urgent screen appear at once:
   no entrance, no stagger, no delay before "Treat now" can be pressed. The Low and Urgent states on
   Now, `alarms/*` and the heading-low meal sheet use none of the patterns below. The only motion
   there is the existing urgent takeover scale-in (which never blocks its button) and the reading's
   pulse, both from the brand spec.
4. **Slow to arrive, quick to leave.** Entrances decelerate (`Out`, or `Calm` for signature
   moments); exits accelerate (`In`) and are shorter.
5. **Once.** Entrances play when something first appears, not on every recomposition, scroll or
   return to a tab (`firstShow`, and `staggerIn` saves that it played). Loops are only for live or
   urgent states: the LIVE dot, the scan ripple, the warm-up glow.
6. **Reduced motion is honoured everywhere.** `Motion.reduced()` reads the system's animator scale.
   When it is off: no travel, no stagger, no loops; at most a short fade (120 ms), and controls
   crossfade colour in 90 ms or less.

## Tokens

### Durations (ms)

| Token | ms | Use |
|---|---|---|
| `INSTANT` | 90 | Reduced-motion colour changes, press alpha |
| `QUICK` | 160 | Exits, press scale, the old value leaving |
| `BASE` | 240 | Selection colour, content swaps, expand/collapse, toast |
| `CALM` | 360 | Entrances, screen push/pop, sheet, segment slide, list placement |
| `SLOW` | 560 | Count-up, ring and bar fills, graph draw-in |
| `DRAW` | 900 | Long reveals (the arrival glow fades over twice this) |
| `LOOP` | 2400 | Ambient loops: LIVE dot, scan ripple, warm-up glow |
| `REDUCED` | 120 | The fade every spatial move becomes under reduced motion |

### Easings

| Token | Curve | Use |
|---|---|---|
| `Standard` | (0.4, 0, 0.2, 1) | Utility: colour, placement, loops |
| `Out` | (0, 0, 0.2, 1) | Entrances |
| `In` | (0.4, 0, 1, 1) | Exits |
| `Calm` | (0.22, 1, 0.36, 1) | Signature settle: count-up, fills, graph draw, segment slide |

### Distance and rhythm

| Token | Value | Use |
|---|---|---|
| `RISE` | 10 dp | Entrance travel (rise, or slide from the side) |
| `STAGGER` | 36 ms | Between items of a list or grid |
| `STAGGER_CAP` | 8 | Only the first 8 items stagger; the rest arrive with the last |
| `FIRST_SHOW` | 1500 ms | How long a screen's first appearance counts as "first" |
| Press | 96 % scale | `pressScale` |

## Helpers (Motion.kt)

| Helper | What it does | Reduced |
|---|---|---|
| `firstShow(keys)` | True for a screen's first 1.5 s per key; saved, so returning doesn't replay | Same |
| `Modifier.staggerIn(index, key, fromX)` | Fade + rise 10 dp (or slide in from the end/start side, mirrored in Arabic), 36 ms apart, once per key | All fade in together, 120 ms, no travel |
| `Modifier.arrivalGlow(color)` | A soft rounded wash behind something just added, fading once | None |
| `tint(color)` | Animated colour for selection | 90 ms |
| `fill(target)` | Progress from empty to its value on first show, then to each new value, 560 ms Calm | Drawn at once |
| `Modifier.settleSize()` | `animateContentSize` 240 ms Out | Jumps |
| `appear()` / `vanish()` | Expand + fade in / shrink + fade out, for AnimatedVisibility | 120 ms fade |
| `Swap(target, rise)` | AnimatedContent crossfade for a changing value or label; small numbers rise a third of their height | 120 ms crossfade |
| `Modifier.pressScale(source)` | 96 % while held | 85 % alpha instead |

## Patterns

### Entrances
Fade in and rise `RISE` (10 dp) over `CALM` on `Out`, list items `STAGGER` (36 ms) apart, capped at 8.

- **Now:** the cards under the number (Next card, 3-hour graph, today's stat tiles, dose cards)
  cascade in on Now's first appearance (`firstShow` held above the In range / High branches, so
  crossing 180 doesn't replay it). Not on the Low or Urgent screens.
- **Logbook:** rows cascade when a day first shows. On switching days they slide in from the side
  of the arrow you pressed (later day: from the end side; earlier: from the start side; mirrored in
  Arabic). Rows scrolled back into view just show.
- **Add sheet:** the entry-type tiles stagger in as the sheet rises.
- **You hub:** the section cards stagger in once (not on every pop back from a page).
- **Insights:** the 14-day summary, the one thing, and "also worth a look" stagger in as the tab shows.
- **Ask:** your question rises in as it's sent; the streaming answer (and "thinking") fades and
  rises in once.

Reduced: everything fades in together in 120 ms, no offset.

### Exits
Shorter than the entrance and accelerating: `QUICK` fade, `In`. Where a block leaves a column it
also shrinks (`vanish()`) so nothing below jumps. Reduced: 120 ms fade.

- Now's chip row when the last chip goes.
- Logbook rows: a deleted row simply goes; the rows below slide up (placement, below).

### Content changes
- **Numbers and labels:** `Swap` (AnimatedContent). Now's stat tile values rise in a little as they
  change; the unit · trend · "updated" line under the number crossfades. The hero number keeps its
  count-up (560 ms Calm from the last value shown) rather than a crossfade.
- **Tab content:** the Trends hub (Graph, Logbook, Insights, Report, Ask) crossfades its content in
  `BASE`. Reduced: 120 ms.
- **Selected segment:** the raised segment of the Trends switch slides to the chosen tab and resizes
  to it in `CALM` on `Calm`; the labels' ink crossfades. Reduced: it moves at once.
- **List placement:** when an entry is added or removed, the other Logbook rows slide to their new
  place in `CALM` (`animateItem`, no fade). Reduced: none.

### Expand / collapse
`animateContentSize` (`settleSize()`) or `AnimatedVisibility` with `appear()` / `vanish()`.

- Now's status chips: the row fades in as it opens; a chip more or fewer settles its size.
- Now's Next card and dose cards: a card appearing or changing settles its height.
- Insights' "why" and "more" reveals (existing `Reveal`).

Reduced: jumps, or a 120 ms fade.

### Selection
Fill, border and ink crossfade with `tint()` in `BASE` on `Standard`; a border that thickens animates
its width too. Reduced: 90 ms.

- `ChoiceChips` / `NumberChips` (every settings picker), the Graph's range selector, the followed
  person's sub-tab chips, the entry editor's choice rows (`ChoiceRow`), the Trends segments, the
  bottom bar's wash (existing).

### Press
`pressScale`: 96 % while held, `QUICK` on `Standard`. Reduced: an 85 % alpha dip. Used on Now's
action buttons; add it to any primary button with its own interaction source.

### Progress
Rings and bars sweep to their value with `fill()` (560 ms Calm): from empty on first show, then from
the old value to each new one. Reduced: drawn at once.

- The warm-up ring on Now (existing, with its breathing glow).
- Time in range: the five-band bar on the Graph and the Report grows in from the start.
- Supplies: each pen's days-left ring.
- Not animated: the widget maker's previews, and progress inside alarm, low or fasting surfaces.

### Success
A quiet acknowledgement after a save: the toast rises 10 dp in `BASE`, holds about 2.2 s (longer
when accessibility settings ask), lifts 6 dp away; a confirm haptic. A newly added Logbook entry
also rises in and glows softly once (`arrivalGlow`, sage at 14 %), so the eye finds it. Reduced:
the toast fades in and out, no glow.

Spec, not yet built: a tick that draws in (stroke trim 0 to 1 over `BASE` on `Out`) at the start of
the save toast. Reduced: the tick shows whole.

### Charts
- **Graph:** the line draws left to right once per range (560 ms Calm, clip from 0 to the full
  width) and the target band fades up with it; the end dot lands when the line arrives. A new
  reading or a recomposition does not redraw it. Reduced: shown whole at once.
- Now's 3-hour bar strip arrives with its card's entrance; its bars don't animate.

### Loading
Keep it quiet: a spinner or a thin bar only when something is actually being fetched (Following,
the food search). If a skeleton shimmer is ever added it must be subtle: a 6 % ink sweep on `LOOP`,
stopping the moment content arrives. Reduced: a static placeholder, no shimmer.

### Ambient (existing, from the brand spec)
- LIVE dot: opacity 1 to 0.35 over `LOOP`. Reduced: holds solid.
- Scan ripple: three rings, `LOOP`, staggered by a third. Reduced: a static target.
- Warm-up glow: breathes over `LOOP`. Reduced: holds still.

### Navigation and surfaces (existing)
- Screen push / pop under You: 30 dp from the trailing edge (mirrored in Arabic) + fade, `CALM`.
- Bottom sheets: scrim and a soft haptic on present.
- Bottom-bar destinations: fade in `BASE` / out `QUICK`.

Reduced: each becomes a 120 ms crossfade in place.

## Checklist for new motion
- Is it on an alarm, low, urgent or heading-low surface? Then no.
- Opacity plus at most 12 dp, 180 to 420 ms (fills and draws up to 560 ms)?
- Does it play once (first appearance, a real data change), not on recomposition or scroll?
- With "Remove animations" on, is it at most a 120 ms fade, with no travel?
- Does it use the tokens above, not raw numbers?
