# Sources: fast-change alarms, workout, fasting and sick-day modes, low treatments

The numbers Sukoon uses for these, and where they come from. Collected 2026-10-13. Where guidelines
disagree, the more cautious number is used and the other is named. None of this replaces Kai's
doctor; dose changes stay suggestions he accepts or not.

## Changing fast (rate-of-change alarms)

- FreeStyle Libre arrows: flat < 1 mg/dL/min, slanted 1–2, **"changing quickly" > 2**, no double
  arrow ([LibreLinkUp](https://www.librelinkup.com/articles/glucose-readings)). Dexcom: single
  arrow 2–3, double > 3 (Aleppo et al., J Endocr Soc 2017).
- No guideline sets a rate-of-change *alert* threshold. Sukoon: **2 mg/dL a minute** by default
  (Libre's own arrow), 3 or 4 to choose; falling fast only while still under the high line, rising
  fast off by default.

## Exercise and the bulk

Kai's own research: `Kai's Vault/03 - Personal OS/Fitness And Health/research-2026-07-27-hypertrophy-t1d.md`
(resistance training raises glucose during the session, then insulin sensitivity rises for 24–48 h;
glargine U-300 can't be cut for one session; 15–30 g carbs before lifting; resistance before cardio;
cautious post-workout bolus; bulking at +200–300 kcal/day with 1.6–2.2 g/kg protein over 4 meals;
fat-heavy meals rise 3–5 h later).

- **Riddell et al., Lancet Diabetes Endocrinol 2017;5:377–390** (exercise consensus):
  - Before exercise: under 90 mg/dL take 10–20 g glucose and delay; 90–124 take 10 g before aerobic;
    126–180 is the target; over 270 check ketones.
  - Meal before exercise: bolus −25–75% by intensity and length. **Brief intense / resistance
    exercise: reductions "not advised"**.
  - **Meal after exercise: bolus about −50%**; a low-GI bedtime snack.
  - MDI basal about −20% on exercise days (long-acting basal is less flexible: Toujeo).
  - Low risk "elevated for at least 24 hours in recovery".
  - Resistance training: "better glucose stability", "may cause a modest rise"; do it before aerobic.
- **Moser et al., EASD/ISPAD position statement, Diabetologia 2020** ([PMC7702152](https://pmc.ncbi.nlm.nih.gov/articles/PMC7702152)):
  - Sensor target during exercise 90–180 (ideally 126–180); **low alert 100 mg/dL** 30–60 min
    before and during exercise; high alert 180.
  - Carbs at the low alert by trend: flat 10–15 g, slightly down 15–25 g, down 20–35 g.
  - Stop exercise under 70; don't restart under 54.
  - **Overnight after exercise: low alert 80 mg/dL**; lows "typically occur within 6 to 15 h after".
  - Resistance training rise: **50% of the usual correction**.

## Your goal: maintain, lean bulk, cut

- **Lean bulk**: Kai's research (above): +200–300 kcal a day, 1.6–2.2 g/kg protein. **Iraki et al.,
  Sports (Basel) 2019;7:154**: gain about 0.25–0.5% of bodyweight a week to limit fat gain.
- **Cut**: **Murphy & Koehler, Scand J Med Sci Sports 2022;32:125–137** (meta-analysis): a deficit
  over about 500 kcal a day blunts lean-mass gains. **Helms et al., JISSN 2014;11:20**: lose
  0.5–1% of bodyweight a week. Protein: **Hector & Phillips, IJSNEM 2018;28:170–177** (1.6–2.4,
  higher when leaner); **ISSN position stand, Jäger et al. 2017** (2.3–3.1 g/kg of lean mass in a
  deficit). Sukoon offers 2.0–2.6 g/kg, default 2.2.
- **Maintain**: **ISSN 2017** (1.4–2.0 g/kg for active people); **Morton et al., BJSM 2018;52:376–384**
  (gains plateau around 1.6 g/kg, upper CI 2.2). Default 1.6.
- **Type 1 and dieting**: no guideline gives a percentage. ADA Standards of Care 2026, section 5:
  adjust glucose-lowering medication to avoid lows when cutting calories; avoid very-low-carb diets
  with SGLT2 inhibitors. So the cut says to watch for lows and review doses with the clinic, and dose
  suggestions are not changed by the goal.
- **Maintenance estimate**: Mifflin–St Jeor (Am J Clin Nutr 1990;51:241–247) × 1.55, "moderately
  active".

Sukoon's workout mode: low alarm at 100 during the workout; the next 24 h a late-low watch with the
low alarm at 80 overnight (22:00–07:00); corrections during and within 2 h after at 50%; the meal
after a workout (within 2 h) at −50% by default (0, 25 or 50% to choose); no cut before lifting.

## Fasting (Ramadan)

**IDF-DAR Diabetes and Ramadan Practical Guidelines 2021** ([PDF](https://media.idf.org/media/uploads/2024/07/IDF_DaR_Practical_Guidelines_Ramadan.pdf)):
- **Break the fast** if glucose < 70 mg/dL, > 300 mg/dL (individualised if well controlled before
  Ramadan), or with symptoms of a low, a high, dehydration or illness. Re-check within 1 h at 70–90.
- Glucose checks: suhoor, morning, midday, mid-afternoon, before iftar, 2 h after iftar, any symptoms
  (a CGM covers these).
- MDI basal: **−30–40% and take it at iftar** (type 1 chapter); the general table lists glargine 300
  at −15–30%. Sukoon names both and leaves the basal dose to Kai and his doctor.
- Rapid insulin: iftar dose as usual (adjust from the 2-h post-iftar reading); **suhoor −30–50%**
  (general table 25–50%); no lunch dose.
- "Take Suhoor as late as possible."
- Risk: type 1 on basal-bolus scores high; fasting is a decision for Kai and his doctor.

Sukoon's fasting mode: between suhoor end and iftar no meal dose is suggested; the suhoor meal dose is
suggested at −30%; Now says *break your fast* under 70 or over 300 and *check again within an hour* at
70–90; a suhoor reminder before the fast starts; the long-acting reminder says to take it at iftar.

## Sick days

**ISPAD 2022, sick day management** (Phelan et al., Pediatr Diabetes 23:912–925; written for children,
used here for its rules) ([link](https://onlinelibrary.wiley.com/doi/10.1111/pedi.13415)):
- **"Never completely stop insulin!"** Basal may need +20–30%.
- Glucose and ketones **at least every 1–2 h**.
- Blood ketones (mmol/L): < 0.6 normal; 0.6–0.9; 1.0–1.4; 1.5–2.9 (treat at home with extra insulin);
  **≥ 3.0 risk of DKA: urgent care**.
- Extra rapid insulin: correction +10% (mild) / +20% (moderate–severe) ketosis, or 10–20% of the daily
  total, repeated every 1–2 h (severe) or 2–4 h (mild). Sukoon shows this; it doesn't dose it.
- Fluids: about 100 ml an hour in sips; with carbs under 250 mg/dL, without over it.
- Urgent care: ketones > 1.5 despite extra insulin, vomiting > 2 h, glucose still rising, fruity
  breath, confusion or fast deep breathing.

Sukoon's sick-day mode: a reminder every 2 h to check ketones and drink; ketones log to the logbook
(mmol/L) with the band and what it means; Now's message names the band; high alarms repeat hourly.

## Low treatments

- **ADA Standards of Care 2026, section 6** ([PMC12690178](https://pmc.ncbi.nlm.nih.gov/articles/PMC12690178)):
  glucose preferred under 70; re-treat after 15 min if still low; don't treat with protein; fat slows it.
  (15–20 g is the long-standing wording; Sukoon's 15-15 already uses 15 g.)
- **Rebound** = over 180 mg/dL within 2 h of a reading under 70 (Acciaroli et al., J Diabetes Sci
  Technol 2021, [PMC9294577](https://pmc.ncbi.nlm.nih.gov/articles/PMC9294577)).

Sukoon's treatment stats: carbs logged as a low treatment (from the alarm's *Log what I ate*, or
marked in the editor) are kept out of meal maths and dose learning, and Insights reports how much you
usually take, how often a second treatment was needed, and how often a rebound followed.
