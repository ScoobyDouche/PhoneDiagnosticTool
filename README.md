# Phone Diagnostic Tool

A modern, privacy-focused Android app that provides comprehensive hardware and system diagnostics for your phone.

**All processing happens on-device. No analytics or tracking.** Network permission is used only for optional latency checks.

## Features

- **Device Overview**: Model, manufacturer, brand, Android version, API level, build info, uptime
- **CPU / SoC**: Core count, architecture, supported ABIs, hardware name, processor details
- **GPU**: Renderer and vendor information (via OpenGL ES)
- **Battery**: Level, status, health, temperature, voltage, technology, power source
- **Memory (RAM)**: Total and available memory
- **Network**: Connection type (Wi-Fi / Cellular / etc.) + live latency (TCP connect RTT to 8.8.8.8:53)
- **Storage**: Internal storage total / free / used
- **Display**: Resolution, density, refresh rate, screen metrics
- **Live updates**: Battery, RAM, network latency, and uptime refresh every 2 seconds
- Pause / resume live monitoring and manual refresh
- Clean Material 3 dashboard with cards

## Network Latency

- Detects active network type via `ConnectivityManager`
- Measures TCP connect time to Google Public DNS (`8.8.8.8:53`) with a 3s timeout
- Shows latency in milliseconds, target host, and status (OK / Timeout / No network / Error)
- Updates live along with battery and RAM

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- ViewModel + StateFlow + coroutines
- Minimum SDK 26 (Android 8.0)
- Target SDK 35

## Permissions

- `INTERNET` – required only for latency measurement
- `ACCESS_NETWORK_STATE` – to detect connection type

No other sensitive permissions are used.

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer recommended
- JDK 17

### Build & Run
1. Clone this private repository
2. Open the project in Android Studio
3. Let Gradle sync
4. Connect a device or start an emulator (API 26+)
5. Click Run

### Project Structure
```
app/
  src/main/
    java/com/phonediagnostic/
      MainActivity.kt
      data/          # Hardware info collectors + models
      ui/            # Compose screens, ViewModel, components, theme
    AndroidManifest.xml
    res/
```

## Privacy

- 100% on-device processing
- No analytics or crash reporting that phones home
- Network access is used solely to measure latency to a public DNS server

## Extending the App

Easy next additions:
- Multi-host latency (1.1.1.1, etc.) and average/min/max
- Live CPU frequency / usage
- Battery current (mA) and power (W)
- Sensor list + live readings
- Hardware tests (touch, vibration, etc.)
- Report export (JSON / share)

## License

Private project – all rights reserved by the repository owner.
