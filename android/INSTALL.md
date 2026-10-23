# Murmur for Android: install on your Galaxy Z Fold 7

Murmur is a keyboard with two modes: **voice** (tap a text box, speak, your words appear) and **typing** (a regular keyboard, sized like Gboard). Speech is turned into text either on the phone (download a model under Settings › Phone model; nothing is sent anywhere and it works offline) or with **Cloud · OpenAI** using your own API key, for the fastest, most accurate results.

## 1. Get the app onto the phone (2 minutes)

1. On the Fold, open the **Google Drive** app (your personal Google account) and go to **My Drive › Murmur**.
2. Tap **Murmur-<version>-build<number>.apk** (there's only ever the latest one) and choose **Download**, then open it from the notification (or from **My Files › Downloads**; delete older Murmur APKs there first so you don't open an old one).
3. Samsung will ask to allow installs from this source: tap **Settings**, turn on **Allow from this source**, go back, and tap **Install**.
   - If Play Protect warns "unrecognized app", tap **More details › Install anyway**. That's normal for apps that aren't from the Play Store.
   - If Android shows a 24-hour wait for "unverified developer" apps (new 2026 rule), use the USB option below instead; it skips the wait.
4. Open Murmur: the top of the screen shows **Version … · build …**, so you can check the update installed.

## 2. Set it up (1 minute)

Open **Murmur**:

1. **Murmur Keyboard › Turn on**: enable Murmur Keyboard, then **Switch** and choose it. (Or long-press the space bar on any keyboard to switch.)
2. **Setup › Microphone › Allow**, then choose **While using the app**.
3. **Setup › Send in chat apps › Turn on** (optional, recommended): lets **Send** press WhatsApp's own send button. In the list, tap **Installed apps › Murmur send button**, turn it on, and confirm.
   - **If the switch is greyed out ("Restricted setting")**: go back to Murmur, tap **Open App info**, tap **⋮** in the top-right corner, choose **Allow restricted settings**, confirm with your fingerprint or PIN, then turn the switch on.
4. **Transcription**: pick **On this phone** (then download a model under **Phone model**) or **Cloud · OpenAI** and paste your API key.

## Using it

- **Voice mode:** no keys. Tap any text box and it starts listening right away. Your words appear in the box as you speak.
  - The **big green button** is the live waveform: green while listening, red while paused. Tap it to pause or resume; nothing is lost.
  - **Send** finishes the last words and sends: in WhatsApp and Messages it taps the app's send button; in Google search it searches.
  - Toolbar: **keyboard** (switch to typing), **clipboard**, **delete word** (tap: one word; **hold: clears the whole text box**), **settings**.
  - To hide the keyboard, use the phone's back button.
- **Typing mode:** a regular keyboard: long-press the top row for numbers, double-tap shift for caps lock, double-space for a period, hold delete to delete quickly, the globe switches English / Hebrew, long-press space to switch keyboards. **Voice** (top left) switches back.
- **Clipboard:** your last 10 copies, up to 10 pinned clips, and your snippets. Tap one to type it in.
- **Snippets:** Settings › Snippets › + Add. Say the phrase ("my address") and the full text is typed.
- **AI cleanup:** Settings › AI cleanup (needs the OpenAI key): removes "um", fixes grammar, keeps only your corrections.
- **Phone models**: Settings › Phone model. **Base** (160 MB) is the best balance, **Small** (375 MB) is most accurate but slower, **Tiny** (105 MB) is fastest but makes more mistakes. None is built in, which keeps the app small.
- **Language**: Auto-detect, or pick one (English, Hebrew, …) for slightly better accuracy and speed.
- Settings › **Opens with** picks which mode the keyboard starts in.

## Murmur Bubble (the second app)

**Murmur Bubble** is a separate app you can install alongside Murmur: instead of a keyboard, it puts a small moss-green bubble just above whatever keyboard you use (Gboard stays your keyboard). It has the same engine and settings (phone models, Cloud · OpenAI, AI cleanup, snippets, the Hebrew word fixes).

- Install **Murmur-Bubble-<version>-build<number>.apk** from the same Drive folder.
- Open **Murmur Bubble**: **Microphone › Allow** (and allow notifications: a quiet "Murmur is listening" note appears only while you hold the bubble), then **Murmur Bubble › Turn on** (Installed apps › Murmur Bubble).
- **Hold** the bubble, speak, **let go**: the text appears at your cursor. **Cancel**: slide your finger away (the bubble turns clay red), then let go. **Tap mode**: Settings › How to talk › Tap to talk.
- **Move it**: touch the bubble and slide right away. Murmur remembers the spot for each app; Settings › Bubble side and Reset positions.
- If a box won't accept the text, it's copied instead: long-press in the box and tap **Paste** ("Copy last dictation" in the app also gets it back).
- While the Murmur keyboard is your active keyboard, the bubble stays hidden (the keyboard has its own voice mode).

## Good to know

- **After installing an update**, Android may turn accessibility apps off. If Send stops pressing WhatsApp's send button, turn **Murmur send button** back on (step 2.3); if the bubble stops appearing, turn **Murmur Bubble** back on. Samsung may also put the bubble to sleep: **Settings › Apps › Murmur Bubble › Battery › Unrestricted**.
- Murmur only listens while voice mode is listening (green). The green mic dot in the corner shows exactly when.

## Option: install over USB (skips any waiting period)

1. On the Fold: **Settings › About phone › Software information**, tap **Build number** 7 times, then **Settings › Developer options › USB debugging** on.
2. Plug the phone into the Mac, tap **Allow** on the phone, then on the Mac run:

```bash
~/Library/Android/sdk/platform-tools/adb install -r ~/Desktop/Murmur/Murmur.apk
```

## For rebuilding

Project: `~/Desktop/Murmur/MurmurAndroid`, with two editions (product flavors): `keyboard` (Murmur) and `bubble` (Murmur Bubble). Shared code is in `app/src/main`, each edition's own in `app/src/keyboard` and `app/src/bubble`. Bump that edition's `versionCode`/`versionName` in `app/build.gradle.kts`, then build with `./gradlew assembleKeyboardRelease` or `./gradlew assembleBubbleRelease`; it copies the APK to `~/Desktop/Murmur/Murmur.apk` (or `Murmur-Bubble.apk`) and uploads it to your Google Drive (My Drive › Murmur) as `Murmur-<version>-build<code>.apk` (or `Murmur-Bubble-…`), moving older ones of the same app to the Drive trash (`publish.sh` + rclone remote `murmurdrive`). The signing key is `murmur-release.jks` with its password in `keystore.properties`. Keep both: updates must be signed with the same key to install over the old version.
