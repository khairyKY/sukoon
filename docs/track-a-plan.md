# Track A — Full App Delivery (on SimulatedSource)

**Goal:** a complete, polished, navigable, daily-driver-shaped app where *every* screen works and *every* feature functions against simulated glucose. The only thing it can't do is read the real sensor — that's Track B, which slots in behind `GlucoseSource` with zero UI changes.

**Definition of done (Track A):** install → onboard → see live glucose → get alarms → log a meal → view trends/AGP → share with a follower who sees your data → trigger an emergency countdown → read it from a home-screen widget — all in EN + Egyptian Arabic, light + dark, **with no real sensor, entirely on simulated data.** That is a legitimately demoable, portfolio-grade app.

**Independence:** Track A has *zero* dependency on Track B. It consumes whatever readings arrive through the `GlucoseSource` interface; the simulator is just one source. Nothing here is throwaway demo scaffolding — it is the permanent app shell both tracks feed.

---

## Status legend
✅ done/merged · ◐ partially exists · ⬜ not started

## A0 — Foundation ✅ (done, on `main`)
`GlucoseSource` abstraction · Room schema (readings/sessions/events/calibration) · metrics module (TIR/GMI/SD·CV/GVI/PGS/trend) + unit tests · unit-system handling · disclaimer gate · Home screen (all 7 states) · SimulatedSource wired into Home via `HomeViewModel` + pure `HomeUiStateMapper` (PR #27).

---

## A1 — App navigation shell  ⬜  · *(new issue)*
Replace Home's static decorative bottom bar with real Compose Navigation.
- `NavHost` + 5-tab bottom nav: **Home · Logbook · Insights · Sharing · Settings** (PLAN §4).
- Tab state preserved across switches (`rememberSaveable` / nav back-stack).
- The 4 non-Home tabs land as stub screens, filled in by later milestones.
- **Accept:** navigate all 5 tabs; Home shows live sim data; back-stack behaves.

## A2 — Persistence pipeline  ⬜  · *(new issue)* · **architecturally central**
Right now the sim feeds Home through an in-memory deque; nothing is persisted. Logbook/Insights/Graph all need readings in Room.
- A `GlucoseRepository`/collector subscribes to the active `GlucoseSource` and writes each reading to `ReadingEntity`; tracks the active `SensorSessionEntity`.
- Screens read from Room via Flows (single source of truth), not from the source directly.
- Home's `HomeViewModel` re-pointed at the repository (its rolling window becomes a Room query).
- **Accept:** kill + relaunch the app → history survives; Home, Graph, Logbook all read the same persisted stream.
- *Note:* the foreground service that keeps this collector alive is A10; the collector *logic* lands here.

## A3 — Graph & history screen  ⬜  · *(new issue)*  · design 8j
Full interactive glucose graph from Room.
- Time-range toggles **3h / 6h / 12h / 24h**; tap-to-inspect a point; range-shaded target band.
- **Accept:** toggles re-window correctly; renders from persisted sim data; empty range handled.

## A4 — Logbook  ⬜  · *(new issue)*  · design 8k/8l + 6g/7l
- Timeline list: readings interleaved with logged events (meals/insulin/finger-prick/calibration).
- **Quick-entry sheet** — one-tap-first per PLAN §11 (number + type is all that's required; tags/photos optional). Writes `EventEntity`.
- Edit/delete manual entries; empty state.
- **Accept:** log a 30g carb in ≤2 taps; it appears on the timeline and as a pin on the graph.

## A5 — Alerts engine (L1/L2/L3)  ⬜  · **issue #8**  · design 6f/7k, 8q
- Notification channel per severity; **DND-bypass** for urgent; deliberate-confirm snooze.
- Alarm settings screen: high/low/urgent thresholds, snooze rules.
- Predictive slope-based "heading low" (L1) using the metrics trend.
- **Accept:** drive the sim into a low → L2 fires; into <55 → L3 fires loud + DND-bypass; snooze needs a deliberate action.

## A6 — Cloud + sharing (Supabase)  ⬜  · **issues #11, #12, #13**  · design 8b, 8s/8t/8u, 6d/7i
- Supabase backend: Auth, `readings` table, **RLS** (followers read only approved users).
- Sign-in/up wired into onboarding (A7).
- Sharing tab: my followers, invite/approve flow, per-contact settings + emergency designation.
- **In-app follower viewer mode** — read-only Home variant on a Realtime subscription (no source logic).
- **Accept:** device A (sim) syncs; device B logs in, is approved, watches A's live glucose.

## A7 — Onboarding flow  ⬜  · *(new issue)*  · design 6a/7f, 8c, 8d, 6b/7g, 6c/7h
- Splash/brand intro · permissions priming (BT/NFC/notifications/battery/phone — the *why* of each) · units & target-range setup · sign-in (A6) · sensor-pairing + warm-up **screens** (real NFC hookup is Track B; here the UI + a sim "pair" path).
- **Accept:** first launch walks the full sequence once; returning launch skips to Home.

## A8 — Emergency escalation (L4)  ⬜  · **issue #10**  · design 8r
- Trigger = **unacknowledged L3** for N seconds (+ the BLE-silence-after-low path, testable by stopping the sim).
- Cancellable countdown ("Calling [contact] in 60s — I'm OK") → `ACTION_CALL`. Never instant-dial.
- Push to approved followers on L3/L4 (A6 transport).
- **Accept:** sim urgent low, ignore it N s → countdown appears; cancel works; timeout would dial (verify with a safe test number).

## A9 — Insights / Reports  ⬜  · **issues #15, #19**  · design 8m/8n/8o/8p, 5c/7d
- TIR stacked bar · GMI gauge · CV/GVI/PGS cards each with a plain-language "what's good" explainer · **AGP** 14-day median overlay · **Export to PDF**.
- All values come from the metrics module over Room data.
- **Accept:** metrics match hand-computed values for a known sim window; PDF opens/shares.

## A10 — Settings + sensor/device  ⬜  · **issue #14 (UI half)** + *(new issue)*  · design 8v/8w, 8x–8ab
- Settings home · units & targets · theme (light/dark/auto) · integrations toggles · account/profile · about (re-read disclaimer, version).
- Sensor/device status screen (age, health, stop/re-scan) · **calibration entry UI** with the safety caps (the *application* of calibration is Track B B10).
- **Data-source picker** (Simulated ↔ Libre) — the toggle that makes Track B swappable.
- **Accept:** flip units mg/dL↔mmol/L updates everywhere; theme switch works; calibration entry rejects out-of-cap values.

## A11 — Widget + system surfaces  ⬜  · **issue #9** + *(new issue)*  · design 8ad, 8ae, 8ac
- **Glance** home/lock-screen widget with in-range/low/stale states.
- Persistent **foreground-service** notification ("Sukoon is running") that hosts the A2 collector.
- Notification looks per severity (ties to A5).
- **Accept:** widget updates as sim moves; service survives screen-off; stale state greys out per PLAN §5.

## A12 — Localization sweep + motion polish  ⬜  · **issue #20** + *(new issue)*
- Audit every screen for EN + AR string parity + RTL correctness (we build bilingual per-screen, so this is a sweep, not a rebuild).
- Motion pass to the **calm-water/ripple** identity (`_design-export/Sukoon Motion Spec.dc.html`): ripple on new reading, calm↔turbulent transitions as the second accessible channel (PLAN §5).
- **Accept:** switch device to Arabic → whole app mirrors correctly; motion reads as "calm," not busy.

---

## Track A tail / ecosystem (post-core, still sim-compatible)
Not part of the core "full app" but run fine on sim and land after A1–A12:
- **Wear OS** complication/watch face (issue #16, design 6h/7m) — sim data streams to watch.
- **Nightscout** upload (issue #17) · **Health Connect** write (issue #18) — export sim data.

## Cross-track shared work (see Track B)
- Foreground service: A11 builds it for the collector; Track B B9 hardens it for 24/7 BLE.
- Calibration: A10 = entry UI (capped); Track B B10 = applying it to real raw→glucose.
- Warm-up real minutes: Home's `WarmingUp` currently shows a placeholder; Track B B9 supplies real countdown from the sensor session.
