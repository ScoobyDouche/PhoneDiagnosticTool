# Distributing Phone Diagnostic Tool

Primary channel today: **[GitHub Releases](https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases)** (APK).

This document covers what the repo already supports for **stores**, and what only you can do in each store’s console.

## Artifacts CI produces

| Artifact | When | Use |
|----------|------|-----|
| `PhoneDiagnostic-debug` | Always | Sideload / continuous testing (fixed CI debug key) |
| `PhoneDiagnostic-release-apk` | Always (signed only if secrets set) | Alt stores, direct download, F-Droid-style sideload |
| `PhoneDiagnostic-release-aab` | Always (signed only if secrets set) | **Google Play** upload |

## 1. Create a release keystore (do this once)

On a trusted machine:

```bash
keytool -genkeypair -v \
  -keystore phonediagnostic-release.keystore \
  -alias phonediagnostic \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

Back up the `.keystore` file and both passwords offline. **Never commit them.**

Encode for GitHub Actions:

```bash
base64 -w0 phonediagnostic-release.keystore > release.keystore.b64
```

## 2. GitHub Actions secrets

Repo → **Settings → Secrets and variables → Actions** → New repository secret:

| Secret name | Value |
|-------------|--------|
| `RELEASE_KEYSTORE_BASE64` | Contents of `release.keystore.b64` |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | e.g. `phonediagnostic` |
| `RELEASE_KEY_PASSWORD` | Key password |

After the next green run on `main`, download:

- **PhoneDiagnostic-release-apk** — signed APK for F-Droid / sideload
- **PhoneDiagnostic-release-aab** — signed AAB for Play Console

> **Note:** Existing users on debug-signed GitHub builds must **uninstall once** when switching to release-signed packages (different certificate).

## 3. Google Play

### Prerequisites

- [Google Play Console](https://play.google.com/console) developer account (one-time registration fee)
- Identity verification as required by Google for your account type
- **AAB** upload (required for new apps) — produced by CI as above
- **Play App Signing** — enroll when creating the app; keep your **upload** keystore as the one in GitHub secrets
- Privacy policy URL — use the raw or GitHub-rendered `PRIVACY.md`, or host a short page:
  - https://github.com/ScoobyDouche/PhoneDiagnosticTool/blob/main/PRIVACY.md
- Data safety form — declare **no data collected / no data shared** (matches the app: local-only diagnostics; optional user-disabled latency probe to `8.8.8.8`)
- Store listing: title, short/full description, screenshots (phone), feature graphic, app icon
- For many new personal accounts: **closed testing** with the minimum number of testers for the required period before production

### Suggested listing copy (short)

**Title:** Phone Diagnostic Tool

**Short description:**  
On-device diagnostics for CPU, battery, sensors, and more. No accounts, no analytics.

**Full description:**  
See README features list; stress privacy and optional network probe.

### Permissions justification (Play declarations)

Be ready to explain each permission the same way the README table does. `PACKAGE_USAGE_STATS` and foreground-service “special use” get extra scrutiny — the manifest already documents the FGS subtype.

### Target API

`targetSdk = 35` today. Watch Play’s yearly floor (API 36 called out for late 2026 in public guidance) and bump before submission if required.

## 4. F-Droid

Best fit for a MIT, no-telemetry diagnostics app.

1. Draft metadata lives at [`metadata/com.phonediagnostic.yml`](../metadata/com.phonediagnostic.yml).
2. Prefer F-Droid building from **tagged git commits** (reproducible / auditable) rather than shipping a prebuilt APK only.
3. Process overview: [Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/)
4. You will typically open a merge request against [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the metadata file.
5. Screenshots: reuse `docs/screenshots/`.

Until inclusion is accepted, keep shipping APKs on GitHub Releases.

## 5. Other stores

| Store | Artifact | Notes |
|-------|----------|--------|
| Amazon Appstore | APK (or their current requirement) | Separate developer account |
| Samsung Galaxy Store | APK / their portal format | Extra OEM review possible |
| Accrescent / Obtainium | APK from GitHub | Great for power users; no extra packaging |

## Checklist before first store upload

- [ ] Release keystore created and backed up
- [ ] GitHub secrets set; CI produces **signed** release APK + AAB
- [ ] VersionCode / versionName bumped for the store release if needed
- [ ] Privacy policy URL works without login
- [ ] Screenshots meet store size rules (Play: phone screenshots, feature graphic)
- [ ] Decide: stay on GitHub-only until F-Droid lands, or go Play + GitHub in parallel
- [ ] Changelog entry notes signing identity if cutting over from debug CI builds
