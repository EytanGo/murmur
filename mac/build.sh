#!/bin/zsh
# Builds Murmur.app, signs it, and installs it to ~/Applications.
set -euo pipefail
cd "$(dirname "$0")"

APP=build/Murmur.app
IDENTITY="Apple Development: Created via API (55T38T9F28)"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

xcrun swiftc -O -swift-version 5 -target arm64-apple-macos14.0 \
  -o "$APP/Contents/MacOS/Murmur" Sources/*.swift

cp engine/engine.py "$APP/Contents/Resources/"
[[ -f icon/Murmur.icns ]] && cp icon/Murmur.icns "$APP/Contents/Resources/"

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Murmur</string>
  <key>CFBundleDisplayName</key><string>Murmur</string>
  <key>CFBundleIdentifier</key><string>co.choclement.murmur</string>
  <key>CFBundleExecutable</key><string>Murmur</string>
  <key>CFBundleIconFile</key><string>Murmur</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>CFBundleVersion</key><string>1</string>
  <key>LSMinimumSystemVersion</key><string>14.0</string>
  <key>LSUIElement</key><true/>
  <key>NSMicrophoneUsageDescription</key><string>Murmur listens while you hold your shortcut and turns your speech into text.</string>
</dict>
</plist>
PLIST

codesign --force --sign "$IDENTITY" "$APP"

# Install (quit any running copy first).
pkill -x Murmur 2>/dev/null || true
mkdir -p ~/Applications
rm -rf ~/Applications/Murmur.app
cp -R "$APP" ~/Applications/
echo "Installed ~/Applications/Murmur.app"
