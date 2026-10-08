# Sukoon on iPhone, and in any browser

Sukoon is an Android app. There is no App Store version, and there can't be one that pairs a sensor
without Apple's paid developer program (a free Apple account can't use NFC to read a Libre). What
works on an iPhone today is **following**: seeing someone's glucose live, from the web.

| You are… | On Android | On iPhone |
| :-- | :-- | :-- |
| **Wearing the sensor** | The Sukoon app (pairs the Libre 2 / 2 Plus itself) | Not possible with Sukoon. Use Abbott's LibreLink, and followers can still watch you through LibreLinkUp in Sukoon on Android |
| **Following someone who wears Sukoon** | The Sukoon app (*I follow someone*), with full alarms | **The follow page**, below |
| **Following someone on LibreLink or Dexcom** | The Sukoon app (You → People → LibreLinkUp / Dexcom) | Abbott's LibreLinkUp or Dexcom Follow apps |

## The follow page: https://khairyky.github.io/sukoon/

The same number, arrow, 3-hour chart and words as the app, in English or Arabic.

**The person wearing the sensor** (in Sukoon on Android):

1. You → People → **Invite**. Sukoon makes a one-time code (it works once, for 24 hours).
2. **Send** shares the code and the link in one message (WhatsApp, SMS…).

**The person following, on an iPhone:**

1. Open the link **in Safari** (Chrome works too on iOS 16.4 and later, through its own Share button).
2. Tap **Share** (the square with an arrow), then **Add to Home Screen**, then **Add**.
3. Open **Sukoon** from the home screen. It runs full screen, like an app.
4. **Create account** (name, email, password), then type the **invite code**.
5. Tap **Turn on** under *Sound an alarm here when they go low*. iPhone only allows sound after a tap.

The page shows step 2 itself until it's on the home screen. Sign in from the home-screen copy: on
iPhone, the home-screen page and Safari keep separate logins.

**On an Android phone without the app:** open the link in Chrome; *Install* (or ⋮ → *Add to Home
screen*) does the same.

## What the page can't do

- **Alarms sound only while the page is open on screen.** iPhone stops web pages in the background.
  For when nobody is watching, the wearer's **emergency contacts** (You → People) get a text and a
  call when an urgent low goes unanswered: set those up, with the follower's number.
- No logbook, no reports: it's for following. Everything else is in the app, on the wearer's phone.

## Privacy

The page talks only to Sukoon's Supabase project, signed in as you. Row-level security means you see
only the people who invited you, and only while they keep you. Nothing is stored on the page beyond
your sign-in and language.
