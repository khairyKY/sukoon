# Sukoon's Guide: content

The source for You → Guide (and the getting-started card on Now). Every topic has the same shape so
it reads at a glance: a one-line **what it's for**, numbered **steps** with the exact path, **good to
know**, and, where things can go wrong, **if it doesn't work**. Facts follow docs/behaviour.md.

**The app reads `app/src/main/assets/guide/guide-en.json`, `guide-ar.json` and `guide-ar-EG.json`**
(same structure; a step's `link` is a screen MainScaffold.openLink knows, and GuideContentTest checks
all three match and every link exists). When the app changes, change this doc, then the three files.
The app's guide has two topics more than this list: Learning from your meals, and Ask Sukoon.

Paths are written as the app shows them: tab → page → control (You → Sensor → *Start a new sensor*).

---

## Group 1 · Get started

### 1. Start or connect your sensor  · icon `ic_sensor`
**For:** getting readings from your Libre 2 or 2 Plus straight into Sukoon, no other app needed.

**A new sensor**
1. Put the sensor on as usual.
2. You → Sensor → **Start a new sensor** → **Start it**.
3. Hold the top of your phone flat on the sensor until it says **Started**.
4. Wait an hour: the sensor warms up, and Now counts down to the first reading.

**A sensor that's already running**
1. You → Sensor → **Pair again**.
2. Hold the phone on the sensor until it buzzes. The last 8 hours appear at once.

**Good to know**
- A sensor talks to one phone at a time. Pairing Sukoon takes it from LibreLink or DiaBox; scanning it in that app takes it back.
- **Check sensor** only reads it: what state it's in, which day, and its value now.
- Lost it halfway ("Lost it for a moment")? Keep the phone still. It carries on by itself.

**If it doesn't work**
- "Hasn't been started yet": use **Start a new sensor**, not Pair again.
- "Has ended or failed": it's time for a new sensor.

### 2. Now: your number at a glance  · icon `ic_drop`
**For:** knowing where you are and what to do, in one look.

1. The big number is your newest reading; the arrow is where it's heading.
2. The colour says low, in your range or high. The message under it says what matters now, and one next step.
3. The two buttons at the top log food and insulin; "u active" is rapid insulin still working.

**Good to know**
- A reading older than 10 minutes turns grey and is never shown as current.
- During a sensor's first hour, any reading shows in grey and counts for nothing: no alarms, reports or AI.
- Pull down to bring in the latest MyFitnessPal meals.

### 3. Keep Sukoon running  · icon `ic_sync`
**For:** alarms that reach you at 3 a.m. too.

1. You → Help & about → **Setup**: allow everything it lists.
2. On Samsung or Xiaomi, also open the phone's Settings › Apps › Sukoon and set Battery to **Unrestricted**; on Xiaomi turn on **Autostart** too.
3. Leave Sukoon's small notification on: it's what keeps it running, and it shows your number.

**Good to know**
- If the phone stops Sukoon anyway, a watchdog wakes it and the no-readings alarm sounds.
- After a restart or an update it reconnects by itself within a minute or two.
- Keep the phone within a few metres; a body or a wall shortens that. You → Sensor → Connection counts the last day's gaps.

### 4. Safety first  · icon `ic_help`
**For:** knowing what Sukoon is, and isn't.

- Sukoon is not a medical device. If a number doesn't match how you feel, check with a finger-prick.
- Follow your care team on treatment. Dose suggestions are a beta, and only maths on your own numbers.
- In an emergency, call your local emergency number.

---

## Group 2 · Alarms and emergencies

### 5. Alarms  · icon `ic_bell`
**For:** hearing about a low or a high before it becomes a problem.

1. You → **Alarms**: set your low (70) and high (180) lines, and turn each alarm on or off.
2. **Sounds**: pick a pack (Astral, Orbit, Glass, Clear or Bells), or a phone sound or your own file for any alarm.
3. **See and hear each alarm** to try them.
4. **Can alarms reach you?** lists anything blocking them, with a fix for each.

**Good to know**
- Urgent low (under 55) is always on and can't be moved: it takes over the screen and repeats every 5 minutes until you answer.
- While a low sounds, the alarm volume is lifted, and put back after.
- **I'm treating it** starts the 15-minute wait, then checks you again.
- **What your alarms did** shows every alarm, what got through and how it was answered.
- Highs can stay quiet at night (quiet hours); lows never do.

### 6. Emergency contacts  · icon `ic_phone`
**For:** someone being told when you can't answer.

