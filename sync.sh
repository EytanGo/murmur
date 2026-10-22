#!/bin/zsh
# Copies the latest code from the two working folders into this repo (skipping builds,
# signing keys and large model files), then commits and pushes.
set -euo pipefail
R="$(cd "$(dirname "$0")" && pwd)"
rsync -a --delete --exclude build/ --exclude '*.apk' --exclude '.DS_Store' ~/Desktop/Murmur/ "$R/mac/"
rsync -a --delete --exclude .git/ --exclude .gradle/ --exclude build/ --exclude app/build/ \
  --exclude local.properties --exclude keystore.properties --exclude '*.jks' --exclude '*.apk' \
  --exclude 'app/libs/*.aar' --exclude 'app/src/main/assets/models/' --exclude '.DS_Store' --exclude .idea/ \
  --exclude fetch-deps.sh ~/Desktop/MurmurAndroid/ "$R/android/"
cd "$R"
git add -A
git diff --cached --quiet && { echo "Nothing new."; exit 0; }
git commit -m "${1:-Update Murmur}"
git push
