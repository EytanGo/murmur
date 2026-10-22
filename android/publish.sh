#!/bin/zsh
# Publishes a release APK to its two homes, replacing the previous copy in each:
#   ~/Desktop/Murmur/Murmur.apk  and  your Google Drive › My Drive › Murmur › Murmur.apk
set -euo pipefail
APK="$1"
RCLONE=/opt/homebrew/bin/rclone

cp "$APK" ~/Desktop/Murmur/Murmur.apk
echo "Saved ~/Desktop/Murmur/Murmur.apk"

if "$RCLONE" listremotes 2>/dev/null | grep -qx 'murmurdrive:'; then
  "$RCLONE" copyto ~/Desktop/Murmur/Murmur.apk murmurdrive:Murmur/Murmur.apk
  echo "Uploaded to Google Drive › My Drive › Murmur › Murmur.apk (replaced the old one)"
else
  echo "warning: Google Drive isn't connected (rclone remote 'murmurdrive'); upload skipped" >&2
fi
