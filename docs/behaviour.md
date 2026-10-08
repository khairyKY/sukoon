# How Sukoon behaves

Every state the app can be in, what decides it, and what happens when you answer it. This is the
contract: the code follows this document. If they disagree, one of them is a bug.

Numbers are mg/dL. "Fresh" means the newest reading is at most **10 minutes** old.

---

## 1. Where the reading comes from

| Source | What it is | Alarms? |
|---|---|---|
| **Sensor** (Libre 2 Plus over Bluetooth) | Real readings, one a minute | **Yes**: the only source that ever sounds an alarm |
| **LibreLinkUp** (a connection, You → Sensor) | Someone's Libre through Abbott's app (Libre 3 included), polled every minute, about a minute behind; needs the internet | **Yes** |
| **Demo** | Made-up readings to try the app | **Never**. Demo data must not wake anyone or text their family |

You → Alarms says "Alarms are off right now" in red whenever the sensor isn't the source.

---

## 2. Home: the glucose state

Picked first by the sensor's life, then by the newest reading. One state at a time.

| State | When | What Home shows |
|---|---|---|
| **Sensor ended** | The sensor's life is up (15 days for a Libre 2 Plus) | "Sensor ended", how to start a new one |
| **Warming up** | First 60 minutes of a new sensor | Countdown ring to the first reading |
| **No sensor** | Nothing paired, or no reading ever | Pair a sensor / enter the code |
| **Signal lost** | Newest reading older than 10 min | The last value greyed, how long ago, help to reconnect |
| **Urgent** | Under **55** | Red banner, big number, "Do this now: 15 g fast carbs", *I've treated it* / *Alert emergency contact* |
| **Low** | 55 to 69 | Big number, "You're going low", *I've treated it* / *Snooze 15 min* |
| **In range** | 70 to the top of your range (180 unless you set it lower, You → Alarms → Your range) | Number, last 3 hours as bars (one per 15 min, a low or high in it shows), your chosen stats, the message |
| **High** | Over the top of your range | Same layout as in range, amber |

Urgent on Home starts exactly where the urgent-low alarm does (under 55). Time-in-range figures
still use the international bands (under 54 = very low).

**Your range** is 70 to a top you choose, 120 to 180 (70–140 is "time in tight range"). Only the top
moves: under 70 is a low everywhere. Home, its messages and "in range" stat, the graph's band and line
colours, widgets and the status-bar number use it. Insights and the doctor report keep the
international 70–180 and add a line for your range. Changing it moves the high alarm too if the alarm
sat at the old top; the high alarm can be set from 120.

---

## 3. Home: the message under the number

The first rule that fits wins, most urgent first. A message can carry **one next step**; it never
suggests an insulin dose.

### Under 70

| Message | When | Next step |
|---|---|---|
| **Treated** | *I've treated it* pressed, or carbs logged, in the last 15 min | Wait. Check again at (time + 15 min) |
| **Still low** | Treated 15 to 60 min ago and still under 70 | Have another 15 g of fast carbs |
| **Low** | Any other low | Have 15 g of fast carbs now |

### 70 and over

| # | Message | When | Next step |
|---|---|---|---|
| 1 | **Heading low** | At the current rate, under 70 within 20 min | Have carbs ready |
| 2 | **Very high for a while** | Over 250 for 2 hours or more | Check ketones |
| 3 | **Rebound** | Over 180 after a low in the last 3 hours | Let it settle, don't chase it |
| 4 | **Insulin still working** | Over 180 with rapid insulin still active | Don't stack another dose |
| 5 | **After a meal** | Meal logged in the last 3 h, and over 180 or rising | — |
| 6 | **High for N minutes** | Over 180 (wording changes with how long, and over 250) | Over 250: water. Over an hour: water and a walk |
| 7 | **Back from a low** | In range, a low ended in the last hour | A snack if a meal is far off |
| 8 | **Morning rise** | Rising between 04:00 and 08:59 | — |
| 9 | **Rising, no meal** | Rising, nothing logged | Log the meal if you ate |
| 10 | **Bedtime** | 21:00 to 23:59, under 110 and falling or insulin active | A bedtime snack |
| 11 | **Active today** | Workout in the last 12 h and it's evening or night, or in the last 6 h, falling, under 140 | Carbs by the bed, or carbs with you |
| 12 | **New sensor** | First 25 hours of a sensor | Finger-prick before acting on it |
| 13 | **Quiet night** | 00:00 to 04:59 | — |
| 14 | **Good night** | 05:00 to 10:59 after a night all in range | — |
| 15 | **In range for N hours** | In range 3 hours or more | — |
| 16 | **Good day / steady** | Anything else; the calm wording changes each hour, not each reading | — |

