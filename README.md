# Garetto's Jukebox

A music player for Android that plays the songs saved on your phone, with a
neon purple look and a full "Audio Lab" of sound controls.

## What's inside

- **Library** – Songs, Albums and Artists, with search and Shuffle.
- **Now Playing** – spinning record with your album cover, dancing bars,
  seek bar, shuffle and repeat. Swipe down on the record to close it.
- **Audio Lab** (the sliders icon)
  - Equalizer you drag with your finger, 10 built-in presets, save your own
  - Bass boost, Surround, Loudness, Reverb
  - Crossfade (0–12 seconds) and gapless playback
  - Left/right balance
  - Sleep timer
- Keeps playing in the background, with lock-screen and notification controls.

## Getting it onto your phone (about 20–30 minutes the first time)

### 1. Install Android Studio (free)
Download it from https://developer.android.com/studio and install with the
default options. The first launch downloads extra pieces; let it finish.

### 2. Open this project
1. Unzip `GarettosJukebox.zip` somewhere easy to find.
2. In Android Studio choose **Open** and pick the `GarettosJukebox` folder.
3. If asked, click **Trust Project**.
4. Wait for the progress bar at the bottom ("Gradle sync") to finish. The first
   time can take 5–10 minutes. If it offers to install missing SDK pieces or
   accept licences, say yes.

### 3. Get your Pixel ready
1. On the phone: **Settings → About phone**, tap **Build number** 7 times.
2. **Settings → System → Developer options**, turn on **USB debugging**.
3. Plug the phone into the computer with a USB cable.
4. Tap **Allow** on the phone when it asks about USB debugging.

### 4. Run it
1. At the top of Android Studio your Pixel should appear in the device list.
2. Press the green **Run ▶** button.
3. The app installs and opens. Tap **Allow music access**.

The app stays on your phone after you unplug it.

### 5. Add music
The jukebox plays audio files stored on the phone (MP3, FLAC, M4A, WAV, OGG).
Copy them into the phone's **Music** folder, then reopen the app. Songs from
streaming apps such as Spotify are not files on the phone, so they won't show.

## If something goes wrong

- **Sync fails mentioning Gradle, Java or the Android Gradle Plugin** – Android
  Studio usually shows a blue link such as "Upgrade" or opens the *AGP Upgrade
  Assistant*. Accept it and let it sync again.
- **Phone doesn't appear** – try a different USB cable or port, and check the
  phone for the "Allow USB debugging" prompt.
- **A red error in the Build panel** – copy the message and send it to Claude;
  it's usually a one-line fix.
- **Surround / bass sound subtle** – these use the phone's built-in effects and
  are strongest on headphones.

## Good to know

- The look is an original design inspired by a purple, late-night, 999 mood.
  It contains no artist photos, logos or artwork. Covers come from your own
  music files.
- Nothing leaves your phone. The app has no internet permission.
