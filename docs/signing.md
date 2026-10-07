# Signing

Release builds are signed with Sukoon's own key: `keystore/sukoon-release.jks`, with its alias and
passwords in `local.properties`. Both are gitignored and live only on the build machine.

**Back up both the `.jks` file and the four `release.*` lines from `local.properties`.** Without
them, no update can ever be installed over a release build again: the app would have to be
uninstalled, which deletes everything in it.

Certificate SHA-256: `04:02:41:08:C8:89:66:2C:D4:5E:69:7D:74:DD:0A:21:BD:65:5C:A9:CA:35:FD:DF:8E:C4:09:4D:26:61:27:05`

## Moving a phone from the debug key (one time)

Builds up to 0.6.x were signed with the debug key. Android won't update an app across keys, so:

1. Install the last debug-signed build (`gradle :app:assembleRelease -PdebugSigned`). It has backup.
2. In it: You → Reports → Backup → **Back up everything**, and save the zip somewhere off the app.
3. Uninstall Sukoon.
4. Install the release-signed build.
5. You → Reports → Backup → **Restore**, pick the zip. Sukoon closes; open it again.

Everything comes back: readings, logbook, settings, alarms, the sensor pairing and meal photos.
Home-screen widgets have to be added again.
