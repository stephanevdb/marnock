#!/usr/bin/env bash
# Build Marnock and launch it as a real .app (required for UserNotifications / local network).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

CONFIG="${1:-debug}"
swift build -c "$CONFIG"

BIN="$(swift build -c "$CONFIG" --show-bin-path)/Marnock"
APP="$ROOT/.build/Marnock.app"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$ROOT/Marnock/Info.plist" "$APP/Contents/Info.plist"
cp "$BIN" "$APP/Contents/MacOS/Marnock"
chmod +x "$APP/Contents/MacOS/Marnock"
if [ -f "$ROOT/Marnock/Resources/AppIcon.icns" ]; then
  cp "$ROOT/Marnock/Resources/AppIcon.icns" "$APP/Contents/Resources/AppIcon.icns"
elif [ -f "$ROOT/../../branding/Marnock.icns" ]; then
  cp "$ROOT/../../branding/Marnock.icns" "$APP/Contents/Resources/AppIcon.icns"
fi

# Ad-hoc sign so Gatekeeper/local-network prompts work more reliably in dev.
if command -v codesign >/dev/null 2>&1; then
  codesign --force --deep --sign - \
    --entitlements "$ROOT/Marnock/Marnock.entitlements" \
    "$APP" 2>/dev/null || codesign --force --deep --sign - "$APP"
fi

echo "Launching $APP"
open "$APP"
