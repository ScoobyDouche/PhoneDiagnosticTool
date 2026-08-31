# Phone Diagnostic Tool

Privacy-focused Android diagnostics: CPU, GPU, battery, RAM, storage, display, sensors, cameras, and optional network latency — all on-device.

**No accounts. No analytics SDKs. No cloud upload of reports.**

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

## Privacy

See **[PRIVACY.md](PRIVACY.md)**.

Short version: diagnostics run locally. The only network use is an optional latency probe you can turn off.

## Download APK (GitHub Actions)

1. Open **Actions** → **Build APK**
2. Open the latest **green** run
3. Download artifact **PhoneDiagnostic-debug**
4. Unzip and install the `.apk` (allow install from unknown sources)

Debug builds use a fixed CI keystore so updates can install over previous CI builds.

## Build from source

**Requirements:** JDK 17, Android SDK 35

```bash
git clone https://github.com/ScoobyDouche/PhoneDiagnosticTool.git
cd PhoneDiagnosticTool
# Optional: decode CI debug keystore for local parity
# base64 -d keystore/debug.keystore.b64 > keystore/debug.keystore
gradle assembleDebug   # or open in Android Studio
```

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
- Version **1.12.0**

Diagnostics are stored only on the device: a rotating log and a 24 h metric
history live in internal storage, and the app opts out of Android cloud backup
and device transfer entirely (`allowBackup="false"`).

## License

[MIT](LICENSE)
