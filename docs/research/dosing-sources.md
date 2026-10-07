# Dose suggestions (beta): sources of truth

Research for the beta dose calculator and the learning on top of it (Kai, 2026-10-07).
Kai's calls: **carb counting**, **a ratio per meal** (breakfast / lunch / dinner), **the AI may
talk about doses** once beta is on. Kai doesn't follow a system yet, so his numbers start empty.

## The book: «افهم سكر», د. عمرو عمر

- Dr. Amr Omar: MSc medicine (Kasr Al Ainy), MSc paediatrics, diabetes and endocrine specialist at
  Egypt's National Diabetes Institute, AACE member, certified diabetes educator (Egyptian MoH).
  277 pages, 2020, third edition. Covers diagnosis to treatment, technology and recent studies.
  ([Goodreads](https://goodreads.com/ar/book/show/55781390))
- **Not extracted yet.** The only online copies are on pirate PDF sites, which we don't use. With a
  copy Kai owns (PDF or photos), extract its *facts and numbers* (Egyptian food carb portions,
  the ratio/correction rules it teaches, its targets) into this file in our own words, with page
  numbers. Not its text. Where it disagrees with the sources below, note both.

## The maths everyone agrees on

```
meal dose       = carbs (g) / carb ratio (g per unit)                 ← per meal slot
correction      = (glucose now − target) / correction factor (mg/dL per unit)
suggested dose  = meal dose + max(0, correction − insulin still working) ← never below 0
                  (below target the correction is negative and takes some off the meal)
```
Taught exactly this way in practice ([Davidson et al., Endocr Pract 2008;14:1095](https://1library.net/document/qmvwjp4q-analysis-guidelines-insulin-dosing-insulin-correction-carbohydrate-insulin.html)).

### Starting points when a number is empty (from the total daily dose, TDD)

| Rule | Carb ratio | Correction factor | Source |
|---|---|---|---|
| Classic | 500 / TDD | 1800 / TDD | Walsh & Roberts, *Pumping Insulin* |
| AIM | 2.8 × weight (lb) / TDD | 1700 / TDD | Davidson 2008 |
| Measured with CGM (near-normal control) | 217 / TDD + 3 | 1076 / TDD + 12 | [King & Armstrong, J Diabetes Sci Technol 2007](https://journals.sagepub.com/doi/reader/10.1177/193229680700100422) |

King & Armstrong found the published rules give **too little** insulin at good control. For a
first suggestion that's the safe direction: start with the classic rule and let the learning
correct it. Sukoon already has TDD (`Insight.Formulas`, complete days only).

## Learning the numbers from the logbook

- **What a "right" dose is** (King & Armstrong 2007): a meal dose was right if glucose is back
  within **±20% of the pre-meal value at 4 h**. A correction was right if it lands **80–120 mg/dL
  at 4 h**. This is the grading rule for each clean meal.
- **Adjust a little each time, from repeated meals** (run-to-run control): treat each day's meal
  as a batch, nudge the ratio from yesterday's outcome, and converge over several meals. Clinically
  tested for meal doses and for basal ([Owens et al. 2006](https://www.ccdc.ucsb.edu/publications/13078); [basal](https://www.ccdc.ucsb.edu/publications/13080)).
- **Ratios from CGM + insulin data** can be estimated directly and protect against lows
  ([Schiavon et al., sensor-and-pump insulin sensitivity](https://www.omicsdi.org/dataset/biostudies-literature/S-EPMC5771547); [JKU: diurnal ratios from CGM](https://research.jku.at/en/activities/identification-of-diurnal-variations-in-insulin-to-carbohydrate-r/)).
- **Insulin works differently through the day** (Hinshaw et al., Diabetes 2013, already cited by `Insight.CarbResponse`): hence a ratio per meal.
- **The lesson from the adaptive calculator RCT** ([ABC4D, Unsworth et al., Diabetes Technol Ther 2023;25:414](https://spiral.imperial.ac.uk/entities/publication/4793ac27-84ff-47b5-b84d-1deac730b4ab)):
  37 adults on injections. The adaptive calculator was **safe**, but its time in range was no
  better than a plain calculator, because people accepted fewer of its suggestions (79% vs 94%)
  and took less. For Sukoon: show *why* (the meals it learned from), change slowly, and let Kai
  accept each change. A suggestion nobody trusts does nothing.
- **Carb counting + a calculator works on injections**: BolusCal RCT, 51 adults, HbA1c −0.7 to
  −0.8% at 16 weeks ([BolusCal study](https://researchprofiles.ku.dk/en/publications/use-of-an-automated-bolus-calculator-in-mdi-treated-type-1-diabet/)).

## Meals the maths misses

- **Fat and protein** raise glucose 3–5 h later; a high-fat, high-protein meal needed **~65% more
  insulin** on average, with large person-to-person differences (Bell et al., Diabetes Care 2015).
  Fat-protein units: 1 FPU = 100 kcal from fat + protein (Pańkowska 2009). ISPAD: the best dose for
  these meals is undefined, so personal adjustment from glucose response is advised
  ([review](https://ade.adea.com.au/managing-the-ups-and-downs-from-dietary-protein-and-fat-the-john-hunter-childrens-hospital-approach-in-type-1-diabetes-care)).
  Sukoon already flags these meals (`slowMeal`) and measures them (`Insight.RichMeals`): learn a
  personal extra % for them, don't apply a fixed 65%.
- **Exercise**: lows for up to 24 h after; meal doses before exercise are commonly reduced
  (Riddell et al., Lancet Diabetes Endocrinol 2017). Already measured by `Insight.ActivityLows`.

## Guided tests (later, opt-in)

Each starts only when in range and steady, with no insulin working and no exercise that day. Each
stops and tells you to treat the moment glucose goes under 70 (warn at 90).
- **Long-acting check**: skip a meal; glucose should stay within ~30 mg/dL. Drifting up or down
  means the basal dose is off (Walsh, *Pumping Insulin*).
- **Carb ratio check**: a meal with known carbs (packaged, weighed), dose by the ratio, grade at 4 h
  by the ±20% rule above.
- **Correction check**: when high with no food in the last 3 h, correct by the factor, grade at 4 h
  (80–120 mg/dL).

## Egyptian food carbs

- MyFitnessPal brings carbs for logged meals; the AI estimate covers the rest.
- Official reference: *Food Composition Tables for Egypt*, National Nutrition Institute (via
  [FAO INFOODS](https://www.fao.org/infoods/infoods/tables-and-databases/egypt/en)), for the
  quiz's plates (koshari, ful, aish baladi). The book likely has Egyptian portions too.

## Safety rails (beta)

- Off by default; a "Beta: dose suggestions" switch with a clear warning. Kai only, for now: a dose
  calculator for others is a regulated medical device (FDA / EU MDR).
- No suggestion when under 70 or falling fast toward it. That case is treat the low (15 g, recheck
  in 15 min, ADA Standards of Care §6).
- Insulin still working offsets the correction only, as pump bolus calculators do (earlier meal
  insulin is busy with earlier food); never below 0; round **down** to the pen's step (0.5 or
  1 u); a maximum dose Kai sets.
- Always shown with its maths ("45 g ÷ 12 = 3.8, +0.5 correction, −1.0 still working → 3 u").
  Never typed into the amount for him.
- Learned changes are proposals with their evidence (n meals, the range). They only apply once
  accepted, and at most ±20% at a time.
- To change when beta is on: the "never suggests a dose" lines in `docs/behaviour.md`,
  `AiPrompts.ASK_RULES`, and the Insights wording.
