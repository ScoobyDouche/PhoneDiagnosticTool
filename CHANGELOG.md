# Changelog

All notable changes to Phone Diagnostic Tool are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a practical semantic versioning scheme for a single-app
Android project (`MAJOR.MINOR.PATCH`).

## [1.2.0] — 2026-09-12

### Added
- **Optional elevated access (Shizuku or root).** A new *Elevated access* section
  in Settings lets you opt into reading data the platform otherwise keeps behind
  sysfs permissions an ordinary app cannot get. Three tiers:
  - **Off** (default) — unchanged behaviour; nothing elevated is read.
  - **Shizuku** — helper reads run as the shell user (UID 2000), the same reach
    as `adb shell`. No root, nothing permanent to the device. Requires the
    Shizuku app installed and started, and a one-tap grant.
  - **Root** — reads run via `su`, reaching nodes even the shell user cannot.
  The chosen backend is used only as a *fallback* when the direct read is denied,
  so devices that already expose a node are unaffected. First consumers are the
  **battery fuel gauge** (so the capacity-health percentage can appear on devices
  that block it from apps) and **per-core CPU clocks / frequency range** on
  locked-down devices. Settings shows live status (installed / running /
  permission / root detected) and why a tier is or is not active.
- **"Read via Shizuku / root" markers.** Any value that was only obtainable
  through elevated access is tagged as such on the Battery and CPU screens, so
  it is visible which readings the feature actually unlocked on your device.
- **Aggregate `uevent` battery fallback.** When a device blocks the individual
  fuel-gauge nodes but leaves `/sys/class/power_supply/*/uevent` readable (common
  on Samsung), capacity health and charge cycles are parsed from that instead.
  Cycles fall back to the kernel-standard `POWER_SUPPLY_CYCLE_COUNT` when the
  Android 14 broadcast field is absent — which several vendors never populate.
- **System-wide process list via elevated access.** With Shizuku (or root) the
  RAM detail screen shows every running process from `dumpsys meminfo`, with
  per-process CPU load from `dumpsys cpuinfo`, instead of the self-only view
  Android's `hidepid` otherwise limits apps to. Tagged "Read via Shizuku / root".
- **Thermal load-test mode.** A heavier CPU stress option that adds
  transcendental math and strided memory thrashing on top of the FPU loop to
  drive more heat, in addition to now running one worker per core.

### Fixed
- **Multi-touch pad showed only the peak.** It now shows the live count of
  fingers currently down (which falls back as you lift them) alongside the
  session max, instead of appearing stuck at the highest number seen.
- **Load test only used 4 threads.** It now runs one worker per CPU core, so it
  actually saturates the whole processor on 6-, 8- and higher-core devices
  rather than a fixed half of it; the worker loop is also hardened against the
  JIT optimising the synthetic work away.

### Notes
- **Still signed with the public CI key.** Moving to a private release key was
  prepared in 1.1.3 but not carried out, so this release is signed with the key
  whose private half is committed to this repository: anyone can build an APK
  that Android will accept as an update over it. Publishing under that key is now
  a deliberate per-run choice in the release workflow rather than a silent
  fallback, and the release page says so. The switch to a private key will cost
  one uninstall when it happens, since Android does not accept a new certificate
  as an update.
- 1.1.3 was never published, so this release also carries everything listed under
  it above.
- The feature is entirely opt-in and off by default; with neither Shizuku nor
  root present the app behaves exactly as before. Adds the Shizuku client API
  and provider (`dev.rikka.shizuku`); no new runtime network use. Rooting can
  trip a hardware fuse (e.g. Samsung Knox) and break banking/wallet apps — the
  Settings copy says so, and the app only ever reads.

## [1.1.3] — 2026-09-05 (never published)

Prepared but never tagged. These changes first reached users in 1.2.0.


### Security
- **The release workflow refuses to sign with the public key by default.** Every
  release through 1.1.2 was signed with a key committed to this public
  repository, so anyone could build an APK that Android accepts as an update to
  it. The workflow used to fall back to that key whenever the release secrets
  were missing or mistyped, silently and looking like a normal release; it now
  stops instead, and records the signing certificate in the run log. **The key
  itself has not changed** — see the note under 1.2.0.
- **Releases now ship the release build, not the debug build.** The published
  APK carried `android:debuggable`, the Compose tooling libraries and no
  minification — 16.9 MB against 1.2 MB. The signing key is unchanged, so this
  installs over 1.1.2 in place. See [docs/SECURITY-AUDIT.md](docs/SECURITY-AUDIT.md);
  the key itself being public is tracked there and not addressed by this release.

### Added
- **Battery health.** Charge cycle count on Android 14 and newer, from the
  battery-changed broadcast. Plus remaining capacity as a percentage of the
  factory rating where the fuel gauge exposes it — most phones do not, and the
  screen says which route is closed rather than estimating a number. Android
  keeps its own state-of-health percentage as a system API, so no ordinary app
  can read it.
- **Charging power.** Live watts, in or out, from voltage x current. Answers
  whether a charger or cable is actually delivering.
- **Storage speed test** in Tools. Writes and reads back a 64 MB file in the app
  cache and reports sequential throughput, then deletes it. Uses varied bytes so
  a compressing layer cannot flatter the result, and `fsync`s so the write figure
  is the flash rather than the page cache.
- **Quick Settings tile** showing battery temperature and RAM use. Samples only
  battery and memory, never a full collect. Settings gains a one-tap **Add to
  Quick Settings** button on Android 13 and newer, since third-party tiles are
  never added automatically and finding one in the shade's edit screen is the
  step people miss; below 13 it shows where to look instead.

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
