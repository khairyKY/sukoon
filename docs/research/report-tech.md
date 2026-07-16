# Comprehensive Technical Analysis of Custom Continuous Glucose Monitoring (CGM) Applications

**Key Points:**
*   **Mechanics & Protocols:** Community-driven Continuous Glucose Monitoring (CGM) applications interface with Abbott FreeStyle Libre sensors by intercepting Bluetooth Low Energy (BLE) streams or using Near Field Communication (NFC) protocols, bypassing proprietary vendor restrictions.
*   **Reverse Engineering Status:** Libre 2 (EU) BLE decryption is well-documented and primarily handled via out-of-process (OOP2) algorithms or patched vendor apps. Libre 3 requires intercepting a heavily encrypted challenge-response handshake involving a LibreView account ID.
*   **Architecture Stack:** For a robust CGM application, native Kotlin (Android) is overwhelmingly recommended over cross-platform frameworks like Flutter or React Native due to superior BLE performance, lower latency, and reduced battery consumption during continuous background scanning. 
*   **Algorithmic Complexity:** Converting raw sensor values to actionable data requires sophisticated time-weighted linear regression, noise smoothing, and advanced analytics like the Patient Glycemic Status (PGS) and Glucose Management Indicator (GMI).
*   **Legal & Regulatory:** Developing unapproved software that intercepts and modifies medical device data explicitly violates Abbott’s Terms of Service and operates entirely outside FDA/CE regulatory frameworks, carrying inherent liability risks.

**Introduction for the Layperson:**
Continuous Glucose Monitors (CGMs) like the Abbott FreeStyle Libre series have revolutionized diabetes management by providing real-time glucose readings without the need for constant finger-pricking. However, the software provided by the manufacturer often operates in a "locked" ecosystem, restricting how users view, export, or share their own data. In response, a robust "Do-It-Yourself" (DIY) and #WeAreNotWaiting community has reverse-engineered these devices. By understanding the radio signals (NFC and Bluetooth) these sensors emit, developers have created custom applications (like DiaBox, Juggluco, and xDrip+) that offer advanced alarms, better data sharing, and custom calibrations. While this empowers users, it is a legally complex and technically delicate process. The underlying software must translate raw electrical signals into reliable medical data while maintaining a constant, battery-efficient connection to the sensor. 

This comprehensive technical report serves as a blueprint for software engineers aiming to build a modern CGM companion application, detailing the end-to-end mechanics, cryptographic protocols, data algorithms, recommended technology stacks, and the surrounding legal landscape.

---

## 1. End-to-End Mechanics of CGM Applications

To build a companion app for a FreeStyle Libre sensor, an engineer must first understand the lifecycle of the sensor and the physical mechanisms by which data is generated and transmitted.

### Sensor Chemistry and Raw Data Generation
The FreeStyle Libre is an interstitial fluid continuous glucose monitor [cite: 1]. The actual sensor probe, inserted just beneath the skin, consists of a small channel filled with the enzyme glucose oxidase [cite: 1]. When glucose in the interstitial fluid interacts with this enzyme, it releases electrons, generating a tiny electrical current [cite: 1]. This raw electrical signal is captured by the sensor's internal hardware and converted into a digital "raw value." Because interstitial fluid glucose lags behind capillary blood glucose (by approximately 5 to 10 minutes), algorithms are required to project and smooth the data to estimate the actual blood glucose level [cite: 2]. 

### The Initialization and Warm-up Period
When a new Libre sensor is applied, it cannot be used immediately. It requires an initial NFC scan to activate [cite: 1, 3]. This activation triggers a mandatory 60-minute "warm-up" period [cite: 4]. During this time, the sensor chemistry stabilizes within the body. In unofficial apps like Juggluco or xDrip+, the software must recognize this state and prevent the display of glucose readings until the timer expires, matching the behavior of the official LibreLink app [cite: 3].

### Sampling Intervals and Data Cadence
The Libre sensors measure glucose continuously, generating a new raw reading every 1 minute [cite: 1, 5]. 
*   **Internal Storage:** The sensor retains the last 2 hours of minute-by-minute data and 14 days of averaged 15-minute interval data in its onboard memory [cite: 6].
*   **Displayed Cadence:** While the sensor records every minute, older community apps like xDrip+ were historically tailored to 5-minute intervals (matching Dexcom hardware) [cite: 7, 8]. To adapt to Libre's 1-minute cadence, xDrip+ averages the last 5 minutes of 1-minute data into a single 5-minute block [cite: 9]. However, modern apps like DiaBox and Juggluco support true 1-minute cadence displays, updating the user interface and widgets every 60 seconds [cite: 5, 10].

### Unit Conversion
The raw converted glucose value is typically calculated internally in milligrams per deciliter (mg/dL). To support international users, the app must convert this to millimoles per liter (mmol/L). The conversion factor is mathematically straightforward, based on the molecular weight of glucose:
*   $1 \text{ mmol/L} = 18 \text{ mg/dL}$ [cite: 11]

---

## 2. Sensor Protocols (State of the Art, 2024–2026)

The primary technical hurdle in building a CGM app is establishing the connection to the sensor. Abbott utilizes proprietary encryption and Bluetooth handshakes that vary wildly between sensor generations and geographic regions.

