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
3. ✅ Followers + sharing (A6) on Supabase: accounts, one-time invite codes, RLS schema (supabase/migrations — Kai runs it once), reading upload, followers/following lists, live follower viewer, background follower alerts (special-use FGS). Followers use the app (no web page). Decided 2026-10-03: no WhatsApp Business/Cloud API — alerts stay SMS + call, WhatsApp is the manual pre-filled option.
4. ✅ Health Connect: MyFitnessPal (and other apps') meals in as Logbook carb entries via the change log (edits and deletions follow), sensor readings out as interstitial blood glucose (client ids, no duplicates); syncs every 15 min, on every return to the app, and on "Sync now". Client pinned at 1.1.0-beta01 (1.1.0 needs compileSdk 36 + AGP 8.9.1).
5. ✅ Sensor lifecycle: real warm-up countdown on Home (from the pairing's start), "sensor ended" Home state with "Connect a new sensor", last-day banner on Home, sensor card shows when it ends; one-time notices: ready after warm-up, a day left, an hour left, ended.
6. ✅ Calibration (opt-in, B10): weighted least squares on the newest 4 steady finger-pricks from 7 days; slope only with a 40+ mg/dL spread; caps ×0.8–1.25 and ±20 mg/dL; pairs >40 mg/dL/40 % apart or taken on a fast arrow left out; a reading under 70 is never raised. Stored readings stay raw; the repository applies it on the way out (screens, alarms, uploads).
7. ✅ Insulin on board: rapid doses on the exponential activity curve OpenAPS/Loop use (peak 75 min, 5 h by default; You → Insulin), basal excluded; shown on Home beside the shortcuts and in the Logbook sheet before another dose ("doses close together add up").
8. ✅ Report (Trends → Report): AGP percentile bands by time of day (5–95, 25–75, median) over 7/14/30 days with time active, mean, GMI, CV and time in ranges; one-page A4 PDF (PdfDocument) with the consensus targets, shared through the share sheet. Same renderer for screen and PDF.

## After those
- ✅ Start on boot and after updates (BootReceiver).
- ✅ Glucose in the status bar: the sensor notification shows value + arrow + age; the number is the status-bar icon.
- ✅ User guide: You → Guide (12 topics, EN/AR) + a getting-started card on Now.
- ✅ Insights into the AI brief (dose rules deliberately left out); active insulin in the brief; glucose-at-entry in the CSV and the brief; "glucose now" on the entry sheet.
- ✅ Settings completion (A10): theme (like the phone / light / dark), quiet hours for highs (lows always sound). mmol/L dropped: Kai and his father use mg/dL (2026-10-03).
- ✅ Logbook polish: when it happened (now, 15/30/60 min ago, or a picked time; edits can move it); meal photos (taken or picked, also used by the AI estimate; thumbnails in the list; replace/remove).
- ✅ Localization + motion sweep (A12): EN/AR parity checked (589 strings each); motion per the design's spec — tokens (ui/theme/Motion.kt), live pulse, reading count-up, graph draw-on, list/card cascade, urgent-low entrance + breathing reading, button press dip, quick tab fades, haptic on save; all honour the phone's "Remove animations".
- ✖ Wear OS: dropped, no Wear OS watch (Kai, 2026-10-03).

## Redesign round (Kai, 2026-10-04)
Designs: the "Sukoon — onboarding, logbook & settings redesign" canvas (claude.ai artifact 9FCYkkZafJkRVJkJBDknvN), approved by Kai.
- ✅ Home messages: HomeBriefs picks the one observation that matters (trend and where it's heading, insulin still working, meals, activity, recent lows, time of day, today so far, sensor age) plus one next step, or "Nothing to do right now". Never a dose; lows follow the 15-15 rule and "I've treated it" starts the 15-minute wait.
- ✅ Home chart: the Trends chart for the last 3 hours (line over the 70–180 band) replaced the 15-minute bars, which averaged short lows away and stayed green.
- ✅ Every alarm full-screen (low, going low, high, no readings; yours and followed people's), colour by kind, with a snooze row (urgent low capped at 5 min); closes itself when the alarm is over.
- ✅ You divided: hub (profile, "N things need you", eight sections with live summaries) and a page per section.
- ✅ Adding entries, direction A (Kai's pick of three): tiles + "Again", a big number pad, meal + rapid insulin + when injected on one screen, undo. MyFitnessPal meals bring every nutrient (Room v3) and show MyFitnessPal's own icon (from the phone), what the meal did to glucose, and "Add insulin" when none was logged; pick a MyFitnessPal meal to add its insulin. "Powered by MyFitnessPal" not used: it would claim a partnership.
- ✅ Onboarding by role (wearer / follower / both; account optional for a wearer) with each role's steps; follower Home (their live number, chart, words, Call/Message) and Trends; a strip of followed people for "both".
- ✅ Connection note in You → Sensor: the last 24 hours' gaps and the longest (2026-10-07).

## MyFitnessPal, smarter insights, polish (Kai, 2026-10-06 → 07)
- ✅ MyFitnessPal workouts (10 min+) as Activity entries, today's steps and water, all through Health Connect.
- ✅ Home stats you choose (up to 4 of calories, carbs, protein, fat, rapid/long insulin, time in range, average, lows, steps, water), between the chart and the message.
- ✅ Dark mode done properly (theme-aware tokens, every hard-coded colour fixed) and the app's language chosen inside the app (Like the phone / English / Egyptian Arabic, Android 13+).
- ✅ Smarter insights: this week vs last, carbs per 10 g at each meal, fat/protein-rich meals 4 h later, highs after treating a low, lows after workouts, nights, bigger-carb days; each with its source, all in the AI brief.
- ✅ Alarms: "What your alarms did" (every alarm, what got through, every answer), "Can alarms reach you?", lows lift the alarm volume while they sound, "I'm treating it" on a low's notification, the high alarm defaults to 180.
- ✅ "I've treated it" answers whichever low is sounding, buzzes, counts down the 15 minutes, then "Still low. Have another 15 g." Home urgent starts where the urgent alarm does (under 55).
- ✅ docs/behaviour.md: every state, rule and answer.
- ✅ Brand fonts bundled (Hanken Grotesk, Newsreader; variable, OFL).
- ✅ You → Alarms and You → Apps & data rebuilt to their design boards; "See and hear each alarm".
- ✅ Long-acting reminder (You → Insulin), pull down on Home or the Logbook to sync MyFitnessPal, MyFitnessPal always in the meal entry, Home's bar strip back (a low or high in 15 min shows).
- ✅ Widgets (design "Round 3 · Widgets"): seven styles (Number, Number + graph, Graph, Ring, Today, Insulin with "Took it", Following), five landing sizes, and a widget maker (You → Appearance) with the real widget as its preview; "Add to home screen" places it at the chosen size; the launcher's reconfigure opens the maker too.
- ⏸ Why alerts didn't reach Kai on the 5c3be68 build: needs the phone on USB (logcat, notification channels, app ops).

## More sources, followers anywhere, updates (Kai, 2026-10-07)
- ✅ Follower web page for an iPhone (web/, GitHub Pages): enter the invite code, live number, chart, alarms while open.
- ✅ LibreLinkUp and Dexcom Share: follow people through them, or use one as this phone's own glucose (polled every minute).
- ✅ Release signing key (docs/signing.md), backup and restore of everything for the one-time key change.
- ✅ New alarm sounds, made for Sukoon (tools/make_sounds.py); calmer, shorter texts with the details moved to the Guide; Arabic counts with proper plurals.
- ✅ Updates: a dismissable notice when a newer Sukoon is on GitHub, Check for updates, and Update (downloads, checks it's signed by the same key, installs).
- ✅ Alarm state survives a restart; the sensor reconnects within seconds after an update.
- ✅ Injection sites (design "Round 4 · Where the insulin went"): "Where?" on rapid and long-acting entries with a two-figure body map (spot and side in one tap), the spot rested longest suggested, sites in the logbook rows, You → Insulin & logbook heat map for 30 days, a rotation insight, sites in the AI brief, a Guide topic (Room v4).
- ⏸ Bubble (Libre 1 transmitter): no hardware to test with.

## Dose suggestions, your range, sounds (Kai, 2026-10-07 → 08)
Research and rules: docs/research/dosing-sources.md; behaviour: docs/behaviour.md §8–9.
- ✅ v0.7.0 (first release-key build) and v0.7.1 (sound packs: Astral default, Orbit, Glass, Clear, Bells; You → Alarms → Sounds).
- ✅ Dose suggestions (beta): carb counting with a ratio per meal, correction factor, target, maximum, pen step (whole units by default); the maths on every new rapid entry; nothing when low or dropping; the AI may work out doses while it's on.
- ✅ Learning: every meal gets a verdict, clean meals teach a ratio per meal time, a least-squares fit over every usable meal and correction teaches the factor and ratios with nothing to start from; Use moves 20% at a time; a Home notice when a number is ready; CSV export; "How to make a meal count".
- ✅ Starting ratios for every age (logbook, else weight and age, else the common adult start), About you (weight, height, age, sex), parent lock (PIN) guarding alarms, range, doses and profile.
- ✅ Your range (70 to 120–180) on the everyday screens; onboarding sets it, each alarm's line, and the dose setup.
- ✅ Correction on Home when above your range and not eating.
- ✅ Main CSV adds injection site, source app and nutrients; Gemini moves to the next model when one hangs.
- 🗓 Egyptian food portions from «افهم سكر» once Kai has a copy to read from; guided tests (basal, ratio, correction).

## Meals from foods, sensor page, any day, cloud (Kai, 2026-10-08)
Behaviour: docs/behaviour.md §11–14.
- ✅ MyFitnessPal shares a running day total (one record per day, rewritten as a new record each time): split into meals by when they're logged, followed by date; the meal screen remade (dose first, *When did you eat?*, *What was it?*, one list of what's in it); every meal opens on it.
- ✅ Meals from foods: Open Food Facts search (Arabic and English, photos, *Sold in Egypt*), barcodes with Sukoon's own camera, 88 sourced home dishes, *Ask the AI*, your usual foods, typed amounts, the plate.
- ✅ Sensor page redone (state from the last reading, problems on top, life bar), Bluetooth-off warnings, 3-day notice; You opens on its hub, Troubleshoot on Sensor.
- ✅ Any day in the logbook and graph; unlimited meal photos read together by the AI; daily backup to a cloud app (opt-in); 12-hour clock; R8 on release builds.
- ✅ v0.8.0.
- 🗓 Check the home-dish values against the Egyptian National Nutrition Institute tables or «افهم سكر» when a copy is available.

Rules: no APK builds or installs unless Kai asks (compile + unit tests only); no AI attribution in commits or PRs.

iPhone (Kai's father, own sensor): not possible without Apple's paid developer account — free personal teams can't use NFC to pair a Libre. Options: an Android phone with NFC (runs Sukoon fully), or the $99/yr account + a Mac + an iOS port. Kai declined the license (2026-10-03).
