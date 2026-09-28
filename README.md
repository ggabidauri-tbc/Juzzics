# Juzzics

**Your music, offline, better together.**

Juzzics is an Android music player for the songs on your phone, with a twist: phones running
Juzzics find each other over Bluetooth and Wi-Fi and do things together, **with no internet and
no mobile signal**. Party on a beach, a road trip, a hike in the mountains, a festival field:
it all works between the phones themselves.

<p align="center">
  <a href="https://github.com/ggabidauri-tbc/Juzzics/releases/latest"><b>⬇ Download the latest APK</b></a>
</p>

---

## What it does

### 🎵 A music player that stays out of the way
- Your library by **songs, albums, artists and playlists**, with a mini player that swipes up to a full player
- **Synced lyrics** that follow the song, found online once and then **kept for offline**
  (*Trip prep* gets lyrics for your whole library before you leave)
- Home screen **widget** and a **quick settings tile**
- Remembers where you left off

### 🎉 Music with friends nearby
| | |
|---|---|
| **Party** | Every phone plays the same song at the same moment, in sync. Songs stream to the other phones as they play |
| **Car DJ** | One phone plays in the car, everyone adds songs from their own phone to its queue, in turns |
| **Blend & taste match** | Your music mixed with your friends', and how similar your tastes are |
| **Listen to theirs** | Browse a friend's songs, play them on their phone or stream one to yours |
| **Sing along** | Your phone becomes a microphone: your voice plays live over the music on the party phone |
| **Shout-out** | Hold, talk, let go: a voice message plays on everyone's phone over the music |

### 🧭 Staying together, offline
| | |
|---|---|
| **Friend radar** | Who's where, how far, which way: a radar that turns with you (GPS + compass) |
| **Map** | Everyone on a real map with their trail. **Save map areas** before a trip and the map works without signal |
| **AR finder** | Hold the phone up: name tags float over the camera picture where your friends are |
| **Meeting points** | Long-press the map: everyone gets an arrow and a distance to it |
| **Come to me** | Friends' phones buzz and point to you |
| **Walkie-talkie** | Hold to talk to everyone, live, the music turns down while you speak |
| **Group chat & photos** | Messages, quick replies, "I'm here" with your location, and photos, with delivery ticks |
| **Check on friends** | A heads-up when a friend's battery is low, or someone hasn't moved or been heard of for a while |

### 📡 Built for no signal
- **Relayed through friends:** messages, positions, photos and voice hop from phone to phone,
  so friends out of your range still get them (A ↔ B ↔ C: A reaches C)
- **Reconnects by itself** when a friend walks out of range and comes back
- **Keeps working in the background**, with the screen off
- **Bump to connect:** tap two phones together to pair them, no codes

---

## Install

1. On your Android phone (**Android 7.1 or newer**), open the
   [latest release](https://github.com/ggabidauri-tbc/Juzzics/releases/latest) and download the `.apk`.
2. Open it. The first time, Android asks to allow installing apps from your browser or files app.
3. Updates install over the old version; your songs, lyrics, playlists and friends stay.

To play together, your friends install Juzzics too, then everyone opens **Nearby** and taps
**Find friends** (or **Bump**).

## Permissions, and why

| Permission | Used for |
|---|---|
| Music & audio | Playing the songs on your phone |
| Nearby devices, Bluetooth, Wi-Fi | Finding and connecting to friends' phones |
| Location | Required by Android to find nearby phones; the radar, map and AR finder. Shared **only with connected friends**, only while you turn it on |
| Microphone | Sing along, shout-outs, walkie-talkie, only while you use them |
| Camera | The AR finder and taking photos for the chat. Nothing is recorded |
| Notifications | Messages, "come to me", low battery alerts, and the notice shown while Nearby runs in the background |
| Internet | Only to find lyrics and download map areas. Everything else works without it |

**Privacy:** nothing goes to a server. Songs, messages, photos, voice and locations travel
directly between your friends' phones. Location sharing is off until you turn it on, and
stops by itself after the time you pick.

---

## Building

Requirements: Android Studio (recent), JDK 17+.

```sh
git clone https://github.com/ggabidauri-tbc/Juzzics.git
cd Juzzics
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Note: an app started with Android Studio's **Run** button is a test build that other phones
refuse to install. To share the app, use a release (see below).

**Releases** are built and signed by GitHub Actions when a version tag is pushed:
see [RELEASING.md](RELEASING.md).

### Built with
Kotlin · Jetpack Compose (Material 3) · Media3 / ExoPlayer · Google Nearby Connections ·
MapLibre with [OpenFreeMap](https://openfreemap.org) tiles · CameraX · Room · Koin · Coil ·
Glance · lyrics from [LRCLIB](https://lrclib.net)

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.
