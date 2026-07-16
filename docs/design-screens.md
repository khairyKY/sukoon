# Sukoon — Screen Inventory (Design Scope)

The full list of screens to design. For a glucose app, the **states** of a screen are as much design work as the screens themselves (a calm Home vs. an urgent-low Home are effectively two designs) — states are called out where they matter.

Status legend: `[ ]` to design · `[x]` shipped. Update as you go.

**All checked off 2026-07-16** — confirmed shipped per `_design-export/Sukoon Screen Map.md`: every item below is designed, in both English and Egyptian Arabic. Design-shipped ≠ implemented — see `docs/PLAN.md` §9 phases / Akiflow for build status. Disclaimer gate (§1) is the only one with real Compose code behind it so far.

---

## 1. First-run / onboarding
- [x] Splash / brand intro (the calm-water moment)
- [x] Disclaimer gate — "not a medical device," must-accept
- [x] Sign in / Sign up (Supabase auth — needed for cloud sharing)
- [x] Permissions priming — Bluetooth, NFC, notifications, battery, phone-call (why each)
- [x] Units & target range setup — mg/dL vs mmol/L, personal range
- [x] Sensor pairing — NFC scan to activate → BLE connect
- [x] Warm-up — the 60-min countdown, no reading yet

## 2. Home / Now (the hero — one screen, several states)
- [x] In-range (calm)
- [x] High / Low
- [x] Urgent (alarm state)
- [x] Warm-up (no data yet)
- [x] Signal lost / stale (greyed `---`)
- [x] No sensor connected

## 3. Graph & history
- [x] Full graph view — interactive, time-range toggles (3/6/12/24h)

## 4. Logbook
- [x] Logbook list — readings + logged events, timeline
- [x] Quick-entry sheet — add carb / insulin / finger-prick (one-tap, anti-mySugr flow)
- [x] Empty state

## 5. Insights / Reports
- [x] Insights overview — Time-in-Range bar, GMI, variability cards
- [x] AGP view — the 14-day pattern
- [x] Metric explainer — what GVI/PGS mean, "what's a good score"
- [x] PDF export preview — doctor-shareable report

## 6. Alarms & emergency
- [x] Alarm settings — high / low / urgent thresholds, snooze rules
- [x] Active urgent alarm — full-screen, DND-bypassing
- [x] Emergency countdown — "Calling [contact] in 60s — I'm OK" (the L4 screen)

## 7. Sharing / contacts
- [x] My contacts — who's following me
- [x] Invite / approve follower — the request flow
- [x] Per-contact settings — share toggle + designate emergency contact
- [x] Follower (viewer) mode — read-only view of someone else's glucose (a stripped-down Home)

## 8. Sensor / device
- [x] Sensor status detail — age, time remaining, connection health, stop/re-scan
- [x] Calibration — finger-prick entry with the safety caps

## 9. Settings
- [x] Settings home — the menu
- [x] Units & targets
- [x] Theme — light / dark
- [x] Integrations — Nightscout, Health Connect
- [x] Account / profile
- [x] About — re-read disclaimer, version

## Not screens, but need designing
- [x] Notifications — one look per severity (info / warning / urgent)
- [x] Home-screen / lock-screen widget — with its own in-range / low / stale states
- [x] Persistent service notification — the "Sukoon is running" bar

---

## Suggested design order
**MVP:** groups 1, 2, 3 + widget + notifications — a working, glanceable, alarming app.
**Then:** 4 (logbook), 8 (sensor/calibration), 9 (settings).
**Standout portfolio features:** 5 (insights), 6 (emergency), 7 (sharing) — once the core loop feels right.
