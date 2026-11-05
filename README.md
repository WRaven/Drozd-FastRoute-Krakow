# MPK Kraków FastRoute

Android app for searching Kraków public-transport connections, showing live
departure information, and playing radio streams. The implementation follows
the architecture and phases in the project specification.

## Create the public GitHub repository

After authenticating GitHub CLI, create and publish this local repository:

```powershell
gh auth login --hostname github.com --web
gh repo create WRaven/MPK-Krakow-FastRoute --public --source=. --remote=origin --push
```

The Android Gradle project and `gradlew` are added in Phase 1. The tag-triggered
APK release workflow becomes runnable once that phase is in place.
