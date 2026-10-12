# Notices: a message to everyone who has Sukoon

Every phone with Sukoon (GitHub and Play builds, wearers, followers and demo users alike) reads
`web/notices.json` from Sukoon's GitHub Pages site when the app starts and then every 6 hours, and
shows each notice it hasn't shown before as a notification ("Notices from Sukoon" channel). Tapping
it opens the notice's link, or the app. The newest notice that is still running also sits on Now as
a slim banner that opens into the full notice, until "Don't show again" or its last day.

## Send one

1. Edit [`web/notices.json`](../web/notices.json) on `main` (GitHub's web editor works from a phone).
2. Add an entry at the end of the array (newest last):

```json
[
  {
    "id": "2026-10-12-sensor-start",
    "date": "2026-10-12",
    "title": "Start new sensors in Sukoon",
    "body": "You → Sensor → Start a new sensor. No other app needed.",
    "title_ar": "شغّل الحساس الجديد من سكون",
    "body_ar": "إنت ← الحساس ← شغّل حساس جديد. من غير أي تطبيق تاني.",
    "link": "https://github.com/khairyKY/sukoon/releases/latest",
    "until": "2026-10-26"
  }
]
```

3. Commit. The "Follower page" workflow publishes `web/` in about a minute.

| Field | |
|---|---|
| `id` | Required, unique, never reused: a phone shows each id once. A date prefix keeps them unique. |
| `title`, `body` | Required. English. |
| `title_ar`, `body_ar` | Optional. Shown when the app is in Arabic. |
| `link` | Optional, `https://` only (anything else is ignored). |
| `date` | Optional day it was sent (`YYYY-MM-DD`), shown as "From Sukoon · today". |
| `until` | Optional last day (`YYYY-MM-DD`). Set it, so new installs don't get old news. |

## Timing

Not instant: up to 6 hours for the phone's next check, plus up to 10 minutes of GitHub Pages
caching. A phone with notifications turned off for Sukoon gets the notice once they're back on.
Old entries can stay in the file; remove them when they no longer matter.
