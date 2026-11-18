# Bolo English

A free, private Android app that helps a child (age 4-12) practise speaking English with
**Tara the parrot**. Built for personal use; not published on any app store.

- 24 lessons in 6 themes: Me & My Family, Animals, Food, Colours & Shapes, At School, Feelings & My Day
- Activities: Listen & Repeat, Picture Talk, Questions, and Role-play (e.g. the fruit shop)
- Word-by-word feedback (green = heard, orange = practise again), 1-3 stars, Hindi help after 2 tries
- Stars, daily streak and a sticker book; a parent corner behind a maths question
- No internet permission, no ads, no accounts. Progress stays on the phone.

## Install on your phone

1. Copy `dist/BoloEnglish-1.0.apk` to the phone (download it, or send it to yourself).
2. Open the file. Android will ask to allow installs from that app (Files, Chrome or WhatsApp):
   tap **Settings → Allow from this source**, go back, and tap **Install**.
3. If Google Play Protect shows "Unsafe app blocked" or "App scan recommended", tap
   **More details → Install anyway** (it warns about every app that is not from the Play Store).
4. Open **Bolo English**, enter your child's name and age, and allow the microphone when asked.

## For the best experience (one-time setup, optional)

- **Hindi voice for Tara's help:** Settings → System → Languages → Text-to-speech output →
  Google (speech services) → Install voice data → Hindi (India).
- **Offline, private speech recognition:** Google app → Settings → Voice → Offline speech
  recognition → download **English (India)**. Without it, the Google speech service may need
  the internet to understand your child.
- Requires Android 8.0 or newer and the Google app (pre-installed on almost all phones).

## How it works

Speech is recognised by Android's built-in speech service (English, India). The app compares the
words heard with the lesson's expected answers (tolerant of small pronunciation differences,
"color/colour", numbers like "7"/"seven", and extra filler words). Tara's voice is the phone's
built-in text-to-speech. There is no AI service and nothing costs money.

## Updating the app later

1. Edit lessons in `tools/gen_lessons.py`, then run `python3 tools/gen_lessons.py`.
2. Raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.
3. Put the signing key back: copy `release.jks` to `keystore/release.jks` and create
   `keystore.properties` next to this README with the values from the key file you were given
   (`storeFile=keystore/release.jks`, `storePassword=...`, `keyAlias=bolo`, `keyPassword=...`).
4. Build with `gradle :app:assembleRelease` (needs `ANDROID_HOME` pointing to an Android SDK with
   platform 34).

The signing key is **not** stored in this repository on purpose. Keep your copy safe: an update
signed with a different key cannot install over the old app, and you would have to uninstall it,
which deletes your child's progress.

## Development

- Unit tests: `gradle :app:testDebugUnitTest` (scoring, lessons, progress, attempt logic)
- Source: `app/src/main/java/com/bolo/english/` (lesson, scoring, progress, speech, voice, ui)
- Design: `docs/superpowers/specs/2026-11-18-bolo-english-personal-app-design.md`
