# Comprehensive Research Report: Next-Generation CGM Application Design & User Experience

**Key Points:**
*   **The DIY CGM Landscape is Evolving:** Open-source and third-party apps like DiaBox, Juggluco, and xDrip+ fulfill critical clinical gaps left by official manufacturer apps (e.g., calibration, predictive alerts, raw data access), but frequently suffer from fragmented, overly technical, and inaccessible user interfaces. 
*   **Clinical Metrics are Shifting:** While HbA1c remains a standard, Continuous Glucose Monitoring (CGM) specific metrics—particularly Time in Range (TIR), Glycemic Variability Index (GVI), and Patient Glycemic Status (PGS)—are increasingly prioritized for their real-time clinical value in preventing microvascular complications.
*   **Accessibility is a Medical Imperative, not an Afterthought:** Medical device UI/UX must adhere to FDA human factors guidelines (IEC 62366, ISO 14971). Red/green color-coding is fundamentally flawed for the visually impaired; alarm fatigue is a life-threatening risk; and high-contrast, glanceable typography is essential for both daily management and cognitive offloading.
*   **A "Smart" Home Screen is the Holy Grail:** The ideal CGM application interface must seamlessly blend immediate actionable data (current value, trend arrow, delta), predictive insights, and context (insulin-on-board, carbohydrates, activity) without inducing cognitive overload.

### Introduction to the Redesign Mandate
The continuous glucose monitoring (CGM) software ecosystem is heavily bifurcated. On one side are official OEM applications (Abbott FreeStyle Libre, Dexcom G7) that are FDA-approved, visually polished, but functionally restrictive. On the other side is the "DIY / Nightscout" ecosystem (DiaBox, xDrip+, Juggluco), which provides advanced functionality such as raw Bluetooth polling, custom calibrations, and algorithmic forecasting, but is severely lacking in User Experience (UX) and User Interface (UI) maturity [cite: 1, 2]. 

This report provides an exhaustive, multi-angle UX and Product strategy for designing a modern replacement for "DiaBox." DiaBox currently reads Abbott FreeStyle Libre sensors directly via Bluetooth but is visually dated, utilizing a cramped dark theme, inconsistent visual hierarchy, and poorly prioritized statistical displays [cite: 3, 4]. To construct a world-class portfolio piece, this document synthesizes user feedback, competitor breakdowns, clinical standards, and FDA UI/UX guidelines into a highly actionable feature specification and product architecture.

***

## 1. DiaBox Feature Audit & User Pain Points

DiaBox serves as a critical "bridge" application for diabetics, particularly those using Abbott's FreeStyle Libre 1, 2, and 3 sensors. It bypasses the need for near-field communication (NFC) scanning by establishing a direct Bluetooth connection to stream glucose values every 1-5 minutes [cite: 3, 4].

### 1.1 Core Feature List
*   **Direct Bluetooth Streaming:** Reads Libre sensors directly, converting flash glucose monitoring into true continuous monitoring [cite: 3, 5].
*   **Sensor Calibration:** Allows users to input finger-prick blood glucose values to offset algorithmic inaccuracies, a feature notably missing from the official Libre app [cite: 3, 6].
*   **Advanced Alarms:** Customizable high, low, and urgent alerts [cite: 3, 7].
*   **Third-Party Integrations:** Uploads data directly to LibreView (for endocrinologist sharing), Nightscout, and xDrip+ [cite: 5, 8].
*   **Watch Connectivity:** Pushes notifications and basic displays to smartwatches [cite: 3, 9].
*   **In-App Analytics:** Calculates standard deviation, eA1C, GVI (Glycemic Variability Index), and PGS (Patient Glycemic Status) [cite: 7, 9].

### 1.2 User Pain Points and Frustrations
Despite its functional superiority over official apps, DiaBox suffers from severe UX and technical friction, causing high churn among less technically inclined users.

*   **Installation Friction:** DiaBox is banned from the Google Play Store and Apple App Store due to regulatory and manufacturer pressure. Users must sideload APKs on Android or use complex TestFlight/Self-signing workarounds on iOS, creating a massive barrier to entry [cite: 5, 8, 10, 11].
*   **Connection Stability Issues:** Users frequently report dropped Bluetooth connections, requiring phone restarts or NFC rescans to re-establish the stream [cite: 4, 12]. The app fails to clearly communicate *why* a signal is lost, leaving users anxious [cite: 12].
*   **Cramped and Confusing UI:** The interface is excessively dark and cluttered. The "stats strip" presents complex metrics (GVI, PGS, eA1C) without clinical context or clear visual formatting, often displaying blank values if data is insufficient [cite: 9].
*   **Alarm Fatigue and Mismanagement:** While customizable, the alarm interface is overwhelming. Users report that alarms can be intrusive, and the snooze mechanics are not intuitive, leading to dangerous "alarm fatigue" where users simply disable alerts entirely to get peace [cite: 6, 7].
*   **Weak Information Architecture:** The settings menu is a "kitchen sink" of features (i-Algorithm, Integration, UserProfile) with no logical grouping, making it difficult to configure critical sensor parameters [cite: 6, 11].

### 1.3 Why Users Stay vs. Why They Leave
**Why they stay:** Users are inherently loyal to DiaBox because it liberates their data. The ability to calibrate inaccurate sensors, bypass manual scanning, and upload to Nightscout makes it a cornerstone of the DIY artificial pancreas community [cite: 3, 5].
**Why they leave:** Users abandon DiaBox for official apps (or simpler alternatives like GlucoseDirect) when OS updates break the Bluetooth bridge, or when the visual clutter and technical troubleshooting outstrip the benefits [cite: 12, 13].

***

## 2. Competitive UX Teardown

To design a best-in-class CGM interface, we must extract successful UI patterns and avoid the pitfalls of current market leaders.

