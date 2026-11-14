# Lehar

A personal, dark and minimal Android music app. The home screen is a **Mood Pad**: drag the dot (calm ↔ energy, sad ↔ happy) or tap a chip, and Lehar builds a Hindi/Punjabi/English queue for that vibe. Songs play through the YouTube player, lyrics come from LRCLIB, and everything you like or play stays on your phone.

> Personal use only. Background playback of YouTube is not allowed by YouTube's terms, so this APK is not for the Play Store.

## Install (Android 8+)

1. Copy `lehar-1.0.apk` to the phone and open it.
2. Allow **Install unknown apps** for the app you opened it from (Files / Chrome / WhatsApp).
3. On first launch, allow **notifications** (for lock-screen controls) and choose **Allow** when asked about **battery optimisation**. Without this, Android may stop the music in the background.

Updates: install the new APK over the old one. Your library stays, as long as every APK is signed with the same key (keep `lehar-signing-key.zip` safe).

## What to test on the phone

| Test | Expected |
|---|---|
| Tap **Gym** | Within a few seconds, a queue of about 25 mixed-language songs starts |
| Lock the screen / open another app | Music keeps playing; lock screen shows title, art, ⏮ ⏯ ⏭ |
| Play an Instagram reel, then close it | Lehar pauses, then resumes by itself |
| Incoming call | Pauses during the call, resumes after |
| Player → swipe left / right, double-tap art | Next / previous song, like |
| ⋯ → Lyrics timing | − / + moves lyrics by 0.5s, remembered per song |
| Kill the app from recents, reopen | Same queue and song are ready (paused) |
| Search "kesariya" | Songs and Artists sections |

If something fails, note the step and the phone model. That is enough to fix it.

## How it works

```
Android shell (Java, no Gradle)            UI (HTML/CSS/JS in assets/www)
  BgWebView  ── always "visible" ──►         Mood Pad · Player · Search · Library · Settings
  PlaybackService: MediaSession,            engine/: mood, queue, ytm, lyrics, api (pure + tested)
    notification, audio focus               ytplayer.js: two hidden YouTube players
  Net: HTTP for YouTube Music + LRCLIB      store.js: IndexedDB on the phone
```

- Search, suggestions and radio use YouTube Music's internal API (`WEB_REMIX`). When the Songs filter is empty in a region, search falls back to the Videos filter.
- The smart queue never repeats a song heard in the last 3 hours or the last 50 plays.
- Songs you finish (≥ 50%) in a mood are tagged with that mood, so future queues include your own picks.

Spec: `docs/specs/2026-11-14-lehar-design.md` · Plan: `docs/plans/2026-11-14-lehar-v1.md`

## Develop

```bash
bash scripts/setup-sdk.sh        # once: Android platform 34 + build-tools 35 into /opt/android-sdk
npm test                         # unit tests (node --test)
npm run ui-test                  # UI flows in Playwright with a mocked Android bridge, screenshots in build/shots
bash scripts/build.sh            # → build/lehar-1.0.apk (signs with ~/.lehar/lehar.jks, created on first build)
VERSION_CODE=2 VERSION_NAME=1.1 bash scripts/build.sh   # next release
```

The signing key is never committed. To build an update that installs over 1.0, restore `lehar.jks` and `pass` from `lehar-signing-key.zip` into `~/.lehar/`.
