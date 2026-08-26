# Privacy Policy — Phone Diagnostic Tool

**Last updated:** 2026-08-26

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
- `ACCESS_NETWORK_STATE` — used to detect connection type (Wi‑Fi, cellular, etc.)

When **Network latency check** is enabled (default on, can be turned off in Settings), the app measures TCP connect time to Google Public DNS at `8.8.8.8:53`. No DNS query payload beyond a TCP connect is required for this measurement, and no diagnostic report is uploaded.

When the latency check is disabled, the app does not open network connections for diagnostics.

## Sharing and export

If you use Share or Copy, a text or JSON report is handed to the Android share sheet or clipboard. Where that data goes next is controlled by **you** and the app you pick (Messages, email, etc.).

## Children

The app is not directed at children and does not knowingly collect data from children.

## Changes

If this policy changes, we will update this file in the repository and the in-app About section when practical.

## Contact

Open an issue on the project GitHub repository for privacy questions.
