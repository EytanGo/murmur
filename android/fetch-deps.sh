#!/bin/zsh
# Downloads the two large pieces kept out of git (GitHub's 100 MB file limit):
# the sherpa-onnx engine (AAR) and the built-in Whisper "base" model + voice detector.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p app/libs app/src/main/assets/models/base
curl -L -o app/libs/sherpa-onnx.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
curl -L -o app/src/main/assets/models/silero_vad.onnx https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
for f in base-encoder.int8.onnx base-decoder.int8.onnx base-tokens.txt; do
  curl -L -o app/src/main/assets/models/base/$f https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/$f
done
echo "Done. Build with ./gradlew assembleRelease (needs keystore.properties + murmur-release.jks, not in git)."
