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
  the last selected station.
- Home/work addresses are stored as coordinates; the GPS context engine finds
  nearby stops for quick routing, already-at-destination detection, and random
  destinations. Existing saved stop IDs remain supported.
- Compose provides first-run GTFS import progress, a current-location or
  typed-address/name origin, address search with automatic stop selection and
  an optional "Wybierz przystanek" control, grouped stop names with automatic
  platform selection for the selected route,
  home/work/radio/random quick actions, and manual schedule refresh.
- Address routes compare up to six nearby stop-name groups within 1.5 km.
  Walking to the first stop, between transfer stops, and from the final stop
  is included in door-to-door arrival estimates; results include the fastest
  option and a lower-walking alternative. Distances are Haversine estimates
  at 4.5 km/h, not pedestrian-road directions.
- Stop codes remain available internally for TTSS mapping and stop matching,
  but are not shown in user-facing stop labels.
- Stop-name search ignores Polish diacritics and suggests up to three
  recently selected stop names from either search field.
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