| Application | Strengths (UX / UI) | Weaknesses (UX / UI) | Reusable Patterns for DiaBox Redesign |
| :--- | :--- | :--- | :--- |
| **Dexcom G7** [cite: 2, 14, 15, 16] | Beautiful, modern UI with clear icon labels. Excellent onboarding tutorials. Features a 12-hour sensor grace period UI. Direct-to-Apple-Watch support. | Alert volumes are tied to system settings, causing missed critical alarms. Graph colors (red/yellow backgrounds) can fail contrast tests and look abrasive. | **Pattern:** The "Grace Period" countdown visualization. Clear icon+text navigation bar. Prominent, rounded typography for current values. |
| **Abbott Libre 3** [cite: 17, 18, 19] | Extremely clean layout. Swipeable 12-hour graph. The "Insights" tab effectively visualizes Time-in-Range and daily patterns. | Cannot zoom deeply into the graph. No calibration UI. "Boring" aesthetic. Unbearably loud, un-snoozable FDA-mandated alarms that cause user frustration. | **Pattern:** The "Insights" tab structure (TIR, Low event breakdowns). Quick-logging buttons for food/insulin directly on the home screen. |
| **Sugarmate** [cite: 20, 21, 22, 23] | Unmatched glanceability. Excellent Mac menu bar and Apple Watch integrations. Automated phone calls for urgent overnight lows (a life-saving feature). | Recent UI updates (v3.0/v4.0) forced confusing logging mechanics. High server-dependency causes "loading" glitches. | **Pattern:** "Glanceable" lock screen widgets. "Emergency Contact Call" feature for critical lows. |
| **Juggluco** [cite: 24, 25, 26, 27] | Open-source, incredibly robust sensor support. Broadcasts data seamlessly to other apps. Includes detailed AGP statistics. | Visually chaotic. Forced landscape modes. Hidden menus requiring a "PhD to master." UI feels stuck in the Android 2.0 era. | **Pattern:** Robust local network broadcasting and Libreview bridging. |
| **GlucoseDirect** [cite: 28, 29, 30, 31] | Native iOS Swift/SwiftUI app. Beautiful integration with Apple Health, Dynamic Island, and lock screen widgets. | Highly experimental (TestFlight only). Apple's API changes frequently break its Calendar-based watch complications. | **Pattern:** Dynamic Island live activities. Apple HealthKit sync flows. |
| **Nightscout / xDrip+** [cite: 32, 33, 34, 35] | The gold standard for DIY data. Calculates advanced metrics (GVI, PGS). Predictive line rendering on the main graph. | Extremely technical setup (MongoDB, Heroku). Highly utilitarian interface with zero visual polish. | **Pattern:** Predictive trend lines extending past the current time. Caregiver "Follower" mode UI. |