---

## 4. Alarms

Alarms judge only a **fresh** reading. Hysteresis stops flapping: a low clears 5 above its line,
a high 10 below its line.

| Alarm | Fires when | Default | You can change | Repeats while true | Sound |
|---|---|---|---|---|---|
| **Urgent low** | Under 55 | Always on | Nothing: can't be turned off or moved | Every **5 min**; snooze capped at 5 | Alarm stream, looping 1 min, alarm volume lifted to at least 80% |
| **Low** | Under your low line | 70 | Line 60 to 110, on/off | After its snooze (15 min) | Alarm stream, 30 s, volume lifted to at least 50% |
| **Going low** | Heading under your low line within 20 min | On | On/off | Once per episode | Alarm stream, 8 s, volume lifted to at least 50% |
| **High** | Over your high line | 180 | Line 120 to 400, on/off, quiet hours | After its snooze (60 min) | Notification stream, 8 s |
| **No readings** | No reading for N min | 20 min | 10 to 120, on/off | Every 30 min | Notification stream, 8 s |

- Sounds come from a pack (You → Alarms → Sounds): Astral by default, Orbit, Glass, Clear or Bells, one sound per alarm and one for the long-acting reminder. An alarm given its own phone sound or file keeps it.
- Urgent low takes over from low, and low from going low. Easing out of an urgent low into a low is the same episode, not a new alarm.
- Each alarm, every time: a notification, the full-screen alert (over the lock screen, or over any app with "display over other apps"), and its sound. The volume goes back to where it was once the sound stops.
- Lows sound even if notifications are blocked. Highs and no-readings stay silent if you blocked their notifications.
- At the default line (180), Home's "high" and the high alarm start together. Move the line up and Home shows "high" a while before the alarm sounds.

### Answering an alarm

| You press | Where | What happens |
|---|---|---|
| **I'm treating it** | Your own urgent low or low (alert screen or notification) | Sound stops; urgent checks back in 5 min, low after its snooze; Home starts the 15-minute countdown |
| **OK / Snooze N min** | Any other alarm | Sound stops; silent for N min; comes back if still true |
| **Snooze choices** | Alert screen | Same, with your chosen minutes (urgent capped at 5) |
| **Swipe the notification away** | Notification | Counts as an answer (restarts the emergency clock), no snooze |
| **I've treated it** | Home, while low or urgent | Answers whichever low is sounding, buzzes, and the button becomes "Check again in N min", then "Check your glucose now". *Log what you had* opens a carbs entry |
| **See and hear each alarm** | You → Alarms | *Hear* plays an alarm's sound for 5 s; *See* runs its real path (sound, notification, full screen) with a sample value, marked TEST. Answering it never touches a real alarm |

The alert screen closes by itself once its alarm is over (back in range, signal back).

### What your alarms did

You → Alarms keeps the last 60 alarm events: each one that went off and what got through
(**sounded**, **took the screen**, **notification**), each answer, and each one that ended. When
something didn't get through, it says what, in red.

### Can alarms reach you?

You → Alarms checks each of these (at the top while something is wrong, under the alarms otherwise) and gives a one-tap fix for anything missing (the
Home banner shows too):

- notifications on, and no alarm channel switched off in the phone's settings
- full-screen alerts allowed (Android 14+)
- display over other apps
- Do Not Disturb access (lets lows through DND)
- battery: unrestricted, plus the phone maker's own background setting

---

## 5. Emergency contacts

Only with at least one contact saved.

1. **Starts when** an urgent low has gone unanswered for your chosen minutes (default 10), **or** the
   readings stopped while you were under 70 and nobody answered the no-readings alarm.
2. **60-second countdown** on the full screen and the notification, with *I'm OK*.
3. **Texts every contact** (glucose, how long, location) and **calls the first one**. A notification
   says who was told and when.
