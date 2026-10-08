# Getting Sukoon onto Google Play

Researched 2026-10-09 against Google's own pages (links inline). Play's rules move; re-check anything
marked *verify* in the Console when you get there.

## Where things stand

| | Done in the code | Left for Kai |
| :-- | :-- | :-- |
| Package name | The Play build is `io.github.khairyky.sukoon` (`com.sukoon.app` is taken on Play by an unrelated app). GitHub builds keep `com.sukoon.app`. Change it in `app/build.gradle.kts` *before the first upload* if you want another; it can never change after. | Decide |
| Target API | `targetSdk 36` / `compileSdk 36` (required since 31 Aug 2026) | — |
| 16 KB pages | CameraX 1.4.2; every native library checked at 16 KB alignment | — |
| Self-update | Off in the Play build (`SELF_UPDATE=false`, no `REQUEST_INSTALL_PACKAGES`); Play updates it | — |
| Dose suggestions | Off in the Play build (`DOSE_BETA=false`): Play and Health Connect treat dose calculators as regulated medical devices | — |
| Exact alarms | Play build drops `USE_EXACT_ALARM` and asks for *Alarms & reminders* in the setup checklist | — |
| Account deletion | In the app (You → People → *Delete my account*) and on the follow page | Run `supabase/migrations/20261009000000_delete_account.sql` in the Supabase SQL editor |
| Privacy policy | https://khairyky.github.io/sukoon/privacy.html (EN + AR), linked in You → Help and from Health Connect | Live once `web/` is merged |
| In-app disclosures | One-time "I agree" before emergency texts (glucose + location) and before the AI gets data | — |
| Build | `:app:bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab` | Upload |

Builds: `github` flavor = the GitHub releases (as before); `play` flavor = the store. Unit tests:
`:app:testGithubDebugUnitTest`; release APK: `:app:assembleGithubRelease`; store bundle: `:app:bundlePlayRelease`.

## 1. Only you can do these

