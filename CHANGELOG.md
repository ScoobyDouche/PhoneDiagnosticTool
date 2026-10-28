# Changelog

All notable changes to Phone Diagnostic Tool are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a practical semantic versioning scheme for a single-app
Android project (`MAJOR.MINOR.PATCH`).

## [Unreleased]

### Added
- Deeper diagnostics: CPU clusters, governors, available frequencies and
  online cores; kernel memory (cached, buffers, active, swap, ZRAM);
  SELinux mode, debuggable build and build tags; sensor reporting mode,
  wake-up and batching; camera ISO / exposure ranges, flash, capabilities
  and physical cameras; readable thermal zone names. All are included in
  text and JSON reports.
- Screens keep their scroll position when you back out of a submenu.
- Privacy audit screen: apps holding camera, microphone, location,
  contacts, SMS, call log and other sensitive grants, plus apps with
  accessibility, notification-listener or device-admin access, and a
  "This app" card explaining each of the tool's own permissions.
- Reports can leave out device identifiers (fingerprint, bootloader,
  baseband, build host, kernel build line); on by default.
- Latency target setting: Cloudflare, Quad9, Google, or the router only.
- Copied reports are marked sensitive and cleared after a minute.
- Optional "Block screenshots" setting (FLAG_SECURE).
- Battery charger rating, drain rate and estimated time left; capacity
  health and cycle count in reports.
- Flash storage type and JEDEC wear / spare-block status.
- System activity: awake vs deep-sleep time, last wake reason and top
  wakeup sources.
- Android thermal status and headroom on the Thermals screen; load test
  reports sustained throughput and flags throttled runs.
- Media codecs screen: hardware vs software playback and recording per
  format, with max resolution and frame rate.
- Display modes and HDR peak brightness; Wi-Fi standard; cellular
  generation and signal (no permission, no cell identifiers); security
  patch age and StrongBox / hardware keystore support.
- Gradle wrapper (`./gradlew`), so the build instructions work as written.

### Changed
- More tab is more compact: links are grouped per section and the six
  export actions are one card of buttons.
- The latency check now defaults to Cloudflare (1.1.1.1) instead of
  Google (8.8.8.8).

### Fixed
- CPU topology is derived from each cluster's peak clock instead of the
  current clocks, and governors / frequencies cover every cluster rather
  than stopping at the first offline core.
- Background monitor, Quick Settings tile and live refresh no longer
  re-read kernel memory stats on every sample.

## [1.3.2] — 2026-10-27

### Fixed
- Serialise live / refresh / elevated collection with a `Mutex` so concurrent
  collects cannot race on report state or the elevated shell reference.
- Re-sync the background-monitor Settings toggle when returning to the app
  after stopping the monitor from the notification.
- Only track successfully registered sensor listeners for cleanup.
- Document battery current sign convention and show charging/discharging
  direction next to the mA reading.

## [1.3.1] — 2026-10-26

### Security
- **Private release key now in use.** GitHub Releases from this version onward
  are signed with the private release keystore (the four `RELEASE_*` secrets).
  The workflow continues to refuse a public-key fallback unless
  `allow_public_key_signing` is explicitly ticked on a dispatch. Existing users
  on public-key builds must uninstall once before installing a private-key
  build.

### Changed
- Dependency refresh (kept compatible with AGP 8.7.2 + compileSdk 35):
  - Compose BOM **2024.10.01 → 2025.10.01**
  - Kotlin **2.0.21 → 2.1.21**
  - `core-ktx` 1.15.0 → 1.16.0
  - Lifecycle 2.8.7 → 2.9.4
  - `activity-compose` 1.9.3 → 1.10.1
  - Coroutines 1.9.0 → 1.10.2
- README tightened and updated for private-key signing and 1.3.1.
- Security audit doc updated: finding 1b marked resolved.

### Fixed / Internal
- Unit tests expanded: `BatteryInfo.powerWatts` edge cases (null current,
  non-positive voltage, charging vs discharging polarity, zero current).

## [1.3.0] — 2026-10-06

### Added
- **Connectivity & audio** on Overview: NFC and Bluetooth present/on, connected
  audio device kinds (no Bluetooth name/address).
- **Security**: verified-boot, bootloader lock, encryption where exposed.
- Network detail and latency burst (min/avg/max/jitter/loss).

### Changed
- Background monitor samples only battery and memory.
- Storage speed read figure labelled *may be cached*.

## [1.2.1] — 2026-09

### Fixed
- Document Android 13+ restricted-settings path for Usage Access after sideload.

## [1.2.0] — 2026-09

### Added
- Optional elevated access via Shizuku or root for gated sysfs diagnostics.

## Earlier

See git tags and prior release notes on GitHub Releases for 1.1.x and 1.0.0.
