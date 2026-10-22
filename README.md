# Murmur

Private, on-device voice typing for Mac and Android, powered by Whisper. Hold a key (Mac) or tap a text box (Android), speak, and your words are typed for you. Nothing is sent to the cloud.

## Mac app (`mac/`)

A tiny menu bar app in Swift.

- Hold **Right Option** (configurable), speak, let go: the text is typed wherever your cursor is.
- Hold or tap mode, model and language settings, launch at login.
- Transcribes in chunks while you talk, so text lands almost instantly when you let go.
- If no text box is focused, finds the main one in the front window and types there; otherwise keeps the text on the clipboard.
- Engine: [faster-whisper](https://github.com/SYSTRAN/faster-whisper), running locally in its own Python environment.

**Build:** `cd mac && ./build.sh` (installs to `~/Applications/Murmur.app`). It needs the engine environment at `~/Library/Application Support/Murmur/venv` with `faster-whisper` installed (Python 3.11):

```bash
uv venv --python 3.11 "$HOME/Library/Application Support/Murmur/venv"
uv pip install --python "$HOME/Library/Application Support/Murmur/venv/bin/python" faster-whisper
```

## Android app (`android/`)

Kotlin app for Android 10+, built for the Galaxy Z Fold 7.

- **Murmur Keyboard**, a Gboard-style keyboard with two modes:
  - **Voice mode:** no keys. Tap any text box and it starts listening; your words appear live as you speak. The waveform button pauses (green listening, red paused), and **Send** finishes and sends (search/go, or taps the app's send button in chat apps like WhatsApp).
  - **Typing mode:** a regular QWERTY keyboard with number hints, caps lock, symbols and double-space period.
- **Floating bubble** for use with other keyboards: hold to talk above the keyboard, remembered position per app.
- Engine: Whisper (base, built in; tiny/small downloadable) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), with voice detection for live, chunked transcription.

**Build:**

```bash
cd android
./fetch-deps.sh            # engine + model files, too large for git
./gradlew assembleRelease
```

Release builds need a signing key (`murmur-release.jks` and `keystore.properties`), which aren't in this repo. See `android/INSTALL.md` for installing on a phone.

## Keeping this repo up to date

The apps are developed in `~/Desktop/Murmur` and `~/Desktop/MurmurAndroid`. Run `./sync.sh "message"` to copy the latest code here, commit and push.
