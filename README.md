# Drozd FastRoute

<img src="./app/src/main/res/drawable-nodpi/drozd_logo.png" width="180" alt="Drozd FastRoute logo: a thrush above a route" />

[Polski](./README.pl.md) | **English**

Android app for searching Kraków public-transport connections, showing live
departure information, and playing radio streams. This document describes the
current application and its development workflow. The app interface is currently
in Polish; English documentation does not change the interface language.

**Kraków public-transport timetables — unofficial application.** Drozd FastRoute
is an independent project, not an official MPK or ZTP Kraków application and not
affiliated with either organization. It was previously named MPK Kraków FastRoute;
the new display name does not change existing installations or saved data.

## Install and get started

1. Use Android 8.0 or newer and an internet connection for the first timetable
   download.
2. Open [GitHub Releases](https://github.com/WRaven/Drozd-FastRoute-Krakow/releases)
   and download the APK from the desired channel. Public test builds are
   prereleases named **Test build N**; regular releases use `vMAJOR.MINOR.PATCH`.
   Within the test channel, choose the highest build number, not just the first
   release listed.
3. Open the APK, allow installation from the browser or file manager if Android
   requests it, and confirm installation.
4. Open the app and wait for the first GTFS timetable import. Its progress is
   visible; route search becomes available when the import finishes.
5. Allow foreground location for automatic origin selection, Live map centering
   and local weather, or enter your origin manually.

Stable **1.0.0** is named **Drozd FastRoute**, without "test", and updates the
existing test installation using the same Android package and signing key.
An `upgrade-v1.0.0` prerelease carries the identical stable APK for older
updaters that only search prereleases. After installing it, the release app
checks stable releases.

From **1.0.1**, the updater, release link and QR code use the renamed repository
directly. Installed 1.0.0 can discover this update through GitHub's old-address
redirect; the Android package, signing key and local data remain unchanged.

From **1.0.2**, clock times in route results and Live use `00:00–23:59`, not raw
GTFS hours such as `25:06` (displayed as `01:06`). Route times show `(+1)` only
when that time is on the calendar day after the journey starts. GTFS service-day
values remain unchanged internally, preserving night-trip order and durations.

To update an existing installation, open **Ustawienia → Sprawdź aktualizacje**
(Settings → Check for updates), then **Pobierz i zainstaluj** (Download and install).
If required, use **Zezwól na instalowanie** to grant this app installation permission
and confirm the Android installer. Do not uninstall first: a compatible update
preserves the local timetable and preferences. Debug and release builds now share
the permanent package identity; use the stable APK for normal use.

## Using the app

| Feature | How to use it |
| --- | --- |
| Route search | Enter an origin and destination address/name, select suggestions or use the current location. **Wybierz przystanek** allows explicit stop selection. The date/time editor supports departure or latest-arrival planning. Results include transport legs, walking estimates and available live departures. |
| Home and work | Use **Ustaw: Do domu** / **Ustaw: Do pracy** to configure a destination, arrival time and weekdays. Subsequent taps plan arrival for the next scheduled occurrence. Edit saved schedules in Settings or the work-schedule quick action. |
| Shortcut names | In Settings, edit **Nazwa skrótu do domu** and **Nazwa skrótu do pracy**, then tap **Zapisz nazwy skrótów**. Names cannot be blank. Both persist after restart and also appear in schedule headings and commute notifications. Renaming does not change the destinations or schedules. |
| Custom route | Long-press a result to store its endpoints as the single **Własny** shortcut. Saving another custom route replaces the previous one. |
| Live map | Tap **Live**. The map initially opens on Kraków and centers on your location when available. Tap a vehicle for its whole route and scrollable list of all stops with arrival time and minutes remaining. Tap a stop, choose a line/direction and workday, Saturday or Sunday/holiday for a full hourly timetable. The **Pojazd** value is a fleet number, not a line number. |
| ZTP notices | Read the scrolling ZTP/active-disruption ticker or tap it to open the corresponding notice/details. It scrolls at 36 dp/s, 20% faster than the previous default. **Komunikaty MPK** and the MPK Kraków X quick action provide additional information. |
| Weather | Tap **Pogoda** for current conditions and the next 24 hours near your location. The view includes temperature, precipitation, wind and soil temperature. Pressure is omitted; the hourly soil-temperature/wind line remains compact. |
| Radio | Use **Uruchom radio** / **Zatrzymaj radio**, choose a station, or use the gear button to manage streams. Android media controls remain available while playing. |
| Commute reminders | Save a home/work schedule and tap **Włącz powiadomienia**. Grant notification and background-location permissions when requested. A reminder is scheduled 15 minutes before the calculated departure. |
| Timetable and diagnostics | Settings provides **Odśwież rozkład GTFS** and a bounded search diagnostic log with copy/clear actions. The automatic updater also checks the timetable at startup and daily. |
| Sharing | Settings contains a QR code and a link to GitHub Releases, plus contact and release-detail actions. |

At city-wide map zoom (level 13 or lower), unselected stops are 5 dp dots. They
grow smoothly between zoom levels 13 and 15 and regain their previous 14 dp size
at level 15 or higher. The selected stop keeps its 18 dp highlight at every zoom.
Scaling responds to zoom gestures and buttons without removing stops or changing
the map center.

The main screen hides quick actions while typing to leave room for suggestions.
The app and its launch screen follow the phone's light/dark mode; Android 12 and
newer also use the system dynamic color palette. There is no separate in-app
color-profile selector.

## Permissions, network and stored data

- Foreground location is used for nearby-stop/origin actions, Live and weather.
  If location is denied, a manually entered origin remains usable and the Live
  map retains its Kraków starting view.
- Background location and notifications are needed for commute reminders.
  Radio playback uses an Android media foreground service.
- Internet is needed for GTFS imports, live data, notices, map tiles, weather,
  radio streams and application updates. These requests use the active network;
  downloading timetables, map tiles or audio may consume mobile data.
- Timetables, destinations, shortcut names, schedules, recent selections and
  radio preferences are stored in the app's local database/preferences. Android
  backup is enabled, so system backup settings can affect copies of app data.
- Weather requests send the selected current coordinates to Open-Meteo. Address
  lookup uses Android's device-provided geocoder. Map requests go to the tile
  provider; these features are not entirely offline.
- The diagnostic log is kept in memory, is bounded, and does not record entered
  addresses or GPS coordinates. Weather and tram-line lookup caches are also
  in memory, not permanent location histories. Matching bus/tram GTFS archives
  are cached in the app's cache directory for an hour; they contain public
  schedules, not the user's location history.

## Implementation status

- The Room v6 database stores GTFS stops, routes, trips, stop times, route
  shapes, service calendars, date exceptions, and a materialized index of
  adjacent trip connections. The importer downloads the official Kraków feed
  to a temporary cache file, reports download/import/indexing progress, and
  replaces its data transactionally in batches. Existing databases build the
  connection index after migration instead of doing expensive connection
  joins on every search.
- The route planner filters services for the requested date, scans departures
  from the indexed connections within a two-hour window, requires at least
  three minutes when changing
  vehicles, and includes walking transfers between stops up to 500 meters.
  Its geographic grid limits footpath generation to nearby stops. Static route
  results are displayed before the separate live-departure request completes.
- The TTSS client maps stops by stop code or supplied normalized stop-name
  mapping, applies reported delay to static departures, and returns explicitly
  marked static estimates when live data is unavailable. Positive live delays
  appear as red minute badges beside departure estimates.
- The "Live" quick action displays ZTP bus and tram vehicle-position feeds on
  an OpenStreetMap map, refreshing positions and selected-trip predictions
  every 30 seconds while the map is open. Vehicle markers show the line number
  and a direction arrow when ZTP reports a bearing. Tap a vehicle to highlight
  its whole GTFS route and browse all stops from start to terminus, with arrival
  time, minutes remaining, passed/current-stop status and ZTP real-time updates
  when available. The list starts at the current stop but can be scrolled back.
  Scheduled
  estimates and live delays are clearly distinguished. The map supports pan
  and pinch/button zoom. It opens centered on Kraków, then centers and marks
  the user's current location when location permission is granted; without
  permission it stays centered on Kraków. Bus line IDs are matched to known
  line names; tram line numbers are resolved from `GTFS_KRK_T.zip`, whose trip
  IDs match the tram position feed (unlike the combined `GTFS_KRK.zip` used by
  the route planner). The tram mapping is cached in memory for an hour, and
  unavailable line numbers are reported explicitly. Fleet numbers shown as
  "Pojazd" are separate from line numbers. Map tiles and vehicle positions
  include visible source attribution. Trip details use the matching `GTFS_KRK_A.zip`
  or `GTFS_KRK_T.zip`, not the planner database. Both consumers share a disk
  archive cache, and a missing trip triggers one forced refresh before reporting
  an error. Arrival timestamps take priority over delay values; delays propagate
  to following stops according to GTFS-Realtime. Skipped, canceled and no-data
  statuses do not invent arrival predictions. Live data older than 90 seconds
  falls back to explicitly labeled scheduled times.
  Stop markers scale from small city-view dots to their normal close-up size;
  vehicle and user-location markers retain their existing sizes.
- Selecting a stop in Live loads its full timetable from the local planner GTFS
  without waiting for TTSS. Choose the line and destination, then workday,
  Saturday or Sunday/holiday. Hours/minutes include the whole service day;
  `(+1)` indicates after-midnight times on the following calendar day. A concrete
  representative date from the next 35 days is shown for each day type, applying
  the GTFS calendar and date exceptions. Polish public holidays are classified
  separately from workdays. This is a scheduled board, not a live prediction;
  unusual dates and route variants may differ from the displayed pattern.
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
  The ZTP ticker scrolls at 36 dp per second, 20% faster than Compose's default.
- The launch screen and window background follow the same system light/dark
  profile as Compose, including system tonal colors on Android 12 and newer.
  System bar icons keep the matching contrast; when Compose starts, the window
  background is synchronized with its exact color scheme.
  The supplied thrush-and-route artwork is cropped locally and used for the
  launcher icon, startup logo and this documentation. Transparent corners and
  safe margins preserve the full emblem under Android's icon/splash masks.
  The startup logo adds no artificial loading delay.
- Address routes compare up to six nearby stop-name groups within 1.5 km.
  Walking to the first stop, between transfer stops, and from the final stop
  is included in door-to-door arrival estimates; results include the fastest
  option and a lower-walking alternative. Distances are Haversine estimates
  at 4.5 km/h, not pedestrian-road directions.
- The route form can plan a one-time departure or latest arrival by date and
  time. The startup origin defaults to the current location when permission is
  granted, and the quick actions open the official ZTP/KMK service notices.
- The weather quick action requests foreground location only when tapped and
  shows current conditions plus an hourly 24-hour forecast from Open-Meteo,
  including precipitation amounts and probability, wind, and near-surface soil
  temperature. Pressure is omitted from the weather view to keep the hourly
  soil-temperature/wind details compact. Weather codes include fog and
  thunderstorms.
  Forecasts are cached in memory for 15 minutes by approximate location; no
  weather location is stored. The weather view includes a compact legend,
  credits Open-Meteo, and links its CC BY 4.0 license.
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
  Home/work shortcut names are editable and persisted independently of their
  destinations and schedules; the names also appear in schedule headings and
  commute notifications. Existing installations retain "Do domu"/"Do pracy"
  until the names are changed.
  The test app checks the latest prerelease; the production app checks the
  latest stable release. The app checks for updates at startup and offers
  "Pobierz i zainstaluj" or "Pomiń"; skipping suppresses that release until a
  newer version appears. It downloads the APK from the matching GitHub release,
  verifies that it belongs to this app, then opens Android's installer. Android
  may require permission to install apps from this source and always asks the
  user to confirm installation. The GitHub release page remains available for
  details. If no matching APK release has been published, the updater reports
  that none is available. Radio settings support managing streams, and the
  radio action toggles playback.
- TTSS predictions are matched to static departures at the selected origin;
  unmatched or unavailable live data remains visibly static.
- Settings include an in-memory, bounded search diagnostic log with copy and
  clear actions. It reports database/index/CSA stages and counts without
  recording addresses or GPS coordinates.

## Limitations and troubleshooting

- After the initial import, the stored timetable can support static route
  planning without a new download. Live departures, vehicle positions, notices,
  map tiles, weather and radio still depend on their external services.
- The planner scans a two-hour departure window. Address routing examines up to
  six nearby stop groups within 1.5 km, requires at least three minutes to transfer
  and considers inter-stop walks up to 500 m. It is not an exhaustive journey
  planner for every possible connection.
- Walking distances use straight-line Haversine estimates at 4.5 km/h, not
  pedestrian street routing; obstacles and actual walking times can differ.
- The combined planner feed and bus/tram realtime feeds use different IDs; Live
  trip details therefore use the matching A/T archive. A newly changed or special
  trip may still be absent from ZTP's published archive; this is reported explicitly
  after refreshing. Without shape data, the map joins the trip's stops and labels
  the line as approximate. The stop board depends on the imported planner timetable
  being current; update the database in Settings if it is outdated.
- Missing live/line metadata is shown as an unavailable/incomplete-data warning.
  Scheduled estimates are not presented as confirmed live data.
- If location is unavailable, enable phone location services and check the app's
  permission. Address search also depends on geocoder availability on the device.
- Commute reminder delivery depends on permissions, Android background scheduling
  and available data; it is not a guaranteed alarm.
- If an update cannot install, check its channel, package, signing certificate,
  version and Android installation permission. Do not remove the app or its data
  merely to bypass a signing mismatch.
- Weather text may wrap on narrow displays or at larger accessibility font sizes;
  pressure removal shortens the details without hiding the remaining values.

## Data sources and credits

| Data | Source |
| --- | --- |
| Planner timetable | [ZTP combined GTFS](https://gtfs.ztp.krakow.pl/GTFS_KRK.zip) |
| Live vehicles and trip schedules | [ZTP GTFS-Realtime feeds](https://gtfs.ztp.krakow.pl/), matching [bus GTFS](https://gtfs.ztp.krakow.pl/GTFS_KRK_A.zip) and [tram GTFS](https://gtfs.ztp.krakow.pl/GTFS_KRK_T.zip) |
| Stop departure predictions | [Kraków TTSS](http://www.ttss.krakow.pl/) |
| Service notices | [ZTP notices feed](https://ztp.krakow.pl/feed?post_type=komunikat) |
| Map | [OpenStreetMap contributors](https://www.openstreetmap.org/copyright) |
| Weather | [Open-Meteo](https://open-meteo.com/), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |
| Radio | The providers of the configured station streams |

ZTP GTFS-Realtime and Open-Meteo data include CC BY 4.0 attribution in the app.
Data-source licenses are separate from the application's source-code licensing.
External data availability and accuracy remain the responsibility of the providers.

## Local development and validation

The [app build configuration](./app/build.gradle.kts) targets Java 17 and Android
SDK 36, with Android 8.0/API 26 as its minimum. Use the checked-in Gradle wrapper,
a JDK compatible with the project, Android SDK Platform 36 and Build Tools 36.0.0.
The [CI workflow](./.github/workflows/release.yml) uses Java 17.

Windows/PowerShell example:

```powershell
git clone https://github.com/WRaven/Drozd-FastRoute-Krakow.git Drozd-FastRoute-Krakow
Set-Location '.\Drozd-FastRoute-Krakow'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Set `ANDROID_HOME` to your actual SDK location if it differs. The local debug APK
is written to `app\build\outputs\apk\debug\app-debug.apk`. Its default local signing
key is not the CI test key; do not use it to update an existing CI-signed test app.

Focused tests for the recent presentation changes:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'pl.wraven.mpkfastroute.feature.search.ServiceNewsTickerTest' `
  --tests 'pl.wraven.mpkfastroute.feature.search.WeatherForecastSheetTest' `
  --tests 'pl.wraven.mpkfastroute.feature.search.LiveStopMarkerSizeTest' `
  --tests 'pl.wraven.mpkfastroute.core.update.GitHubUpdateCheckerTest' `
  --tests 'pl.wraven.mpkfastroute.ui.theme.StartupThemeTest'
```

The suite also covers route planning, GTFS parsing, live-feed mapping, weather
requests and persistent shortcut names. Unit tests do not replace device checks:

1. Install an update over the preceding test build; confirm saved destinations
   and names remain.
2. Force-stop and relaunch in dark mode, then in light mode; check launch colors
   and readable system-bar icons. Check the new logo on the launcher and startup
   screen: the thrush, route and frame should not be cut off.
3. Open Live with/without location permission; check centering and line labels on
   markers and after tapping a vehicle. Zoom out to the whole city, then zoom in:
   stop dots should grow smoothly and shrink again when zooming out; a selected
   stop remains highlighted.
4. Rename both shortcuts, save and restart; check names, destinations and schedules.
5. Check ticker movement and the weather soil-temperature/wind line on the phone,
   including a larger font setting.
6. Tap a tram (for example line 22) and a bus: check the whole route, scroll from
   the first to last stop, and compare arrival time/minutes with LIVE or ROZKŁAD
   labels. Reopen the same trip and check that passed stops remain accessible.
7. Tap a stop (for example Sołtysowska Zakłady): choose a line/direction and each
   day type. Scroll the hours to the end; check evening and after-midnight entries,
   the reference date, and an explicit message for lines not running that day.

## Versioning and in-place updates

- Releases use semantic version tags: `vMAJOR.MINOR.PATCH` (for example,
  `v1.2.0`). The tag becomes the Android `versionName`.
- GitHub Actions assigns the workflow run number as Android `versionCode`.
  Gradle reads `APP_VERSION_NAME` and `APP_VERSION_CODE` from the release
  workflow and uses `git describe --tags --always` for local builds.
- The permanent Android application ID is `pl.wraven.mpkfastroute.debug`,
  retained from the installed test app so 1.0.0 preserves user data. The
  internal Kotlin namespace remains `pl.wraven.mpkfastroute`. The `.debug`
  suffix does not make a release APK debuggable or require "test" in its name.
- All releases must keep the same Android `applicationId` and be signed with
  the same release key. Changing either prevents Android from installing an
  APK over the existing app.
- The release workflow deliberately fails unless the four signing secrets
  below are configured. It never silently publishes an unsigned or differently
  signed APK.

The existing signing key is now the permanent key for both compatible stable
and test updates. GitHub Actions deliberately reuses the already configured
secrets below; do not generate a replacement key for 1.0.0:

| Secret | Value |
| --- | --- |
| `TEST_KEYSTORE_BASE64` | Base64 encoding of the existing keystore file |
| `TEST_KEYSTORE_PASSWORD` | Existing keystore password |
| `TEST_KEY_ALIAS` | Existing signing key alias |
| `TEST_KEY_PASSWORD` | Existing signing key password |

Local release builds still read `ANDROID_KEYSTORE_FILE`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.
Point them to the same permanent key. CI maps the existing secret values to
those build inputs without changing the secrets themselves.

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

Main-branch debug APKs still appear as "Drozd FastRoute (test)" and check
prereleases. Release APKs appear as "Drozd FastRoute", are not debuggable and
check stable releases. Channel selection uses the build type (`BuildConfig.DEBUG`),
not the retained package suffix. Both builds share local data and the same
permanent signing identity; they are not separate applications.
If signing secrets are missing, main-branch CI still validates but does not upload
a test APK artifact, while release CI fails. Pull-request builds are for
validation only and do not publish APK artifacts.

Main-branch CI validates the app and uploads a signed test APK artifact when the
stable test secrets are available; it does not automatically create a test release.
To make a verified artifact available on a phone and in the test app updater,
publish it as a public, non-draft prerelease with a versioned APK asset. Do not
substitute a locally signed APK. The updater derives the CI version from the asset
name (for example, `Drozd-FastRoute-Krakow-v0.1.0-ci.N-buildN-debug.apk`) and selects
the highest compatible-channel version rather than relying on release-list order.
Previously published APK names remain supported. New Actions artifacts use
`Drozd-FastRoute-Krakow-ci-build-N`.

For the initial transition to stable 1.0.0, attach the verified release APK both
to the stable `v1.0.0` release and to the non-draft `upgrade-v1.0.0` prerelease.
The latter lets old installations discover the same APK without changing their
old updater code. Do not substitute a debug APK for this transition.

## Repository rename and compatibility

The same GitHub repository was renamed from `WRaven/MPK-Krakow-FastRoute` to
[WRaven/Drozd-FastRoute-Krakow](https://github.com/WRaven/Drozd-FastRoute-Krakow)
after installation of stable 1.0.0 was confirmed. Existing releases, history,
Actions configuration and signing secrets remain in that repository.

The git remote, documentation, and primary updater/QR URLs now use the new name.
The installed 1.0.0 updater follows GitHub's redirect from the old API address
and already trusts downloads from both exact repository paths. Version 1.0.1
uses the new address directly and retains this compatibility.

Do not reuse the old repository name: GitHub's redirects depend on it remaining
unused. Do not create a replacement repository or regenerate signing keys.
See [GitHub's repository-renaming guidance](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository).

Earlier APKs only trust the old release-download path. GitHub redirects alone do
not make their updater accept assets under the new name; users still running
those versions may need to install a compatible bridge APK manually.

Keep Android application IDs, signing keys, database names and preference keys
unchanged throughout this migration. The visible brand and repository name are
separate from the permanent Android identity. The unofficial label clarifies the
project's independence; it is not a guarantee of trademark clearance.
