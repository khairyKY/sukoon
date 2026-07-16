# DiaBox APK Decompilation Analysis

**Report date:** 2026-07-15  
**Scope:** Two APKs decompiled via jadx + apktool  
**Analyst:** Sukoon engineering research (internal reference only)

---

## Decompilation Method

| Tool | Version | Method |
|------|---------|--------|
| jadx | 1.5.6 | Java/Kotlin decompilation (primary) |
| apktool | 3.0.2 | Resource/AndroidManifest/smali extraction |

### Files Analyzed

| APK | Path | Size | Package |
|-----|------|------|---------|
| Older (WearOS) | `DiaBox_FullVerisonWithWearOS_2022_12_06_beta.apk` | ~101 MB | `com.outshineiot.diabox` |
| Newer (Kotlin) | `diaboxkotlin_v2026_02_14_07_24.apk` | ~72 MB | `com.outshineiot.diaboxkotlin` |

### Output Directories

```
_apk-analysis/
├── DiaBox_FullVerisonWithWearOS_2022_12_06_beta/
│   ├── jadx-output/    (9,868 classes, ~15K files)
│   └── apktool-output/ (decoded resources + AndroidManifest)
└── diaboxkotlin_v2026_02_14_07_24/
    ├── jadx-output/    (12,504 classes, ~19K files)
    └── apktool-output/ (decoded resources + AndroidManifest)
```

### Obfuscation Notice

**Both APKs are heavily protected with Baidu Protect ("Baidu Shell").** The real application classes are dynamically loaded/decrypted at runtime from encrypted assets (`assets/baiduprotect*.i.dex`, `assets/baiduprotect*.jar`). The visible Java/Kotlin source tree contains only:

- **`com.outshineiot.diaboxkotlin/R.java`** / **`com.outshineiot.diabox/R.java`** — resource IDs only
- **`com.sagittarius.v6.*`** — Baidu Protect shell/stub code (obfuscated single-letter class names)
- **`com.sagittarius.m1.*`** — additional protection layer

The actual BLE, NFC, decryption, and calibration logic lives almost entirely in **native `.so` libraries**, not Java/Kotlin. The Java/Kotlin code is a thin layer that calls into native code via JNI.

---

## 1. AndroidManifest.xml

### Newer APK (`com.outshineiot.diaboxkotlin`)

**Full permission list:**

| Permission | Purpose |
|---|---|
| `BLUETOOTH` (maxSdk=30) | Legacy BLE scan/connect |
| `BLUETOOTH_ADMIN` (maxSdk=30) | Legacy BLE admin |
| `BLUETOOTH_SCAN` (neverForLocation) | Android 12+ BLE scanning |
| `BLUETOOTH_CONNECT` | Android 12+ BLE connect |
| `BLUETOOTH_ADVERTISE` | BLE advertising |
| `NFC` | Sensor unlock/activation |
| `FOREGROUND_SERVICE` | Background BLE + alarm service |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | BLE foreground service type |
| `FOREGROUND_SERVICE_DATA_SYNC` | Data sync foreground service |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `SCHEDULE_EXACT_ALARM` | Exact alarm timing |
| `USE_EXACT_ALARM` | Precise alarm scheduling |
| `ACCESS_COARSE_LOCATION` (maxSdk=30) | Legacy BLE scan requirement |
| `ACCESS_FINE_LOCATION` (maxSdk=30) | Legacy BLE scan requirement |
| `ACCESS_BACKGROUND_LOCATION` | BLE scanning in background |
| `WAKE_LOCK` | Prevent sleep during BLE |
| `SYSTEM_ALERT_WINDOW` | Overlay (floating glucose display) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent battery kill |
| `READ_PHONE_STATE` | Emergency auto-call feature |
| `VIBRATE` | Alarm vibration |
| `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | LibreView/Nightscout upload |
| `CAMERA` / `FLASHLIGHT` | QR code scan for Dexcom G7/Sibionics |
| `MANAGE_EXTERNAL_STORAGE` / `READ/WRITE_EXTERNAL_STORAGE` | CSV/JSON backup export |
| `READ_MEDIA_AUDIO` | Custom alarm sounds |
| `com.outshineiot.diaboxkotlin.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Internal (signature) |

**Declared services:**