1. You → People → **Emergency contacts** → add from your phone book (or type a number).
2. Allow SMS and calls when Setup asks.
3. Choose how long an urgent low may go unanswered (10 minutes by default).

**What happens**
1. An urgent low (or lost signal while low) goes unanswered for that long.
2. A 60-second countdown fills the screen, with a loud sound and **I'm OK**.
3. Everyone gets a text, and the first contact gets a call. Again after 30 minutes if there's still no answer.
4. Once you're back to 70 or more, they get a "back to normal" text.

**Good to know**
- Any answer, even swiping the alarm away, restarts the clock.
- On Now, **Alert my emergency contact** texts, WhatsApps or calls them yourself.

---

## Group 3 · Food and insulin

### 7. Log a meal  · icon `ic_food`
**For:** knowing what went in, so the numbers make sense later.

1. Tap the food button on Now (or + in Trends → Logbook) → **Meal**.
2. Find what you ate: search (yours first, then 88 home dishes, then Open Food Facts), the **barcode** button, **Photo**, or **Just carbs**.
3. Set **How much?** for each food; the plate adds it up.
4. Add the insulin you took for it on the same screen, and save.

**Good to know**
- **Ask the AI** estimates any dish from its name or photos (your own Gemini key).
- A barcode that isn't found can be **added as your own**; the next scan finds it.
- Every entry shows your glucose at that moment; meals show what they did to it afterwards.
- Saved by mistake? **Undo** on the confirmation.

### 8. Insulin and dose suggestions  · icon `ic_insulin`
**For:** logging doses quickly, and (if you want) a suggested dose with its maths.

1. The insulin button on Now → rapid or long-acting → the amount on the number pad → **Where?** (optional) → save.
2. "u active" on Now is rapid insulin still working (5 hours by default, You → Insulin).
3. **Dose suggestions (beta):** You → Insulin → turn on, then fill your ratio per meal, correction factor, target and maximum.

**Good to know**
- A suggestion shows its maths; **Use** puts it in the amount, only **Save** logs it.
- No suggestion when you're under 70, or under 100 and falling: treat that first (15 g).
- **Learning from your meals** shows what your logged meals say about your ratios. Nothing changes until you press **Use**, and then by at most 20%.
- Under 18, suggestions only turn on behind a parent lock.

### 9. Long-acting reminder  · icon `ic_clock`
**For:** never wondering "did I take it?".

1. You → Insulin → **Reminder** → on, and pick the time.
2. If no long-acting was logged in the 12 hours before, a notification comes at that time.
3. **Took it** logs your usual amount; **In 30 min** waits.

**Good to know**
- It comes back every 30 minutes, 3 times, and disappears as soon as the dose is logged anywhere.
- The Insulin widget has **Took it** too.

### 10. Injection sites  · icon `ic_eye`
**For:** moving your injections around, so insulin works the same every time.

1. When you log insulin, tap **Where?** and tap the spot on the body map (your left is on the left).
2. The dashed ring is the spot that has rested longest: a good next choice.
3. You → Insulin shows the last 30 days as a map.

**Good to know**
- The same spot again and again can form lumps under the skin that take up insulin unevenly.
- Belly is fastest (a few centimetres from the navel), then the back of the upper arms, outer thighs and upper outer buttocks. (Frid et al., Mayo Clin Proc 2016)

### 11. MyFitnessPal and Health Connect  · icon `ic_sync`
**For:** meals you log in MyFitnessPal showing up in Sukoon, with every nutrient.

1. In MyFitnessPal: More → Apps & Devices → **Health Connect** → Connect, and allow everything.
2. In Sukoon: You → Apps & data → **Health Connect** → allow.
3. Pull down on Now or the Logbook to bring the latest in.

**Good to know**
- Workouts of 10 minutes or more, steps and water come in the same way; your readings go out to Health Connect.
- MyFitnessPal shares one running total per day, so Sukoon splits it into meals by when you logged them.

**If it doesn't work**
- You → Apps & data → **Resync** says how many MyFitnessPal meals Health Connect has. None? Update MyFitnessPal and connect again.

---

## Group 4 · Understand your numbers

### 12. Graph, Insights and Report  · icon `ic_doc`
**For:** seeing the patterns behind the days.

1. **Trends → Graph:** any range from 3 hours to 14 days, any past day, with time in range and GMI (an A1C estimate).
2. **Insights:** patterns like recurring lows at a certain hour, each with where the rule comes from.
3. **Report:** the one-page AGP summary clinics use, as a PDF to share.

**Good to know**
- Insights are observations, never instructions.
- Readings from a sensor's first hour are left out of all three.

