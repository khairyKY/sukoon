# Sukoon (سكون)

A native Android continuous glucose monitoring (CGM) companion app for the FreeStyle Libre 2 (EU), built as a calmer, less cluttered replacement for the community app DiaBox.

"Sukoon" is Arabic for calm/stillness — chosen because that's the actual clinical goal (stable, non-erratic glucose), not just a mood. The design language follows from it: a still-water/ripple visual metaphor, motion and texture as a second accessible channel alongside a colorblind-safe palette, and reassuring copy instead of clinical readouts.

> **Not a medical device.** This app is for personal/educational use. Never make insulin or treatment decisions from its readings alone — confirm with a finger-prick meter. See the in-app disclaimer gate (shown on first launch).

## Status

Early, active development. See [`docs/PLAN.md`](docs/PLAN.md) for the full product/technical plan and phased roadmap, and [`docs/design-screens.md`](docs/design-screens.md) for the screen inventory (all screens are designed; most aren't implemented yet). Work is tracked via GitHub issues/milestones mirroring the plan's phases.

## Why this exists

DiaBox works but is closed-source, visually dated, and cramped. This project reimplements the same core idea — reading a Libre 2 sensor's encrypted BLE stream directly, no NFC re-scan required — with:
- A redesigned, colorblind-safe, dual-encoded (color *and* motion/texture) UI
- Smart alerting with an emergency-contact escalation path (unacknowledged-alarm trigger, cancellable countdown, then auto-call)
- Free real-time cloud sharing with chosen contacts (Supabase — chosen specifically for being SDK-agnostic, so a future macOS/Windows viewer isn't locked out)
- The clinical metrics DiaBox already has (TIR, GMI, SD/CV, GVI, PGS) — reimplemented, unit-tested, and explained in plain language

Full rationale for every decision above lives in `docs/PLAN.md`, distilled from two deep-research reports in `docs/research/`.

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Room (SQLite) for local persistence
- Coroutines/Flow throughout
- Supabase (Postgres + Realtime + Auth) for cloud sync/sharing
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

Direct Libre 2 BLE reading is a clean-room reimplementation from publicly documented protocol research — see `docs/PLAN.md` §1 for the licensing rationale (deliberately not copying GPL'd community code).

## Building

Open in Android Studio (this repo was scaffolded without access to the Android SDK/Gradle locally, so the first sync in Studio is the first real build verification it's had). `minSdk 26`, `compileSdk 35`.

```
./gradlew assembleDebug
./gradlew test
```

## Contributing

Solo project for now, but run like a real one — see [`CONTRIBUTING.md`](CONTRIBUTING.md) for branch naming and commit conventions.

## License

TBD — private repo while the Libre BLE reverse-engineering work is unfinished and unvalidated against real hardware.