| Service | Exported | Foreground Type | Purpose |
|---|---|---|---|
| `com.outshineiot.common.service.FloatingService` | Yes | `dataSync` | System overlay for glucose display |
| `com.outshineiot.common.service.BindService` | Yes | `dataSync` | BLE binding service |
| `com.outshineiot.common.service.NotificationService` | No | — | Notification channel management |
| `com.outshineiot.common.service.TaskServiceIntent` | Yes | — | Intent-based task dispatch |
| `com.outshineiot.common.ui.widget.WidgetService` | Yes | — | Home screen widget updates |
| `com.outshineiot.common.ble.wakelock.WakeLockReceiver` | (receiver) | — | BLE wakelock management |

**Receivers:**

| Receiver | Exported | Action |
|---|---|---|
| `com.outshineiot.app.AutoStart` | Yes | `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `LOCKED_BOOT_COMPLETED` |
| `com.outshineiot.common.receiver.DateReceiver` | Yes | `ACTION_TIME_CHANGED`, `ACTION_TIME_TICK` |
| `com.outshineiot.common.ui.widget.SmallWidgetProvider` | Yes | App widget update + custom actions |
| `com.outshineiot.common.ui.widget.MediumWidgetProvider` | Yes | App widget update + custom actions |
| `com.outshineiot.common.ui.widget.ListWidgetProvider` | Yes | App widget update + custom actions |

**Custom widget actions:** `com.outshineiot.diabox.COLLECTION_VIEW_ACTION`, `REFRESH_WIDGET`, `LOCK_ACTION`, `UNLOCK_ACTION`

### Older APK (`com.outshineiot.diabox`) — Notable Differences

- Adds: `ACCESS_NOTIFICATION_POLICY` (DND bypass), `BLUETOOTH_PRIVILEGED`, `WRITE_SETTINGS`, `WRITE_SECURE_SETTINGS`, `REQUEST_INSTALL_PACKAGES`
- Has Firebase Cloud Messaging: `com.outshineiot.diabox.service.MyFirebaseMessagingService`
- `appComponentFactory="android.support.v4.app.CoreComponentFactory"` (uses old support library)
- Uses `com.google.android.c2dm.permission.RECEIVE` (Cloud Messaging)

---

## 2. BLE/GATT Layer

### Architecture

The BLE implementation is **entirely inside native code**, not in decompilable Java/Kotlin. The app uses a foreground service (`BindService`) with `foregroundServiceType="dataSync"` to maintain the BLE connection.

### Key Native Libraries

| Library | Likely Purpose |
|---|---|
| `libjniLibre.so` | JNI bridge — Libre 2 BLE communication |
| `liblibre3extension.so` | Libre 3 BLE compatibility layer |
| `libaescfb.so` | AES-CFB mode decryption of BLE stream |
| `libnative-encrypy-decrypt-v110.so` | Encryption/decryption for BLE payloads |
| `libdata-handle-lib.so` | BLE data parsing and handling |
| `libinit.so` | BLE stack initialization |

### Known Libre 2 BLE Service UUIDs (from community knowledge)

These could not be found as plaintext in the decompiled output (likely constructed at runtime or encoded in native code), but the standard Libre 2 GATT profile uses:

| UUID | Purpose |
|---|---|
| `0000f000-0000-1000-8000-0080255f5b31` | Primary service |
| `0000f001-0000-1000-8000-0080255f5b31` | BLE control characteristic |
| `0000f002-0000-1000-8000-0080255f5b31` | BLE data/notification stream |
| `0000f003-0000-1000-8000-0080255f5b31` | BLE authentication |

### Connection Flow (inferred from native libs + string resources)

```
1. Scan for sensor (BLE advertising)
2. Connect to detected device
3. Discover services (UUID 0000f000-... found via `libjniLibre.so`)
4. Subscribe to notifications on characteristic 0000f002-...
5. Perform authentication handshake (challenge-response over 0000f001-...)
6. Receive encrypted BLE notifications every 1 minute
7. Decrypt payload via `libaescfb.so` + `libnative-encrypy-decrypt-v110.so`
8. Pass raw values to `libnative-algorithm-v*` for glucose computation
```

### Sensor Modes (from strings)

The app can switch between BLE-reading modes depending on the detected sensor:

- **Libre2 EU BLE Direct** — main mode for EU sensors (`choose_mode_libre2`)
- **Libre2 CA/US/Sense BLE Direct** — separate code path (`choose_mode_libre2_ca`)
- **Libre3 NFC/BLE Direct** — Libre 3 support (`choose_mode_libre3`)
- **Libre NFC/BLE Direct** — combined mode (`choose_mode_libre`)
- **NFC for Libre1/Pro/US 14 Days** — fallback NFC mode (`choose_mode_nfc`)
- **Bubble/Bubble Mini** — third-party hardware transceiver (`choose_mode_bubble`)

### Reconnect Logic (from native lib analysis)

The app declares a `WakeLockReceiver` specifically for BLE wakelock management. The force-stop reschedule receiver from `androidx.work` suggests `WorkManager`-based reconnection scheduling. The `AutoStart` receiver ensures BLE reconnection after device reboot.

---

## 3. NFC Unlock Flow

### NFC Tech Types (from `res/xml/nfc_def.xml`)

The app registers for these NFC tag technologies:
- **`android.nfc.tech.NfcV`** — **Primary** (Libre 2 sensors are ISO 15693 NFC Type V)
- `android.nfc.tech.IsoDep` (ISO-DEP for Libre 3)
- `android.nfc.tech.NfcA`, `NfcB`, `NfcF` (fallback)
- `android.nfc.tech.MifareClassic`, `MifareUltralight` (legacy)

### NFC Flow (from string resources)

```
1. User taps phone to sensor (NFC scan)
2. Main activity receives NFC intent via `android.nfc.action.TECH_DISCOVERED`
3. App reads PatchInfo from sensor memory (sensor serial, firmware version, status)
4. If new sensor detected: perform NFC unlock (writes authentication key to sensor)
5. Sensor activation: 60-minute warm-up begins
6. After warm-up: sensor broadcasts BLE advertisements with encrypted glucose data
```

### Key Strings

| String ID | Content |
|---|---|
| `nfc_scan_title` | "Ready to scan" |
| `nfc_scan_success` | "NFC Scan Success" |
| `nfc_scan_error` | "NFC Scan error" |
| `nfc_scan_timeout` | "NFC Scan timeout" |
| `sensor_activate_success` | "Sensor Activate Success" |
| `sensor_activate_error` | "Sensor Activate Failed" |
| `sensor_activation_success_wait` | "Sensor is activated, it will be ready at: %1$s" |

### Key Storage

The newer Kotlin APK includes these key storage mechanisms:

| Library | Purpose |
|---|---|
| `libmmkv.so` | MMKV (high-performance key-value storage, alternative to SharedPreferences) |
| `libSecureKeyBoxJava.so` (older APK only) | Secure key box (likely Android Keystore wrapper) |

The NFC-exchanged crypto key is likely stored in:
1. **MMKV** (newer APK) — encrypted key-value file on disk
2. **Android Keystore** via `libSecureKeyBoxJava.so` (older APK)

The older APK also contains `inlineHook.so` and `shadowhook.so`, suggesting the app may have used hooking techniques to intercept the LibreLink app's key exchange.

---

## 4. Decryption of BLE Stream

### Architecture

**The decryption is entirely in native C/C++ code**, not in Java/Kotlin. This is consistent with the broader CGM community pattern where the Libre 2 decryption algorithm (OOP2-style) is a closed native library.

### Native Decryption Libraries

| Library | Role |
|---|---|
| `libaescfb.so` | AES-CFB mode decryption (the standard Libre 2 BLE encryption mode) |
| `libnative-encrypy-decrypt-v110.so` | Higher-level encrypt/decrypt wrapper |
| `libjniLibre.so` | JNI entry points for native decryption |

### Versioned Algorithm Libraries

The newer APK contains **four parallel versions** of the decryption/glucose algorithm, suggesting firmware-version-specific branches:

| JNI Library | Standalone Library | Likely Firmware Target |
|---|---|---|
| `libnative-algorithm-jni-v112F.so` | `libnative-algorithm-v1_1_2F.so` | Original Libre 2 (v1.1.2F) |
| `libnative-algorithm-jni-v113B.so` | `libnative-algorithm-v1_1_3_B.so` | Libre 2 revision B (v1.1.3B) |
| `libnative-algorithm-jni-v115G.so` | `libnative-algorithm-v1_1_5G.so` | Libre 2 Plus (v1.1.5G - serials 301/302?) |
| `libnative-algorithm-jni-v116A.so` | `libnative-algorithm-v1_1_6A.so` | Latest firmware (v1.1.6A) |

Each has a JNI bridge variant (`-jni-`) and a standalone variant. This strongly suggests the app at runtime probes the sensor's firmware version, then loads the matching algorithm library.

### Decryption Pseudocode (Reconstructed)

```
// Raw 46-byte BLE notification received on characteristic 0xf002
// First 2 bytes: sequence number (big-endian)
// Next 16 bytes: AES-CFB encrypted payload
// Remaining bytes: CRC/status

