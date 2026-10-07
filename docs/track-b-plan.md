# Track B — Real Libre 2 EU Sensor Pipeline

**Goal:** a `LibreBleSource` that reads Kai's actual FreeStyle Libre 2 (EU) sensor and emits the same `GlucoseReading` stream the simulator does. When it's done, flip the data-source picker (Track A A10) and the *entire* Track A app runs on real glucose — no other changes.

**Why this is a separate track:** it's deep and narrow, safety-critical, and iterative against hardware. Track A never blocks on it. Everything here produces bytes; Track A consumes them.

**Hardware available in-house (the unblocker):** Kai is diabetic, always wears an EU Libre 2, has an NFC + Bluetooth Android phone, and can sacrifice an expiring sensor to experiment on. So the validation loop (B7) is runnable now — not blocked on hardware as earlier assumed. A spare/expiring sensor is ideal for the *plumbing* stages (B1/B2/B4) even if it no longer streams valid glucose — test the mechanics separately from the numbers.

**Licensing — UPDATED (2026-10-03, Kai): port MIT-licensed code with attribution.** The Libre 2 EU decoder is a Kotlin port of GlucoseDirect (MIT), whose Libre 2 code descends from DiaBLE and LibreTools (both MIT) — notices in `THIRD_PARTY_NOTICES.md`. GPL sources (xDrip+, Juggluco, xdripswift) stay off-limits so Sukoon stays fully owned. (Replaces the 2026-07-15 "clean-room only" rule; the goal — no GPL obligations — is unchanged.)

**Overriding safety rule:** nothing in B3–B6 is "done" until the validation loop (B7) shows decoded numbers matching LibreLink/finger-prick. Wrong crypto fails *silently* — it produces plausible-but-wrong glucose, which is a patient-safety risk, not a bug. Code that "runs" is not code that's correct here.

---

## Status legend
✅ done · ◐ partial · ⬜ not started

## B0 — NFC ISO15693 layer ✅ (merged, PR #25)
`data/source/libre/`: real ISO15693 command framing + CRC-B (unit-tested vs the published catalog check value), `NfcV` session wrapper, block reads, and the "Get Patch Info" custom command. Key derivation is a deliberate stub (`UnimplementedLibreKeyDerivation` throws). Flagged unverified: the patch-info command byte `0xA1` and the UID byte order — both need B1.

## B1 — NFC read: hardware validation  ⬜  · *(new issue)* · **needs real + spare sensor**
Prove the B0 layer against actual hardware.
- Verify NfcV tag discovery, UID read + **byte order** (the flagged unknown), `Get System Info`, block reads, and the **patch-info command** (`0xA1` — confirm or correct).
- From patch info, identify the **sensor generation/region** (drives B8).
- **Accept:** clean, repeatable NFC read of the sensor's memory blocks + patch info; UID order and patch-info command confirmed in code. (Spare sensor fine — this is plumbing, not glucose.)

## B2 — Activation / BLE-streaming enable handshake  ⬜  · *(new issue)*
Libre 2 ships with BLE *disabled*; a one-time NFC activation command provisions streaming and returns the material the BLE side needs.
- Reverse the NFC command sequence that unlocks BLE; capture the sensor's activation response.
- Persist activation state to the `SensorSessionEntity` (a sensor is activated once, then streams for its life).
- **Accept:** after activation, the sensor begins BLE-advertising; the activation response is captured and stored.

