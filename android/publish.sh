#!/bin/zsh
# Publishes a release APK to its two homes:
#   ~/Desktop/Murmur/Murmur.apk  and  Google Drive › My Drive › Murmur › Murmur-<version>-build<code>.apk
# The Drive copy is named after the version so you can tell at a glance which one you have.
# Older Murmur APKs in that Drive folder go to the Drive trash (recoverable for 30 days).
set -euo pipefail
APK="$1"
VERSION="$2"
CODE="$3"
NAME="Murmur-${VERSION}-build${CODE}.apk"
RCLONE=/opt/homebrew/bin/rclone

cp "$APK" ~/Desktop/Murmur/Murmur.apk
echo "Saved ~/Desktop/Murmur/Murmur.apk (version $VERSION, build $CODE)"

if "$RCLONE" listremotes 2>/dev/null | grep -qx 'murmurdrive:'; then
  "$RCLONE" copyto ~/Desktop/Murmur/Murmur.apk "murmurdrive:Murmur/$NAME"
  "$RCLONE" lsf murmurdrive:Murmur/ 2>/dev/null | grep -E '^Murmur.*\.apk$' | grep -vx "$NAME" | while read -r old; do
    "$RCLONE" deletefile --drive-use-trash "murmurdrive:Murmur/$old" && echo "Moved the old $old to the Drive trash"
  done
  echo "Uploaded to Google Drive › My Drive › Murmur › $NAME"
else
  echo "warning: Google Drive isn't connected (rclone remote 'murmurdrive'); upload skipped" >&2
fi
