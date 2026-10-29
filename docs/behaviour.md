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
| **Finger-prick mode** | The sensor's life is up (15 days for a Libre 2 Plus); or no reading for **30 min**; or no sensor but a finger-prick in the last day. Off by itself when readings return | An amber line saying why (tap: the sensor page); the last finger-prick, its colour, how long ago and which finger; *Prick now*; insulin still working; today's pricks; dose cards from a prick under **15 min** old (a sensor reading counts for 10) |
| **Warming up** | First 60 minutes of a new sensor, or the newest reading is from a sensor's first hour | Countdown ring to the first reading; a first-hour reading in grey, "not counted" |
| **No sensor** | Nothing paired, or no reading ever | Pair a sensor / enter the code |
| **Signal lost** | Newest reading 10 to 30 min old | The last value greyed, how long ago, help to reconnect |
| **Urgent** | Under **55** | Red banner, big number, "Do this now: 15 g fast carbs", *I've treated it* / *Alert emergency contact* |
| **Low** | 55 to 69 | Big number, "You're going low", *I've treated it* / *Snooze 15 min* |
| **In range** | 70 to the top of your range (180 unless you set it lower, You → Alarms → Your range) | Number, last 3 hours as bars (one per 15 min, a low or high in it shows), your chosen stats, the message |
| **High** | Over the top of your range | Same layout as in range, amber |

