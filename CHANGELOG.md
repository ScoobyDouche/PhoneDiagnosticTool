# Changelog

All notable changes to Phone Diagnostic Tool are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows a practical semantic versioning scheme for a single-app
Android project (`MAJOR.MINOR.PATCH`).

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
- **Connectivity & audio card** on the Overview: whether NFC and Bluetooth are
  present and switched on, and which kinds of audio device are connected
  (speaker, wired, Bluetooth, USB…). No new permissions; the Bluetooth name and
  address are never read.
- **Security card**: verified-boot state, bootloader lock, and encryption where
  the platform exposes them.
- Network detail and latency burst improvements.

### Changed
- Background monitor samples only battery and memory (cheaper).
- Storage speed test read figure labelled *may be cached*.

## [1.2.1] — 2026-09

### Fixed
- Document Android 13+ restricted-settings path for Usage Access after sideload.

## [1.2.0] — 2026-09

### Added
- Optional elevated access via Shizuku or root for gated sysfs diagnostics.

## [1.1.2] and earlier

See git history for prior release notes.
