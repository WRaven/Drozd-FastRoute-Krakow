# MPK Kraków FastRoute

Android app for searching Kraków public-transport connections, showing live
departure information, and playing radio streams. The implementation follows
the architecture and phases in the project specification.

## Implementation status

- The Room v2 database stores GTFS stops, routes, trips, stop times, service
  calendars, and date exceptions. The importer downloads the official Kraków
  feed and replaces its data transactionally in batches.
- The route planner filters services for the requested date, scans departures
  within a two-hour window, applies a three-minute transfer penalty, and
  includes walking transfers between stops within 150 meters.
- The TTSS client maps stops by stop code or supplied normalized stop-name
  mapping, applies reported delay to static departures, and returns explicitly
  marked static estimates when live data is unavailable.
- Radio streams run in a MediaSession foreground service, support system
  playback controls, and persist the last selected station.
- GPS quick actions, onboarding, and the Compose search interface that wires
  these components together are still to be implemented.

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

Debug APKs attached to CI runs are for testing only; they use Android's debug
signature and cannot be installed as updates to the stable, release-signed APK.
Use APKs from GitHub Releases when verifying in-place updates.
