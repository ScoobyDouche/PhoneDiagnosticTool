# Phone Diagnostic Tool

A modern, privacy-focused Android app that provides comprehensive hardware and system diagnostics for your phone.

**All processing happens on-device. No data collection, no tracking, no internet required.**

## Features

- **Device Overview**: Model, manufacturer, brand, Android version, API level, build info, uptime
- **CPU / SoC**: Core count, architecture, supported ABIs, hardware name, processor details
- **GPU**: Renderer and vendor information (via OpenGL ES)
- **Battery**: Level, status, health, temperature, voltage, technology, power source
- **Memory (RAM)**: Total and available memory
- **Storage**: Internal storage total / free / used
- **Display**: Resolution, density, refresh rate, screen metrics
- Clean Material 3 dashboard with cards
- Designed for extension (sensors, network, real-time monitoring, export, tests)

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Coroutines
- Minimum SDK 26 (Android 8.0)
- Target SDK 35

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
      data/          # Hardware info collectors
      ui/            # Compose screens & components
      ui/theme/      # Material 3 theme
    AndroidManifest.xml
    res/
```

## Permissions

The app requests only the minimum necessary permissions. Battery and system info use public APIs that require no special permissions on modern Android.

## Privacy

- 100% on-device
- No analytics, no crash reporting services that phone home
- No internet permission in the base version

## Extending the App

The code is structured so you can easily add:
- Live CPU frequency / usage monitoring
- Battery current (mA) and power (W) tracking
- Sensor list + live readings
- Network details
- Hardware tests (touch, vibration, etc.)
- Report export (JSON / share)

## License

Private project – all rights reserved by the repository owner.
