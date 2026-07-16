# Sukoon (سكون) — Product & Technical Plan

**Name locked (2026-07-15): Sukoon** — Arabic for calm/stillness. Working codename was "DiaBox v2"; project folder is `D:\Coding\sukoon`. The name is the design brief: calm-water/ripple visual language, motion+texture as a second accessible channel alongside the colorblind-safe palette (calm blues/teals = in-range, turbulence = out-of-range), reassuring tone of voice. Full theme exploration to happen at the UI-generation step.

**Decisions locked (2026-07-13, revised 2026-07-13):** Android-only, native Kotlin · **Direct Libre 2 (EU) BLE is the primary/first data source** (LibreLinkUp cloud dropped from near-term plan — unreliable in Egypt for EU sensors) · goal = BOTH daily-driver + portfolio · new features: **emergency auto-call, free realtime cloud sync, live sharing with chosen/emergency contacts**.

Sources: two Gemini deep-research reports in `research/` — `report-tech.md` (how-to-build) and `report-product-ux.md` (product/UX). This plan is the actionable merge, updated after user feedback.

**Reference material:** two DiaBox APKs sit in the project root (`DiaBox_FullVerisonWithWearOS_2022_12_06_beta.apk`, `diaboxkotlin_v2026_02_14_07_24.apk`) — for decompiling/studying its BLE + i-Algorithm implementation as reverse-engineering reference, not for redistribution.

---

## 0. Strategy in one line
Build against a `GlucoseSource` abstraction (still valuable for UI dev and testing), but **go straight at direct Libre 2 EU BLE** as the real target — no cloud-bridge detour, since LibreLinkUp doesn't reliably serve EU sensors in Egypt. Add a dedicated realtime backend (separate concern from sensor reading) purely for contact-sharing and emergency alerting.

---

## 1. The data-source abstraction
```
interface GlucoseSource {
    val readings: Flow<GlucoseReading>   // value, trend, timestamp, source
    val status: StateFlow<SourceStatus>  // connected / warming-up / stale / error
    suspend fun connect(); suspend fun disconnect()
}
```
Implementations:
1. **SimulatedSource** — replay a recorded CSV or synthetic sine+noise. Kept as a dev/test harness (fast UI iteration without waiting on sensor pairing, doubles as an automated test fixture) — **not** a shipped fallback anymore.
2. **LibreBleSource** — **primary target, built early and tested thoroughly on real hardware.** Libre 2 EU: one-time NFC unlock (NfcV / ISO 15693 shared-key exchange) → continuous encrypted BLE stream (AES-CFB) → glucose conversion. Reference the **open-source** implementations: xDrip+ (OOP2), Juggluco, GlucoseDirect, DiaBLE (raw hex/GATT testbench).
   - **DiaBox is a dead end as a code reference (confirmed by APK analysis, `research/diabox-apk-analysis.md`):** both APKs are Baidu-Shell-protected — the real BLE/UUID/decryption/calibration logic lives in native `.so` libraries (`libaescfb.so`, `libjniLibre.so`, `libcalibrat2.so`, plus 4 firmware-versioned algorithm libs v112F/v113B/v115G/v116A), not in readable Java/Kotlin. So we build from the open community implementations, not from DiaBox.
   - **Calibration** ("i-Algorithm") is independently reimplementable — DiaBox cites paper **PMC4764224** (least-squares regression); implement as capped time-weighted linear regression (see §8 caps).
   - Adopt DiaBox's **sensor auto-detection** pattern: EU BLE-Direct vs US/CA/Sense vs NFC-only paths (we only need EU BLE-Direct now, but structure for it).
   - **Licensing — DECIDED (2026-07-15): clean-room reimplement.** Write Sukoon's decoder from the public Libre 2 AES key-derivation spec (the documented *method*), do NOT copy xDrip+/Juggluco source. Those are GPL-3.0 — copying their code would force Sukoon to be GPL (all source public, freely re-distributable), which is wrong for an owned portfolio + public app. Use them only to *understand* the approach, then implement independently. Result: Sukoon's code stays fully owned, under whatever license Kai chooses, no copyleft contamination.