### Libre 1 (NFC Only)
The first-generation FreeStyle Libre (and Libre Pro) does not feature a Bluetooth transmitter. Data can only be extracted by physically scanning the sensor via NFC [cite: 12]. 
*   **Protocol:** Uses ISO 15693 NFC protocols. 
*   **Community Workaround:** Because holding a phone to the arm continuously is impractical, the community developed hardware bridges (e.g., MiaoMiao, Bubble, BluCon) [cite: 12, 13]. These small devices sit on top of the Libre 1, read it via NFC every 5 minutes, and broadcast the data to the phone over BLE [cite: 12, 13].

### Libre 2 (Encrypted BLE Stream)
The Libre 2 introduced native BLE but restricted its use. Officially, the BLE connection was originally used solely to push high/low alarms, requiring the user to NFC scan to see the actual number [cite: 5].
*   **Activation:** The sensor is activated via an NFC scan, which exchanges a shared cryptographic key [cite: 1, 3].
*   **BLE Decryption (EU vs US):** 
    *   *EU Firmware:* In Europe, the BLE stream can be unlocked to transmit actual glucose values continuously. The community utilizes an "Out of Process" (OOP2) algorithm, a closed-source wrapper of Abbott's decryption algorithm, to decrypt the raw BLE payloads [cite: 3, 9]. Alternatively, developers reverse-engineered the LibreLink APK (`libre2-patched`) to force the app to broadcast the decrypted values locally to other apps [cite: 14, 15].
    *   *US Firmware:* Abbott heavily locked down the Libre 2 US and Australian models, making direct BLE interception nearly impossible without extreme workarounds [cite: 7].
*   **Newer Iterations:** In late 2023/2024, Abbott released the "Libre 2 Plus" (serial numbers starting with 301 and 302). These introduced new `Patchinfo` variables (e.g., `0x7f0e31` and `0x7f0e30`) that temporarily broke OOP2 and xDrip+ connectivity until community patches were issued [cite: 16].

### Libre 3 (Fully Encrypted BLE)
The Libre 3 is a continuous BLE streaming device right out of the box, requiring no routine NFC scans after initial activation [cite: 12].
*   **Protocol & Handshake:** The security protocol is highly complex, utilizing ECDH (Elliptic Curve Diffie-Hellman) shared secrets and AES-128-CCM session keys (`kEnc`) [cite: 17]. 
*   **Activation Hijacking:** The sensor authenticates using a `blePIN` and a challenge-response mechanism [cite: 17]. Community apps like Juggluco manage to hijack this connection, but they require the user to provide the exact `Libreview Account ID` used to activate the sensor [cite: 6]. Without this Account ID, the Libre 3 drops the connection (BLE status 19) immediately after the certificate exchange [cite: 18].
*   **Data Storage:** Internally, official apps store Libre 3 data in an encrypted RealmDB (`trident.realm`), which requires rooting Android (via tools like Frida) or using iOS jailbreak tools to extract the decryption key [cite: 1].

---

## 3. Open-Source Projects to Learn From

For a builder entering this space, reviewing existing reference implementations is vital. The `#WeAreNotWaiting` community hosts several repositories that provide foundational logic.

| Project Name | Language / Stack | License | Purpose & Value for a New Builder |
| :--- | :--- | :--- | :--- |
| **Juggluco** [cite: 8, 19] | C / Java | Custom/Closed Source UI | The foremost reference for **Libre 3 BLE handshake bypassing**. It successfully sends 1-minute broadcasts locally and connects directly to Libre 3 without root [cite: 6, 8]. |
| **xDrip+** [cite: 9, 13] | Java (Android) | GPL-3.0 | The industry standard for Android DIY CGM. Excellent reference for the **OOP2 integration**, complex alerting, and noise-smoothing algorithms. Best for learning how to handle 5-minute backfilling [cite: 3, 9]. |
| **GlucoseDirect / LibreDirect** [cite: 20] | Swift / SwiftUI | MIT / GPL | A clean iOS implementation [cite: 20]. Highly recommended for iOS developers to understand Core Bluetooth implementations for Libre and handling LibreLinkUp API polling [cite: 20, 21]. |
| **DiaBox** [cite: 22, 23] | Kotlin/Java | Proprietary / Closed | Community app known for its **i-Algorithm** calibration [cite: 23]. While closed source, its documentation details how multi-point calibration ought to be handled for Libre sensors [cite: 23]. |
| **DiaBLE** [cite: 2, 24] | Swift | MIT | Acts as a BLE/NFC testbench for Libre sensors [cite: 2]. An incredible resource for viewing raw hex dumps of Libre 1/2/3 NFC commands and GATT characteristics [cite: 2, 24]. |
| **AndroidAPS / Loop** [cite: 7, 8] | Kotlin / Swift | GPL | Automated insulin delivery systems. Valuable for learning how an app should broadcast BG readings (e.g., local intents) so that artificial pancreas systems can ingest them safely [cite: 7]. |

*Note on Reference:* For Libre 2 BLE decoding, the cleanest reference is the integration of the **OOP2** library found in the `xDrip+` repository, combined with the documentation from `libre2-patched` [cite: 3, 14].

---

## 4. Algorithms: Signal Processing and Analytics

Converting raw signals to glucose and providing deep analytics is what separates a basic BLE reader from a medical-grade companion app.

