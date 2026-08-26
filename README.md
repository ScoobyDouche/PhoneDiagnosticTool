# Phone Diagnostic Tool

A modern, privacy-focused Android app that provides comprehensive hardware and system diagnostics for your phone.

**All processing happens on-device. No analytics or tracking.** Network permission is used only for optional latency checks.

## Download the APK (no PC required)

This repo has a **GitHub Actions** workflow that builds a debug APK on GitHub’s servers.

1. Open the repo: https://github.com/ScoobyDouche/PhoneDiagnosticTool  
2. Go to the **Actions** tab  
3. Select the **Build APK** workflow  
4. Open the latest successful run  
5. Under **Artifacts**, download **PhoneDiagnostic-debug**  
6. Unzip it → you’ll get an `.apk` file  
7. On your phone: allow install from unknown sources for your browser/files app, then open the APK to install  

You can also start a build manually: **Actions → Build APK → Run workflow**.

Artifacts are kept for 14 days.

## Features

- **Device Overview**: Model, manufacturer, brand, Android version, API level, build info, uptime
- **CPU / SoC**: Core count, architecture, supported ABIs, hardware name, processor details
- **GPU**: Renderer and vendor information
- **Battery**: Level, status, health, temperature, voltage, technology, power source
- **Memory (RAM)**: Total and available memory
- **Network**: Connection type + live latency (TCP to 8.8.8.8:53)
- **Storage**: Internal storage total / free / used
- **Display**: Resolution, density, refresh rate, screen metrics
- **Live updates**: Battery, RAM, network latency, and uptime every 2 seconds
- Pause / resume and manual refresh

## Tech Stack

- Kotlin · Jetpack Compose · Material 3
- ViewModel + StateFlow + coroutines
- Min SDK 26 · Target SDK 35

## Permissions

- `INTERNET` – latency measurement only
- `ACCESS_NETWORK_STATE` – connection type

## Build locally (optional)

1. Clone this private repository
2. Open in Android Studio
3. Sync Gradle → Run on a device/emulator (API 26+)

## Privacy

- On-device processing only
- No analytics
- Network used only to measure latency to public DNS

## License

Private project – all rights reserved by the repository owner.
