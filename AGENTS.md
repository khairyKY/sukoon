# AGENTS.md — Sukoon (سكون)

Instructions for any AI coding agent (Claude Code, Codex, OpenCode, Copilot, Antigravity) working in this repo. Read this before touching code.

## What this project is

Native Android CGM companion app for the FreeStyle Libre 2 (EU) — a calmer, open-source replacement for DiaBox. Kotlin 2.0 + Jetpack Compose (Material 3) + Room. The maintainer (Kai) is a Type 1 diabetic building this for daily personal use. Treat correctness of glucose data as a safety issue, not a feature.

## Non-negotiable safety rules

Never weaken, bypass, or "simplify away" any of these, even if a test or refactor would be easier:

1. **Not a medical device.** The first-launch disclaimer gate (`ui/onboarding/DisclaimerGateScreen.kt`, `data/prefs/DisclaimerPrefs.kt`) must block all readings until accepted.
2. **Calibration caps are bounded** so calibration math can never mask a hypoglycemic value. Don't widen the caps.
3. **Stale-data guard:** readings grey out to `---` after 10 minutes of silence. Never display an old value as current.
4. **No silent failures** in the BLE/NFC data path — surface errors through `SourceStatus`.
5. When in doubt on anything glucose-math related: stop and ask Kai instead of guessing.

## Architecture (dual-track)

Everything consumes glucose data through one abstraction — `app/src/main/java/com/sukoon/app/data/source/GlucoseSource.kt`:

- **Track A (UI/product):** runs on `SimulatedSource.kt` so UI work never blocks on hardware. Home screen, time-ranged graph, metrics.
- **Track B (hardware):** Libre 2 reverse-engineering under `data/source/libre/` — `Iso15693.kt` (NFC framing + CRC), `LibreKeyDerivation.kt`, `LibreNfcSession.kt`. This code is correctness-critical; small, heavily-tested changes only.

Key dirs:

```
app/src/main/java/com/sukoon/app/
  data/db/          Room entities + AppDatabase (schemas tracked in app/schemas)
  data/repository/  GlucoseRepository + mappers
  data/source/      GlucoseSource, SimulatedSource, libre/ (Track B)
  domain/           GlucoseMetrics, GlucoseUnit
  ui/               home/, graph/, onboarding/, theme/, components/, navigation/
  di/AppContainer.kt  manual DI — no Hilt; follow the existing pattern
```

## Build & test

- Gradle 8.9, JDK 17, compileSdk 35, minSdk 26. Version catalog: `gradle/libs.versions.toml`.
- **Note:** the `gradlew` wrapper scripts are not committed (only `gradle/wrapper/gradle-wrapper.properties`). Use a local Gradle 8.9 (`gradle test`) or generate the wrapper (`gradle wrapper`) — ask before committing wrapper files.
- Unit tests: `gradle :app:testDebugUnitTest`. All tests must pass before any commit; Track B changes require tests (see `Iso15693Test` for the style).
- Room schema changes: keep `room.schemaLocation` output committed; never edit past schema JSON.

## Conventions (see CONTRIBUTING.md)

- Branches: `feat/#12-short-description`, `fix/#12-...`, `chore/#12-...` — always reference the GitHub issue.
- Conventional Commits with a `closes #N` footer.
- One issue per task from `docs/PLAN.md` §9; milestones map to phases.
- Match existing code style: comment density is low-but-purposeful, comments explain *why* (see minSdk comment in `app/build.gradle.kts`).

## Design system — "calm water"

- Tone: calm, reassuring; no red panic screens. Stability is stillness.
- State colors are **dual-encoded** (color + motion/shape, never color alone): sage teal `#3E7A63` in-range, warm amber high, coral red low/urgent. Theme lives in `ui/theme/Color.kt` / `Theme.kt`.
- Derive any new visual from `ui/theme/` — never invent placeholder palettes.
- Design reference docs: `docs/design-screens.md`, `_design-export/`.

## Docs index

| File | What |
|---|---|
| `docs/PLAN.md` | Master plan, phased roadmap (§9) |
| `docs/track-a-plan.md` / `docs/track-b-plan.md` | Per-track detail |
| `docs/research/diabox-apk-analysis.md` | DiaBox reverse-engineering notes |
| `docs/research/report-tech.md`, `report-product-ux.md` | Background research |

## Current state — update this section at every handoff

> Last updated: 2026-10-07

- Phase 0 on `main`: Track A items A1–A3 done (home screen, Room persistence, interactive time-ranged graph). 37 unit tests passing.
- Track B: ISO 15693 framing + CRC and key-derivation groundwork merged; no live-sensor session yet.
- Next up per roadmap: continue Phase 0/1 items in `docs/PLAN.md` §9.

## Working with Kai

- Address him as **Kai**; keep replies scannable, direct but gentle, no fluff. English for this project.
- He's a frontend-leaning developer (React/Tailwind strength); explain Android/Kotlin internals plainly — don't assume deep platform knowledge.
- **Surgical edits over rewrites.** Never rewrite a whole file when a focused diff works.
- **Never commit, push, or open a PR without his explicit OK** ("looks good, push it"). Run builds/tests freely.
- **Never auto-delete files** — always give him a review window first.
- At the end of a work session: update the *Current state* section above, then summarize what changed and what's next in your final reply.
