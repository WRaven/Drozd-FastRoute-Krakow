# MPK Kraków FastRoute

Android app for searching Kraków public-transport connections, showing live
departure information, and playing radio streams. The implementation follows
the architecture and phases in the project specification.

## Implementation status

- The Room v5 database stores GTFS stops, routes, trips, stop times, service
  calendars, date exceptions, and a materialized index of adjacent trip
  connections. The importer downloads the official Kraków feed to a temporary
  cache file, reports download/import/indexing progress, and replaces its data
  transactionally in batches. Existing databases build the connection index
  after migration instead of doing expensive connection joins on every search.
- The route planner filters services for the requested date, scans departures
  from the indexed connections within a two-hour window, requires at least
  three minutes when changing
  vehicles, and includes walking transfers between stops up to 500 meters.
  Its geographic grid limits footpath generation to nearby stops. Static route
  results are displayed before the separate live-departure request completes.
- The TTSS client maps stops by stop code or supplied normalized stop-name
  mapping, applies reported delay to static departures, and returns explicitly
  marked static estimates when live data is unavailable.
- Radio streams run in a MediaSession foreground service, stay bound to the
  media session while active, support system playback controls, and persist
  the selected station. The editable default list includes Eska Rock, Antyradio,
  Radio Pogoda, and Radio Kraków.
- Home/work destinations are stored as coordinates with recurring arrival
  times and selected weekdays. Their quick actions find a route that arrives
  by the next matching scheduled time; a long press on a route saves its
  endpoints as the single "Własny" quick action. Existing saved stop IDs remain
  supported.
- Compose provides first-run GTFS import status, a current-location or
  typed-address/name origin, debounced address and stop suggestions, recent
  address/stop selections, an optional "Wybierz przystanek" control, grouped
  stop names with automatic platform selection, and home/work/custom/radio
  quick actions. A separate work-schedule action opens its saved arrival-time
  and weekday editor; long suggestion lists scroll within a bounded area so
  they do not push the active search field off screen. The start screen keeps
  its search controls compact and combines active GTFS-RT service alerts with
  official ZTP RSS notices in a tappable scrolling ticker, with a shortcut to
  the official MPK Kraków profile on X. Recent address and stop suggestions
  appear only while a search field is focused with the keyboard open; quick
  actions and route-planning controls hide while typing.
- Address routes compare up to six nearby stop-name groups within 1.5 km.
  Walking to the first stop, between transfer stops, and from the final stop
  is included in door-to-door arrival estimates; results include the fastest
  option and a lower-walking alternative. Distances are Haversine estimates
  at 4.5 km/h, not pedestrian-road directions.
- The route form can plan a one-time departure or latest arrival by date and
  time. The startup origin defaults to the current location when permission is
  granted, and the quick actions open the official ZTP/KMK service notices.
- The weather quick action requests foreground location only when tapped and
  shows current conditions plus an hourly 24-hour forecast from Open-Meteo.
  Forecasts are cached in memory for 15 minutes by approximate location; no
  weather location is stored. The weather view credits Open-Meteo and links
  its CC BY 4.0 license.
- Saved home/work schedules can trigger a commute notification 15 minutes
  before the calculated departure. The notification shows upcoming TTSS
  departures in its collapsed preview and refreshes every five minutes with
  route-relevant ZTP GTFS-Realtime service alerts.
  Android notification and background-location permissions are required; if
  any alert feed is unavailable, the notification reports incomplete data
  instead of claiming there are no disruptions. ZTP's GTFS-Realtime feeds are
  published under CC BY 4.0. Live departure and alert requests use Android's
  active network, so Wi-Fi is used when available and mobile data is the
  fallback.
