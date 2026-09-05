# Changelog

All notable changes to Phone Diagnostic Tool are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a practical semantic versioning scheme for a single-app
Android project (`MAJOR.MINOR.PATCH`).

## [1.1.3] — 2026-09-05

### Security
- **Releases now ship the release build, not the debug build.** The published
  APK carried `android:debuggable`, the Compose tooling libraries and no
  minification — 16.9 MB against 1.2 MB. The signing key is unchanged, so this
  installs over 1.1.2 in place. See [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md);
  the key itself being public is tracked there and not addressed by this release.

### Added
- **Battery health.** Remaining capacity as a percentage of the factory rating,
  read from the fuel gauge. Reported only when the device exposes both figures
  and the ratio is plausible — most phones deny apps access, and the screen says
  so rather than estimating.
- **Charging power.** Live watts, in or out, from voltage x current. Answers
  whether a charger or cable is actually delivering.
- **Storage speed test** in Tools. Writes and reads back a 64 MB file in the app
  cache and reports sequential throughput, then deletes it. Uses varied bytes so
  a compressing layer cannot flatter the result, and `fsync`s so the write figure
  is the flash rather than the page cache.
- **Quick Settings tile** showing battery temperature and RAM use. Samples only
  battery and memory, never a full collect.

### Fixed
- The Storage screen was never wired to string resources despite 37 being
  defined for it, so it stayed English regardless of device language. The 1.1.0
  claim of full localisation was wrong about that one screen.
- Sensor detail numbered its fourth and later axes with a hardcoded label.

### Changed
- Version bumped to **1.1.3** (versionCode **30**).

## [1.1.2] — 2026-09-03

### Fixed
- The source-repository link on the About screen did nothing when tapped. It was
  styled as a link — primary colour, a URL for its text — but no click handler
  had ever been attached, so it was decoration. Two defects, in fact: the
  displayed string is deliberately scheme-less (`github.com/...`) because it
  reads better, and `Uri.parse` on a scheme-less string yields a relative URI
  that resolves to nothing, so even a wired-up tap would have failed silently.
  The link now opens in a browser, with a separate `about_source_link` string
  holding the full `https://` URL.

### Changed
- Version bumped to **1.1.2** (versionCode **29**).
- The link gets an underline, a 48dp minimum touch target, and an
  `onClickLabel` so screen readers announce the action while still reading out
  the address.
- If no browser can handle the intent (kiosk builds, some AOSP images), the URL
  is copied to the clipboard instead of the tap dying silently.

### Internal
- Audited the rest of the app for the same pattern; this was the only dead link.
  `about_source_url` was the only URL string present, and the other
  `colorScheme.primary` text uses are chart colours, not link styling.

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

### Internal
- New `Release` workflow: one `workflow_dispatch` (or a pushed `v*` tag) runs the
  tests, builds the artifacts and publishes the tagged GitHub Release with the
  binaries attached. It verifies the requested version against
  `versionName` and refuses to reuse an existing tag.
- Release bodies now live in `docs/release-notes/`, falling back to the matching
  `CHANGELOG.md` section.

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
