# Phone Diagnostic Tool

Privacy-focused Android diagnostics: CPU, GPU, battery, RAM, storage, display, sensors, cameras, and optional network latency — all on-device.

**No accounts. No analytics SDKs. No cloud upload of reports.**

[![Latest release](https://img.shields.io/github/v/release/ScoobyDouche/PhoneDiagnosticTool)](https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases/latest)
[![Build APK](https://github.com/ScoobyDouche/PhoneDiagnosticTool/actions/workflows/build-apk.yml/badge.svg)](https://github.com/ScoobyDouche/PhoneDiagnosticTool/actions/workflows/build-apk.yml)

## Screenshots

<p>
  <img src="docs/screenshots/overview.png" alt="Overview" width="200" />
  <img src="docs/screenshots/battery.png" alt="Battery" width="200" />
  <img src="docs/screenshots/network.png" alt="Network" width="200" />
  <img src="docs/screenshots/sensors.png" alt="Sensors" width="200" />
</p>

> Add PNGs under `docs/screenshots/` (see that folder’s README). Until then, install the [latest release](https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases/latest) and capture the real UI on a device.

## Features

- Device overview (model, Android version, security patch, fingerprint, board, bootloader, uptime)
- CPU / SoC with best-effort current core frequencies and min/max range
- GPU (OpenGL ES)
- Live battery, RAM, and network status
- Sensors list + brief live samples (accelerometer, gyro, light, proximity, etc.)
- Tap any sensor to stream it live with per-axis charts
- Camera characteristics (facing, pixel array, focal lengths, hardware level)
- Optional TCP latency check to `8.8.8.8:53` (can be disabled in Settings)
- Network detail: IP addresses, DNS & private DNS, Wi‑Fi link speed / band / signal, carrier, plus a 5-probe latency burst with min / avg / max / jitter / loss
- Storage volumes + per-app breakdown (with Usage Access)
- Process RAM detail
- History: battery, temperature and RAM trends charted from up to 24 h of samples
- Background monitor with rotating log (capped at **5000** lines)
- CPU load test (1 / 5 / 10 min) with a k-ops/s score
- Share as text or JSON, share as a file attachment, save to a file, or copy to clipboard
- Theme: system / light / dark
- Settings + About (license & privacy summary)
- UI chrome fully externalised to string resources (ready for translation packs)

## Privacy

See **[PRIVACY.md](PRIVACY.md)**.

Short version: diagnostics run locally. The only network use is an optional latency probe you can turn off.

## Download

Distribution is **GitHub Releases** — no store account required.

1. Open the [latest release](https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases/latest)
2. Download the `.apk`
3. Allow install from unknown sources when prompted

Debug builds use a fixed CI keystore, so a new release installs over an older one without uninstalling.

**Bleeding-edge builds** — Actions → **Build APK** → latest green run → artifact **PhoneDiagnostic-debug** (zipped; deleted after 14 days).

## Changelog

See **[CHANGELOG.md](CHANGELOG.md)** for release history.

## Build from source

**Requirements:** JDK 17, Android SDK 35

```bash
git clone https://github.com/ScoobyDouche/PhoneDiagnosticTool.git
cd PhoneDiagnosticTool
# Optional: decode CI debug keystore for local parity
# base64 -d keystore/debug.keystore.b64 > keystore/debug.keystore
gradle test             # unit tests
gradle assembleDebug    # or open in Android Studio
gradle assembleRelease  # exercises R8 + resource shrinking
```

CI runs all three on every push and pull request.

APK output: `app/build/outputs/apk/debug/`

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | Optional latency measurement only |
| `ACCESS_NETWORK_STATE` | Detect Wi‑Fi / cellular / etc.; DNS and link details on the Network screen |
| `ACCESS_WIFI_STATE` | Wi‑Fi link speed, band and signal strength |
| `VIBRATE` | Vibration hardware check under Tools |
| `PACKAGE_USAGE_STATS` | Per-app storage (user must grant Usage Access) |
| `REQUEST_DELETE_PACKAGES` | Uninstall from storage detail |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | Optional background monitor |
| `POST_NOTIFICATIONS` | Background monitor notification (Android 13+) |

Camera and sensors use system APIs without requesting CAMERA permission (characteristics only; no capture).

## Tech

- Kotlin, Jetpack Compose, Material 3
- ViewModel + StateFlow
- Min SDK 26 · Target / compile SDK 35
- Version **1.1.0**

Diagnostics are stored only on the device: a rotating log and a 24 h metric
history live in internal storage, and the app opts out of Android cloud backup
and device transfer entirely (`allowBackup="false"`).

## License

[MIT](LICENSE)
