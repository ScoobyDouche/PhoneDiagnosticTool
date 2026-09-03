# Changelog

All notable changes to Phone Diagnostic Tool are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a practical semantic versioning scheme for a single-app
Android project (`MAJOR.MINOR.PATCH`).

## [1.1.1] — 2026-09-03

### Fixed
- Per-app storage breakdown returned almost nothing on Android 11 and newer.
  Since API 30 the platform filters `getInstalledApplications()` down to
  packages the caller can already see, and the app declared neither
  `QUERY_ALL_PACKAGES` nor a `<queries>` element while targeting API 35 — so
  the Storage screen could list little beyond Phone Diagnostic itself. Added
  `QUERY_ALL_PACKAGES`.

### Changed
- Version bumped to **1.1.1** (versionCode **28**).
- README permission table and the privacy policy document the new permission.

### Notes
- `QUERY_ALL_PACKAGES` is a restricted permission on Google Play and would need
  a justification form there. This app is distributed through GitHub Releases,
  where that review does not apply; revisit if it is ever submitted to Play.
- The installed-app list is read on demand and displayed only. It is not
  written to the diagnostic log, not part of an exported report, and never
  leaves the device.

## [1.1.0] — 2026-09-02

### Added
- Full extraction of user-facing UI strings into `strings.xml` across every
  screen (Dashboard, More, Tools, Battery, CPU, Sensors, Network, RAM,
  History, Thermals, Sensor detail, Storage, Settings, About).
- Format strings with positional arguments for counts and ranges so future
  translations stay grammatically correct.

### Changed
- Version bumped to **1.1.0** (versionCode **27**).
- README and privacy policy dates refreshed for this release.

### Notes
- Diagnostic log lines and raw metric formats remain English by design so
  shared reports stay consistent regardless of the phone language.
- Only the default (`values/`) locale is shipped; additional language packs can
  be added later without code changes.

## [1.0.0] — 2026-08-31

First stable release. Previously numbered in the 1.11.x range during development;
renumbered to 1.0.0 as the first proper public release (versionCode 26).

### Added
- History screen — battery, temperature and RAM trends from a rolling 24-hour
  on-device store.
- Network detail — interface addresses, DNS / private DNS, Wi-Fi link speed /
  band / signal, carrier details, 5-probe latency burst (min / avg / max /
  jitter / loss).
- Live sensor streaming with per-axis charts.
- Report export — save as `.txt` / `.json`, share as a real file attachment.
- Load test k-ops/s score and peak temperature.
- Unit tests for models and report export; CI builds both debug and release.

### Fixed
- System back button exiting the app from every detail screen.
- Multiple resource leaks (`getprop` processes, sockets, ToneGenerator, EGL).
- Excessive sampling cost during load tests and background monitoring.
- Process memory attribution, duplicate LazyColumn keys, locale decimal bugs.
- Adaptive launcher icon not used; white flash on cold start in dark mode.
- Diagnostic data eligible for cloud backup (now fully opted out).

[1.1.0]: https://github.com/ScoobyDouche/PhoneDiagnosticTool/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases/tag/v1.0.0