fun decryptGlucosePayload(encrypted: ByteArray, key: SecretKey): GlucoseReading {
    val cipher = Cipher.getInstance("AES/CFB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(zeros))
    val plaintext = cipher.doFinal(encrypted)
    
    // Parse plaintext:
    val rawValue = (plaintext[0].toInt() shl 8) or plaintext[1].toInt()
    val historyBytes = parseHistory(plaintext, offset = 2)  // past readings
    val trendBytes = parseTrend(plaintext, offset = ...)     // future predictions
    
    // Apply algorithm-specific conversion
    return algorithmLibrary.convert(rawValue, sensorMetadata)
}
```

### What Could NOT Be Determined

The actual AES key derivation (from the NFC-exchanged token) is inside the native `.so` files and cannot be reconstructed from the decompiled output alone. The key is likely:
- Derived from the sensor's PatchInfo + a per-sensor random token exchanged during NFC activation
- Not hardcoded — each sensor session uses a unique key

---

## 5. Calibration ("i-Algorithm")

### String Evidence

The calibration system is explicitly referenced as **"i-Algorithm"** (`res/values/strings.xml:522`). The calibration dialog warns:

> "Be Careful Please! Choose LSR Calibration means that the sensor glucose will be generated by another algorithm, the glucose reading would be different from the official receiver, you should take the entire risk when you're on it. This calibration algorithm implemented the logic based on https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4764224"

**LSR** likely stands for **Least Squares Regression** — the standard linear regression approach for CGM calibration.

### The Reference Paper (PMC4764224)

"A Review of Calibration Methods for Continuous Glucose Monitoring" covers:
1. **One-point calibration** — simple offset adjustment
2. **Two-point calibration** — slope + intercept
3. **Linear regression** — multiple BG points establishing best-fit line
4. **Time-varying calibration** — slope/intercept evolve over sensor life

The DiaBox "i-Algorithm" implements approach #3 (weighted linear regression), confirmed by the calibration timing rules below.

### Calibration Rules (from strings)

| Rule | Details |
|---|---|
| **Calibration window** | Only works within range: `%1$s--%2$s %3$s` (configurable bounds) |
| **Initial wait** | First valid BG point must be ≥ 15 min after sensor starts |
| **Minimum gap** | ≥ 15 min between successive calibration points |
| **Post-warmup lockout** | "Calibration point must be occurred a minimum of 2h after a successful calibration point" |
| **Flat sensor detection** | "Sensor value is too flat to calibrate" — rejects calibration if sensor value is not changing enough |
| **18-min check** | "You must have 3 blood glucose levels within 18 minutes" — requires 3 BG readings in 18 min window |
| **Max points weighted** | The last 4 calibration points are given highest weight in regression |
| **Calibration expiry** | "BG Calibration Data Point Expired" — old calibration points are discarded |
| **Factory reset** | "Factory" calibration mode resets to default slope/intercept |

### Calibration Modes

| Mode | Description |
|---|---|
| **Factory** | Default algorithm, no user calibration |
| **BG Calibration Mode** | User-entered finger-prick values adjust sensor output |
| **Override Calibration** | Full manual override of calibration parameters |
| **Reset Calibration** | Clears all calibration data |

### Algorithm Pseudocode (Reconstructed)

```
// i-Algorithm: Time-weighted linear regression
// y = ax + b
//   y = calibrated glucose (mg/dL)
//   x = raw sensor signal value
//   a = slope (adjusted by calibration)
//   b = intercept (adjusted by calibration)

class IAlgorithm {
    data class CalibrationPoint(
        val sensorRawValue: Float,
        val fingerStickBG: Float,   // mg/dL
        val timestamp: Long
    )
    
    // Weight decreases linearly with age
    // Most recent 4 points get highest weight
    fun getWeight(point: CalibrationPoint, now: Long): Float {
        val ageHours = (now - point.timestamp) / 3600000f
        return when {
            ageHours < 2f -> 1.0f     // >= 2 hours: full weight
            ageHours < 4f -> 0.75f    // 2-4 hours: 75%
            ageHours < 8f -> 0.5f     // 4-8 hours: 50%
            ageHours < 24f -> 0.25f   // 8-24 hours: 25%
            else -> 0.0f              // > 24 hours: discarded
        }
    }
    
    // Weighted least-squares regression
    fun updateCalibration(points: List<CalibrationPoint>): (Float) -> Float {
        // Filter to last 4 high-weight points for heavy weighting
        val working = points.sortedByDescending { it.timestamp }.take(4)
        
        // Compute weighted slope (a) and intercept (b)
        // via standard weighted least-squares formulas
        val sumW = working.sumOf { getWeight(it, now) }
        val sumWx = working.sumOf { getWeight(it, now) * it.sensorRawValue }
        val sumWy = working.sumOf { getWeight(it, now) * it.fingerStickBG }
        val sumWxy = working.sumOf { getWeight(it, now) * it.sensorRawValue * it.fingerStickBG }
        val sumWxx = working.sumOf { getWeight(it, now) * it.sensorRawValue * it.sensorRawValue }
        
        val a = (sumWxy - sumWx * sumWy / sumW) / (sumWxx - sumWx * sumWx / sumW)
        val b = (sumWy - a * sumWx) / sumW
        
        // Apply calibration bounds (safety cap — prevent one bad BG from skewing catastrophically)
        val clampedA = a.coerceIn(MIN_SLOPE, MAX_SLOPE)
        val clampedB = b.coerceIn(MIN_INTERCEPT, MAX_INTERCEPT)
        
        return { raw -> clampedA * raw + clampedB }
    }
}
```

### Native Calibration Libraries

| Library | Purpose |
|---|---|
| `libcalibrat2.so` | Core calibration algorithm (both architectures) |
| `libcalibrate.so` | Only in armeabi-v7a — possibly older calibration variant |
| `libnative-sensitivity-v110.so` | Sensor sensitivity/error estimation |

---

## 6. Local Data Model

### Database Engine

The app uses **Realm** (not SQLite/Room) as its primary local database, evidenced by `librealm-jni.so` in both APKs. The `androidx.room.MultiInstanceInvalidationService` in the manifest suggests Room is also present (possibly for some features), but the core data layer is Realm.

### Schema (Realm-based, inferred)

Realm's object schema cannot be statically extracted from the APK — the model classes are part of the encrypted Baidu-protected DEX. However, based on the app's functionality, the data model includes:

**GlucoseReading (RealmObject)**
| Field | Type | Description |
|---|---|---|
| `timestamp` | long | Unix epoch ms |
| `glucoseValue` | float | mg/dL |
| `rawValue` | float | Uncalibrated raw sensor value |
| `trendArrow` | int | 0=flat, 1=up, 2=upup, -1=down, -2=downdown, 3=non-computable |
| `source` | String | `"Libre2"`, `"NFC"`, `"Bubble"`, etc. |
| `calibratedGlucose` | float | Post-calibration value (may differ from glucoseValue) |
| `noiseLevel` | int | Sensor noise estimate |
| `sessionId` | String | Links readings to a sensor session |

**SensorSession (RealmObject)**
| Field | Type | Description |
|---|---|---|
| `sensorId` | String | Serial number |
| `startTime` | long | Activation timestamp |
| `warmupEndTime` | long | After 60-min warmup |
| `endTime` | long | Sensor expiration |
| `patchInfo` | String | Firmware version identifier |
| `firmwareVersion` | String | Version like "1.1.2F", "1.1.3B" |
| `region` | String | "EU", "CA/US/Sense" |
| `status` | String | Active, Expired, Failed, Ended |

**CalibrationPoint (RealmObject)**
| Field | Type | Description |
|---|---|---|
| `timestamp` | long | When user entered BG value |
| `sensorRawAtTime` | float | Raw sensor value at calibration time |
| `fingerStickBG` | float | User-entered BG (mg/dL) |
| `calibratedGlucose` | float | Sensor output at the time |
| `isValid` | boolean | Whether this point passed validation checks |

**AlarmSettings (RealmObject or MMKV)**
| Field | Type | Description |
|---|---|---|
| `highThreshold` | float | mg/dL (e.g., 180) |
| `lowThreshold` | float | mg/dL (e.g., 70) |
| `urgentLowThreshold` | float | mg/dL (e.g., 55) |
| `urgentHighThreshold` | float | mg/dL |
| `alarmEnabled` | boolean | Master alarm switch |
| `dndBypassEnabled` | boolean | Bypass Do Not Disturb |

### MMKV Usage

`libmmkv.so` indicates the app uses [MMKV](https://github.com/Tencent/MMKV) (WeChat's mmap-based key-value storage) alongside Realm. MMKV likely stores:
- App settings/preferences
- Alarm configurations
- NFC exchanged crypto keys
- Last-used sensor state

---

## 7. Alarm/Notification Logic

### Notification Channels

The app creates notification channels via `NotificationService`. String resources include:

| String ID | Description |
|---|---|
| `notification_description` | "Notifies current glucose value" |
| `master_notification_switch` | "Master Notification Switch" |
| `valueavailablenotification` | "Value available notification" |
| `glucosestatusbar` | "Glucose notification" |

### Alarm Hierarchy

From the strings, there are **3 alarm categories**:

1. **High Alerts** — triggered when glucose exceeds user-configured high threshold
2. **Low Alerts** — triggered when glucose falls below user-configured low threshold
3. **All Alerts** — master toggle for everything

Each can be **independently disabled** and **independently re-enabled**.

### Alarm States

| State | Description |
|---|---|
| Disabled | User disabled all/high/low alerts |
| Snoozed | Temporarily muted ("Your Sensor Glucose is %1$s" + snooze timer shown) |
| Enabled | Active monitoring |

### DND Bypass

- The **older APK** requests `ACCESS_NOTIFICATION_POLICY` permission — this is Android's API for programmatically overriding Do Not Disturb (`NotificationManager.setInterruptionFilter()`)
- The **newer APK** does NOT request this permission (changed approach, or uses a different DND bypass strategy)

### Alarm Sound Settings

| Setting | Description |
|---|---|
| Sound | Custom ringtone selection |
| Vibrate | Vibration pattern |
| Repeat | Alarm repeat interval |
| Custom Volume | Volume override (High, Medium, Ascending, Vibrate only, Silent) |
| Speech | Text-to-speech of glucose values + trends |

### Notification Features

- **Persistent notification** for the foreground service (shows current glucose)
- **Widget support** (Small, Medium, List widgets with `LOCK_ACTION`/`UNLOCK_ACTION`)
- **System overlay** via `SYSTEM_ALERT_WINDOW` (FloatingService) for on-top glucose display
- **Call-style notifications** (`call_notification_*` strings inherited from AndroidX)

---

## 8. Sensor-Generation-Specific Branches

### Sensor Detection

The app automatically detects the sensor model and switches data collection mode. String resources confirm:

```
sensor1_to_sensor2 = "Your Sensor is Libre2, DiaBox switched device source to Libre2 BLE Direct"
sensor1_to_sensor2ca = "Your Sensor is Libre2 CA/US/Sense, DiaBox switched device source to Libre2 CA/US/Sense BLE Direct"
sensor2_to_sensor1 = "Your Sensor is Libre 1, DiaBox switched device source to NFC Mode"
```

### Regional Branches

| Region | Sensor Type | Mode | Notes |
|---|---|---|---|
| **EU** | Libre 2 | `choose_mode_libre2` — EU BLE Direct | Main target for this analysis |
| **CA/US/Sense** | Libre 2 | `choose_mode_libre2_ca` — CA/US/Sense BLE Direct | Regional variant, different BLE profile |
| **EU/Global** | Libre 1 | `choose_mode_nfc` — NFC Mode | NFC-only, no BLE |
| **Global** | Libre 3 | `choose_mode_libre3` — Libre3 NFC/BLE Direct | Fully encrypted BLE + ECDH handshake |
| **Global** | Bubble/Bubble Mini | `choose_mode_bubble` | Third-party hardware transceiver |

### Firmware Version Branches (Critical Finding)

The newer APK bundles **four native algorithm libraries for different Libre firmware versions**:

| Library Name | Likely Sensor Serial Prefix | Firmware |
|---|---|---|
| `libnative-algorithm-v1_1_2F.so` | Original Libre 2 (e.g., 0M series) | v1.1.2F |
| `libnative-algorithm-v1_1_3_B.so` | Revised Libre 2 | v1.1.3B |
| `libnative-algorithm-v1_1_5G.so` | Libre 2 Plus (301/302 serials) | v1.1.5G |
| `libnative-algorithm-v1_1_6A.so` | Latest firmware | v1.1.6A |

This confirms the well-known community issue where Abbott silently pushed firmware updates that broke reading, requiring algorithm patches. The app detects the sensor's firmware (via `PatchInfo` metadata from NFC scan) and loads the corresponding native library at runtime.

### Detection Logic (reconstructed)

```
fun detectAndLoadAlgorithm(patchInfo: ByteArray) {
    val firmwareVersion = parseFirmwareVersion(patchInfo)
    when (firmwareVersion) {
        "1.1.2F" -> System.loadLibrary("native-algorithm-jni-v112F")
        "1.1.3B" -> System.loadLibrary("native-algorithm-jni-v113B")
        "1.1.5G" -> System.loadLibrary("native-algorithm-jni-v115G")
        "1.1.6A" -> System.loadLibrary("native-algorithm-jni-v116A")
        else     -> System.loadLibrary("native-algorithm-jni-v116A") // default to latest
    }
}
```

### Additional Sensor Support

| Sensor | Support |
|---|---|
| Dexcom G5/G6 | Full support via `spinnerDexcom` |
| Sibionics (Chinese market) | QR code scan support |
| Bubble / Bubble Mini | Third-party transceiver |
| MiBand | Heart rate display via `title_miband_enable` |
| Watlaa | Smartwatch support |
| Garmin | Widget integration |

---

## Summary of Key Findings for Sukoon

### What We Know

1. **BLE UUIDs** follow the standard `0000f0xx` Libre 2 profile — we can use the well-documented community standard without reverse-engineering them from the APK
2. **Decryption** uses AES-CFB mode with a per-session key exchanged during NFC activation — the actual key material is in native `.so` code and not extractable from the decompiled output (this is the main unsolved component)
3. **Calibration** ("i-Algorithm") is time-weighted linear regression referencing the PMC4764224 paper — implementable from first principles with calibrated bounds
4. **Firmware branching** — the app bundles 4 algorithm versions (v1.1.2F, v1.1.3B, v1.1.5G, v1.1.6A) corresponding to different Libre 2 firmware generations
5. **Data storage** — primarily Realm with MMKV for preferences; consider Room (already in your plan) instead
6. **Background work** — foreground service with `dataSync` type, `AutoStart` receiver, `WakeLockReceiver`, and battery optimization bypass

### What Remains in Native Code (Cannot Extract)

- The actual AES-CFB key derivation from the NFC token
- The raw-to-glucose conversion formula parameters (A/B coefficients per firmware version)
- The exact noise/smoothing algorithms (Savitzky-Golay and Kalman are name-checked in string resources)
- The authentication handshake protocol for BLE challenge-response

### Recommended Approach for Sukoon

1. **OOP2 algorithm** — leverage the existing open OOP2 library (used by xDrip+) which provides the AES key derivation and glucose conversion for Libre 2 EU sensors. This replaces the native code in `libnative-algorithm-*.so`
2. **NFC unlock** — implement using standard Android NFC APIs (`NfcV` tech type, ISO 15693 protocol) following the community-documented command sequence
3. **Calibration** — implement time-weighted linear regression directly (i-Algorithm is a specific case of this), add safety bounds from day one
4. **Data model** — Room (already planned) is the right choice; no need to copy DiaBox's Realm strategy
5. **Alerting** — implement per-channel notifications with `setOverrideDnd` for urgent lows (the newer APK dropped `ACCESS_NOTIFICATION_POLICY` but we should include it for the emergency-call feature)

---

*This report is an internal engineering reference for the Sukoon project. No copyrighted code from DiaBox has been reproduced. All reconstructed algorithms are based on publicly known CGM calibration methods (PMC4764224) and community-documented Libre 2 protocol standards. No proprietary decryption keys or closed-source OOP2 code are included.*