1. **Developer account** (personal): https://play.google.com/console/signup. US$25 once, by a card in
   your legal name (prepaid cards aren't accepted; an Egyptian card may need online/international
   payments switched on at the bank). Government ID and address verification, phone and email codes,
   and proving an Android phone through the Play Console app. Egypt is supported; a free app needs no
   merchant account. Public on the listing: your name, country and developer email.
   [Account requirements](https://support.google.com/googleplay/android-developer/answer/13628312)
2. **App signing:** when creating the app, choose to **use your own key** and upload Sukoon's release
   key (`keystore/sukoon-release.jks`) with Google's PEPK tool, then make a *separate upload key*.
   GitHub and Play builds then share one signature. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
3. **Closed test before production** (personal accounts made after Nov 2023): **12 testers opted in for
   14 days in a row**, then *Apply for production*. Family, friends from the WhatsApp status, the
   follower circle: each needs a Google account and must opt in from the test link and stay in.
   [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
4. **Videos** (screen recordings on your phone with the sensor, uploaded unlisted to YouTube):
   the sensor connection running (foreground service *connectedDevice*), following someone
   (*specialUse*), and an urgent-low emergency text (for the SMS declaration).
5. **Supabase:** run the delete-account migration (above).

## 2. Store listing

**App name** (30): `Sukoon: glucose for Libre 2`
**Category:** Medical. **Contact:** your developer email; website https://github.com/khairyKY/sukoon

**Short description** (80):
- EN: `Read your Libre 2 directly, with alarms, a logbook and family following. Free.`
- AR: `اقرأ حساس ليبر ٢ مباشرة، بمنبّهات وسجل ومتابعة للعائلة. مجانًا.`

**Full description (EN):**

> Sukoon reads your FreeStyle Libre 2 or 2 Plus sensor directly over Bluetooth, every minute, and
> turns it into something calm: one number, its trend, and what to do now.
>
> • Alarms for lows, highs and lost signal that reach you, with Do Not Disturb and silent mode
> • If an urgent low goes unanswered, Sukoon texts and calls the people you choose
> • Family can follow you live, from the app or any phone's browser (iPhone too)
> • Log meals from foods: search in Arabic or English, scan a barcode, 88 home dishes, or ask the AI
> • Insulin, finger-pricks, activity, injection sites and photos in one logbook
> • Any day's graph, insights with sources, and a one-page report for your doctor
> • MyFitnessPal and Health Connect, Nightscout, backup to your own cloud
> • English, Standard Arabic and Egyptian Arabic; light and dark
>
> Free and open source, no ads, no tracking: https://github.com/khairyKY/sukoon
>
> Sukoon is not a medical device and does not diagnose, treat, cure or prevent any condition. It is
> not affiliated with Abbott. Always confirm with a finger-prick meter and talk to your healthcare
> professional before changing your treatment.

**Full description (AR):**

> سكون يقرأ حساس فري ستايل ليبر ٢ أو ٢ بلس مباشرةً عبر البلوتوث كل دقيقة، ويحوّله إلى شيء هادئ: رقم
> واحد، واتجاهه، وما يجب فعله الآن.
>
> • منبّهات للهبوط والارتفاع وانقطاع الإشارة تصل إليك حتى في وضع عدم الإزعاج
> • إذا لم يُستجب لهبوط خطير، يرسل سكون رسالة ويتصل بمن تختارهم
> • يتابعك أهلك مباشرةً من التطبيق أو من متصفح أي هاتف، حتى الآيفون
> • سجّل وجباتك من الأطعمة: ابحث بالعربية أو الإنجليزية، امسح الباركود، ٨٨ طبقًا منزليًا، أو اسأل الذكاء الاصطناعي
> • الأنسولين ووخز الإصبع والنشاط وأماكن الحقن والصور في سجل واحد
> • رسم أي يوم، وملاحظات بمصادرها، وتقرير من صفحة واحدة لطبيبك
> • MyFitnessPal وHealth Connect وNightscout، ونسخة احتياطية في سحابتك
> • الإنجليزية والعربية الفصحى والمصرية، ومظهر فاتح وداكن
>
> مجاني ومفتوح المصدر، بلا إعلانات ولا تتبّع.
>
> سكون ليس جهازًا طبيًا ولا يشخّص أو يعالج أو يمنع أي حالة، وليس تابعًا لشركة Abbott. تأكد دائمًا
> بجهاز وخز الإصبع واستشر مختصًا قبل تغيير علاجك.

**Graphics:** icon 512×512 PNG (from `docs/images/sukoon-mark.svg`); feature graphic 1024×500;
2–8 phone screenshots (Home, alarm, meal from foods, graph, insights, follow). No Abbott logos.
[Listing assets](https://support.google.com/googleplay/android-developer/answer/9866151)

## 3. Play Console → App content (draft answers)

**Privacy policy:** https://khairyky.github.io/sukoon/privacy.html
**Ads:** No. **Target audience:** 18 and over (avoids the Families policy; supervised use by a
parent is still possible). **Content rating:** IARC questionnaire: no violence etc.; *yes* to "shares
the user's location with others" (emergency texts) and to user-to-user sharing (followers).
**App access:** give reviewers this: "No sensor needed: on first start choose *I wear a sensor*,
skip pairing, then You → Sensor → *Demo data*. Following: invite code from a second install, or skip."

**Health apps declaration** ([form](https://support.google.com/googleplay/android-developer/answer/14738291)):
tick *Diseases & conditions management*, *Nutrition & weight management*, *Medication & treatment
management*, *Activity & fitness*. **Do not** tick *Medical device apps* (Sukoon isn't cleared as one;
that's why the Play build has no dose suggestions).

**Health Connect** (same form): READ_NUTRITION (meals from MyFitnessPal into the logbook),
READ_EXERCISE (workouts for insights on lows after activity), READ_STEPS and READ_HYDRATION (Home
stats you choose), WRITE_BLOOD_GLUCOSE (share your readings with other health apps),
READ_HEALTH_DATA_IN_BACKGROUND (meals logged in MyFitnessPal arrive while Sukoon is in the
background, so the dose and meal reminders see them). [Health Connect publish](https://developer.android.com/health-and-fitness/health-connect/publish)

**Foreground services** ([declaration](https://support.google.com/googleplay/android-developer/answer/13392821)):
- *connectedDevice* (SensorService): "Keeps the Bluetooth connection to the user's glucose sensor,
  which sends a reading every minute. If it stops, readings and low-glucose alarms stop." Video: pairing
  and the ongoing notification.
- *specialUse* (FollowService): "A family member follows a person with diabetes and must be woken by
  that person's low-glucose alarms within a minute; the service keeps the live connection." Video:
  following and an alarm. (If rejected: move follower alarms to push messages.)

**SMS** ([policy](https://support.google.com/googleplay/android-developer/answer/10208820)):
Permissions Declaration Form → SEND_SMS → exception *Physical safety / emergency alerts*: "When an
urgent low-glucose alarm goes unanswered, the user may be unconscious; Sukoon texts their chosen
emergency contacts their glucose and location. A text the user must confirm can't work here."
CALL_PHONE needs no form.

**Full-screen intent:** answer that alarms are core: glucose alarms for lows can be life-threatening.
The app already asks for the permission where Android doesn't grant it.

**Data safety** ([form](https://support.google.com/googleplay/android-developer/answer/10787469)):
| Data type | Collected | Shared | Why | Optional |
| :-- | :-- | :-- | :-- | :-- |
| Health info (glucose) | Yes (Supabase, for followers) | No (user-initiated transfers) | App functionality | Yes |
| Name, email | Yes (account) | No | Account management | Yes |
| User IDs | Yes (account) | No | Account management | Yes |
| Photos, user content | Yes (to Gemini, user's key) | No | App functionality | Yes |
| Approximate/precise location | Yes (emergency text only) | No | App functionality (safety) | Yes |
| Crash logs, analytics, ads IDs | No | No | — | — |
Encrypted in transit: yes. Users can request deletion: yes, in the app and on the follow page.

## 4. Things to watch after launch

- Next target-API deadline (likely API 37, Aug 2027: *verify*).
- Developer verification for sideloaded apps (2027 worldwide): register the GitHub package too.
- Reviewers may question *specialUse* and SEND_SMS; the fallbacks are noted above.
