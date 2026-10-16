# Sukoon for Windows

Your glucose and its alarms on the laptop, read from Sukoon's cloud: no Nightscout server, no VPN, nothing
running on the phone except Sukoon itself (with following turned on, You → People).

- **Alarms:** urgent low fills the screen in red with a sound until you snooze it; low, high and no signal
  show as a card in the corner. The thresholds are in `%APPDATA%\Sukoon\desktop.json` (`urgent`, `low`,
  `high`, `stale_minutes`, …).
- **Nightscout feed for widgets:** `http://127.0.0.1:17580/api/v1/entries/sgv.json?count=240` (also
  `/api/v1/entries.json`, `/api/v1/status.json`, `/pebble`), only on this computer. Point a Rainmeter
  Nightscout skin there; any `token=` it adds is ignored.

## Set up

1. Python 3.10+ (python.org), nothing else to install.
2. In PowerShell: `powershell -ExecutionPolicy Bypass -File desktop\install.ps1`. It starts now and at every sign-in.
3. Sign in with your Sukoon account (the one sharing uses). You don't need a second account: you can read your own readings.
4. To show someone you follow instead: `python desktop\sukoon_desktop.py --who`, then restart it.

Sign out: `python desktop\sukoon_desktop.py --sign-out`. Stop it at sign-in: delete `Sukoon.lnk` from `shell:startup`.

Only your sign-in's refresh token is kept, in `%APPDATA%\Sukoon\desktop.json`; your password isn't stored.
Not a medical device: your phone's alarms are the ones to rely on.

Tests: `cd desktop && python -m unittest test_sukoon_desktop`.