### Raw Value Conversion and Smoothing
Because interstitial fluid glucose lags behind blood glucose, the raw values are prone to physiological noise. 
*   **Smoothing:** Systems like xDrip+ apply a 10 to 25-minute moving average or exponential smoothing function to prevent jagged jumps [cite: 7, 9].
*   **DiaBox i-Algorithm:** DiaBox employs a "multi-point calibration" algorithm using a time-weighted linear regression: $y = ax + b$, where $x$ is the raw sensor value and $y$ is the calibrated value [cite: 23]. If more than three finger-prick calibration values are entered, the algorithm heavily weights the most recent 4 entries to update the slope ($a$) and intercept ($b$) [cite: 23]. This adjusts the effective measuring range of the sensor dynamically [cite: 23].

### Trend Arrow Computation
Trend arrows (e.g., $\uparrow$, $\rightarrow$, $\downarrow$) are calculated based on the rate of change (ROC) over a specific time window (usually 15 to 30 minutes). A common implementation calculates the delta between the current reading and the reading 15 minutes prior, divided by 15.
*   ROC < -2 mg/dL/min: $\downarrow\downarrow$
*   ROC between -1 and -2: $\downarrow$
*   ROC between -1 and 1: $\rightarrow$
*   ROC > 2 mg/dL/min: $\uparrow\uparrow$

### Advanced Analytics Formulas
To mimic or exceed the analytics provided by DiaBox, the app must calculate standardized CGM metrics based on the 2019 ATTD Consensus [cite: 25].

#### 1. Estimated A1C (eA1C) / Glucose Management Indicator (GMI)
Historically called eA1C, this metric estimates laboratory HbA1c based on average glucose (AG) [cite: 25, 26]. The modern formula is derived from continuous CGM data (requiring at least 10–14 days of data) [cite: 27, 28].
**Formulas:**
*   **GMI (%)** = $3.31 + 0.02392 \times \text{AG (mg/dL)}$ [cite: 26, 29]
*   **GMI (mmol/mol)** = $12.71 + 4.70587 \times \text{AG (mmol/L)}$ [cite: 27, 30]

#### 2. Glycemic Variability: Standard Deviation (SD) and Coefficient of Variation (CV)
Glycemic variability indicates how wildly blood sugar swings.
*   **Standard Deviation (SD):** The standard deviation of all glucose measurements within the timeframe [cite: 31].
    $$ SD = \sqrt{ \frac{\sum (x_i - \bar{x})^2}{n - 1} } $$ [cite: 31]
*   **Coefficient of Variation (%CV):** A standardized measure of variability. The clinical target is to keep %CV $\leq 36\%$ [cite: 25, 30].
    $$ CV = \left( \frac{SD}{\bar{x}} \right) \times 100 $$ [cite: 32, 33]

#### 3. Time in Range (TIR)
TIR calculates the percentage of total CGM readings that fall within specific clinical brackets [cite: 25, 33].
*   **Very Low (Level 2 Hypoglycemia):** < 54 mg/dL (Target: < 1%) [cite: 33]
*   **Low (Level 1 Hypoglycemia):** 54 – 69 mg/dL (Target: < 4%) [cite: 33]
*   **Target Range (TIR):** 70 – 180 mg/dL (Target: > 70%) [cite: 33]
*   **High (Level 1 Hyperglycemia):** 181 – 250 mg/dL (Target: < 25%) [cite: 33]
*   **Very High:** > 250 mg/dL [cite: 30]

#### 4. Patient Glycemic Status (PGS)
PGS is an advanced composite metric designed to capture mean glucose, variability, TIR, and the severity of hypoglycemia into a single score [cite: 34]. Lower scores indicate better control. The formulation relies on several piecewise functions [cite: 35]:
$$ PGS = f(GVP) + g(MG) + h(PTIR) + j(N54, N70) $$ [cite: 35]
Where:
*   $GVP$ = Glucose Variability Percentage
*   $MG$ = Mean Glucose
*   $PTIR$ = Percent Time in Range
*   $N54$ = Hypoglycemic episodes per week < 54 mg/dL
*   $N70$ = Hypoglycemic episodes per week 54–69 mg/dL

The sub-functions are rigorously defined as [cite: 35]:
*   $f(GVP) = 1 + \frac{9}{1+\exp(-0.049(GVP - 65.47))}$
*   $g(MG) = 1 + 9\left(\frac{1}{1+\exp(0.1139(MG - 72.08))} + \frac{1}{1+\exp(-0.09195(MG - 157.57))}\right)$
*   $h(PTIR) = 1+\frac{9}{1+\exp(0.0833(PTIR - 55.04))}$
*   $j(N54, N70) = a(N54) + b(N70)$
*   $a(N54) = 0.5 + 4.5(1-\exp(-0.91093 \times N54))$
*   $b(N70) = 0.5714 \times N70 + 0.625$ (if $N70 \leq 7.65$), else $5$ [cite: 35]

---

## 5. Integrations and Upload Targets

A standalone app provides limited value if the data remains trapped on the phone. Modern architectures require interoperability with broader health ecosystems.

