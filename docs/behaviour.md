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
| **In range** | 70 to 180 | Number, last 3 hours as bars (one per 15 min, a low or high in it shows), your chosen stats, the message |
| **High** | Over 180 | Same layout as in range, amber |

Urgent on Home starts exactly where the urgent-low alarm does (under 55). Time-in-range figures
still use the international bands (under 54 = very low).

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
| **High** | Over your high line | 180 | Line 150 to 400, on/off, quiet hours | After its snooze (60 min) | Notification stream, 8 s |
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

**Pens**: whole units by default (You → Insulin → Your pens). The insulin keypad takes a decimal point
only for half-unit pens, and suggestions round down to the pen.

## 10. Known limits

- Snoozes, answers, "treated at" and texts already sent are kept across a restart. A countdown cut off by one starts again from 60 seconds (with its screen and sound) if the alarm is still unanswered.
- If the phone kills the app, nothing inside it can sound. A watchdog covers that: every reading pushes a system alarm to just past your no-readings line (20 min + 2 by default). If readings stop because the app was killed, Android wakes Sukoon there; the sensor and alarms restart, and *No readings* goes off if the sensor is still quiet. It checks again every 5 minutes until readings return. The battery items in the check above still matter: they keep it from coming to that.
