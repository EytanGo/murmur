# Murmur

Voice typing for Mac and Android, powered by Whisper. Hold a key (Mac) or tap a text box with the Murmur Keyboard (Android), speak, and your words are typed for you. It runs on-device by default; optionally it can use OpenAI's cloud transcription with your own API key, with automatic fallback to the on-device model.

## Mac app (`mac/`)

A tiny menu bar app in Swift.

- Hold **Right Option** (configurable), speak, let go: the text is typed wherever your cursor is.
- Hold or tap mode, model and language settings, launch at login.
- Transcription on this Mac, or **Cloud · OpenAI** (`gpt-4o-transcribe` / `gpt-4o-mini-transcribe`, your own key) with chunks sent in parallel over a kept-open connection; falls back to the local model if a request fails.
- Transcribes in chunks while you talk, so text lands almost instantly when you let go.
- If no text box is focused, finds the main one in the front window and types there; otherwise keeps the text on the clipboard.
- Engine: [faster-whisper](https://github.com/SYSTRAN/faster-whisper), running locally in its own Python environment.

**Build:** `cd mac && ./build.sh` (installs to `~/Applications/Murmur.app`). It needs the engine environment at `~/Library/Application Support/Murmur/venv` with `faster-whisper` installed (Python 3.11):

```bash
uv venv --python 3.11 "$HOME/Library/Application Support/Murmur/venv"
uv pip install --python "$HOME/Library/Application Support/Murmur/venv/bin/python" faster-whisper
```

## Android app (`android/`)

Kotlin app for Android 10+, built for the Galaxy Z Fold 7. Styled with the "Pebble" design: soft sage surfaces, Bricolage Grotesque and Nunito (bundled, SIL Open Font License).

- **Murmur Keyboard**, a Gboard-style keyboard with two modes:
  - **Voice mode:** no keys. Tap any text box and it starts listening; your words appear live as you speak. The waveform button pauses (green listening, red paused), and **Send** finishes and sends (search/go, or taps the app's send button in chat apps like WhatsApp).
  - **Typing mode:** a regular QWERTY or **Hebrew** keyboard (globe key switches) with number hints, caps lock, symbols and double-space period.
  - Toolbar: **clipboard** (last 10 copies, up to 10 pinned, and snippets) and **delete word** (tap: one word; hold: clear the whole text box). Sized and spaced like Gboard.
- **Snippets:** say a phrase like "my address" and the full text is typed.
- **AI cleanup** (optional, needs an OpenAI key): removes filler words, fixes grammar and punctuation, keeps only self-corrections.
- **Hebrew and Yiddish terms** said in English (Aryeh, mikvah, Shabbos…) are corrected on the on-device models.
- A small accessibility service (optional) lets **Send** press a chat app's own send button (WhatsApp, Messages).
- Engine: **Cloud · OpenAI** (your own key), or Whisper on the phone (tiny/base/small, downloaded in the app; none is bundled, so the APK is ~35 MB) via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), with voice detection for live, chunked transcription.

**Build:**

```bash
cd android
./fetch-deps.sh            # speech engine + voice detector, too large for git
./gradlew assembleRelease
```

Release builds need a signing key (`murmur-release.jks` and `keystore.properties`), which aren't in this repo. See `android/INSTALL.md` for installing on a phone.

## Keeping this repo up to date

The apps are developed in `~/Desktop/Murmur` (Mac) and `~/Desktop/Murmur/MurmurAndroid` (Android). Run `./sync.sh "message"` to copy the latest code here, commit and push.