*   **Nightscout:** The defacto standard for DIY diabetes data. The app should perform a REST HTTP POST to the user's Nightscout instance (`https://YOUR-SITE.com/api/v1/entries.json`) containing the glucose value, timestamp, and device name [cite: 13]. Secure uploads require passing the `API_SECRET` hashed via SHA-1 [cite: 13].
*   **Android Health Connect / Google Fit:** In 2024–2026, Google deprecated direct Google Fit APIs in favor of **Health Connect**. The app must request `WRITE_BLOOD_GLUCOSE` permissions and push data blocks periodically. Juggluco currently features native Health Connect integration for minute-by-minute writes [cite: 36].
*   **Apple HealthKit:** On iOS, data must be written to `HKQuantityTypeIdentifier.bloodGlucose`.
*   **LibreLinkUp Unofficial API:** Many users follow family members using Abbott's LibreLinkUp cloud. Projects like `nightscout-librelink-up` demonstrate how to use Abbott's JWT authentication to periodically scrape `https://api.libreview.io/llu/connections` to pull data if direct BLE fails [cite: 37, 38].

---

## 6. Recommended Architecture & Stack (2026)

Given the stringent technical requirements (NFC, background BLE polling, continuous foreground services, rock-solid alerting), the choice of technical stack is mission-critical.

### Cross-Platform (Flutter/React Native) vs. Native (Kotlin/Swift)
While frameworks like Flutter and React Native offer rapid cross-platform development, they are **strongly discouraged** for a CGM app relying on continuous BLE connections.
*   **Latency and Reliability:** BLE operations in cross-platform frameworks must cross a platform channel boundary. Benchmarks show that Flutter incurs an 18% latency penalty for BLE operations and uses roughly 60% more battery during continuous scanning compared to Native Android [cite: 39, 40]. React Native performs even worse, demonstrating high latency variance and significant advertisement packet loss [cite: 40].
*   **Platform Bugs:** Android's BLE stack is notoriously fragmented. When a BLE bug occurs on an OEM device, Native developers can implement low-level Bluetooth fixes. Flutter developers must wait for the plugin maintainers (`flutter_blue_plus`) to push an update [cite: 39].
*   **Verdict:** Build natively. Use **Kotlin with Jetpack Compose** for Android, and **Swift with SwiftUI** for iOS [cite: 41, 42]. If cross-platform is an absolute mandate, utilize **Kotlin Multiplatform (KMP)** to share business logic (algorithms, DB) while keeping the BLE and UI layers strictly native [cite: 41, 42].

### Android Architecture Specifics
*   **Background Work:** The app must utilize a **Foreground Service** with a persistent notification to prevent the OS from killing the BLE scanner [cite: 14, 39].
*   **OEM Killers & Doze Mode:** Chinese OEMs (Xiaomi, Huawei, Samsung) aggressively kill background apps to save battery. The app must prompt the user to ignore battery optimizations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and disable OEM-specific app killers [cite: 43].
*   **Local Persistence:** Use **Room (SQLite wrapper)**. It provides thread-safe, observable data streams (via Kotlin Flows) perfectly suited for plotting 1-minute interval graphs without blocking the main UI thread.

### iOS Constraints and App Store Risks
*   **Core Bluetooth in Background:** Apple allows BLE in the background via the `UIBackgroundModes` key (`bluetooth-central`). However, maintaining a connection 24/7 requires flawless state restoration handling; if the app crashes in the background, iOS will not automatically wake it unless the sensor triggers a characteristic notification [cite: 39, 44].
*   **NFC Backgrounding:** iOS restricts background NFC reading. The user must manually trigger an NFC scan session via the `CoreNFC` framework, limiting the fully automated "tap and go" experience slightly compared to Android [cite: 44].
*   **App Store Policy:** Apple strictly enforces medical guidelines. Apps that display real-time blood glucose data but lack FDA/CE clearance are routinely rejected under App Store Review Guidelines. Apps like *GlucoseDirect* circumvent this by operating exclusively via Apple TestFlight or requiring users to build the app locally using Xcode [cite: 20, 21].

---

## 7. Legal, Regulatory, and Safety Context

Building a CGM application resides in a legal gray area. While the `#WeAreNotWaiting` movement prioritizes patient autonomy, the developer assumes significant risk.

### Regulatory Status (FDA/CE)
Applications like DiaBox, Juggluco, and xDrip+ are completely unregulated. They hold no FDA clearance (510(k)) or European CE mark for medical software. Consequently, they are legally classified as experimental tools [cite: 4, 44].

### Abbott Terms of Service (ToS) and Reverse Engineering
Abbott’s Terms of Use explicitly forbid the activities required to build these apps.
*   **Prohibitions:** The ToS dictates that users may not "reverse engineer, decompile, disassemble, decode, create derivative works of, gain access to the source code, or modify LibreView" or the sensors [cite: 45]. 
*   **Data Hijacking:** Abbott actively issues warnings and blocks apps. In January 2023, Abbott emailed DiaBox users stating: *"Diabox is an unauthorized application... that poses a serious health and safety risk to consumers"* [cite: 22]. Abbott aggressively defends its ecosystem using the DMCA (Digital Millennium Copyright Act) against developers who publish proprietary decryption keys [cite: 1].