### Key Takeaways for the Redesign
1.  **The Home Screen is Sacred:** It must instantly answer three questions: *What is my number? Where is it going? What happened recently?* Dexcom G7 excels here; Juggluco fails.
2.  **Contextual Graphs:** Users want to see *why* their glucose is changing. Allowing users to drop pins (insulin, carbs, exercise) directly onto the timeline is critical for pattern recognition [cite: 34, 36].
3.  **Actionable Insights over Data Dumps:** Raw numbers (e.g., standard deviation) mean nothing without context. The UI must interpret data visually (e.g., Libre 3's TIR bar charts) [cite: 17].

***

## 3. Metrics & Reports Diabetics Actually Care About

DiaBox currently displays a cramped stats strip. A modern redesign must elevate these metrics, explaining their clinical relevance and visualizing them intuitively. 

### 3.1 Core Clinical Metrics
*   **Time-in-Range (TIR):** The percentage of time a patient spends within the target range (typically 70–180 mg/dL or 3.9–10.0 mmol/L). *Clinical Value:* TIR is rapidly replacing HbA1c as the gold standard for daily management. A TIR of >70% correlates to a significantly reduced risk of long-term microvascular complications [cite: 37, 38, 39].
*   **Time-Below-Range (TBR) & Time-Above-Range (TAR):** TBR (<70 mg/dL) targets are strictly <4%, and severe TBR (<54 mg/dL) targets are <1% [cite: 38, 40]. *Visualization:* Best represented as a vertical stacked bar chart (Green for TIR, Red for TBR, Yellow/Orange for TAR) [cite: 40].
*   **GMI (Glucose Management Indicator) / eA1C:** An algorithmic estimation of laboratory HbA1c based on average CGM glucose. *Clinical Value:* Provides a familiar benchmark for patients and endocrinologists, though it can deviate from actual lab A1c [cite: 39, 41, 42].

### 3.2 Advanced Variability Metrics (The DiaBox Differentiator)
DiaBox and xDrip+ utilize advanced formulas developed by research algorithms to measure glycemic stability [cite: 43, 44].
*   **Standard Deviation (SD) & Coefficient of Variation (CV):** Measures how far glucose swings from the average. A CV of <36% indicates stable blood sugar [cite: 37, 40].
*   **GVI (Glycemic Variability Index):** Measures the "length of the line" of the glucose graph over a specific period, normalized against a flat baseline. 
    *   *Scale:* 1.0–1.2 = Low/Non-diabetic; 1.2–1.5 = Modest; >1.5 = High variability [cite: 43, 45].
*   **PGS (Patient Glycemic Status):** A highly complex composite index combining GVI, Mean Glucose, Time-in-Range, and a non-linear penalty for severe hypoglycemic episodes. 
    *   *Scale:* <35 = Excellent; 35-100 = Good; >100 = Poor [cite: 44, 45, 46]. *Clinical Value:* PGS provides a single, unified "score" of a patient's overall diabetes control, punishing dangerous lows more severely than highs [cite: 44, 47].

### 3.3 The Ideal "Reports / Insights" Architecture
The redesigned app must feature an **Insights Tab** divided into two temporal views:
1.  **Daily Overview:** A fluid, interactive Ambulatory Glucose Profile (AGP) overlaying the current day against the 14-day median curve [cite: 17, 48]. 
2.  **14/30/90-Day Trends:** A dashboard highlighting the "Big Three": TIR (Stacked Bar), GMI (Gauge chart), and CV/GVI (Line trend). 
3.  **Export:** A one-tap export to generate a standard, doctor-shareable AGP PDF document, bypassing the need to upload to LibreView [cite: 25, 48].

***

## 4. Feature-Gap & Upgrade Opportunities (Prioritization Matrix)

To elevate the app from a basic reader to a comprehensive diabetes management platform, we must bridge the feature gaps identified in competitor apps.

| Feature Idea | User Value | Build Effort | Priority | Description & Justification |
| :--- | :--- | :--- | :--- | :--- |
| **Lock Screen Widgets / Live Activities** | **High** | Medium | **P1 (MVP)** | Users demand zero-click glanceability. Implementing iOS Dynamic Island / Android Floating Widgets prevents the need to unlock the phone [cite: 24, 28, 49]. |
| **Smart Predictive Alerts** | **High** | High | **P1 (MVP)** | Moving from static thresholds to algorithmic slope-based predictions (e.g., "Predicted low in 20 mins"). Reduces severe hypoglycemic events [cite: 13, 50]. |
| **Event Logging (Carbs/Insulin) on Graph** | **High** | Medium | **P1 (MVP)** | Plotting meals and bolus insulin directly on the CGM curve allows users to visually assess insulin-to-carb ratios and timing [cite: 34, 36]. |
| **Apple Watch / Wear OS Complications** | **High** | High | **P2 (Later)** | Direct-to-watch streaming is highly requested but technically complex due to background app refresh limits and OS fragmentation [cite: 51, 52]. |
| **"Follower" / Caregiver Share Mode** | Medium | High | **P2 (Later)** | Secure WebRTC or Nightscout bridging to allow parents to monitor children. Sugarmate's "Call on Urgent Low" feature should be replicated here [cite: 21, 22, 34]. |
| **Light/Dark Theming & Localization** | Medium | Low | **P2 (Later)** | DiaBox is currently stuck in a dark theme. Allowing dynamic OS-level theming and RTL (Arabic) support broadens the global user base [cite: 53]. |
| **Automated AGP / Doctor PDF Export** | Medium | Medium | **P3 (Later)** | Standardized clinical reporting for endo visits [cite: 25, 54]. |

***

## 5. UX & Accessibility Best Practices for Medical Apps

Designing a CGM app is not standard UI design; it falls under the purview of Medical Device Human Factors Engineering (e.g., FDA guidelines, ISO 14971, IEC 62366) [cite: 55, 56, 57]. The interface must prevent catastrophic user errors.

### 5.1 Color Use & Colorblind Safety
Relying strictly on Red (Low/High) and Green (In-Range) is a critical accessibility failure, as Protanopia and Deuteranopia (red-green colorblindness) affect up to 8% of men [cite: 58]. 
*   **Best Practice Palette:** Shift to a **Blue / Orange / Yellow** paradigm, or utilize varying lightness and saturation. For example: Dark Blue (Low), Teal/Gray (In-Range), Bright Orange/Yellow (High) [cite: 58, 59, 60]. 
*   **Dual Encoding:** Never rely on color alone. Use iconography (e.g., an exclamation mark triangle for urgent lows, a downward double-arrow for rapid dropping) and text labeling alongside color shifts [cite: 16, 59].

### 5.2 Typography, Units, and Glanceability
Diabetics check their phones up to 50 times a day, often while walking, driving, or waking up from sleep [cite: 61]. 
*   **Typography:** The current glucose number must be the undisputed visual king of the screen. Use a highly legible, geometric sans-serif font (e.g., SF Pro, Inter, or Roboto) with tabular figures so the numbers do not "jump" as they change [cite: 62].
*   **Unit Handling (mg/dL vs mmol/L):** The app must flawlessly support both systems. The US uses mg/dL (e.g., 105), while the UK/EU uses mmol/L (e.g., 5.8). Because the numbers look entirely different, the UI must dynamically scale typography to fit the decimal point without clipping. Ensure the conversion factor (1 mmol/L = 18 mg/dL) is strictly maintained in the backend [cite: 16, 63, 64, 65].

### 5.3 Alarm Urgency and Escalation (FDA Compliance)
The FDA distinguishes between *Alerts* (situational awareness) and *Alarms* (immediate risk requiring action) [cite: 57, 66, 67]. Alarm fatigue is a massive issue in DiaBox [cite: 7].
*   **Hierarchical Design:** 
    *   *Level 1 (Info):* Trending down. (Single vibration, soft banner).
    *   *Level 2 (Warning):* 75 mg/dL. (Audible chime, sticky notification).
    *   *Level 3 (Urgent):* <55 mg/dL. (Loud, bypassing "Do Not Disturb," flashing screen) [cite: 15, 17, 50].
*   **Confirmation Flows:** Snoozing a Level 3 alarm must require deliberate cognitive action (e.g., a "Slide to Snooze" or large confirmation button) to prevent accidental dismissal during sleep-drunkenness [cite: 55, 56].

### 5.4 Safety-Oriented Design to Avoid Misreads
*   **Stale Data Visualization:** If the Bluetooth connection drops, the app *must not* show the last known number as if it were current. After 10 minutes of signal loss, the number must be greyed out, struck through, or replaced with a "---" to prevent a user from bolusing insulin based on a 30-minute-old reading [cite: 26, 49].

***

## 6. Recommended Information Architecture & Screen Inventory

The current DiaBox app suffers from a flat, disorganized structure. The redesigned app will utilize a standard, ergonomic **Bottom Navigation Bar** consisting of four core pillars: **Home**, **Logbook**, **Insights**, and **Settings**.

### 6.1 Navigation Model & Screen Blueprint

**1. Home / Now (The Dashboard)**
*   *Purpose:* Immediate situational awareness.
*   *Key Elements:* 
    *   Massive, high-contrast Current Glucose Value.
    *   Large Trend Arrow (angled dynamically based on rate of change).
    *   Delta value (e.g., +3 mg/dL in the last 5 mins).
    *   Sensor Status Pill (Time remaining, e.g., "3d 14h left", Bluetooth connection status) [cite: 6].
    *   Interactive 3H / 6H / 12H Line Graph with a shaded "Target Range" background [cite: 17, 27].
    *   Floating Action Button (FAB) for quick-logging carbs, insulin, and exercise [cite: 17].

**2. Logbook / History**
*   *Purpose:* Chronological timeline of events.
*   *Key Elements:*
    *   List view of timestamped readings, mixed with user-entered event tags (Meals, Insulin, Calibrations).
    *   Ability to edit or delete manual entries [cite: 36].

**3. Reports / Insights**
*   *Purpose:* Clinical analytics and trend visualization.
*   *Key Elements:*
    *   Time-in-Range Stacked Bar Chart (Target: >70%) [cite: 38].
    *   Clinical Dashboard: GMI, CV (%), GVI, and PGS scores, complete with tiny "info" tooltips explaining what a good score is [cite: 42, 45].
    *   AGP (Ambulatory Glucose Profile) heat map showing 14-day median curves [cite: 37, 48].
    *   "Export to PDF" button for endo visits [cite: 48, 54].

**4. Settings & Device Management**
*   *Purpose:* Reorganize DiaBox's chaotic configuration menu.
*   *Key Elements:*
    *   *Alarms Configuration:* Dedicated visual sliders for High, Low, and Urgent limits. Snooze rules [cite: 3].
    *   *Sensor Management:* NFC Scan button, Start/Stop sensor, Calibration entry matrix [cite: 6, 26].
    *   *Integrations:* Toggles for Nightscout sync, LibreView upload, Apple Health / Google Health Connect [cite: 1, 24].
    *   *Theme & Units:* mg/dL vs mmol/L toggle. Light/Dark mode.

### 6.2 Implementation Roadmap for Portfolio Build

To demonstrate product thinking to prospective employers, the portfolio presentation should divide the feature spec into a realistic roadmap:

**Phase 1: Minimum Viable Product (MVP)**
*   Core Bluetooth streaming engine (mocked for UI purposes).
*   Redesigned Home Screen (Current Value, Trend, 6H Graph).
*   Colorblind-safe dynamic UI coloring.
*   Basic High/Low Alert modals.
*   Live Activities / Lock Screen Widget.

**Phase 2: Advanced Patient Tools**
*   Insights Tab with TIR, GMI, GVI, and PGS calculations.
*   Event Logging (Carbs/Insulin) with graph pins.
*   Calibration flow.

**Phase 3: Ecosystem Expansion**
*   Apple Watch / Wear OS companion apps.
*   Caregiver "Follower" mode.
*   Nightscout / HealthKit API integrations.

***

### Conclusion
Redesigning the DiaBox app is an exercise in balancing profound technical complexity with empathetic, human-centered design. By stripping away the visual clutter of the original app, adopting FDA-backed human factors guidelines for alarms and accessibility, and elevating vital clinical metrics like Time-in-Range and the Glycemic Variability Index, this product redesign will bridge the gap between "DIY hacker tool" and "world-class medical software." This approach not only ensures a visually stunning portfolio piece but demonstrates a deep, rigorous understanding of healthcare UX.

**Sources:**
1. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFyRqRPxyMEv89zo3V-qNrk9_6YthTOP-pISclGKhQt1ALc4-F5UWI08qN_Qaa2zmWjidC-nKfe6G0aEMQWH5oQgARIj_I1JDbytXbvSHeXyaLme4UdAO8eP0YdWUlSMF1LJAgJmEYkKIYtt-GQM9D-_B38sIK6JC0PiwDvxWzFQz3wylOjSUK_3-Xis-O2)
2. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGAsE8qHqGwQ_-ysPgMj0HGgytkIHdYqbMOgpTMlSeBYCCn7P88fFy0TJxBIV8Eg6XZ8Zag2uxz10D9iiJmJDexcxZ5KnZP-U0YIFL55I_u436UDujfriK9YR03dMg0cSFeeKBt7FhC_vZy5s-CLw-F3mWqIhMD3Sd2kjPBkTpnnSzfvsQ=)
3. [oddhogg.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGxjhIO8WcIqvpJPqOtSjAO-Y45LaCTAjHy7Xl0wSA-jBhlmfUwUUOHkkC0dA3sR3SMvz-yWGVrjSr2PqCQNFrFlnAGGJW_MrB1cx67BRlkmHV5BnLQ1rOyzq0xH2wHQ80AfRQ1el7mshTW2nkD5tj70_ZXBENP)
4. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGVNc-ZcSfbiSjESv-Vfjku6964rOH5kuKdVWa6r3vpTN1Pbv9m5LcKKtCuj0pqc1zOKxBp30oMUJ8FqjXlUOcK1iDHXdGcP6MLBX1DLpECvdz4mDY31_rbYYZDx0BsNSonarErzOoKatSkLJuo1_XeD32EXIwd-LmeVMHG7EXz3urK1eeDjAi8e_-frxn_vQmrrKRY_4PMILcJrCLMqxU=)
5. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFk8ssFanS6ZpGmD5XUtYZEDGJv5cPFHKdA4ydP-ZrZiHfbxME1nz19u4YTHittL4b0X3-T_wYvOnsHzwK6V_J3WGILMXGw5ccu3og8IhUhANBkTj0iRUtMHGWpjbJobZVa-yJdzx13jmPLJ1BAQwmlPVJChkHEUYtljVi75hmc4BAJZ5plqDdpKJCtK-LT9JE1QTJiMhdipi1exmA=)
6. [youtube.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHRp6IVLqn7vqloya1WHSui7kIO0AhmZ8eqTueOdQ6gNPbzyDqxboOcx6Eb4QETb8Zl8ESBad1pRqbMoavb5u1mrTb2WLGoWCBkQ38baGc4Ipnm4RIoZzm8VxTbPikN67w=)
7. [diabetesforo.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGL3r607csCOSSqZfXBUQp16yJZbfeEyDdo8-F5-jOvkFCRr9UT8b1bj5MIG7TiZ2NdcJnXSCamEu94AxPSgYz30nOyVJZSMOJcTwqBEmyDNhTskcXpk_1LWoLe76p8ldLvxpr0)
8. [youtube.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEGQqPG_IyijtoIB_E68KS6LyPP3dN1YoUal-CFN-KCYYGjicp16z67yOA9lJifDNtAP3G_9SAk0z7IgfY7qUStHwAX-bgFHmfQE8a13zpiAMIbUG7YitBWaNQyjnO2QfE=)
9. [diabetes.org.uk](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG8YkK_MP_dGXxxTUhPqgOOsUDe-jiWjMddR0Dohov536Gwdv9U7BehEPkyeiHFzjYyelFgfSp1EOfYJwprC1Ukcu98EHvat9QbrcemGvuwO0bHhaHdPA36t4FHlUh_SzQKglzB4obwecDOvxtfCEwZmX7gN9A=)
10. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE_5h1G5w4WdR6y65eyzbM1Ajck_A9oTL_qPdTGOUwenTpuP4-nLZi-x3mscBjnczFe9NK7xnXhwuVvdz3VDhnlxbb59CoTMJyLVMfU3eUljrbHY4gblbAwrwNlEJq9GNagQgssHCsum_xbdnztUAC_P55b4_3AKOrprTK6e8njKK-sYoR3Ulhm3DqeoerLJRSke8l1YVQg)
11. [scribd.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFt3QuRrbOLf9YU-T6y2_nw00kpfp-bkxZMM6TwVUfuMzFO9feFFe_CvFhFvGcOEu1X5iMLGyon7ut3kQ28BkD4Bc_TfnCzHVqemZ3Y4sIeddX7KHkn4F0_E72juU6sW234bmH-IJlLzXwutNSde1MebCvfNQ==)
12. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEsPoOJ_gnqphD34-4DpSep5Dl1LW0RPLCHf90uHkAjD1UapRb7AeYgRmnbSLB8fkeM-YWlCsKD1KRslXeiC1ycuEHN42f1ie8EK9e5Dd6l8x7zPR-ca009DFXfk9dKDZnN_tFa4V25C9gGcq_DZa5qBJ_eMrR7e_7mXuBxkc8UPHJkagVqxt5tEKZoVMHilErmVS2OWkapq10=)
13. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEo5HWFCMJGlYj_mzQ7IKXnkvcnnRnMxA9f3IqXwp6ecZ41XCmGTcj7vX0R3zbNoEw4HFVHWxySRn2igLps-XTrkjKZnZhLqbhqglgvEqWsNc0pxfc_EJzPZCCu7CMEpJvJqn1C8lCFkCz7Y3A1ZQ_GWwmf5NyNK4rNMtDm81mZ-pQoV-oFdcKOgoEg7j9zvDZebzanWWE=)
14. [sarahhormachea.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFW02wn_QHUURPrqnaor6ojxgF_lXR6Txyp5OMuDVMeMrZN4j8ordze8zgybfCUKXpZcg25TBMXjM__cn8XiTXwTS4qH6tfxpJ4ZrhV5RZ5HbnGOqxgg-MjkzyqtH88lclPI-FrZQIo4di5A8rT-4e6r-NEeCsE5aU=)
15. [medium.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFsrHc2t0DA7GGMVHtOnOXUll0ghu06ufXl6To5gHxne1E2t6d1gI5av8c4iBmysMF04xvuLyhzXvMr0Bb3_kUBLs_u3XaBnyCDxLHNFncERTf8jeFlyiQlqz1vfR0DUsQ1VXupaBiDUAQgwF5LX6UVdA==)
16. [uxdesign.cc](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFgEXZATndz52l98_OlV2ZGfVPBN5jJH9xm5SofpHG8UyOFa7hcoLnODzBo2Ytnhlcgzu0G-sW7IESyU79F5KYm-LGgo5WCKebYv0TWErN9w-Xa4H3etUWhy2FY5X9Kjqs1WF8udOimBiGtzibe712qDcYkOh-Rf6IN6Uxm2C3r-A==)
17. [diabetech.info](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHmhXk-uTccoRxtwEey6dkWmaisNO_EVWHgAkzn69IPd9xf8YtggCe1Qeh0HbA_te-0G2iJtckMe63aOOCVbwwONpT_bCC74XEW5hu1CkWmhMP-FKPfEPLoV1GVLC32kO7GTl0J7rq67dGxd2JKwcynNDNonzqj9sZ6adcn47h97QHNbGCzeFbrwzildFK7lQ==)
18. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEfbrdzTw99W5BualC9N8zPWySqQvgOjjpO5wC_eL2t5qnJ8pInkSex2fWw5M_pZ36qWbdLyDheGqHHPMoSOSMQ4nKGr2PJ5q_U3NjTamcQDcB6q35BU5HhqAEhDW21QcE2lVYw_PfLCkcZiQEIgWRNiRsCNdvjLCj8pjS4q7bJfEjggc3vU4vSl8Xc58IckT4fv0GuCKtrzw==)
19. [sarahhormachea.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG8j84Yd_wDvqd6t-M8gBSmglQsMrvW0cMvgc471iVZ_ZNwsHjw-kxcLDraabp3grodhrSwNGdhnIjvvasoGEN_59OK04LMpt7UGxtZf4P5I_U9MCNl38CKmo8ggGNGIeEJeC2fjDL6iMYmvuEBXi8xllEre9wVGCVpmcFQ41ueFw==)
20. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG24MzQw9QqqCd2UQG2ZAbSxBokrv9gbMDdcNrNbaFhe4ApBg-oEaFLEe8ci7A_oQvpu4nhcX91CC8hSJfc0EfaJeNP1M1dq9D5les0uTiyrMEFBs6eCMN6HtIrxSACX6WCFjZ8ka_dQyYfjQ1mnBZCbriGeS8mnYg=)
21. [apple.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEomQshRUXOzYC0oWKTCOQ6FZDjsaBEjIrQ3usND-JLdRbPAaIvq1qlCKOfNsv-WLJ48EaMyndp5By-vksEkGmzKhBUjNPx4n-iw2dSfqBJS0Uz5uJf5y9nqwtmU1xDkXo1k8PL1w4nzJ-9PhkFF0i_sFSDwkZOHnJ_LgXqjw==)
22. [fudiabetes.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGagzcLgMX5XKeh3Ny28uErAyLTsNQJm9KcKgwYxYWE7KWGeKPkBINtLG-H9Q8_zaYIJEhScBTySmq_LPSvEUyNNpOWq5btOIHSVbMJbFyxbEqM6MupPpR_qd-dkJ1mDRBxICWSRUPC644X_syiK_gRO1g=)
23. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFKtlEe0TZMznyMJiFKa-Gmgkt11pDaVhh5nAYvJsyZpsI1kbdNZq1dWsvE1NN4y2lpcGRQCb7TWFDa7ti2LBSz0KbTsr0XDyqO--pNF1PTZg3ZEtl-z4lbjIx59A8WoR5LkA-bhJxhiiUJ09hL0t4b53Gq6pplSucaqDHvdseZqU67wc7D1xhpnw==)
24. [google.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHcVs1i5cLUdGw2luRvtxjKhByRaFdScIMHv7uFIrk70MIs0IXHbT5DN1In2iEgxTxLujUzY3vQGotMgbff20WpD9533b2wSpXCKdH6Pk29qrwkIOYt7WiT_R-YbG6kCTE_DLShS4cKsi3KR5S6gMM=)
25. [ycombinator.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEQvj57CdTPm9twZnFnRk9WHrTi2VUBmcYn8s64xVWrSBlfv9w6eHoRcU4P-7HsYpn2kQoJ1uOD7PuQf4zdccbndtlH3PDZOIxDDaghALgULdU7UjG2hsjHA2BR8qZagDl8eg==)
26. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHTL8XvyiFD0a5O9HQggRl3nRIxpvlCiJ_lYEa-oRpCc_JwF6C7PV2qtS6P_ceuYsBs8pTE-zdYJEp_0ocl4DwHC-r2YwLlJv1s7nf9zUFx8AmhDjUJqTgmIA==)
27. [juggluco.nl](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHirct8pASFHXVcYPzqMTkOHs9EGm3bngx9fztm0CF5Asl3NciNxEq8_5r4UUTnxp0HdPA_6rxrctXZVCP9v3IvLdBgrEczG8_uHatOVHE37d3HZR8TWmt6sTPLyJ_1uf9-00059Lc7RQ==)
28. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHGeAnFC02tC5-3Zt5mxEUfR3Rm4TCoc6ll71PrQNfiuFulndGImN5ZrhkVb0MLVYSfBcn3G-edmJrEe_EgaaMWhJYcPdSfmFUDHxaKyh4PXj97iz00gFEvOBns0SDjGsh8j93wZLzU4ywZbOgI8pRZPp1EG48=)
29. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGXeJ2c55_2wv1p0U04OOUBFlmTTzm4c8XifnSLZwCkpPiOUzSoKyrbfijdojF-swp3bcDsLg0LLlZYhyOK-GktMLoj8DL2wXy_Sg_I4nNq9qj5NJJUWxTuOHc8BZ7fDEfkcWFTTMiPbs-kMm8sGHNnPV4qdiOr6f04fobtJ8WbOdLedLwtYIiDnE-J0FLWesMJphYQV7ixKXXSMAgka5Y=)
30. [reddit.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGQZ3TqMjEiJ3wLFU2h99bER7lcXZqNr1o_2zmonJdUTiidf3imYZJfPSX6oept6P1dxeMZ9gB5oxkLfgByN4Neg6dEaiCaT3kX3Rw0iNlrjD-Af6ihpdqcZJ64XzluJXBHCJSFk6IBDPTlkKTgFBgrlKBFi50Cq36gKcg-bdw621LpdgVJcu9RGCjt)
31. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHsOyStCGpo4Wq7c_Ov5R6LCJfg0mimrpxTvNO2UIatXEqjFA0joPQEmT_dnMeEmj9HkiV3PFW83vvmfThz3WK-ricree2J0bMpqvq2Y6sbffu-7wXRyXZ0pkMfAlkZejNWr0w=)
32. [fudiabetes.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHUAuI_y1aPC_IZ6-vZ8ECPvvLji4PQ8Dzzc25DuG-QHMWWyfl_HU-siUW1l2jZ3BYjTmX7VjS3z-TI9PCMOUJDMJW6UW_K6sWfkNovhGGI5WxQwP_GPc3cTUdjTPpNtwJqmqq_zj2ZyzLQb1KkpvF2YCga-6msiQ==)
33. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG-jc7pUWd-TB_nXu5MUJfyEfFSYWHw6TaBQwsZU6IF_-7BK8ghEy574MsfjQxbqHkvP9Bh7zaMqObPDpWLfASa9sryjayR9MG6YlK6HWGuAtlQqfvJzc2RCbLICmtqFCefmAHBNpN0c9tuSA==)
34. [diatribe.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHuWWLkvCYdRzjN-3bQBIAjbehli3eihISeoE0rWer701qYKbt1vCDR3qFA2lXF8O4IzSCXBm354qoSqQM-bBgnkV2IvKAce-ZKmYawa0YoaV1yr7O8g3BWIPLdpCDrwZP2Hcf_aP84dO2LLcV740wFu1X_dGTlpow-AYld-mT4Mk7tVxzBG2ZRjmrGpxjqroT66RyQ5TuNf_u_tL6L3G8qxWuq_C98wqFS)
35. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFK86Z28iYt1rlx4_jL-41lGibAsrwenhPQF50S9aDFHTcra4UcH9k18Ppx2iCuyCeUunXfhGkpK6X0MA0agvvF0qMLC29_DHQPpxcwJgVLe3T8ZxQY7j3i8u4ABmOS3zkfkuLmn-mubFvw9PM36BI=)
36. [github.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHqNxOG6J2SiZxgbE8Wtr_jwhozjvdnbKe3DYx_i4lvztXXlTUKFjtF4MhsGnsYcxeO-_8N3p69UHpN9FaVnbIGQUK3jCMQnRXtPW47OWy40EG9kaskfSdQ_xXrwXYGCT4ovOwOAxM=)
37. [nih.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGbnbmj0MgsovR56ir99dhQ1XIV4MKvBpKUcuZm0IOq0A6shNkS8Nltf7OKrV3GU0OKFTRd_xAuDoQKGgzajnsahmAKD3FSbSyDZqWgg9-gNEuDpyl8t9A0jxh_LKLPkjsbi8vD0tM=)
38. [adces.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFSeKQDrh1nKmSHJnvDOgGpNPrxw8hTXljofXLv1aHqO3dNZiDinGMt75wgs0xVk_smy4HJEwIWrxLLS-fFfGRGZXPV_g6FcJMAVuyNjC9SnBw4Uk6-0rfoaevZWXXjMQfzkW02-_w0TCV_e0hbrTSMVhknhQGOfyWUJfhLtDEMYU0VoUM0vDrZg3NM_YEIAKirl74mbUOPaUoTWm3xwK0TQ5tfpil_kLw59gwiSkmAsQ==)
39. [adameetingnews.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEjShr-XsiChZS9VCrJLwFQkl9TMtGaKc5-LaMgHESWVsd9VBlYLTe3hM_BofWVUn0eJtu7xMxG9UyUGr8Ozma2TWUWdtaPblkGvGmekVxUnK0r6q1zsoeUiWo-fwo5TREHmzR8KkF6R7LGlALWbi3eosRebLrE5NmdPW94BLKVVlmWtUkWqA7AGgpoUeAPUgnTW9W6)
40. [profil.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGtmziEW6HNPufDxTbkHq65EWiiO1tkrVLvU0EjCJHEGB-LWgT9NCfJr4gwOVyWNP3DrMGAm_Lf1TQhkjFKpX7K7_C-ZXmFhLfdIVqF3ep4bDtFanbE4h9lFvgYhPJKB4eY)
41. [diabetesjournals.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFi4rL_67Osytfj1AZqq097dkRxxqx-tTEC9hC-Nlz-gFg6z0FT5ka_MLTpW4sZ6R0h0Tp9Gnxzf-qJpCrwGPjVTHs56SQdv77_dr-eJXXjkh5lq07DO2BzIM8vx7wZHRsGf5Ds7iqrbbUz1362mbcV4RzDqNK7A5TioNqyYwlYTUi896IdHLJG4zD3Kpd7R3fpsoKSrt0WOUMi5hZR7WQqpA==)
42. [github.io](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGbr-2oApxmgnubs6cNP1kebwBGLFqDn6cwADXcXE9PpTnGWElqzKiXAF8zij_o_Bg_bWrDn_vltLNxiaSEgpuGMKHPPfWti0Od-_OiAc1vmlV1YCyYk1sHWruTD8-L_tAOsW_lVQ==)
43. [bionicwookiee.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHJJqN7hScWWVohEbWErJkI4lQTEeUuHbBE7rnBfevYmzuhuYHdZ8VpkcQGzY5F4hDLFF78sVnTfIw0V8M3qKNwvy1GpElVpIfNbAbuFi25WG84vPjM5Fb2SNmxoh7w7vfo2KjtvdaW4QikAFov8w==)
44. [google.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQH3qNbsVxUq38PD5fy34xb4LaY4RpPhxkZerRa3XQlLDu6HZhId9OWCeWNLcuxeFwluE9X3CWz08a-5rpZtsljtcptMY-kezUORhcoVIjFapeLk2IsJ-UmiGWXSAqrZ0H9IBphKTA==)
45. [diabetprosvet.ru](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFLHfupM4kp8KxfHT9VNFMYBO2GMjd8LO_pJ3E-KhFYv8oeb1s0who4AYtYsJHDunXkigqzuBjfVCfG-QiVJlD7Ll8Yz2RpYWdqjGMYyQwGHWinUhA-f_jFK0FxE_AOJAhQALB9Alb_7UZ2k2cIBqTHPZdJfa2YXX3tCtF2MP9DmbAzztgEPJ-0Qrhnjwdkk7pC2Iq6H8Xx1xMUaCMmmh7yjzu8lnSSkmvstyQV9a4XjLf_BmsWTAXNHmY=)
46. [diabetesforo.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFESdVXt95wcOg8z6RebykpjhurbCa5VXmAcsKYJaaHeLl8Gdsd1DbZWTwLXMwjsP4wRGYTKvSo35rJ5c9xj5INfSezGA-ryDjXoiDo2rQzJ-CSm2schXiplL5nF79t3PoSyi6ORHaz2ZSueS9LUvrMxRAoDY5zR0WJ7otfxU2Z-845JwHMQwnkp3l9EYRJa5w=)
47. [fudiabetes.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFRZpSxkQ5m0Bz0m908IrLQqK-2QuVkDvMGKexuwn6Ou9lmPTiutyo_-Pt1NRXyrHabLkwV7871BAb660ixD9LnJmgsKuHw_FpldMI6NxpDas0fco8B58EQ0J_22GMSrAOaKesi0ZppWtCYvn_ee-NtavuAliIPFDKOFJv6NUgwmBewOw==)
48. [juggluco.nl](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFFXLCXwgicWToEJP_OulljhroO0liqM2ew5Hko6EQNsJaCkuwrCDBpBFs8eiZz5sBhxloL66wM8Jwx-zhmHN67g0V2BeD67pSzE-3uFzlgTBvx7JU-jOXu_Tz1KrgFzrOyiQ==)
49. [apple.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHoUXqX63wYXauhmAyguncJDxiqKSfUGAIH45j3QZwvJJmRKc1ceIcTHU1BcMxdJB08jvMCwp7V64-bID4QVZHFzye0op42yxU8i1KwcD92Z8q7FHNhxlxmxdufezUTyc7ABEsrEc727t9WD0uhiBnSmIs0cVPPHg==)
50. [apple.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFt2zgs7eIwZlfEgcmAxSvTVYHHdt-8iWeWcpgJQ__k8R0BUt9rzfKU-AGshh4lzQ72vrXGH2HNI21Tk9UUtaKgeiDR3p0-WPFCOJkpzLdkpyLCgqHRnetp2oKsvd8DxLOUQPYzGv4X-w==)
51. [diabetotech.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFC2u4EWr9OBswB8lsoN1aEgVKSntalp9EzbcKRwZNdbBLXrcD4_jvARZs3Mc6TB_389sUwFEmdUsgkA5NJq6bMmWiGk2njCMwG72PgskFUlLbpJ8u44STsCzkRLKsDj4gpuA==)
52. [medium.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFd2rV2fpVlV8TluuE0K_NMZSS3wMh5s_E8e_XQLUtioOJBa8e9avpM_elRUJpQbkDn8abq3qzwVcfowZUU-3swZ6KHYzpysKaTkQkAvJLjStd2wk7vRwGlbaRhgdigvqieYMJCaZ3pddeM4DnKgFpybNJJFwq9BuBQlbkPbuj4kWIy6A0d8Tt7HdQyCa7zqqK55ykj48mp)
53. [github.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFSX5KPw0Rr-lypmWEfRzmLs1YXpGl6yIzDav_M8MP99XMh91xh9r10-Qw1VrVFJAvr1atWhxNuPrhSByZXHcmLeOLue7kcImXgUWyb_ESGqE7Kf1Vu_tQkw4_jaD3KnHsgtPDZoxSoF-ksE0cXC55T2BqLWnQmfwxMRWUg3OCSumzkbdhma1lqiRChkMP2hX54INbTJ6903zk=)
54. [diatribe.org](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG80-Y8mBhgT9iw-LfHGKFUYfLS7O-WWnyMqWB3PJBoUGBN3zHHAfBauF5Qnkcz0gl7fJwzYB7ywuUNtXyaTSD1ectYK_I3JeML6kRK40K7eEdr36aPzSY4VtWl56pt72YC_JKZGrT7TvCu3s8GARPgDjsnBRsMMr7FoVpVhoKOOEs40mrshREyVTg_nwkCng==)
55. [embien.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEpAWaNPGKhB-AEOsHcgdFzVsoaokZNUiJ7v-P6rzVTDUSjr2eGrA2kttP4qT3zUXdC-IRRqulElPpwf18td72cfNrdPKJMOnNwdzfY0PcMQG8-DJE6TJ_E7nRWkk1JKl7LRF1RfFpCQkvpLdEjcEVyuYbn_xA=)
56. [eleken.co](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQEJCXBCItSC3Bth5M2hhsNTbO-8iN6fiMdth0bVDa9eXa1wOJc88BU1JBv4ivQg9sO3-aFNC4OYR2VZPVkBrVEKsMyLOPqrFqQOjiui_owGDUe0pPm17ptRbyr5yWW6iukDONy0dFld105sJY3nzw==)
57. [fda.gov](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGGE_9FoJ0_g0OjTkm_f0t1IOss6puCXK_dwOrlJIxcZAQp0_iW9WgoiM_ZEUe29YWfhNzpwdd_jYD-20yEzXVtdi6wd3zOyo1D6zr6LUTxPno9_MgLcxnVpAwx0ck=)
58. [venngage.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQG6DF9rBwCjl7RIjkMhYA_E8Q0uqNclPMX-SoqXz6owsh6uhiefDknPmyx0Q5Zughkvb86CmyceWTVFSveIh63xUHwyOh0IoXEsZCvI3YoyTxrxP8QlR-yqUSMlbOockaR8-UfACuKfpwHSV40=)
59. [visme.co](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQELctD9D2oCvbiM-1_-uHx_bKzBN1lzJCRsuQJdaTJ2eW2BtjPgOnYEV5RoEjabteDxLx4A_qiZReOMU3290W4WOltttpqUJLh7Zj7nRa4gl3gnTPZhUh5rvK38CQiA-qqVT7dk3EU2-g==)
60. [europa.eu](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFzxSXhVKpJjPq8TX-twSwtehNXXuoHwUhEm8v3P4FcCwr5HBxSIjYxY4lJ3v6shrVEBuw8DeEDziuwLlEEG6sdB7RRjUFBI2F33ld4mGviCoj4bNXMwmq60T47HWX8nLFQaXJvY39AT9P-HYoiaoLw0qc9h1QTzAeKOmx-zN3gCRKmLjg=)
61. [maori.geek.nz](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFQNBscCc_t7C52CRcx-IbVOtw5_JmBJWwAtR_1xqQE8OSOJzMSikhHTf2YOV0Hz7FE8NDqE6PL0P43oE5w2Zta7ABoF7tPoPwyNZIB33eJXKQrVzVYNXwNBfsRfEUAL7R_EA_WfdwgrJh-oP6TdtgSxuuZ-M3vH2MLHJRUozQsO2ZMlzMf05OBz0s-jKJh)
62. [rondesignlab.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHqVIO_S5uBHUx2VLSqqAqwLmIkJFnlPVN32MGYrC14J61uRRdLSM-mGsps5ya0fcP5ZJUK4wJ2QcH2dk6L2fMW7TYXQ3ALu-_UAQKQ3r-dHUA-aOY8A2w2yslMA5p9u-nIUWvwlxHg18ZNCIz_2_LT_MxD)
63. [bmj.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHCGwx_JIBFHGS0TsIT0Aa2TqjoSU_KQLfTCKAJAwCYj4wJp2As7qG-4Lcmi6ZqJqykuH064QcBggkIv9dFTiVIoh_h9MHs6pT8taB0bCE6kcSbmssV7hmanlo9XCSN80RVReo4)
64. [mayocliniclabs.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQGk5v5H_2Qz0ndUDBMR2UgWimg9CpyC65zowmDp1SThE5XFucT9pOky6CflykSzX5njJx4L4tu1oKW4wtlliPNTME-5SC8LZ_40IwUCmronMMg769pyToWa1jPzHN-M8MBHJM3bQRcKbtAbKSBzHCCN9tJy4h13vQ==)
65. [health2sync.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQHD1SH3CzLACdvxDvlzv4oEyKYA-wIF648q9WyFjxqJnPG5hlPlByBwtudE-W7HYIqxbMYigORF2VCBydJ9GXWQmT3_-qTDt6dRvZ7XBfVmPiOhjvIftemL0YYR89EXbOZDobEyYeunmOUZwhjCuLXdVfI8OX4YsX9xWfCHQ2JVF7q2wiVmJiFeJ5_mvFsCZOTaW9wK65llHg3o)
66. [patientsafetyj.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQFEEfyqaGOO9hHG42v5LUJXmrGC-8xCZau7LMQjC1JzQybt7843Pzf5huElc6kQka4zP7vTozgLOIm5HOffO2qPrQCWdUZZJqrwBkuboKyPNAZYrjk56i0oEp61UIqJ1OoUA9MP6k5j0LAtPMaavVW6iicuukar5hqiPqS3r3PK7mEnKUJuWUIFEbwqvV-xFT5s6wU3CW15lCD-IIEKuYRMn2jdEtJto7a6lT7FwT1N51Ty)
67. [medium.com](https://vertexaisearch.cloud.google.com/grounding-api-redirect/AUZIYQE4eYdrOXxa73urY4kOEGqF0rV0efVU_4V3mkOM6SditOdgyUkUU-4b5LmnD390X-5deOaJnsj40D8QDzT0jUJmwYm_ogrkbWt3PFN88p4uBCQBf4Ihm9ZyE-sxHL2xDIrIBrYpPbaR_6cfeA6eQP6Xgv9WUFTCAdbwsaxaQd0F302i28-YTiswNE2qmNacdohW1Ywv0cJrT5UrFF5Jaw==)
