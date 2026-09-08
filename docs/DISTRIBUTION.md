# Distributing Phone Diagnostic Tool

Primary channel today: **[GitHub Releases](https://github.com/ScoobyDouche/PhoneDiagnosticTool/releases)** (APK).

This document covers what the repo already supports for **stores**, and what only you can do in each store’s console.

## Artifacts CI produces

| Artifact | When | Use |
|----------|------|-----|
| `PhoneDiagnostic-debug` | Always | Sideload / continuous testing (fixed CI debug key) |
| `PhoneDiagnostic-release-apk` | Always (signed only if secrets set) | Alt stores, direct download, F-Droid-style sideload |
| `PhoneDiagnostic-release-aab` | Always (signed only if secrets set) | **Google Play** upload |

## Cutting a GitHub release

One button, no local build, no manual upload.

**Actions → Release → Run workflow**, enter the version without the leading `v`
(e.g. `1.1.1`), and run it. The workflow:

1. Refuses to continue unless the version you typed matches `versionName` in
   `app/build.gradle.kts`, and unless the tag is still free.
2. Runs the unit tests and builds the debug APK, release APK and AAB from that
   commit — a red suite never reaches a tagged artifact.
3. Takes the release body from `docs/release-notes/v<version>.md`, or the
   matching `## [<version>]` section of `CHANGELOG.md`, or GitHub's generated
   notes, in that order.
4. Creates the tag at the built commit and publishes the release with the
   binaries attached — the APK alone unless release signing is configured.

Only signed binaries are attached: without the release secrets below, the
release ships the debug-signed APK alone, because an unsigned APK cannot be
installed and an unsigned AAB cannot be uploaded anywhere.

Pushing a `v*` tag from a workstation runs the same workflow, so the button is a
convenience rather than the only route.

**Releasing therefore means:** bump `versionCode` / `versionName`, add the
`CHANGELOG.md` entry, optionally write `docs/release-notes/v<version>.md`, merge
to `main`, then run the workflow.

> The workflow needs **Settings → Actions → General → Workflow permissions** set
> to *Read and write permissions*. Without it, publishing fails with a 403 on the
> final step and everything before it still passes.

## 1. Create a release keystore (do this once)

> **Read this paragraph before running anything.** This key becomes the app's
> permanent identity. If you lose it you can never publish an update that
> existing installs will accept — the only way back is a new package name and
> asking everyone to reinstall. If someone else obtains it they can publish an
> update that Android installs over yours, inheriting its data and permissions.
> Back it up somewhere you will still have in five years, and nowhere public.

Run this **on your own machine**, not in CI and not in an AI session — the
private key must never pass through a transcript or a log.

```bash
keytool -genkeypair -v \
  -keystore phonediagnostic-release.jks \
  -storetype PKCS12 \
  -alias phonediagnostic \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

It asks for a password, then for name/organisation details. The details are
cosmetic for sideloaded distribution — only the key matters — but they are
baked into the certificate permanently, so put something you are happy to have
public.

**PKCS12 keeps one password for both the store and the key.** So when you fill
in the secrets below, `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD` are
the *same value*. This trips people up; keytool will not warn you.

Then encode it for GitHub:

```bash
# Linux
base64 -w0 phonediagnostic-release.jks > release.keystore.b64
# macOS
base64 -i phonediagnostic-release.jks | tr -d '\n' > release.keystore.b64
```

`.gitignore` covers `*.jks` and `*.b64`, so neither file can be committed by
accident. Delete `release.keystore.b64` once the secret is set — it is the
private key in a form that looks harmless.

## 2. GitHub Actions secrets

**Settings → Secrets and variables → Actions → New repository secret**, four times:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | the whole contents of `release.keystore.b64` |
| `RELEASE_STORE_PASSWORD` | the password you chose |
| `RELEASE_KEY_ALIAS` | `phonediagnostic` |
| `RELEASE_KEY_PASSWORD` | the same password again (see PKCS12 note above) |

The `Release` workflow **refuses to publish** unless all four are present — it
will not fall back to the CI debug key. Every release run also prints the
signing certificate's SHA-256 to the log, so you can confirm which key actually
signed what shipped.

### The one-time cost

A new certificate means Android will not treat the next build as an update.
**Existing installs must be uninstalled first**, which loses their diagnostic
log, metric history and settings. There is no migration path; this is how
Android package signing works.

Do it while the install base is small. The cost only grows.

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
