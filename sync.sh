#!/bin/zsh
# Copies the latest code from the two working folders into this repo (skipping builds,
# signing keys, APKs and large model files), then commits and pushes.
set -euo pipefail
R="$(cd "$(dirname "$0")" && pwd)"
# The Mac app is ~/Desktop/Murmur itself; the Android app and this repo live inside it, so skip those.
rsync -a --delete --exclude build/ --exclude '*.apk' --exclude '*.png' --exclude '.DS_Store' \
  --exclude MurmurAndroid/ --exclude Murmur-GitHub/ --exclude .claude/ ~/Desktop/Murmur/ "$R/mac/"
rsync -a --delete --exclude .git/ --exclude .gradle/ --exclude build/ --exclude app/build/ \
  --exclude local.properties --exclude keystore.properties --exclude '*.jks' --exclude '*.apk' \
  --exclude 'app/libs/*.aar' --exclude 'app/src/main/assets/models/' --exclude model-files/ --exclude '.DS_Store' \
  --exclude .idea/ --exclude .kotlin/ --exclude fetch-deps.sh ~/Desktop/Murmur/MurmurAndroid/ "$R/android/"
cd "$R"
git add -A
git diff --cached --quiet && { echo "Nothing new."; exit 0; }
git commit -m "${1:-Update Murmur}"
git push
