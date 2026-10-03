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

## Next, in order
1. ⬜ Graph overhaul: current value + trend arrow header, minimal y/x axis labels, % in range bar + GMI for the range, minute-by-minute readings list.
2. ⬜ Glucose in the status bar: the sensor notification shows value + arrow + age; status-bar icon draws the number.
3. ⬜ Emergency contacts + escalation (A8): contacts picker; urgent low unacknowledged for N min (or silence after a low) → cancellable 60 s countdown → SMS all + call primary.
4. ⬜ User guide: first-run walkthrough + Help page (pairing, permissions, alarms, widgets, Nightscout, AI, insights).
5. ⬜ Insights into the AI brief (Ask explains patterns using the same numbers).
6. ⏸ Followers + sharing (A6) on Supabase — waiting on Kai's project URL + anon key. Then: auth, readings table, RLS, follow requests/approvals, follower viewer mode with alarms.
7. ⏸ Health Connect: write glucose, read MyFitnessPal meals into the logbook — needs the Health Connect library (DNS on the dev PC is down at the moment).
8. ⬜ Sensor lifecycle: expiry warnings (24 h / 1 h), real warm-up countdown, "sensor ended" state, start-new-sensor guidance.
9. ⬜ Settings completion (A10): units mg/dL ↔ mmol/L everywhere, theme (auto/light/dark), quiet hours for highs.
10. ⬜ Calibration (capped, opt-in) — B10; fits from the logged finger-pricks.
11. ⬜ Insulin on board on Home (rapid insulin still active).
12. ⬜ Reports: 14-day AGP percentile chart + PDF export for the doctor (A9 / #19).
13. ⬜ Logbook polish: edit an entry's time, photo attached to meals.
14. ⬜ Localization + motion sweep (A12).
15. ⬜ Wear OS complication/tile (#16).
