# Marnock

Sync clipboard, notifications, SMS, and call control between Android and macOS over local Wi‑Fi (preferred), with an optional E2E-encrypted internet relay.

## Quick start

### macOS (Homebrew)

```bash
brew tap stephanevdb/marnock https://github.com/stephanevdb/marnock
brew install --cask marnock
open /Applications/Marnock.app
```

### Android (APK)

1. Download [`Marnock-android.apk`](https://github.com/stephanevdb/marnock/releases/latest) from the latest GitHub Release.
2. Open the APK on your phone and allow install from that source if prompted.
3. Launch **Marnock**, grant notification / SMS / phone permissions as needed.

If an update shows **App not installed**, uninstall the old build once (early releases used a different signing key), then install the new APK. Later in-app updates should work.

### Pair

1. On the Mac, open Marnock — it shows a QR code + 6-digit code.
2. On Android, tap **Scan Mac pairing QR**.
3. Stay on the same Wi‑Fi (or turn off Local-only to use the relay).

## Layout

```
branding/   App icon master assets (PNG / ICNS)
protocol/   JSON Schema + shared message type constants
android/    Kotlin + Jetpack Compose companion app
macos/      SwiftUI macOS client (menu bar + window)
relay/      Go WebSocket relay (opaque ciphertext routing)
```

## Protocol

Length-prefixed JSON over WebSocket (`uint32` BE length + UTF-8 JSON). Envelope: `{ type, id, payload }`.

After pairing, application messages are wrapped in `session.frame` and encrypted with **X25519 + ChaCha20-Poly1305** (HKDF-SHA256 salt `marnock`, info `session-v1`).

Discovery service type: `_marnock._tcp`.

## Homebrew details

- **Cask** (`brew install --cask marnock`): prebuilt app from GitHub Releases → `/Applications` (see Quick start).
- **Formula** (`brew install marnock`): builds from source (Xcode 15+). Run `brew caveats marnock` for an optional `/Applications` symlink.
- Untapped formulas may need: `brew trust --formula stephanevdb/marnock/marnock` and `brew trust --cask stephanevdb/marnock/marnock`.
- Tagged releases bump formula/cask version + checksums on `main` automatically.

## Build & run

### Prerequisites

- **macOS app:** Xcode 15+ / Swift 5.9+, macOS 13+
- **Android app:** Android Studio (SDK 35), JDK 17, device/emulator API 28+
- **Relay:** Go 1.22+

### macOS

`swift run` builds a bare executable. That is fine for a quick UI smoke test, but **UserNotifications requires a real `.app` bundle** (otherwise the app aborts on launch). Prefer the helper script:

```bash
cd macos/Marnock
./run.sh          # builds debug, wraps .build/Marnock.app, opens it
# ./run.sh release
```

Alternatives:

```bash
swift build && swift run   # runs without system notification mirroring
# or open Package.swift in Xcode, add Info.plist to the target, and Run
```

Grant local network / notification permission when prompted. Keep the window open for the pairing QR.

### Android

Needs **JDK 17**. On Apple Silicon with Homebrew:

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

```bash
cd android
# Create local.properties with your SDK path, e.g.:
# sdk.dir=/Users/YOU/Library/Android/sdk
# or Homebrew cmdline tools:
# sdk.dir=/opt/homebrew/share/android-commandlinetools

./gradlew :app:assembleDebug
./gradlew :app:installDebug   # with a device/emulator attached
```

Or open `android/` in Android Studio and Run.

Package id is `com.marnock.app`.

Enable **Notification access** from the in-app button. Grant SMS / phone / contacts permissions for those features. Start the sync foreground service (launched automatically).

### Relay (optional internet path)

```bash
cd relay
docker compose up -d   # pulls ghcr.io/stephanevdb/marnock-relay:latest
# or: go run ./cmd/relay -addr :8787
```

On both apps, turn **off** “Local-only” to use the default relay (`wss://marnock.stephanevdb.com/ws`), and ensure devices are already paired (same session key). LAN is preferred when the peer is discoverable.

### CI

GitHub Actions (`.github/workflows/ci.yml`) builds the Android APK and macOS app on every PR/`main` push, publishes the relay image to GHCR on `main` / `v*` tags, and on `v*` tags creates a **GitHub Release** with:

- `Marnock-android.apk`
- `Marnock-macos.zip`

…then bumps `Formula/marnock.rb` and `Casks/marnock.rb` on `main` for Homebrew.

### Releasing & self-update

Both apps check GitHub Releases for a newer version on launch, show a banner + system notification, and offer an **Update** button.

**One-click (recommended):** Actions → **Release** → Run workflow → choose `patch` / `minor` / `major`. That creates the next `v*` tag and dispatches CI to publish the GitHub Release + Homebrew bump.

**Manual:**

```bash
git tag v1.2.3
git push origin v1.2.3
# Wait for Actions → Release assets appear on GitHub
```

- Version comes from the tag (`v1.2.3` → `1.2.3`).
- First macOS install: Homebrew (`brew install --cask marnock`) or `./run.sh`; Android: release APK / Studio. Later updates use the in-app button.
- Android may ask once to allow installs from Marnock. Release APKs share a stable signing key (`android/keystore/`).
- Repo used for checks: `stephanevdb/marnock` (must be public, or clients need a token — not supported yet).

## Features

| Feature | Notes |
|--------|--------|
| Clipboard | Bidirectional text; off until enabled; loop suppression |
| Notifications | Android `NotificationListenerService` → Mac mirror + actions/reply |
| SMS | Threads / messages sync, live receive, send from Mac |
| Calls | State + history; dial / answer / reject (control only) |
| Relay | Device-id routing of opaque blobs; no payload inspection |

**Call audio:** use a Bluetooth headset paired to the phone. The Mac only controls the call.

## Security defaults

- Pairing required before sync
- Clipboard sync off by default
- Local-only mode on by default (relay disabled)
- Relay never sees plaintext
