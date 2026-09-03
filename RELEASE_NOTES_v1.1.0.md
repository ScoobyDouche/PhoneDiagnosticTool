# v1.1.0 — full UI localisation

Phone Diagnostic Tool **1.1.0** (versionCode **27**). Builds on the 1.0.0 feature set with complete extraction of user-facing UI text into string resources.

## What’s new

- All screens now load labels, buttons, and messages from `strings.xml` (Dashboard, More, Tools, Battery, CPU, Sensors, Network, RAM, History, Thermals, Sensor detail, Storage, Settings, About)
- Format strings use positional arguments so future translations stay grammatically correct
- App is ready for language packs (`values-xx/`) without further code changes

## Notes

- Diagnostic **log lines** and **raw metric formats** stay English on purpose, so shared reports look the same regardless of phone language
- Only the default (`values/`) locale ships in this build
- No functional behaviour changes beyond localisation and the version bump

## Install

Download the `.apk` below, allow install from unknown sources, and open it. Requires **Android 8.0 (API 26)** or newer.

This build uses the same CI debug keystore as previous releases, so it installs over 1.0.0 without uninstalling.

## Links

- Changelog: [CHANGELOG.md](https://github.com/ScoobyDouche/PhoneDiagnosticTool/blob/main/CHANGELOG.md)
- Privacy: [PRIVACY.md](https://github.com/ScoobyDouche/PhoneDiagnosticTool/blob/main/PRIVACY.md)

**Full compare:** https://github.com/ScoobyDouche/PhoneDiagnosticTool/compare/v1.0.0...v1.1.0