3. **NightscoutSource** *(optional, later)* — only relevant as an *upload* target for community-standard reports (AGP), not as a read source.
4. ~~LibreLinkUpSource~~ — **dropped from near-term plan.** Abbott's LibreView/LibreLinkUp regional support historically excludes Egypt even for EU-purchased sensors; not worth building against an unreliable dependency. Revisit only if you confirm it actually works for your account/region.

---

## 2. Recommended stack
- **Kotlin + Jetpack Compose** (Material 3) — native wins decisively for continuous BLE (cross-platform pays ~18% BLE latency + ~60% battery penalty and waits on plugin maintainers for OEM bugs).
- **Room (SQLite)** exposed as **Flows** → live 1-min graph without blocking UI.
- **Foreground Service** (persistent notification) for the BLE collection engine — must survive screen-off, Doze, OEM killers.
- Coroutines/Flow throughout.
- **Glance** for lock-screen/home widget (glanceability is the whole point).
- **Notifications:** custom alarm engine, one channel per severity, DND bypass for urgent + emergency.
- **Battery survival:** prompt `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + OEM-killer guidance (Xiaomi/Huawei/Samsung kill background BLE).
- Later: **Wear OS** (Compose for Wear), **Health Connect** (`WRITE_BLOOD_GLUCOSE`).

---

## 3. Metrics module — pure Kotlin, fully unit-tested (your best showcase)
Deterministic math = clean unit tests. Capture:
- **Unit conversion:** `1 mmol/L = 18 mg/dL` (maintain mg/dL internally).
- **TIR brackets:** `<54` very-low · `54–69` low · `70–180` in-range · `181–250` high · `>250` very-high. Targets: TIR > 70%, TBR < 4%, severe (<54) < 1%.
- **GMI (%)** = `3.31 + 0.02392 × meanGlucose_mgdl`
- **SD** = population/sample std-dev; **CV** = `SD / mean × 100` (target CV ≤ 36%).
- **GVI** (Glycemic Variability Index) = path-length of glucose line ÷ length of flat baseline. Scale: 1.0–1.2 low · 1.2–1.5 modest · >1.5 high.
- **PGS** = `f(GVP) + g(MG) + h(PTIR) + j(N54,N70)` (piecewise sigmoids — full formulas in `report-tech.md §4`). Scale: <35 excellent · 35–100 good · >100 poor. Punishes severe lows hardest.
- **Trend arrow** from 15-min rate-of-change (mg/dL/min): `↓↓ <-2 · ↓ -2..-1 · → -1..1 · ↑ 1..2 · ↑↑ >2`.

---

## 4. Redesigned information architecture — 5 tabs
Replaces DiaBox's flat chart/log/settings. **Home · Logbook · Insights · Sharing · Settings.**

- **Home / Now** — giant tabular-figure current value + dynamically-angled trend arrow + delta; **sensor status pill** (time left "3d 14h", BLE connection state); range-shaded 3/6/12H graph with predicted-line extension; **FAB** to log carbs/insulin/exercise.
- **Logbook** — chronological readings interleaved with event tags (meals, insulin, calibrations); edit/delete manual entries.
- **Insights** — TIR **stacked bar**; GMI **gauge**; CV/GVI/PGS cards each with a plain-language "what's good" tooltip; **AGP** (14-day median overlay); **Export to PDF** for the endo.
- **Sharing** *(new)* — manage chosen/emergency contacts; per-contact live-share toggle; emergency-call contact + trigger thresholds; view the shareable follower link.
- **Settings** (de-chaos the DiaBox "kitchen sink") — Alarms (visual sliders + snooze rules) · Sensor/Device (NFC scan, start/stop, calibration matrix) · Integrations (Nightscout upload, Health Connect) · Cloud sync account · Theme + units.

---

## 5. Design system — portfolio-grade & safety-first
- **Colorblind-safe:** blue = low, teal/neutral = in-range, amber/orange = high — **never red/green alone** (~8% of men are red-green colorblind). **Dual-encode** every state with icon + text, not just color.
- **Hero number is king:** geometric sans (Inter/Roboto) with **tabular figures** so digits don't jump as they tick.
- **Glanceable in < 0.5s:** big widget, always-on friendly (diabetics check ~50×/day, often half-asleep or driving).
- **Stale-data guard:** after ~10 min with no data, grey out / strike through / show `---` — never present an old number as current (someone could bolus off it).
- Dark-first but themeable; **RTL/Arabic-ready** (fits your bilingual experience).

---

## 6. Alarm engine — hierarchy + emergency escalation
- **L1 info** (trending down): single vibration + soft banner.
- **L2 warning** (e.g. < 75): chime + sticky notification.
- **L3 urgent** (e.g. < 55): loud, **DND-bypass**, flashing; snooze requires a deliberate slide/confirm (no accidental sleep-dismiss).
- **L4 emergency escalation (new, decided):** trigger = **an L3 alarm going unacknowledged for N seconds** (catches unconscious/asleep cases before glucose gets catastrophically low, not just a raw threshold). Shows a cancellable countdown ("Calling [contact] in 60s — I'm OK") then auto-dials the designated emergency contact via `Intent.ACTION_CALL` (requires runtime `CALL_PHONE` permission). Never auto-dial with zero warning.
  - **Gap to close:** an unacknowledged alarm requires an alarm to have *fired* in the first place — if the BLE connection itself silently drops, there's no active L3 alarm to escalate from. Also treat **prolonged BLE silence following a recent low/borderline reading** as its own escalation path into L4, not just "no data" (ties into the stale-data guard, §5).
- **Predictive:** slope-based "predicted low in ~20 min" beats static thresholds.

---

## 7. Cloud sync & contact sharing (new capability, separate from sensor reading)
Purpose-built for **live sharing with chosen contacts** and **as the transport for emergency notification** — a different concern from reading the sensor, so it's a separate backend, not part of `GlucoseSource`.

- **Backend: Supabase** (Postgres + Realtime + Auth + Row Level Security), decided for exactly the reasons raised — free tier, and critically **not proprietary-SDK-locked**. Firebase's cross-platform reach stops at mobile/web; Supabase is "just" Postgres behind REST + websockets, so it's consumable from *any* future client — a macOS widget, a Windows widget, an iOS app — using plain HTTP/websocket calls, no bespoke SDK required. That directly serves the "universal, future desktop widgets" goal without committing to extra work now.
- **Data flow:** phone writes each new reading to a `readings` table scoped to the user; RLS restricts read access to explicitly-approved followers only.
- **Follower experience (decided): companion login in the same Android app.** A follower logs into the app with their own account and, once approved, gets a read-only "viewer" mode showing the followed person's live data. Requires: Supabase Auth (accounts), a follow-request/approve flow, and a viewer UI variant of the existing Home screen (no BLE/sensor logic — just a Supabase Realtime subscription).
- **The cross-platform path this unlocks (later, not now):** because BLE/NFC reading is the only genuinely Android-locked part, a **viewer-only** client (no sensor access, just reads the same Supabase table) is low-risk to build for iOS/macOS/Windows down the line — it's a thin realtime display, not a medical-device reverse-engineering app. Note this as the architecture's forward door; don't build it until it's actually wanted (Phase 4+).
- **Emergency push:** on L3/L4 alarms, also push a Supabase Realtime event / notification to approved followers, not just trigger the phone call.
- Nightscout upload (§1) stays as a *separate*, optional, community-standard export — not the sharing mechanism.

---

## 8. Safety must-haves (non-negotiable)
1. **First-launch disclaimer gate** — "This is NOT a medical device. Do not make treatment decisions from it. Confirm with a finger-prick." Tap-to-accept.
2. **Calibration caps** — bound slope/offset so one bad finger-prick entry can't silently skew all readings and mask a hypo.
3. **Stale-data handling** (see §5).
4. **Emergency-call false-positive guard** — always a cancellable countdown, never instant-dial (see §6).

---

## 9. Phased roadmap
- **Phase 0 — Foundation:** scaffold · `GlucoseSource` abstraction · Room schema · SimulatedSource (dev/test only) · metrics module + unit tests · unit-system handling · disclaimer gate.
- **Phase 1 — Core BLE + usable app:** **LibreBleSource** (Libre 2 EU direct — the main engineering effort, tested thoroughly on real hardware) · redesigned Home · design system · L1/L2/L3 alerts · Glance widget · self-healing BLE reconnect.
- **Phase 2 — Safety & sharing (the new asks):** L4 emergency-call escalation (unacknowledged-L3 trigger + BLE-silence-after-low path) · Supabase backend (Auth, `readings` table, RLS) · Sharing tab · follow-request/approve flow · in-app viewer mode · calibration flow (capped) · Insights tab (AGP/TIR/metrics).
- **Phase 3 — Ecosystem:** Wear OS complication · Nightscout + Health Connect export · AGP PDF export · localization.
- **Phase 4 — Cross-platform viewers (later, only if still wanted):** thin read-only clients of the same Supabase backend — iOS companion, macOS widget, Windows widget. No BLE/NFC involved, so low risk; build when actually needed, not speculatively.

---

## 10. Biggest risks & unknowns
1. **Abbott firmware changes** silently break BLE decoding — **confirmed by DiaBox carrying 4 separate firmware-versioned algorithm libs** (v112F/v113B/v115G/v116A) to cope with exactly this (incl. the 301/302 serials). You depend on the open-source RE community keeping pace; expect to hit this since Phase 1 leans entirely on direct BLE with no cloud fallback now. Structure the decoder so a new algorithm variant is a pluggable addition, not a rewrite.
2. **Android background-BLE instability** (status-19 drops) → needs robust self-healing reconnect — now higher-stakes since it's the only source.
3. **Calibration can be lethal if uncapped** → enforce bounds (§8).
4. **Emergency auto-call reliability** — `CALL_PHONE` is a sensitive runtime permission; foreground-service timing must stay accurate even with screen off; the unacknowledged-alarm trigger only works if an alarm actually fires, so BLE-drop must be its own escalation path too (§6); test the countdown/cancel flow extensively before trusting it.
5. **Free-tier cloud limits** — fine at personal scale (1 user, few contacts); revisit if usage grows.
6. **Not store-distributable** (sideload only; banned from Play/App Store) — fine for portfolio + personal use; frame it that way.

## 11. Future spec (noted, not yet scoped): Food / Glucose / Instrument logging
Not part of near-term phases — captured now so it isn't lost, and so whoever designs it later doesn't repeat mySugr's mistake.

- **The anti-pattern to avoid (mySugr):** exhaustive mandatory tagging/categorization on every entry — meal type, mood, activity, photo prompts — turns a 5-second log into a chore, which is exactly why people stop logging.
- **Design principle:** quick-entry-first. A carb/insulin/BG entry should take one tap + one number by default; tags/photos/notes are optional, never required.
- **Three loggable domains to eventually support:**
  - **Food:** quick carb-count entry (number + optional name/photo), shown as pins on the glucose graph (already scoped as event logging, §9 Phase 2).
  - **Glucose (manual):** finger-prick BG entries as their own loggable event — not just calibration input — visible in Logbook, plotted alongside the CGM trace.
  - **Instruments:** track insulin delivery devices/consumables — pen or pump in use, dose, injection site, consumable inventory (strips remaining, pen/reservoir expiry) — reuse the "status pill" pattern already used for sensor age (§4).
- No phase assignment yet — revisit once Phase 2's event logging ships and it's clear what's actually missing.

---

## Reference projects
xDrip+ (GPL-3.0 — OOP2 ref, alerting, smoothing) · Juggluco (Libre 2/3 BLE) · GlucoseDirect + DiaBLE (protocol/hex testbench) · Nightscout (upload REST API, only as export target now) · Sugarmate (emergency-call precedent).