### 13. Your range  · icon `ic_contrast`
**For:** a range that matches your goals.

1. You → Alarms → **Your range**: 70 to a top you choose, 120 to 180.
2. Now, the graph's colours, and time in range follow it; reports also keep the international 70–180.

**Good to know**
- Moving the top can move your high alarm with it.

### 14. Calibration  · icon `ic_drop`
**For:** nudging the sensor toward your finger-pricks, when it's clearly off.

1. Log finger-pricks while your glucose is steady (Logbook → **Finger-prick**).
2. You → Sensor → **Calibration** → on.

**Good to know**
- Off by default: the sensor's own calibration is usually best.
- It uses up to 4 steady finger-pricks from the last 7 days, within ±20 mg/dL and ×0.8–1.25, and never raises a low.

---

## Group 5 · People

### 15. Share with family  · icon `ic_people`
**For:** the people who'd want to know, seeing your number live.

1. You → People → **Sharing** → make an account (email and password).
2. **Invite someone to follow you** → **Send**: the message has a one-time code (24 hours) and the follow-page link.
3. On Android they enter the code in Sukoon (*I follow someone*); on an iPhone they open the link, add it to the home screen and enter it there.
4. **Alarms your followers get:** choose which of your alarms sound on their phones. Urgent low always does.

**Good to know**
- Only real readings are shared, never demo data. Either side can end a follow.
- On an iPhone, alarms sound only while the follow page is open: keep emergency contacts set too.

### 16. Follow someone  · icon `ic_people`
**For:** watching a child's or parent's glucose, with their alarms on your phone.

1. You → People → **Enter a code** from their invite (or LibreLinkUp / Dexcom, next topic).
2. Now shows their number, chart and words; Trends shows their graph, insights and report.
3. **Alerts for people you follow:** on, and their alarms sound here with their name.

**Good to know**
- Answering their alarm only snoozes it on your phone. Emergency texts are never sent from a follower's phone.

### 17. LibreLinkUp and Dexcom  · icon `ic_link`
**For:** following people on Abbott's or Dexcom's apps, or using one as your own glucose (a Libre 3, say).

1. You → People → **LibreLinkUp** (or **Dexcom**) → sign in.
2. LibreLinkUp: in their Libre app, Connected apps → LibreLinkUp → add your email. Dexcom: Share on in their Dexcom app.
3. To make one your own glucose: You → Sensor → pick it as the source.

**Good to know**
- Both come through the internet, about a minute (LibreLinkUp) or five (Dexcom) behind the sensor.

### 18. Parent lock  · icon `ic_lock`
**For:** a child's phone where the important settings can't be changed by accident or on purpose.

1. You → your name at the top → **Parent lock** → **Set**, and type a 4–8 digit PIN twice.
2. From then on, changing alarms, the range, doses or the profile asks for the PIN, once per visit.

**Good to know**
- It also guards everything that would cut a parent off: which alarms followers get, sharing, removing a follower, signing out and deleting the account.
- It's for children only: from 18 (the age in About you) it no longer applies.
- Removing it needs the PIN.

---

## Group 6 · Your phone and your data

### 19. Widgets  · icon `ic_eye`
**For:** your number on the home screen, without opening anything.

1. You → Appearance → **Make a widget**.
2. Pick a style (Number, Number + graph, Graph, Ring, Today, Insulin, Following), a size and a background; the preview is the real widget.
3. **Add to home screen**.

**Good to know**
- Long-press a widget to edit it. An old reading greys out, never shown as current.

### 20. Backup, moving phones and your data  · icon `ic_doc`
**For:** never losing your history, and knowing where it goes.

1. You → Export & backup → **Back up everything**: one private file with readings, logbook, settings, sensor pairing and photos.
2. On a new phone: **Restore** that file, and Sukoon opens as it was (widgets need adding again).
3. **Daily backup to your cloud** (optional): pick a file in Drive, OneDrive or Dropbox, refreshed once a day.

**Good to know**
- Nothing leaves your phone unless you turn it on: Nightscout, the AI (your own Gemini key), followers, Health Connect.
- **Notices from Sukoon** may arrive as a notification or a card on Now: news about the app, read every 6 hours from Sukoon's site, with nothing about you sent.
- The full list is in the privacy policy (You → Help & about).

---

## The getting-started card (Now)

Three steps, ticked as they're done, then it goes away:
1. **Start or connect your sensor** → opens topic 1 / You → Sensor.
2. **Make sure alarms can reach you** → Setup.
3. **Add an emergency contact** → You → People.
Buttons: **Open the guide**, **Not now**.
