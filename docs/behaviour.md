# How Sukoon behaves

Every state the app can be in, what decides it, and what happens when you answer it. This is the
contract: the code follows this document. If they disagree, one of them is a bug.

Numbers are mg/dL. "Fresh" means the newest reading is at most **10 minutes** old.

---

## 1. Where the reading comes from

| Source | What it is | Alarms? |
|---|---|---|
| **Sensor** (Libre 2 Plus over Bluetooth) | Real readings, one a minute | **Yes**: the only source that ever sounds an alarm |
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
| **In range** | 70 to 180 | Number, 3-hour chart, your chosen stats, the message |
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
| **Going low** | Heading under your low line within 20 min | On | On/off | Once per episode | Alarm stream, volume lifted to at least 50% |
| **High** | Over your high line | 180 | Line 150 to 400, on/off, quiet hours | After its snooze (60 min) | Notification stream |
| **No readings** | No reading for N min | 20 min | 10 to 120, on/off | Every 30 min | Notification stream |

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
| **Test** | You → Alarms | The real urgent-low path, marked TEST. Answering it never touches a real alarm |

The alert screen closes by itself once its alarm is over (back in range, signal back).

### What your alarms did

You → Alarms keeps the last 60 alarm events: each one that went off and what got through
(**sounded**, **took the screen**, **notification**), each answer, and each one that ended. When
something didn't get through, it says what, in red.

### Can alarms reach you?

The top of You → Alarms checks each of these and gives a one-tap fix for anything missing (the
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
your own alarm settings. Their alarms show their name. Answers only snooze them on your phone.
Emergency texts are never sent from a follower's phone.

---

## 7. Known limits

- Snoozes, "treated at" and the emergency clock live in memory: if Android kills the app, they reset (the alarm comes back sooner, not later).
- If the phone kills the app while it isn't running in the background, no alarm can sound. That's why the battery items in the check above matter.
