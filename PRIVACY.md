# Privacy Policy — Phone Diagnostic Tool

**Last updated:** 2026-10-28

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
- Whether NFC and Bluetooth are present and switched on (never the Bluetooth
  name or address), and which kinds of audio device are connected
- Verified-boot, bootloader-lock and encryption state, where Android exposes it
- Which installed apps hold sensitive permissions or special access (Privacy
  audit screen)
- Cellular generation and signal strength, read without any phone or location
  permission and without cell tower or location identifiers

That information stays on your phone unless **you** choose to share or export a report.

## Network access

Permissions:

- `INTERNET` — used only for the optional latency probe
- `ACCESS_NETWORK_STATE` — used to detect connection type (Wi‑Fi, cellular, etc.), plus DNS servers and IP addresses shown on the Network screen
- `ACCESS_WIFI_STATE` — used to read the current Wi‑Fi link speed, band and signal strength on the Network screen
- `QUERY_ALL_PACKAGES` — used to list installed apps so the storage screen can show which ones are using space, and so the Privacy audit can show which apps hold sensitive permissions

On Android 11 and newer the system hides the installed-app list from apps
that do not hold `QUERY_ALL_PACKAGES`, which is why the per-app storage
breakdown and the Privacy audit need it. The list is read on demand, shown on screen, and never
leaves the device — it is not written to the diagnostic log, not included in
an exported report, and not transmitted anywhere.

The Network screen reads this device's own interface addresses and the active
network's DNS settings. That is displayed locally and never uploaded. MAC
addresses are deliberately not shown. The Wi‑Fi network name is only visible if
you have granted a location permission to this app — it does not request one, so
it normally shows as hidden.

When **Network latency check** is enabled (default on, can be turned off in Settings), the app measures how long a TCP connection takes to open to port 53 of the target you pick in Settings: Cloudflare `1.1.1.1` (default), Quad9 `9.9.9.9`, Google `8.8.8.8`, or **your router only**, which keeps the check on your local network. Only the connection is opened; no DNS query or other data is sent, and no diagnostic report is uploaded.

When the latency check is disabled, the app does not open network connections for diagnostics — including the repeat-probe button on the Network screen, which is unavailable while it is off.

## Elevated access (optional, off by default)

The app can optionally read a few extra hardware values — the battery fuel
gauge, per-core CPU clocks on locked-down devices — that Android otherwise
keeps behind sysfs permissions an ordinary app cannot get. This is **off by
default** and only takes effect if you turn it on in Settings and choose a
backend:

- **Shizuku** — the app talks to the Shizuku manager (which you install and run
  separately) to execute small read-only shell commands as the shell user. This
  needs the `moe.shizuku.manager.permission.API_V23` permission and grants
  nothing unless Shizuku is present and you approve this app inside it.
- **Root** — the app runs the same read-only commands through `su`.

In both cases the commands are limited to reading diagnostic values (e.g.
`cat`-ing a sysfs node for storage wear or wakeup sources), the results are shown on-device exactly like every
other reading, and **nothing about this is transmitted anywhere**. No elevated
access is used, requested, or bound while the feature is set to Off.

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

By default, exported reports leave out the build fingerprint, bootloader and
baseband versions, build host and the kernel build line, which together
identify one exact device build. You can turn this off in Settings. Reports
never contain IP addresses, Wi‑Fi network names, DNS servers, carrier details
or the list of installed apps.

A copied report is marked sensitive, so Android 13+ hides it from the copy
preview and keyboard suggestions, and the app clears it from the clipboard
after a minute if it is still there.

An optional **Block screenshots** setting stops screenshots and screen
recording of the app and hides its preview in Recents.

## Children

The app is not directed at children and does not knowingly collect data from children.

## Changes

If this policy changes, we will update this file in the repository and the in-app About section when practical.

## Contact

Open an issue on the project GitHub repository for privacy questions.
