#!/bin/zsh
# Publishes a release APK to its two homes. Usage: publish.sh <apk> <version> <code> <edition>
#   keyboard: ~/Desktop/Murmur/Murmur.apk         and Drive › My Drive › Murmur › Murmur-<version>-build<code>.apk
#   bubble:   ~/Desktop/Murmur/Murmur-Bubble.apk  and Drive › My Drive › Murmur › Murmur-Bubble-<version>-build<code>.apk
# The Drive copy is named after the version so you can tell at a glance which one you have.
# Older APKs of the same edition in that Drive folder go to the Drive trash (recoverable for 30 days);
# the other edition's APK is left alone.
set -euo pipefail
APK="$1"
VERSION="$2"
CODE="$3"
EDITION="${4:-keyboard}"
RCLONE=/opt/homebrew/bin/rclone

if [[ "$EDITION" == "bubble" ]]; then
  BASE="Murmur-Bubble"
  SAME_EDITION='^Murmur-Bubble.*\.apk$'
else
  BASE="Murmur"
  SAME_EDITION='^Murmur(-[0-9].*)?\.apk$'
fi
NAME="${BASE}-${VERSION}-build${CODE}.apk"

cp "$APK" ~/Desktop/Murmur/$BASE.apk
echo "Saved ~/Desktop/Murmur/$BASE.apk (version $VERSION, build $CODE)"

if "$RCLONE" listremotes 2>/dev/null | grep -qx 'murmurdrive:'; then
  "$RCLONE" copyto ~/Desktop/Murmur/$BASE.apk "murmurdrive:Murmur/$NAME"
  "$RCLONE" lsf murmurdrive:Murmur/ 2>/dev/null | grep -E "$SAME_EDITION" | grep -vx "$NAME" | while read -r old; do
    "$RCLONE" deletefile --drive-use-trash "murmurdrive:Murmur/$old" && echo "Moved the old $old to the Drive trash"
  done
  echo "Uploaded to Google Drive › My Drive › Murmur › $NAME"
else
  echo "warning: Google Drive isn't connected (rclone remote 'murmurdrive'); upload skipped" >&2
fi
