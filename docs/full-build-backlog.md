# Sukoon — full build backlog

Kai's call (2026-10-03): build the whole app, not just the MVP. This is the living list; tick items
as they land and keep the order unless Kai reprioritizes. Track A/B plans hold the original detail.

Legend: ✅ done · ◐ in progress · ⬜ to do · ⏸ blocked (reason)

## Done so far (2026-10-02 → 03)
- ✅ Direct Libre 2 / 2 Plus EU connection: NFC pair (FRAM CRC-gated) + BLE stream, direct-first reconnect + watchdog, foreground service. Validated on Kai's sensor vs DiaBox (±3 % on minute readings).
- ✅ Alarms: urgent low (always on), low, going low, high, no signal; alarm-stream sound, full-screen takeover, DND bypass; custom values.
- ✅ Setup checklist (notifications, Bluetooth, battery, full-screen, overlay, DND, OEM battery page) + Home banner.
- ✅ Home-screen widget 1×1 → half screen, per-widget options; app icon.
- ✅ AI: Ask chat (Gemini) + carb estimate from photo/text.
- ✅ Nightscout upload (entries + treatments), CSV export (Kai's column layout), save interval 1–60 min.
- ✅ Logbook: rapid vs basal insulin, meal + insulin in one save with pre-bolus minutes.
- ✅ Insights engine (consensus targets, CV, recurring lows/highs, dawn, meal outcomes, pre-bolus, stacking, 500/1800 formulas) + acknowledgement gate.
- ✅ Home shortcuts: log food / log insulin.
- ✅ Feedback everywhere: toasts for Nightscout (real `verifyauth` connection test on save), sensor check/connect/forget + "readings are coming in", source switch, AI key, logbook saves, export, widget options.
- ✅ Finger-prick checks (Kai, 2026-10-03): own logbook type (meter mg/dL, 20–600), shown beside the sensor reading at that moment (20/20 agreement band), rings on the graph at their value, Nightscout "BG Check", CSV `fingerstick` rows, AI brief, "Sensor vs finger-pricks" insight. Record only — never changes readings.
- ✅ Alarm sounds (Kai, 2026-10-03): per alarm, a phone sound (system picker) or your own audio file (persisted access, checked playable on pick), 5-s preview; played by Sukoon itself with a fallback chain so a broken file can never silence an alarm.
- ✅ Logbook: each entry shows the glucose at its moment (nearest reading within 5 min, trend arrow, low/high tint).

## Next, in Kai's order (2026-10-03)
1. ✅ Graph overhaul: current value + trend arrow header, range-colored line with gap breaks, only 70/180 + clock labels, adaptive y-axis, 3h–14d ranges, time-in-range bar (5 bands) + average + GMI (same math as Insights), every reading listed newest first with day headers.
2. ✅ Emergency contacts + escalation (A8): contacts from the phone book or typed (stored international), your name for the texts. Urgent low, or a no-signal alarm that began while low, unanswered for N min (default 10; any button or swipe restarts the clock) → 60 s countdown (full screen, urgent sound, "I'm OK", wake lock) → text everyone + call the first; again after 30 min if still unanswered; "back to N" text on recovery; "I'm OK, tell them" after. Optional map link. Home "Alert my emergency contact" = text all / WhatsApp / call. Setup checklist asks for SMS + call once a contact exists. Automatic WhatsApp needs the WhatsApp Cloud API from a server → with #3.
3. ◐ Followers + sharing (A6) on Supabase: ✅ accounts, one-time invite codes, RLS schema (supabase/migrations — Kai runs it once), reading upload, followers/following lists, live follower viewer, background follower alerts (special-use FGS). ⬜ automatic WhatsApp alerts (Edge Function + WhatsApp Cloud API; needs Kai's Meta setup), ⬜ iOS followers via a web app (pending Kai), ⬜ start on boot.
4. ✅ Health Connect: MyFitnessPal (and other apps') meals in as Logbook carb entries via the change log (edits and deletions follow), sensor readings out as interstitial blood glucose (client ids, no duplicates); syncs every 15 min, on every return to the app, and on "Sync now". Client pinned at 1.1.0-beta01 (1.1.0 needs compileSdk 36 + AGP 8.9.1).
5. ⬜ Sensor lifecycle: expiry warnings (24 h / 1 h), real warm-up countdown, "sensor ended" state, start-new-sensor guidance.
6. ⬜ Calibration (capped, opt-in) — B10; fits from the logged finger-pricks.
7. ⬜ Insulin on board on Home (rapid insulin still active).
8. ⬜ Reports: 14-day AGP percentile chart + PDF export for the doctor (A9 / #19).

## After those
- ⬜ Glucose in the status bar: the sensor notification shows value + arrow + age; status-bar icon draws the number.
- ⬜ User guide: first-run walkthrough + Help page.
- ⬜ Insights into the AI brief; glucose-at-entry in the CSV `glucose_mgdl` column and the AI brief; "glucose now" on the entry sheet.
- ⬜ Settings completion (A10): units mg/dL ↔ mmol/L everywhere, theme (auto/light/dark), quiet hours for highs.
- ⬜ Logbook polish: edit an entry's time, photo attached to meals.
- ⬜ Localization + motion sweep (A12).
- ⬜ Wear OS complication/tile (#16).

Rules: no APK builds or installs unless Kai asks (compile + unit tests only); no AI attribution in commits or PRs.
