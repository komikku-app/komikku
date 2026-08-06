# Comicks v1.14.2 Release Notes

## Highlights
- **New Feature**: "Clean invalid downloads" added to Advanced settings. This tool finds and removes download folders and files that are no longer in your library, helping you save storage space.
- **Android 16 Support**: Updated target SDK to 36 to support the latest Android features and security improvements.
- **Telemetry Fix**: Corrected Firebase Analytics and Crashlytics initialization for `app.comick` package, ensuring proper error reporting in production builds.

## What's Changed
### Added
- New Comick-specific strings for UI elements (Panorama cover, Suggestions, Discord RPC).
- Interactor for cleaning invalid downloads.
- Macrobenchmark and Baseline Profile updates for better performance tracking.

### Fixed
- Build configuration issues in `app/build.gradle.kts` (lint and semantic errors).
- Telemetry package name matching logic.

### Technical
- Updated build logic to use JDK 17 and Kotlin JVM 17.
- Cleaned up build script dependencies and configurations.

---

## GitHub Release Text
Paste the following into your GitHub release description:

```markdown
[![GitHub downloads](https://img.shields.io/github/downloads/devil6venom/Comick/v1.14.2/total?label=Downloaded&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/devil6venom/Comick/releases/v1.14.2)

#### What's Changed
##### New
- Added **"Clean invalid downloads"** in Advanced settings to remove orphaned download folders.

##### Improve
- Updated **Target SDK to 36 (Android 16)** support.
- Updated Macrobenchmarks and Baseline Profile generators for better performance optimization.
- Added various Comick-specific translations and UI strings.

##### Fix
- Fixed telemetry initialization for `app.comick` package names.
- Cleaned up build configuration and fixed lint errors in `app/build.gradle.kts`.

##### Based on
**Full Changelog**: [devil6venom/Comick@v1.14.1...v1.14.2](https://github.com/devil6venom/Comick/compare/v1.14.1...v1.14.2)

> [!TIP]
> ### If you are unsure which version to download then go with `Comicks-v1.14.2.apk`
```