### Liability and Required Disclaimers
Abbott completely disclaims all liability for third-party use of its sensors, stating they are not liable for any injury or "interception or unauthorized access to personal information" [cite: 45, 46]. 
A responsible builder **must** bake aggressive disclaimers into the application UI, requiring the user to tap "Accept" upon launch. Standard phrasing must include:
> *"This application is NOT a medical device. It is intended for educational and research purposes only. Do not use the data generated by this app to make treatment decisions (e.g., administering insulin). Always confirm readings with a calibrated capillary blood glucose meter (finger-prick) before taking medical action."* [cite: 4, 44]

---

## 8. Biggest Technical Risks and Unknowns for a New Builder

Before beginning development, the following risks must be factored into the project timeline:

1.  **Catastrophic Firmware Updates:** Abbott frequently pushes silent firmware updates to new sensor batches (e.g., Libre 2+ serial numbers 301 and 302) that alter the `Patchinfo` or the BLE encryption handshake [cite: 16]. Your app could function perfectly on Monday, and fail completely on Tuesday when the user buys a new sensor. You are at the mercy of the reverse-engineering community to crack the new keys [cite: 16, 40].
2.  **The Libre 3 Encryption Black Box:** Unlike Libre 2, the Libre 3 BLE stream is protected by standard ECDH/AES-128-CCM protocols but requires spoofing the LibreView Account ID to complete the challenge-response [cite: 6, 17]. Implementing this in Swift/Kotlin from scratch without triggering an Abbott server-side block is highly complex [cite: 17]. 
3.  **Bluetooth Fragmentation on Android:** Maintaining a 24/7 background BLE connection on Android devices is notoriously unstable. Connection drops (status code 19 - remote device terminated) are common [cite: 18]. The app must have incredibly robust self-healing logic to seamlessly reconnect without user intervention [cite: 18]. 
4.  **Calibration Dangers:** Implementing DiaBox's "i-Algorithm" allows the user to alter the slope and offset of their glucose readings [cite: 23]. If the user inputs a faulty finger-prick value, the algorithm will universally skew all future readings, potentially masking severe hypoglycemia. You must cap the maximum allowable offset and slope adjustments to prevent lethal miscalculations [cite: 7, 23].

