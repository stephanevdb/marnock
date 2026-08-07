#!/usr/bin/env bash
# Update Casks/marnock.rb version + sha256 from a built Marnock-macos.zip.
# Usage: scripts/update-homebrew-cask.sh <version> <path-to-Marnock-macos.zip>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:?version required (e.g. 1.2.3)}"
ZIP="${2:?path to Marnock-macos.zip required}"
CASK="$ROOT/Casks/marnock.rb"

if [[ ! -f "$ZIP" ]]; then
  echo "error: zip not found: $ZIP" >&2
  exit 1
fi
if [[ ! -f "$CASK" ]]; then
  echo "error: cask not found: $CASK" >&2
  exit 1
fi

SHA="$(shasum -a 256 "$ZIP" | awk '{print $1}')"

# Portable in-place edit (macOS/Linux).
tmp="$(mktemp)"
awk -v ver="$VERSION" -v sha="$SHA" '
  /^  version / { print "  version \"" ver "\""; next }
  /^  sha256/   { print "  sha256 \"" sha "\""; next }
  { print }
' "$CASK" >"$tmp"
mv "$tmp" "$CASK"

echo "Updated $CASK → version=$VERSION sha256=$SHA"