**First-hour readings** (the sensor's own minutes 0–59, flagged *warm-up*) show only on Home (grey), the
graph and the status bar ("warming up", no number icon, no colour). Reports, insights, time in range,
alarms, uploads (followers, Nightscout, Health Connect), calibration, the CSV and the AI never count them.

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
| **No readings** | No reading for N min (held while the paired sensor warms up or after it has ended: it sends nothing to act on) | 20 min | 10 to 120, on/off | Every 30 min | Notification stream, 8 s |
| **Falling fast** | Dropping 2+ a minute (3 or 4 to choose) while still under your high line, and no low coming yet | On | Rate, on/off | Once per episode | Alarm stream, 8 s |
| **Rising fast** | Climbing that fast, not already high | Off (meals do it) | Rate, on/off | Once per episode | Notification stream, 8 s |

- Sounds come from a pack (You → Alarms → Sounds): Astral by default, Orbit, Glass, Clear or Bells, one sound per alarm and one for the long-acting reminder. An alarm given its own phone sound or file keeps it.
- Every alarm also vibrates in its own pattern, for as long as its sound plays, so a phone in a pocket says which alarm it is: urgent low **SOS**, low **Pulse**, going low **Rising**, high **Heartbeat**, no readings **Tap** (You → Alarms → each alarm → Vibration: also Off, except for urgent low). Sukoon vibrates itself, not the notification channel (Android fixes a channel's pattern for good once it's made). Lows vibrate as alarms (through silent mode), highs and no-readings as notifications.
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

### Pausing every alarm

*Pause alarms* (Now → Today: 30 min, 1 h, 2 h or 4 h; or *Pause all alarms for 1 h* on any of your own alarm screens except urgent low) keeps every alarm quiet, your own and the ones for people you follow, **except urgent low**, which nothing silences. What's sounding stops at once. While paused, Now shows an *Alarms paused · until …* chip (tap to bring them back) and Today has *Resume*. When the pause ends, any alarm that is still true fires straight away. Under the parent lock (a PIN, under 18) pausing needs the PIN and the alarm screens don't offer it.

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
- battery: unrestricted (a phone maker's own background settings have no API; the Guide's Keep Sukoon running says where to look)

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

**What followers get** (You → People → *Alarms your followers get*): the wearer picks which of their
alarms sound on followers' phones, in the app and on the follow page; **urgent low always does**. It is
saved on the wearer's account (needs `supabase/migrations/20261012000000_shared_alarms.sql`). Until that
runs, for a wearer who hasn't chosen, and on followers' phones with an older Sukoon, every alarm comes
through: a follower never gets fewer alerts by accident. LibreLinkUp and Dexcom people: every alarm.

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

**Finger-pricks and meals** (You → Reminders). One alarm, always set to the next reminder; re-planned
whenever the logbook, the sensor or the settings change, and every 10 minutes.

*Meal times*: for each of breakfast, lunch and dinner, Sun–Thu and Fri–Sat kept apart, the median time
of that meal over the last 3 weeks (from 3 meals on; meals of 10 g or more, not low treatments, not an
imported meal timed when it was logged). A time you set wins; *Set by you* goes back to the learned one.
A meal with neither has no reminders.

| Reminder | When | Only in finger-prick mode | Counts toward the daily cap | Skipped when |
|---|---|---|---|---|
| Before a meal | 15 min before its time | Yes | Yes | That meal is logged (from 90 min before its time), or a prick within an hour of it |
| 2 h after a meal | 2 h after any logged meal | Yes | Yes | A prick from 90 min after the meal |
| Bedtime | The time you set (23:30) | Yes | Yes | A prick within an hour of it |
| After a low | 15 min after a prick under 70, again every 15 min, 4 times at most | Yes | No | A prick of 70 or more |
| Meal missing | 20 min after a meal's time | No | No | That meal is logged |

- **Daily cap**: 2 to 20, 12 to start; checks after a low and meal questions don't count.
- Nothing at night after a workout (Kai's call); the late-low watch's alarms cover that with a sensor.
- Buttons: *Log it* (the finger-prick entry), *In 15 min*, *Skip*; after a low *I ate more* (a low
  treatment); a missing meal *The usual · ful + bread, 60 g* (logs it now: the meal reminder then asks
  about insulin), *Something else*, *Didn't eat*. Each reminder comes once; missed by over 45 min
  (phone off), it's dropped, not sent late. A prick logged anywhere takes the finger-prick ones away.
- Finger-prick mode as on Now (§2). Now shows *Next check around 19:30* under *Prick now*.

**Glucose meter** (You → Glucose meter). A Bluetooth meter with the standard Glucose Profile (GLS,
service 0x1808), such as the CareSens S Fit BT. Pair once: meter off, hold its button until *bT*,
*Find meter*, pick it, type the 6 digits from its screen when Android asks. After that Android keeps a
background connection waiting; whenever the meter wakes and shows *SYNC*, Sukoon asks for the tests
stored since the last sync (the first time: all of them) and logs each as a finger-prick at the
meter's time, its before/after-meal, fasting or bedtime flag as the note. A test within 3 min of a
prick you typed with the same value isn't doubled; one timed in the future (the meter's clock ahead)
is skipped. The page shows what Sukoon found on the meter: the glucose service, how many tests it
holds. Without the service it says so, and pricks stay typed by hand. Forget clears it here (the
pairing in Android's Bluetooth settings stays).

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
It shows on You → Insulin & doses (with the last 30 days on the body map) and as an insight in
Trends (Frid et al., Mayo Clin Proc 2016).

## 9. Dose suggestions (beta)

Off until turned on in You → Insulin & doses. Carb counting: a carb ratio per meal (breakfast
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

**When you're low**: no dose is suggested under 70, or under 100 and dropping.

**After a low**: for 2 hours (You → Insulin → More: 1, 1½, 2 or 3 hours, or 30 minutes to 4 hours)
after any reading or finger-prick under 70, no dose is suggested at all
(not the meal, not a correction), even once you're back over 70: carbs eaten during or after a low
are treating it, and insulin for them brings the low back (Kai, 12 Oct: a meal logged going low, then
1 u suggested at a steady 76). The editor says *Back from a low* and when suggestions return; Home
shows no meal or correction card. Earlier builds told you the meal's dose "for once you're back over
70"; that line is gone.

**Logging the treatment**: a low or urgent-low alarm (screen and notification) has *Log what I ate*
next to *I'm treating it*. It answers the alarm the same way (sound off, the 15-minute check starts)
and opens a new meal, so what you treated with is in the logbook.

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
alarm setting, the dose settings or the profile asks for it, once per visit to You, and so does
anything that would cut a parent off as a follower: which alarms followers get, sharing on/off,
removing a follower, signing out and deleting the account. Under 18, dose suggestions only switch on
once a parent has set one. Removing it needs the PIN. **It is for children only:** from 18 (the age in
About you) it doesn't apply and isn't offered, even if a PIN was set before; with no age it applies.
The age is behind the lock, so a child can't raise it to get out.

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
- **Start a new sensor** (NFC, behind a confirmation: it can't be undone): only for a Libre 2 / 2 Plus
  EU whose decrypted memory says it was never started. Sukoon sends Activate, reads the memory back
  until it says *warming up*, then pairs as usual. A sensor already warming up or running is just
  paired. If pairing fails after starting, it says the sensor is running and to tap *Pair again*.
- **Pair again** refuses a sensor nobody started ("hasn't been started yet", which used to restart the
  warm-up countdown each time) and one that has ended.
- **Check sensor** (read-only) says *Not started*, *Warming up*, *Running* (day N of M and the value
  its memory reads now) or *Ended*; a value only while running.
- A read the phone loses halfway (*Tag was lost*) doesn't end Pair, Start or Check: the card keeps
  listening ("Lost it for a moment") and carries on when Android finds the sensor again. Any answer
  from the sensor, even an error, ends it with a message.
- Notices: 3 days, 1 day and 1 hour before the end, and when it ends. Bluetooth off shows on Home,
  and the no-readings alarm says Bluetooth is off when it is.

## 14. Days, backup, clock

- The logbook and the graph show the last 24 hours (or the chosen range) up to now; the *‹ Today ›*
  bar goes to any earlier day (its entries, totals and the whole day's graph).
- Cloud backup (opt-in, You → Export & backup): the full backup zip, written to one file you chose in a
  cloud app, on choosing and then once a day (checked hourly while Sukoon runs). Sukoon keeps
  permission to that file only. *Stop* releases it.
- Times follow You → Appearance → Clock: the phone's, 12-hour or 24-hour, everywhere (alarms,
  notifications and widgets included).

## 15. Following only, and what the phone needs

- Someone who only follows (role *I follow someone*) sees Home and Trends of the person they follow
  (graph, insights and report from the shared readings; meals and insulin stay on the wearer's
  phone), and a You tab with People (who they follow), Alarms (can they reach you, what they did),
  Appearance and Help. *I wear a sensor too* switches to both roles and brings back the rest.
- The setup checklist (Home's banner, You → Help, onboarding) lists only what applies: Bluetooth
  only to a wearer whose source is the sensor; emergency texts only to a wearer with a contact;
  full-screen alarms on Android 14+; *Alarms & reminders* (exact alarms) where Android makes the app
  ask. Battery shows where it stands: **Unrestricted** (on), **Optimized** or **Restricted** (off),
  read from Android itself, with the page that fixes each.
- Before an emergency contact is added, and before an AI key is saved, a one-time screen says what
  will be sent where; nothing happens until *I agree*.

## 16. Notices from Sukoon

- Every install (GitHub and Play, wearer, follower or demo) reads `web/notices.json` from Sukoon's
  GitHub Pages site when it starts and every 6 hours, and asks GitHub for a new release at most once
  a day on the same rhythm. How to send one: docs/notices.md.
- A new notice comes as a notification (channel *Notices from Sukoon*); which ones and how many at
  once is `Notices.toShow`. The newest one still running sits on Now as a card until it is closed;
  closing it is remembered on that phone. Arabic text when the app is in Arabic; a link only if https.
- Not instant: up to 6 hours, plus up to 10 minutes of GitHub caching. Phones on 0.8.1 or older
  don't read notices; they see a new release on Now, with its notes under *What's new*.

## 17. Insights, Ask and the after-workout check

- **Insights** (Trends): the 14 days in one line (time in range, ↑/↓ against the week before); **the one
  thing**, the most important finding (lows first, merged from five rules with their likeliest cause:
  workouts or doses close together), with one button: the first action not already in effect (turn on
  Going low, the after-workout check, dose suggestions, Learning, calibration, the site map, log insulin),
  else *Look at it* (the graph over the same 14 days). Then at most 3 more as one-line rows, the rest
  folded. Every row opens its full card (numbers and source). Not enough data says what to log (3 days
  of readings; meals on 5 days) with the button. A followed person's Insights show no buttons.
  Spec: docs/insights-rework.md.
- **After-workout check** (on by default, You → Alarms → Gentle checks): 30 to 90 minutes after a
  logged workout ends (own or MyFitnessPal), once, if there's a fresh reading: "After your workout: 92 ↘",
  plus one line only when falling. Not an alarm; lows keep their own.
- **Ask**: under the title, what the answers are based on (days, readings and log entries the AI gets:
  real readings without impossible spikes), or an amber *Demo data* chip when that's all there is. The
  first suggestion asks about Insights' one thing (not on demo data).

## 18. Today, and your goal

Where things live, so no screen carries more text than it needs:

- **Now** shows *state* only: one row of chips under the header (alarms paused, workout, the night's
  low alarm after one, fasting with iftar time, sick day, ketones due, missing readings to fill, and
  with a goal, today's kcal and protein so far). Each chip opens where you change it.
- **Today** (Now's sliders button) switches temporary things on, one line each: *Pause alarms*
  (behind the parent lock's PIN), *Workout*, *Fasting today*, *Sick day*. Links: *Goal & training*
  and *Why these numbers*.
- **You → Fitness & food** holds what lasts: the **goal** (none, maintain, lean bulk, cut) with its
  daily kcal and protein, the meal after a workout, and the Ramadan times.
- **The Guide's** *Workouts, fasting, sick days and your goal* holds the reasons and the citations.

| Goal | Calories | Protein | Rate |
|---|---|---|---|
| **Maintain** | maintenance | 1.4–2.0 g/kg (default 1.6) | — |
| **Lean bulk** | +200 / **+250** / +300 | 1.6–2.2 g/kg (default 2.0) | gain 0.25–0.5% of weight a week |
| **Cut** | −300 / **−400** / −500 | 2.0–2.6 g/kg (default 2.2) | lose 0.5–1% a week; "eating less usually means less insulin: review your doses with your clinic" |

Maintenance is yours, or estimated: Mifflin–St Jeor × 1.55 (lifting 3–5 days a week), to 10 kcal.
Protein per meal: 0.4–0.5 g/kg over about 4 meals. Ask gets the goal and its targets.

**Weight**: *Log weight* (Fitness & food) adds a weight entry to the logbook (20–300 kg); the profile's
weight follows the latest one, so the targets do too (a weight typed in the profile stays until the
next weigh-in). From 3 weigh-ins spanning 10+ days in the last 28, the page shows the trend (kg a week,
least squares) and, for a bulk or cut, the pace against the rate above: on track, or about 100 kcal a
day more or less.

Every number below is sourced in `docs/research/modes-sources.md`.

| Mode | While it's on | Dose suggestions (beta) |
|---|---|---|
| **Workout** (1, 1½ or 2 h from Today, or any logged workout) | Low alarm at **100** (Moser 2020). Before starting, by glucose: under 90 "10–20 g and wait", 90–125 "about 10 g", over 270 "check ketones first" (Riddell 2017) | Corrections during and 2 h after: **half** (lifting rises). The meal within 2 h after: **−50%** by default, or −25% / as usual / a custom 0–75% in 5% steps (You → Fitness & food → Training and You → Insulin, one setting). Nothing cut before lifting |
| **After a workout** (24 h from its end) | Low alarm at **80** overnight, 22:00–07:00 (Moser 2020; lows "for at least 24 hours", Riddell 2017) | — |
| **Fasting** (your suhoor-end and iftar times) | Now: under 70 *break the fast now*; 70–90 *check again within the hour*; over 300 *break the fast, correct, check ketones* (IDF-DAR 2021). A suhoor reminder 45 min before Fajr. The times are set once in Fitness & food | Suhoor meal **−30%** (IDF-DAR 30–50%); none suggested during the fast; iftar as usual. Basal: the sheet quotes IDF-DAR (take it at iftar, −30–40%; −15–30% for Toujeo) and leaves it to Kai's doctor |
| **Sick day** | A reminder every 2 h to check ketones and drink; Now names the last reading's band (ISPAD 2022: < 0.6 normal, 0.6–1.4 raised, 1.5–2.9 high, ≥ 3.0 urgent care); high alarms repeat hourly | — |

Ketones are a logbook entry (mmol/L, 0–8); their tile shows on a sick day or above 250.

## 19. Filling gaps, and low treatments

- **Gaps**: the sensor keeps 8 hours of readings (every 15 min). Any NFC tap (pairing, starting,
  *Check sensor*) fills what Bluetooth missed and says how many it added. When readings are missing for
  30 minutes or more within those 8 hours, Now offers the tap; it opens You → Sensor ready to read.
- **Low treatments**: carbs marked *Treating a low* (on by itself from a low alarm's *Log what I ate*,
  or when logged under 70; a switch in the carbs editor) are not a meal: no meal dose, nothing learned
  from them, not an "after a meal" message. Insights reports, from 3 treated lows: the usual grams, how
  many needed a second round within 30 minutes, and how many went over 180 within 2 hours (a rebound).