- WorkManager checks the official static GTFS feed at app startup and daily,
  importing it when its ETag or last-modified value changes. The request uses
  the currently active network (Wi-Fi when connected, with mobile-data
  fallback); WorkManager waits until a network is available. A small
  percentage ring in the app bar appears only while the download/import runs.
  Existing timetable data remains available until the replacement commits;
  first-time route search becomes available after the initial ZTP import.
  Service calendars determine when newly published timetable data applies.
- Stop codes remain available internally for TTSS mapping and stop matching,
  but are not shown in user-facing stop labels.
- Stop-name search ignores Polish diacritics and suggests up to three
  recently selected stop names from either search field.
- Settings can check GitHub releases, open the releases page, show a QR code
  for sharing it, edit home/work arrival schedules, and contact the author.
  The test app checks the latest prerelease; the production app checks the
  latest stable release. The app checks for updates at startup and offers
  "Aktualizuj" or "Pomiń"; skipping suppresses that release until a newer
  version appears. Updating opens the public release page and Android asks the
  user to confirm installation. If no matching release has been published,
  the updater reports that none is available. Radio settings support managing
  streams, and the radio action toggles playback.
- TTSS predictions are matched to static departures at the selected origin;
  unmatched or unavailable live data remains visibly static.
- Settings include an in-memory, bounded search diagnostic log with copy and
  clear actions. It reports database/index/CSA stages and counts without
  recording addresses or GPS coordinates.

## Versioning and in-place updates

- Releases use semantic version tags: `vMAJOR.MINOR.PATCH` (for example,
  `v1.2.0`). The tag becomes the Android `versionName`.
- GitHub Actions assigns the workflow run number as Android `versionCode`.
  Gradle reads `APP_VERSION_NAME` and `APP_VERSION_CODE` from the release
  workflow and uses `git describe --tags --always` for local builds.
- The permanent Android application ID is `pl.wraven.mpkfastroute`. Do not
  change it after publishing the first APK.
- All releases must keep the same Android `applicationId` and be signed with
  the same release key. Changing either prevents Android from installing an
  APK over the existing app.
- The release workflow deliberately fails unless the four signing secrets
  below are configured. It never silently publishes an unsigned or differently
  signed APK.

Before the first release, create and securely back up one Android release
keystore. Configure these GitHub Actions repository secrets without sharing
their values in chat or committing them to the repository:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Keep the keystore and passwords safe and unchanged for future releases. If the
release key is lost, Android cannot install a differently signed APK as an
update.

Publish a version by tagging a commit and pushing the tag:

```powershell
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

The workflow builds a signed, versioned APK, attaches it to the matching
GitHub Release, and keeps a downloadable Actions artifact.

Main-branch test APKs use the separate `pl.wraven.mpkfastroute.debug` package
and appear as "MPK Kraków FastRoute (test)". Configure a separate, stable test
keystore using these GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `TEST_KEYSTORE_BASE64` | Base64 encoding of the test keystore file |
| `TEST_KEYSTORE_PASSWORD` | Keystore password |
| `TEST_KEY_ALIAS` | Signing key alias |
| `TEST_KEY_PASSWORD` | Signing key password |

On Windows, create the test keystore outside the repository in PowerShell:

```powershell
$keyPath = Join-Path $HOME "mpk-fastroute-test.jks"
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
& $keytool -genkeypair -v -keystore $keyPath -storetype JKS `
  -alias mpk-fastroute-test -keyalg RSA -keysize 2048 -validity 10000 `
  -dname "CN=MPK FastRoute Test, OU=Development, O=WRaven, C=PL"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
```

Use the copied value for `TEST_KEYSTORE_BASE64`, the alias `mpk-fastroute-test`
for `TEST_KEY_ALIAS`, and the passwords you entered for the password secrets.
Keep this test keystore safe and unchanged; CI signs test APKs with it so one
test APK can update another without removing its local database. The test app
remains separate from the regular release app and does not share its data. If
the test secrets are missing, CI still runs validation but does not upload a
test APK artifact. Pull-request builds are for validation only and do not
publish APK artifacts.
