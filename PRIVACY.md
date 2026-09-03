# Privacy Policy — Phone Diagnostic Tool

**Last updated:** 2026-09-02

Phone Diagnostic Tool is designed to be privacy-first.

## Summary

- All device diagnostics run **on your device**.
- There are **no accounts**, **no analytics SDKs**, and **no crash-reporting SDKs** in the app.
- The only network use is an **optional** latency check.

## Data we collect

**We do not collect, transmit, or store your personal data on any server operated by this project.**

The app reads standard Android system APIs on-device to show:

- Device model and build information
- CPU / GPU / memory / storage / display metrics
- Battery status
- Network type and (if enabled) latency

That information stays on your phone unless **you** choose to share or export a report.

## Network access

Permissions:

- `INTERNET` — used only for the optional latency probe
- `ACCESS_NETWORK_STATE` — used to detect connection type (Wi‑Fi, cellular, etc.), plus DNS servers and IP addresses shown on the Network screen
- `ACCESS_WIFI_STATE` — used to read the current Wi‑Fi link speed, band and signal strength on the Network screen

The Network screen reads this device's own interface addresses and the active
network's DNS settings. That is displayed locally and never uploaded. MAC
addresses are deliberately not shown. The Wi‑Fi network name is only visible if
you have granted a location permission to this app — it does not request one, so
it normally shows as hidden.

When **Network latency check** is enabled (default on, can be turned off in Settings), the app measures TCP connect time to Google Public DNS at `8.8.8.8:53`. No DNS query payload beyond a TCP connect is required for this measurement, and no diagnostic report is uploaded.

When the latency check is disabled, the app does not open network connections for diagnostics — including the repeat-probe button on the Network screen, which is unavailable while it is off.

## On-device storage

Two files are kept in the app's private internal storage:

- a **rotating diagnostic log**, capped at 5000 lines (~0.5–1 MB when full)
- a **metric history** of battery, temperature and RAM samples, capped at 24 hours

Both can be cleared from inside the app, and both are removed when the app is
uninstalled. The app sets `allowBackup="false"` and excludes all of its data
from cloud backup and device-to-device transfer, so neither file leaves the
phone through Android's backup system.

## Sharing and export

If you use Share, Save or Copy, a text or JSON report is handed to the Android
share sheet, the system file picker, or the clipboard. "Share as file" writes
one report into the app's private cache and grants the app you choose temporary
read access to that single file; the previous export is deleted each time.
Where that data goes next is controlled by **you** and the app you pick.

## Children

The app is not directed at children and does not knowingly collect data from children.

## Changes

If this policy changes, we will update this file in the repository and the in-app About section when practical.

## Contact

Open an issue on the project GitHub repository for privacy questions.
