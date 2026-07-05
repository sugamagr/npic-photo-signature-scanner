# Release recipe — NPIC Photo & Signature Scanner

The app checks `https://raw.githubusercontent.com/sugamagr/npic-photo-signature-scanner/main/version.json`
on every launch. When `versionCode` there is higher than the running APK, the update
sheet surfaces to the user. This document is the ship recipe for that flow.

## Standing operational rule (m2510+)

**Ship a release, then STOP.** After every code change destined for users, cut a
new release (bump versionCode + versionName, `assembleRelease`, update
`version.json`, `gh release create`, `git push`). Do **NOT** `adb install` the
new APK to any device — the user runs the in-app updater themselves to exercise
the real flow. This rule was set by the user after m2509; older releases (v0.1.0
bootstrap) predated it and were installed manually to seed devices.

## Prerequisites

1. Keystore lives at `/Users/apple/Documents/RdQrScanner-KEYSTORE-BACKUP/keystore.jks`
   and is referenced from `keystore.properties` at the repo root (gitignored).
   If `keystore.properties` is missing when a release task runs, the build now
   **fails at the config phase** with an explicit error (m2509 H3) rather than
   silently producing an unsigned APK that would break `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
   on every user's first auto-update.
2. Install the pre-commit hook once per fresh clone so `keystore.properties`
   or a stray `.jks` can never sneak into a commit:
   ```bash
   ./scripts/git-hooks/install.sh
   ```
3. `gh` CLI installed and authenticated: `gh auth status`.

## Cutting a release

1. **Bump the version.** Edit `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2          // strictly monotonic; Android rejects downgrades
   versionName = "0.2.0"    // human label shown in the sheet subtitle
   ```

2. **Assemble the release APK.**
   ```bash
   ./gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`.

3. **Verify the signature** matches the keystore fingerprint from previous releases.
   ```bash
   $(find ~/Library/Android/sdk/build-tools -name apksigner -type f | sort -V | tail -1) \
       verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
   Expected `Signer #1 certificate SHA-256 digest: 672585525c3d770d8cc32b74a377c039606c6a23e21d6b2572703edb39703a2f`.
   If the fingerprint differs, DO NOT ship — users' installs would fail.

4. **Compute the SHA-256.**
   ```bash
   shasum -a 256 app/build/outputs/apk/release/app-release.apk
   ```
   Copy the hex string.

5. **Rename the APK** to match the URL scheme in `version.json`:
   ```bash
   cp app/build/outputs/apk/release/app-release.apk /tmp/npic-0.2.0-release.apk
   ```

6. **Update `version.json`** at the repo root:
   ```json
   {
     "versionCode": 2,
     "versionName": "0.2.0",
     "apkUrl": "https://github.com/sugamagr/npic-photo-signature-scanner/releases/download/v0.2.0/npic-0.2.0-release.apk",
     "apkSha256": "<paste hex from step 4>",
     "apkSizeBytes": <output of `stat -f%z /tmp/npic-0.2.0-release.apk`>,
     "changelog": "\u2022 Fix ...\n\u2022 Add ...",
     "minSupportedVersion": 1,
     "forceUpdate": false,
     "releaseDate": "<ISO 8601 timestamp, e.g. 2025-08-15T10:30:00Z>"
   }
   ```

7. **Commit and push `version.json`.** The app reads it from `raw.githubusercontent.com`
   at the `main` branch — no release-tag substitution.
   ```bash
   git add version.json
   git commit -m "release(vX.Y.Z): version.json for v0.2.0"
   git push origin main
   ```

8. **Create the GitHub Release** with the APK attached:
   ```bash
   gh release create v0.2.0 /tmp/npic-0.2.0-release.apk \
       --title "v0.2.0" \
       --notes "\u2022 Fix ...\n\u2022 Add ..."
   ```

9. **Verify the URL responds** (raw.githubusercontent.com propagation is usually instant
   but can lag a minute):
   ```bash
   curl -sI https://raw.githubusercontent.com/sugamagr/npic-photo-signature-scanner/main/version.json | head -1
   curl -sI $(jq -r .apkUrl version.json) | head -1
   ```
   Both should be `HTTP/2 200` (the APK URL redirects to a signed S3 URL, `curl -L`
   follows it).

## Rolling back a bad release

Android forbids automatic downgrades. If v0.2.0 crashes on launch:

1. **Fix-forward** — ship v0.2.1 with the fix as soon as possible. This is the primary path.
2. If a critical bug forces every user to upgrade, set `"forceUpdate": true` in
   `version.json` on the fixed release. The sheet hides the "Later" button and
   blocks dismissal until the user updates.
3. For an unrecoverable state where the app can't even reach the update sheet,
   teachers must uninstall + reinstall the old APK manually — this loses all
   local Room data. Avoid at all costs by test-installing every release on a
   physical Samsung A35 before publishing.

## Keystore recovery

The keystore lives outside git in `/Users/apple/Documents/RdQrScanner-KEYSTORE-BACKUP/`
so a `.gitignore` mishap can't leak it. If this directory is ever lost:

1. **You cannot recover.** Every existing user has to uninstall + reinstall.
2. Generate a NEW keystore, ship v1.0.0 with it, and treat it as a brand new app.
3. Back up the new keystore immediately in three places (local, encrypted cloud,
   USB in a safe).

## Bootstrap (first-time install for a teacher)

Because the updater lives inside the app, the first install is manual:

1. Send `npic-X.Y.Z-release.apk` via WhatsApp / Files.
2. If the device is Samsung on One UI 6.1+: `Settings \u2192 Security and privacy \u2192 Auto Blocker \u2192 OFF`.
3. Open the APK from Files. When prompted, grant "Install unknown apps" to the source app.
4. After install, open NPIC once — the next update will surface in-app.
