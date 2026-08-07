# Marnock

Sync clipboard, notifications, SMS, and call control between Android and macOS over local Wi‑Fi (preferred), with an optional E2E-encrypted internet relay.

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

## Pairing

1. Start the macOS app — it shows a QR code + 6-digit pairing code.
2. On Android, tap **Scan Mac pairing QR**.
3. Devices exchange public keys, derive a session key, and store pairing locally.

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

Package id is `com.marnock.app` (replaces the old Free Phone Sync install — uninstall the previous app if it is still on the device).

Enable **Notification access** from the in-app button. Grant SMS / phone / contacts permissions for those features. Start the sync foreground service (launched automatically).

### Relay (optional internet path)

```bash
cd relay
docker compose up -d --build
# or: go run ./cmd/relay -addr :8787
# or pull CI image: docker pull ghcr.io/<owner>/marnock-relay:latest
```

On both apps, turn **off** “Local-only”, set the relay URL to `ws://YOUR_HOST:8787/ws`, and ensure devices are already paired (same session key). LAN is preferred when the peer is discoverable.

### CI

GitHub Actions (`.github/workflows/ci.yml`) builds the Android APK and macOS app on every PR/`main` push, and publishes the relay image to GHCR on `main` and `v*` tags.

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
