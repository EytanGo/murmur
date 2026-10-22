# Murmur for Android: install on your Galaxy Z Fold 7

Murmur adds a small dark button just above your keyboard (Gboard stays your keyboard). Hold it, speak, let go: your words are typed where your cursor is. Whisper runs on the phone itself, so nothing is sent anywhere and it works offline.

## 1. Get the app onto the phone (2 minutes)

1. On the Fold, open the **Google Drive** app (your personal Google account) and go to **My Drive › Murmur**.
2. Tap **Murmur.apk** and choose **Download**, then open it from the notification (or from **My Files › Downloads**).
3. Samsung will ask to allow installs from this source: tap **Settings**, turn on **Allow from this source**, go back, and tap **Install**.
   - If Play Protect warns "unrecognized app", tap **More details › Install anyway**. That's normal for apps that aren't from the Play Store.
   - If Android shows a 24-hour wait for "unverified developer" apps (new 2026 rule), use the USB option below instead; it skips the wait.

## 2. Set it up (1 minute)

Open **Murmur**:

1. **Microphone › Allow**, then choose **While using the app**. (Also allow notifications: a quiet "Murmur is listening" note appears only while you hold the button.)
2. **Murmur voice typing › Turn on**. In the list, tap **Installed apps › Murmur voice typing**, turn it on, and confirm.
   - **If the switch is greyed out ("Restricted setting")**: go back to Murmur, tap **Open App info**, tap **⋮** in the top-right corner, choose **Allow restricted settings**, confirm with your fingerprint or PIN, then turn the switch on.
3. Scroll to **Try it**, tap the box, and hold the bubble above the keyboard.

## Murmur Keyboard (recommended)

Murmur includes its own keyboard, styled like Gboard, with two modes. You don't need the floating bubble with it.

**Turn it on:** in Murmur, under **Murmur Keyboard**, tap **Turn on** and enable Murmur Keyboard, then tap **Switch** and choose it. (Or long-press the space bar on any keyboard to switch.)

- **Voice mode:** no keys. Tap any text box and it starts listening right away. Your words appear in the box as you speak.
  - The **left button is the live waveform**: green while listening, red while paused. Tap it to pause or resume; nothing is lost.
  - **Send** finishes the last words and sends: in WhatsApp and Messages it taps the app's send button; in Google search it searches.
  - **Keyboard** (top left) switches to typing.
- **Typing mode:** a regular keyboard: long-press the top row for numbers, double-tap shift for caps lock, double-space for a period, hold delete to delete quickly, long-press space to switch keyboards. **Voice** (top left) switches back.
- Settings › **Opens with** picks which mode it starts in.

## Using the floating bubble (with other keyboards)

- **Hold** the bubble, speak, **let go**: the text appears at your cursor. It's transcribed while you talk, so it lands about half a second after you let go.
- **Cancel**: while holding, slide your finger away (the bubble turns red), then let go.
- **Move it**: touch the bubble and slide right away, in any direction. Murmur remembers the spot for each app separately (e.g. off WhatsApp's voice-note button without changing other apps). In chat apps it starts above the whole chat bar. Settings › Bubble position has the default side and "Reset all positions".
- **Tap mode**: in Murmur's settings, choose "Tap to start, tap again to type".
- **More accuracy**: Settings › Accuracy › **Small** (375 MB download, slower). **Tiny** (105 MB) is fastest but makes more mistakes. **Base** is built in and is the best balance.
- **Language**: Auto-detect, or pick one (English, Hebrew, …) for slightly better accuracy and speed.
- If a box won't accept the text, it's copied instead. Long-press in the box and tap **Paste**. "Copy last dictation" in the app also gets it back.

## Good to know

- **After installing an update**, Android turns accessibility apps off. Turn **Murmur voice typing** back on (step 2.2).
- If the bubble ever stops appearing, Samsung may have put Murmur to sleep: **Settings › Apps › Murmur › Battery › Unrestricted**.
- Murmur only listens while you're holding the button. The green mic dot in the corner shows exactly when.

## Option: install over USB (skips any waiting period)

1. On the Fold: **Settings › About phone › Software information**, tap **Build number** 7 times, then **Settings › Developer options › USB debugging** on.
2. Plug the phone into the Mac, tap **Allow** on the phone, then on the Mac run:

```bash
~/Library/Android/sdk/platform-tools/adb install -r ~/Desktop/Murmur/Murmur.apk
```

## For rebuilding

Project: `~/Desktop/MurmurAndroid`. Build with `./gradlew assembleRelease`; it automatically replaces `~/Desktop/Murmur/Murmur.apk` and uploads over the same file in your personal Google Drive (My Drive › Murmur), via `publish.sh` + rclone remote `murmurdrive`. The signing key is `murmur-release.jks` with its password in `keystore.properties`. Keep both: updates must be signed with the same key to install over the old version.
