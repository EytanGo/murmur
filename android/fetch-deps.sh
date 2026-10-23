#!/bin/zsh
# Downloads the large pieces kept out of git (GitHub's 100 MB file limit): the sherpa-onnx
# engine (AAR) and the voice detector. Whisper models aren't bundled; the app downloads them.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p app/libs app/src/main/assets/models
curl -L -o app/libs/sherpa-onnx.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
curl -L -o app/src/main/assets/models/silero_vad.onnx https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
echo "Done. Build with ./gradlew assembleRelease (needs keystore.properties + murmur-release.jks, not in git)."