## B3 — Key derivation — ◐ ported (`Libre2.kt`: FRAM/BLE keys, streaming-unlock payload), awaiting B7  · *(new issue, replaces the stub)*
Implement `LibreKeyDerivation` from the public spec: UID + patch info + activation response → the BLE streaming key + connection PIN.
- Replace `UnimplementedLibreKeyDerivation`.
- Pure/testable where possible (known-vector tests once B7 yields a known-good vector from Kai's sensor).
- **Accept:** gated entirely on B7 — a derived key is only "correct" if the stream it unlocks decodes to matching glucose.

## B4 — BLE GATT connection + auth  ⬜  · *(new issue)*
- Scan/connect to the activated sensor over BLE; discover service + characteristic UUIDs; subscribe to the notification characteristic.
- Complete the BLE authentication handshake (PIN challenge/response from B3).
- **Accept:** a stable subscribed connection that delivers raw encrypted notification packets. (Spare sensor fine for connection mechanics.)

## B5 — Decrypt the BLE stream (AES-CFB)  ⬜  · *(new issue)*
- Decrypt notification payloads with the B3 key. ◐ Ported: Libre 2 EU uses the community-documented XOR keystream (not AES — DiaBox's `libaescfb.so` is likely for newer firmware). Every packet is CRC-checked; a sensor whose packets fail CRC is rejected, never displayed.
- Parse decrypted bytes into raw sensor value(s) + timestamp/counter.
- **Accept:** decrypted packets parse into a plausible, monotonic-in-time raw series (final correctness is B7).

## B6 — Raw → glucose (mg/dL) conversion  ⬜  · *(new issue)*
- Apply the sensor's factory calibration parameters (read from NFC memory in B1) + the conversion algorithm to turn raw → mg/dL.
- Emit a real `GlucoseReading` through `GlucoseSource` (trend via the existing metrics module).
- **Accept:** gated on B7.

## B7 — THE VALIDATION LOOP  ⬜  · *(new issue)* · **acceptance gate for B3–B6**
The safety-critical acceptance test. This is where "clean-room from the method" becomes "actually correct."
1. Capture real sensor output (NFC memory + live BLE packets) with timestamps.
2. Decode with our B3–B6 pipeline.
3. Compare decoded mg/dL against the **official LibreLink app** (and/or a finger-prick) for the *same sensor at the same moment*.
4. Iterate B3→B6 until they match within tolerance.
- Keep a captured-vector fixture from Kai's sensor as a regression test so future changes can't silently break decoding.
- **Accept:** decoded glucose tracks LibreLink within tolerance across a range of values (low, in-range, high) over a real session.

## B8 — Pluggable firmware-variant decoder  ⬜  · **issue #5**
- Structure B3–B6 so a sensor generation/firmware is a **pluggable variant** selected from patch info (B1) — not a rewrite. DiaBox ships 4 (`v112F/v113B/v115G/v116A`) for exactly this; Abbott's 301/302 serials broke community decoders before.
- Implement only Kai's sensor's variant now; leave the seam for more.
- **Accept:** variant chosen from patch info; adding a new variant is an added class, not edits to the pipeline.

## B9 — Self-healing reconnect + foreground service  ⬜  · **issue #6**
- 24/7 background BLE: reconnect on status-19 drops, back-off, resubscribe — the notoriously flaky part.
- Foreground service (shared with Track A A11) hosts the connection + the A2 collector.
- Real **warm-up** minutes from the sensor session (closes the `WarmingUp` placeholder flagged in PR #27) and real **stale** detection (drives PLAN §5 guard).
- **Accept:** survives phone sleep, walk-away/return, and Doze; reconnects unattended; warm-up + stale states reflect reality.

## B10 — Calibration application (capped)  ⬜  · **issue #14 (back half)**
- Apply user finger-prick calibration to the raw→glucose conversion: capped time-weighted least-squares (per DiaBox's cited paper PMC4764224), most-recent-4 weighted.
- Enforce slope/offset caps (PLAN §7/§8) — a bad entry must not silently skew all readings or mask a hypo.
- (Entry UI is Track A A10; this is the math that consumes it.)
- **Accept:** a plausible finger-prick nudges readings; an out-of-cap entry is rejected; capped adjustment can't invert a low into a normal.

## B11 — Real-hardware soak test  ⬜  · *(new issue)*
- Wear it: continuous readings across a full 14-day sensor life; reconnect after sleep/walk-away; battery survival (with A11 battery-opt handling); accuracy drift vs LibreLink over the sensor's life.
- **Accept:** a full sensor life of reliable, accurate, self-healing readings on Kai's daily phone.

---

## Fallback F1 — bundle DiaBox's native decoder via JNI (only if B3/B7 stalls)  ⬜  · *not planned, documented so it isn't re-litigated*

If the clean-room key derivation (B3) can't be made to pass B7 after real effort, the escape hatch is the xDrip "OOP2" trick: ship DiaBox's native libs (`libjniLibre.so`, `libaescfb.so`, the `v112F/v113B/v115G/v116A` algo libs — all extracted in `_apk-analysis/`) in `app/src/main/jniLibs/` and call them over JNI from `LibreBleSource`, replacing B3+B5+B6 wholesale.

Why it's the fallback and not the plan (decided 2026-07-15):
- **Redistribution:** they're someone else's binaries from a closed-source APK. Fine for a private daily driver, not for a public repo or a portfolio piece.
- **Black box:** when a sensor misbehaves you get no stack, no way to fix it, no way to unit-test the decode. B7's regression fixture becomes the only signal you have.
- **ABI + firmware locked:** ships as-is for four firmware versions and one ABI set; a newer EU firmware means waiting for DiaBox to ship a new lib — i.e. the dependency Sukoon exists to remove.
- **Kills B8/B10:** the pluggable-variant seam and the calibration math both assume we own the raw→glucose step.

Do NOT reach for this to save time on B3. It's for the case where B7 proves the public key-derivation spec no longer matches EU firmware and fresh reverse-engineering is the only alternative. Reskinning/patching the DiaBox APK itself is *not* an option at all — both APKs are Baidu-Shell-protected with the real classes encrypted at runtime (see `docs/research/diabox-apk-analysis.md`); the native libs are the only reusable artifact in there.

---

## Dependency map
- **B1 → B2 → B3 → B4 → B5 → B6** is a hard chain (each needs the prior). **B7** gates B3–B6. **B8** wraps B3–B6. **B9** wraps B4. **B10** extends B6.
- Fastest early signal: **B1 + B2 + B4** (the plumbing) can be proven on the spare sensor before the crypto (B3) is right — de-risks the Android BLE/NFC mechanics separately from the decoding.
- **The gating unknown:** B3/B7 is iterative and may need many cycles; worst case, EU firmware has shifted since the community's public docs and a fresh reverse-engineering step is needed (→ **F1** is the escape hatch, at the cost of B8/B10 and a public repo). Track A is unaffected either way.

## When Track B lands
Flip the A10 data-source picker from Simulated → Libre. The whole Track A app — Home, alerts, logbook, insights, sharing, emergency, widget — runs on real glucose, unchanged. That is the payoff of building everything against the abstraction.