**Sources:**
1. [frdmtoplay.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGgXGOinLW1J4Qof6oFJRsR-5lePlLCECpR29WtrSiQQxf-i5SKpYL0i6JYjE8pbxawTilO5LtZc1tIAvYzWfKUElOhIPPKw8mhcih0Dz3iCpsaPTCv4uu51MNoo_3QW75X-WWpYDi8g1_DuKdd6R6xbxPXKmfyswqzH96wjFIt27M=)
2. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFLUlshtTAITPv9POWP-H60AGJsB-dIPM1Tj3yMjM0Vnha4eILB980vIPwc3dLk_L6r7D8xVYdMXTCOZeTLyILGWA6vuDJvhUbE8p1G0JABngon9aU_xjEvHfbsK45B)
3. [minimallooper.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH2KROScqcD4ABYkvSHC6SjbMJiHe36og3N4u4V4kF2jbJujNgV8NkyyFodD-h3qPG2wixMU272bd6v-DGZ7KLsBu8BQkWEeWGc62uo4uLEO1v71AVHVt2U2bg_UKS4DOr5q0wfJZhby_rvv7SHV6A7GcgoDRe1IHdqcTfokqzYKhlEbSDJCd48kokzO220sFJPjHTFsIlEgP-G23_91zXI-RoCQeRvwjqhIWlQF3w=)
4. [freestyle.abbott](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHYLMXj1t0YOaVTVzCo_n4zHYdeRqK-F8zL-Qlq1CfWn10W_nh4f_9w-DGdiWVOb4DeS3XATv5YQWBtnO0hTqX-e2RQWwwntY69QDAA-TUUGkMBviBPr2xM8OXii7kZgKvWSg1nv_6SIXk=)
5. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEHIcxY3gynabv5Q9rcW39EkjxOF3yKdGCnu4lct_npDvZyC1VNk9O97DtqWt4qUI2-fKLycwoL9rMG-WlSimqQcxJY1iPzxBv5Mq_6MIpsYAohFGvBapJceJ25ljt7Fn42uHoYEWAtbY2LVFijxoi38SNQQti7JOIP3KkrYigMIU5Z9lVh1bE5hMovcdHXdTPGxPTgwe4l)
6. [juggluco.nl](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEmAxT8zCcQgrHCRcNSae7D418qZsWFpXPkxITnET1-I1Zlc0UVRLfbx58X4cjgL1f1NuOMf-VSzkF9cJ-ucsOOo6lC7ZSy_Um9UUdVsKMTPD5x6wDK4H4wY-0aB_bd)
7. [readthedocs.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGYiCE4SL97Y6ZYM8Cti4Cu4UvsY9CzHXjv2S8J8w5V7vWxCV_t2Jq4OyyxJZafrG4IeAzk3TcZ4WuSJacflukPzEaneUbD3cRra4ZyuA8of57TahKRFNbNpwX69-VKrjwDuCqXyByfkdB1aZSwVA5897QeqSF-hzel)
8. [readthedocs.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGDe_9rPGP7hR5CrgHrglpA9Gb6fuHD--Uk34ig-v0RHqDmhrgMv4rrMmh3bvt4ssJqWpmE37F2Y63aPNAIfK3LElBHKdZyTa0yJEt0Y5jvEt0SZp_czkSu3qrl6MT7kVz7emZXkL6JW-blwGXQRJYcrtQy4b4i4OOrO3Z-)
9. [readthedocs.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEzwhL02o4AF5I2W-YW2q12CRwXaUXVV_ezDF7TmpOu5rESt0noTO_xPkyAnwYwWONPT5jHxZLVfBnfW0pRqieeZYcQrLBsmUPgV2WYaRrZw9K_7B3NlMdgEIi0YgJdTMJ6_vt5JEPDW7C83bF4eJyepduhHsSlSNydE5O1)
10. [diabetes.co.uk](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQF8SrAq6oXuXH_BeGKTmJ6g6N_aY0O_XnyW4I39cKi1OJoJGcLovN1gjuOsUmM2B9PVeOgLpYjtSZXD2VEzcEf9KhNpOShlvql2lNs_3QdNFGcyaXAUZ_wXrbi04TXPzMJtCOZP08ZWjf3pWUFljMObcMgkjikNZVV-3h8axGRihYmfDbE_aX1uf72Y0gNoZ0I=)
11. [umn.edu](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHNMda93WdzSlTiwWwhKEV9jE2dSrw9sDmHbR6f91d9cfHGp93RWti0MaNWUnFJhXj27ilgetEnZaGYDygvRb8YpQ_3uHRPIrNTv1oiQLg97H6W6S3wcsTRmw4C2orZtDuPeg==)
12. [diabetotech.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG2dgqqZfkVV0P841G5yg_Ibc4j8GWUjwKVf0h7K3U9s7UEubP9x6ak774YuxUPePN7Qy3ptiFNCz3JwY1H0np-8mXHvMrM4Kf3AP6jGlb9K73mSgjl7AgW8R_QPzPAM19rNb46VhnXSpTe5-A-Yo9wIjDBp_NFydMCaEKUWA==)
13. [nightscout.pro](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHdgoiWbUwp4Lr7bDQvzZrXlmeS-hBMD-F6B7jkIcE2TG3SxAfrPycFjSG4cz7TnX7S46hQkt4GzsR-edlP73m_BMj42urryUkh8oxVx66FtH2q2SoigOXJ4Fc3FfvrYY9yXt9cRTPx7woiMNUNqmAiO3E=)
14. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGX4hWN6oTUlEa4RzaGCCzBDk4nyAPrkvwtI-qPBwbYKVSxflYsmbrWLxUOVk1SJKZoHdJKeoCqxZfWQ-xTJayJr5DQBeMIN7WzUH2O5XIdnNhBUJquQwso8KT4jdixwvTWkRSt)
15. [readthedocs.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFqKDDvkdaV6tjvExpazNrv2QRsTweVYwoJ1bOSWVJ7GFzJyUrRr9cTDAPgYS6Ie-Gm2Nz7_WVWMxpSAu8rzrspkmusOAe9yByPD8ZU2VoZx11wXvDDBiEBtUEjk2HkEs3jmIrJt5Ln_XR6kbm7Jn3nku9F)
16. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH6c9HAjr6UCXfBdDBxjK6F7DycfLUaDcE91icTLyMqbTkIQ5JdvQHy9FMcTZPa-kyFSL2Ws7L5NBQoiR_aWVWyRSnlg72jTGVMGmA3-pmMXTq2cBqpuwVREjtfne8zg3QHSguGHoQa6ScZ4YINiP6itrKB7A==)
17. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHREeacyRiFLy6v1zB4RI94IaSyNWjVqUKQwFp0izY-wfIIUCO4ErlasXwH9TsLadktjkxYopAqZLw_R2ZqwLhZoXSdoRexNSUgX27xNQkYdN5T1dHQ8KTSnrDYULwvlbi59yFlJes=)
18. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFNl3E1TGBOJngusZo5oBOZ-ECoL3RGZmMcAcRjl1nqwVnqeJ-In16Eig66voy4zr29y_hYnfCCs-Osgqmq40RHq2wfHKweJXrm_o1NUPOHwclPkiaUU-S3UvXyllQ1w8Di1lwrJg==)
19. [readthedocs.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH9qsbl7XqoMZKWKNuINYD9B1XGYUy8XT4lH-y4aJkmP5ioYLfUQlxjF1yRuTjHl1cQ5RDfJEAmCWYsff-kBBDM5Y1Q1RpfuKLyLkqiRzYoLfn0ypKomISqEL_YNF8PHtjCGknezSpBCb6F10V2WjAdmsvSg1Jm8MQI6UXD49k=)
20. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH3EIsE200FACKyPtWonNAX8uTZ0K4y2vmD9r2ueci6P19yOXYZJObQAlkZy1ss2bWSZpQqxhl_sQZfpC6YdBByh_MU-YFTxbimQLQkMKAO9enYRIKjnF3d7elLfMhOd6ZVIZaE)
21. [tidbits.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE8t8eYTJjAwOU4kurdsPVUBaCTQzus_srrEl6vGmZJjbCWuTIUhEfET-qPBxAdzfUDYjjfeixiFwqCG4kCWTRcMSjbVVXyoZv2OQnxA0-tTcRWwH4GhzrEz699FsEoS5wlcx7qiEtjEioKfGbdHEpP_bCW-dwe0JXqDPcwY_Ok)
22. [digital-diabetes.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFiOltyqP8EeAGuBJbkOPoDChsXN4QI2WXs5uhzArI6-ZkUIjCKW8ZGw6GUyns0zynt7empwqQEm39Dt7GrzUC6iwFNRArzLVSAOJDi1oT3qZCYDAZHfBENLBMlRujQMDksTDZ5bjOPaggl-YhheYsqtXJN3Cq5a0wZi7gFXISXySnXk2H0K6jfSUH0cBIBYu00TuGVnM0qLnpoj6xv-Nf95Kk1sJA_0Apu4R551g4iOS-b)
23. [scribd.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFETiFUKEVwKkECOAz9FfzEPuTvsL9klKeawG5Eok4Gw6Uu37W34Baoi1eD-_it6XMauuSDp-pYQ8Ai5D6AHvRvsFQwEPLnQswMr7w6oGgT5bD9g2C_15yhmbvZqvi1kX8MlNJ64ozP82TLRESyYVmyKG_6PUOi1cZoUQ0oyQ==)
24. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHA7kc-dZu8OfeARXvlvGpIDnoWeqk3h4P5DZOf9PtNIUV-wzwDRujEYlET8OD46FiRF-TBXACSMHYkD_uwk2T3QYHQc5rfonmlJLopvQji4UrAwj_e6_U=)
25. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHh0tNw7xRSSzbfsUoj_uedymBpRn7Zw93aqivR9cj0acqP_fc4bcO0vCIEzc2hjGjQmmqmYKgWJQ40-gqEguvSp3f_23jqYaKVe86c9BqF93feqDpC3JZGZk5tqaU9RzdW_Aw=)
26. [healthline.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGP7pg7dpxXi7LT6FZ1-SiVnaz3GxC0as3tiH-pxj8Hf5R3GQqz9WmYentVM2wmhfJeE9fIkVsx0I8EVX90Ww8DJcJcC5tt3VrqVOfZgV15WyV6xmksPCw23kGGCSa5F8qhQmuWzaXBnU0FKwY=)
27. [diabetesqualified.com.au](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG_0O9UG_XNsxo3q0z6R4Rt7elzUye3V9G_-Iymb-QsAKu1S3b5j0hn4g7QWfxywPVnUUSS7pJK4JTG80vs818yOqENun9ZTWpbLvcDLRbrP3boT1A7ywq2zLit-ndw4QDTgCaN9PgRrkjvi3Lilze2NRgx2ZOUDZwWDog_uCo_buK4Nv--WuOm)
28. [dexcom.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFR77vkOvd_3BN8zKj9av5rSRgQ2ZsKRcOZdP1wDdp1Ahy8-izK1aI16Wr7C7xECJvg_LQDfzYvc4XtnwUlz-BMybtGP5s_toh1UoVxj7mpAISqAI8EDa4ck4LqT_EHLWiu0eicp4noJK7M-3w4kt2Jvi2jXeItWg==)
29. [researchgate.net](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE4ETjlBIO5kLzbps6tqaegmxWtGQPEPtO8i8LdmZkwS8C1RLrpoumzkeXjRap8xbNO19nDbwqhyyyX-etT2QnraAbHcYtctdi5yZNvPrBr6M0JJMaDN9gWXztBJYdjoPX3gtzIitQPYoIaBOwg0OOmrmskVm2rCqjQ3HVu7lwslPAoAoRPtx1CIJZGXQVswlJhn20Q3Npmczpksu_Ic_TJXqqC4VOxG-2aZYPKUjPl8lOa59Bnx6fCQtuVBj7jm8c0hat6LMDnCnZe6A==)
30. [sciarc.de](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHU_UfJ4rFejIAd4CaZ-2kys0esksTuERUwO2FF2LQXLshBX0zUW3D3OA2w-FHqZCqVtHLHqX9FApt_vU8UtPvWs8QD7qAb1DhenfASx6M8GuBkjHVcTAqIpuHg5ub3Z4nffAD9-C5KdGPllw4mgoS6Y9S7Nq6iurx1BjYBMYFqv-yBr84et1PI_zcC1plMeTQpN-Ig4b5NF5_W-x5LmSZowh7RSf3bGATnaswZka4=)
31. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH1e2mi-Y0siodBQ-G-tIsZNurgz8p3LQoftCumLvl9U5epJpktKoTS2bQD5BKJiwsmOdqvlksibogYUeEVobj4WWQ6wXCfVuZhfzanFg09YHf-BCaLBWCudHArx7Fbbi56Ps7CC6zjmg==)
32. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFuJ_Vi_dUvmS8zPZ0C6az7I0V2VfqJslyxNXkPINcB0lgIsKj1WrMkSl7tnuOBp9JvYHYq2QrHkfp9Ph8F5O1GaN36wsNQw9HcJslSAbE0ROBNVoiLnqPS7oQX_veEpTaGjBZ768aJ)
33. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGGHpPez4EhoC6oKz8DyJvp2-RXFMBSM0EZSprr-_kpgGbIKbeEFJXs7lUkERrGGEbqjT35vjMzkZb_01z_mp7q24cd4BAhMDfLw_FhnSC7KpuU_HqDc5IRFM5R9YKJyfbPTQPrt1du)
34. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQECzUiWMb_JYR2A_RHK9E-BTNrAKmX_oHzEggzOci3r7Xfs4sqyHINFDRUpYPaJCHIE0iPHu-rMhBTMQO9mw_efuSw_KYghVK_UcPx98qqXMZ_flgClxv6o1uLsAmmdTuCI797pPDUZ)
35. [github.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGo9aDet2_S1NP4mR6EhCcEF6IEgjpXY720H7P1CTU4u1jCkxFyrgieDOiy-noe0hjmeGNS7aRkkIPT8YBLUhHBPQOjn6dpvlOMEXcfkF_ayF_PNqUJi1N9uEia8LNTyhnqEKpGRFoimFU=)
36. [juggluco.nl](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE3VSo7wRmdesQROehEj1fVXaowtODmYYQh73ZauB-6PhsCNW8i81zTZQHNcYmcux6nv_SGII3wapXUiXfh-16Nr80s351TjWVLW8vqov8r3i1iuiBGax1EIftUJwu41Cht0Lb5C5eaOoyUehQ=)
37. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH59VbV4jKr4p83ASwiLRmOUOkau9E9mBAW5g_poT9MnAMpGamOAJGe5mNowYJnh1pTC2bY_L3TWq7x4qEWv_fnj7hw7LJ-UdE59rSLyT3lOMI=)
38. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFD5PibEBIsSGIOxBFSbx8DyJ9lRkC16cvEUhqEc8KDwsycpaAzy7Paip7gVg6jnWYjvLsgK_0dVZ7Kre9Q3AGPlN9KCO7r6NAKA9RZevQI)
39. [varunkudalkar.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHgPpA4T2y797SDIG5A30ADdTqi2EtdZdM3eY5dQnC6CWp-KFLI-bW1A4HTx8C_2NqUHBC5MJscp0jHxiyVzsyTomEpFlaOkeuLrrRPwLJ1Z_hBlnqXZAEDNwAD7YlbmGiliNQpe5gnbZGtPwRlu6kxdQ_R0EEZHtblIBVV-Q==)
40. [medium.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFN7y9Sua7tu6MAd8-Bgs2JcSwtmT_oPyZj1a5llxyX10HeNUBQMaxsovqEsmSw4bxvjfe5nh3-IOZ-UAOc0kCbQuS2Q33y66vOwjRc_k5_X2z51VJS7FD06ZaC3WanlVEkSRpuoM3tH7C1rZF19muyAU65AQQAqQF7_QiK8uhp6gzEv_x4yVxKfTmYMwg-Ilugs7JMNB13CEGf4_16XdSTR5GP)
41. [galaxyweblinks.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH6orDFJqA3dR8KzalSbYuZQx8it28pXZpk7CvD_hlH-NJsmi8OwuObzjxsK5n9pOhTXosuaXwEWkIZsMynFrKHelt9XZGCAVbqt7aXklO25N8vRrpx72JwsfGlmIo7KxblnyKlvntjbe83cw==)
42. [wildnetedge.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHx9a-R0ijFD__tAU4Jm5gl-pWnRwJSqkDjugcHjw5uWhZyId5m8EKddRTaBbMjXNRXZ7PX3-0LjTS2EJBnWfQjlP_5HZZm9rbqH7ZQe_t5hiaYSTqfJeptMTLVkzp4TWUNsjA0otqHp9cU48C55wnY2R4OE3xbyoK4mb1AGOtT_3CuNCSg9mo=)
43. [libhunt.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFXrKHB3upbWPczEOaQoI2fC1rcXrVwoFYw1PQbo1pRvliNGvDJUpCadDSOewo7BlkXiD8tzMz3qB6p_biePLVfe9vs1dnaFgdI6n3ntVon2uWO9M5GVg3HGn95N-5H8QBwrPqhm5Y8)
44. [researchgate.net](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEx_g_TN20gQEayA-eEAYIJOnS0_YOB6QZZ9qjG7xo-p7v9DmE9nGFOkO6n-gFo48dO-0wdTaeT3VPhjly3yjN7UC9BJ4W9G6yLPDUL9sAw-GasXJHFCxYLWHjqAfYAarrziqA2s8qj66OyPpw0NdZ77tzLNse-OWeKXV2aS-UwH5ThPQc9yZJaamXAqcMBFXiJzsGWczQY6CMBNIQb2V72I_mGsffuuutaNaV61ekMN3o4xprYTnA=)
45. [libreview.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHZo7RxCHVkMZ_Dn8nQULtZ0SOLYZqAaKv8xqGDvbrN_a-UF5sUldI25lzwQQW9-rwVcZT6xb_TQSnTftPGfM8-H5KEzqeepz9MH-m3wCh4s3geehV6C_2D8i4ZyMoRSC4twFUlscg=)
46. [freestylebattery.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEaqH_s0_c9Vr8-ac1Ei-D_KwscBx-yYvUi3QfBZzG2D0OB9YOq8GcIi723xbCJyeGRdvDk57EpybnPnVPzFFkL4ihEb7U6J1AlwDSf5FavUNCqrGngx3oCnoPI0bUDBhd1TVPCSg4btle-Zs5SGwGGNWTBDObT)