4. Still unanswered **30 min later**: it all happens again.
5. **Back to normal**: once a fresh reading is 70 or more, the contacts get a "back to normal" text.
6. *I'm OK* after the texts went out also tells the contacts you answered.

Any answer to the alarm, even swiping it away, restarts the clock.

---

## 6. Following someone

The same alarm rules run on the person you follow, checked every minute over the internet, with
your own alarm settings. People come from Sukoon invite codes and from LibreLinkUp (You → People →
LibreLinkUp: anyone sharing from Abbott's Libre app with that account); LibreLinkUp people are
managed in Abbott's app, so there's no "stop following" for them here. Their Home uses the same lines as yours: **very low** under 55 ("Call now"),
**low** under 70, **heading low** when under 70 within 20 minutes, **high** over 180 (calm until 250),
**steady** otherwise, and **no readings** after 10 minutes. Their alarms show their name. Answers only snooze them on your phone.
Emergency texts are never sent from a follower's phone.

---

## 7. Reminders

**Long-acting insulin** (You → Insulin, off until you turn it on). Turning it on asks for a time,
offering 30 minutes after when you usually log long-acting (from the logbook), or 22:00.

| When | What happens |
|---|---|
| The time comes and long-acting was logged in the last 12 hours | Nothing |
| The time comes and it wasn't | A notification: *Took it · 20 U* (logs your last amount again, now), *In 30 min*, or tap to log it yourself |
| Still not logged | The notification comes back every 30 minutes, 3 times |
| You log it anywhere (the logbook, *Took it*) | The notification goes away and the repeats stop |

## 8. Injection sites

Rapid and long-acting entries (and a meal's rapid insulin) ask **Where?**, optionally. Two figures,
front as you look down and back from behind, so your left is on the left in both: belly, thighs,
back of the upper arms, upper outer buttocks, each left or right. One tap picks the spot and the side.

| Situation | What the editor does |
|---|---|
| New dose | The amount pad first; a one-tap amount, or tapping *Where?*, folds the pad and opens the body map |
| *Again* on a dose | Straight to the body map (the amount is already there) |
| Editing a dose | *Where?* shows its spot; tap to change it |
| *Skip* | No site; the entry shows *Where?* in the logbook, and tapping it adds one later |

**The dashed ring** is the suggestion: of the spots you've used for that kind of insulin in the last
30 days, the one rested longest (never the last one). With only one spot so far, the same place on
the other side. Rapid and long-acting rotate separately.

**One spot doing most of the work**: 10 or more doses of a kind in 14 days, half or more in one spot.
It shows on You → Insulin & logbook (with the last 30 days on the body map) and as an insight in
Trends (Frid et al., Mayo Clin Proc 2016).

## 9. Dose suggestions (beta)

Off until turned on in You → Insulin & logbook. Carb counting: a carb ratio per meal (breakfast
04–10, lunch 11–15, dinner 16–21, late otherwise), a correction factor and a target. Any box can
stay empty, and that part is then left out. Sources: docs/research/dosing-sources.md.

| Part | How |
|---|---|
| Meal | carbs ÷ that meal's ratio |
| Correction | (glucose now − target) ÷ correction factor; below target it takes some off |
| Insulin still working | Offsets the correction only, never the meal (earlier insulin is busy with earlier food) |
| Result | Never below 0, rounded **down** to the pen step (0.5 or 1 u), capped at your maximum (default 10 u) |
| Under 70, or under 100 and falling | No suggestion: "treat that first (15 g)" |

Shown on new rapid entries (a meal, insulin for an imported meal, or insulin alone) with its maths.
*Use* puts it in the amount; only Save logs it. With enough complete days in the logbook, the card
offers the textbook starting points (500 and 1800 rules) to fill empty boxes. While it's on, the AI
assistant may work out doses with the same maths and settings.

**Learning the ratios** from the last 30 days, whether suggestions are on or off (You → Insulin →
*Learning from your meals*). Every meal gets a verdict; a meal counts (is *clean*) when:

| Check | Left out as |
|---|---|
| 10 g or more | Under 10 g of carbs |
| Rapid insulin logged from an hour before to 30 min after | No insulin logged with it |
| Readings at the start and 4 h later | Readings missing |
| Started between 70 and 250 | Started out of range |
| Nothing else eaten from an hour before to 4 h after | Something else eaten |
| No more insulin in those 4 h | More insulin within 4 h |
| Under 0.5 u still working from before | Insulin still working |
| No workout from 2 h before to 4 h after | A workout around it |
| Under 35 g of fat and of protein | Very rich |

A clean meal's ratio is carbs ÷ (insulin + glucose change at 4 h ÷ correction factor); the factor is
yours, or the 1800 rule's estimate (says which). With 5 or more clean meals at a meal time the
Learning screen and that meal's tile show the median and the middle half; *Use* moves the ratio toward
it by at most 20% at a time. Nothing changes until you press it. The screen lists every clean meal,
what was left out and why, and exports it all as CSV. *How to make a meal count* is the checklist.

**From everything you've logged** (top of the Learning screen, and a "Your data says" strip on the
correction tile): least squares over every usable meal and every correction taken on its own in the
30 days, change at 4 h ≈ a(meal time) × carbs − b × insulin. b is how far 1 unit lowers you, b ÷ a
each meal time's ratio. It needs no numbers to start from, and a meal without insulin still counts.
A point counts when nothing else was eaten and no other insulin taken (an hour before to 4 h after),
none was still working, no workout, not very rich, starting 70–300 with readings then and 4 h later.
It shows once there are 8 points with 3 at one meal time and the answer is plausible (1 u lowers
10–300, ratios 1:3 to 1:60). *Use* moves each number at most 20% at a time.

**Learned, on Home**: with suggestions on, each time Home opens it works out the learning, and if a
number differs from yours by 10% or more (or you have none) it shows one notice: a meal time's
clean-meal lesson first, then what everything logged says (a ratio, then the factor), e.g. "Lunch:
your meals suggest 1 : 11". *Take a look* opens You → Insulin → Learning; *Not now* hides that
suggestion until a different one appears. It never changes a number itself.

**Meal without insulin, on Home**: with suggestions on, a meal of 10 g or more in the last hour with
no insulin logged for it (linked, or from an hour before it), under 0.5 u still working, and a fresh
reading that isn't low: Home shows the meal's dose with *Log it*, which opens insulin for that meal.
After an hour it stops (a full meal dose that late risks a low); the correction covers a high.

**When you're low**: no dose is suggested under 70, or under 100 and dropping. With a meal, the low
card adds what the meal itself needs (carbs ÷ ratio, rounded down) for once you're back over 70.

**Correction on Home**: with suggestions on and a correction factor set, Home shows "Correction · beta"
when the reading is fresh and above your range, nothing with carbs was logged in the last 2 hours,
and under 0.5 u is still working (otherwise Home's message already says to give it time). It shows
(glucose − target) ÷ factor rounded down, only if that's at least one pen step. *Log it* opens the
insulin entry, where the same suggestion has *Use*.

**Starting ratios** (*Suggest starting ratios*, in the dose card and onboarding), for every age: from
the logbook's daily total if there are 3 complete days (500 and 1800 rules), else from weight and
age (0.5 u/kg a day for adults, 1.0 for 12–17, 0.7 under 12), else for adults the common 1 u per 15 g
and 1 u per 50 mg/dL. A child without a weight gets none. *Fill the empty boxes* or *Use for all*.

**Parent lock**: a 4–8 digit PIN (stored as a salted hash). While it's on, changing your range, any
alarm setting, the dose settings or the profile asks for it, once per visit to You. Under 18, dose
suggestions only switch on once a parent has set one. Removing it needs the PIN.

**Pens**: whole units by default (You → Insulin → Your pens). The insulin keypad takes a decimal point
only for half-unit pens, and suggestions round down to the pen.

## 10. Known limits

- Snoozes, answers, "treated at" and texts already sent are kept across a restart. A countdown cut off by one starts again from 60 seconds (with its screen and sound) if the alarm is still unanswered.
- If the phone kills the app, nothing inside it can sound. A watchdog covers that: every reading pushes a system alarm to just past your no-readings line (20 min + 2 by default). If readings stop because the app was killed, Android wakes Sukoon there; the sensor and alarms restart, and *No readings* goes off if the sensor is still quiet. It checks again every 5 minutes until readings return. The battery items in the check above still matter: they keep it from coming to that.

## 11. Meals from other apps

- MyFitnessPal doesn't send meals: it keeps **one record per day** in Health Connect (10:00–22:00),
  the day's running total, rewritten each time food is added. Sukoon splits it back into meals: each
  rise of at least 1 g carbs or 20 kcal is a meal, timed when it was logged (checked every 15
  minutes); food taken off comes off that day's newest meals. What today held when Sukoon first saw
  it becomes one *Earlier in the day* entry (several meals in one: no dose suggestion, nothing
  learned from it); earlier days are left out. The foods themselves never reach Health
  Connect. Other apps' day-long records are left out.
- Tapping an imported meal shows, in order: when it was logged with *When did you eat?* (the time you
  pick is kept), its carbs and insulin (or *Add insulin*), a *Rich meal* chip, what it did to glucose,
  and one list of what's in it. Calories are whole numbers.
- On Home, a meal from another app whose time you haven't set only gets a dose suggestion when no
  rapid insulin was logged in the 3 hours before it (it may have been logged after you ate and dosed).
- Insulin added for a meal is **linked to that meal** and stamped when you took it (*now* unless you
  change it), so two meals at the same time can't swap doses. Insulin without a link is matched to
  the nearest meal by time, as before.
- A meal another app posts again (Samsung Health passing on MyFitnessPal's) within 10 minutes of a
  MyFitnessPal meal with the same carbs is left out.
- *What was it?* names an imported meal (the name becomes its title). Every meal, imported or
  logged in Sukoon, opens on this screen; yours have *Edit meal* at the bottom.

## 12. Meals from foods

- A new meal starts from its foods: a search box with the barcode button inside it, three tiles
  (Photo, MyFitnessPal, Just carbs) and your usual foods. *Just carbs* is the number pad, as before.
- Search shows **yours** first (everything you've put on a plate, the most recent first, offline),
  then **home dishes** (88 Egyptian and Middle-Eastern dishes in `assets/dishes.json`, each with its
  source; Arabic matches however it's spelt: ة/ه, أ/ا, marks), then **Open Food Facts** after a 0.3 s
  pause, in Arabic and English, with *Sold in Egypt* as a filter. A product without carbs is left
  out: it couldn't be dosed from.
- Dish carbs exclude fibre (as Open Food Facts counts them); stews (molokhia, bamya, besella…) are
  without rice, which is its own food. Portions are typical; the per-100 g values are the sourced part.
- A barcode is read by Sukoon's own camera (ML Kit inside the app): yours first, then Open Food
  Facts. Not there: *Add it as your own*, and the next scan finds it.
- *Ask the AI about "…"* estimates any dish (Gemini, your key); the estimate becomes one of yours,
  counted in portions.
- *How much?*: package parts or the serving, a dish's portion (½, 1, 1½, 2 bowls), −/+, or type it
  in grams or portions. The plate adds carbs, fibre, protein, fat and calories into the meal, names
  it after its foods (unless you named it), and the dose suggestion works from the total.
- Photos: as many as you like per meal; the AI estimate reads them all as one meal.

## 13. Your sensor (You → Sensor)

- The state comes from the newest reading, not the Bluetooth link (the sensor drops it after every
  reading): **Live** under 6 minutes old, **Reconnecting** under 20, else **No signal**; **Warming
  up**, **Ended**, **Bluetooth off** and **Not in use** (another source chosen) override it.
- What needs you is listed first: Bluetooth off (red, *Turn on*), no readings for 10 minutes or
  more (red), ending within 3 days (amber; red on the last day), ended (red).
- Its life is a bar, *Day 12 of 15 · ends …*, amber in the last 3 days and red on the last.
- Notices: 3 days, 1 day and 1 hour before the end, and when it ends. Bluetooth off shows on Home,
  and the no-readings alarm says Bluetooth is off when it is.

## 14. Days, backup, clock

- The logbook and the graph show the last 24 hours (or the chosen range) up to now; the *‹ Today ›*
  bar goes to any earlier day (its entries, totals and the whole day's graph).
- Cloud backup (opt-in, You → Reports): the full backup zip, written to one file you chose in a
  cloud app, on choosing and then once a day (checked hourly while Sukoon runs). Sukoon keeps
  permission to that file only. *Stop* releases it.
- Times follow You → Appearance → Clock: the phone's, 12-hour or 24-hour, everywhere (alarms,
  notifications and widgets included).
