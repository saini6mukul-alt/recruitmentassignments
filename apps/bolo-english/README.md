# Bolo English

A free, private Android app that helps a child (age 4-12) practise speaking English with
**Tara the parrot**. Built for personal use; not published on any app store.

- **🗣️ हिंदी → English (new in 2.0):** the child says or types any Hindi sentence; Tara gives the proper
  English with its meaning, then the child practises saying it (green/orange words, stars). Uses a
  checked phrasebook of 152 everyday sentences first, then Gemini (if a free key is added), then
  Google's free on-device ML Kit translator. Every sentence is saved in **📚 मेरे वाक्य**.
- **💬 Tara से बातें (new in 2.0):** free conversation with Tara in Hindi or English; Tara replies in
  simple English and gently corrects the child. Needs a free Gemini API key. Child-safe instructions.
- 24 lessons in 6 themes, **all unlocked**: Me & My Family, Animals, Food, Colours & Shapes, At School,
  Feelings & My Day; Listen & Repeat, Picture Talk, Questions and Role-play
- Word-by-word feedback, 1-3 stars, Hindi help after 2 tries, daily streak and a sticker book
- Parent corner (⚙️) opens directly: progress, words to practise, settings, Gemini key
- No ads, no accounts. Progress stays on the phone.

## Install on your phone

1. Copy `dist/BoloEnglish-2.0.apk` to the phone (download it, or send it to yourself).
2. Open the file. Android will ask to allow installs from that app (Files, Chrome or WhatsApp):
   tap **Settings → Allow from this source**, go back, and tap **Install**.
3. If Google Play Protect shows "Unsafe app blocked" or "App scan recommended", tap
   **More details → Install anyway** (it warns about every app that is not from the Play Store).
4. Open **Bolo English**, enter your child's name and age, and allow the microphone when asked.

## For the best experience (one-time setup, optional)

- **Free Gemini key (for "Tara से बातें" and better explanations):** on the phone's browser open
  aistudio.google.com/apikey, sign in with a Google account, tap "Create API key", copy it, and paste
  it in Bolo English → ⚙️ Parent → Gemini → Save → Test key. The free tier has daily limits set by
  Google. With a key, the child's sentences are sent to Google's Gemini API.
- **Hindi translation model:** downloads automatically the first time the app has internet (~30 MB);
  after that Hindi → English works offline.

- **Hindi voice for Tara's help:** Settings → System → Languages → Text-to-speech output →
  Google (speech services) → Install voice data → Hindi (India).
- **Offline, private speech recognition:** Google app → Settings → Voice → Offline speech
  recognition → download **English (India)**. Without it, the Google speech service may need
  the internet to understand your child.
- Requires Android 8.0 or newer and the Google app (pre-installed on almost all phones).

## How it works

Speech is recognised by Android's built-in speech service (English or Hindi, India). The app compares
the words heard with the expected English (tolerant of small pronunciation differences, "color/colour",
numbers like "7"/"seven", and extra filler words). Tara's voice is the phone's built-in text-to-speech.
Hindi → English uses, in order: the checked phrasebook (exact match after normalising spelling, so a
sentence with "नहीं" or लड़की/लड़का is never mixed up), Gemini (only with a key), and ML Kit on-device
translation. Nothing costs money.

## Updating the app later

1. Edit lessons in `tools/gen_lessons.py` or phrases in `tools/gen_phrasebook.py`, then run that script.
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

- Unit tests: `gradle :app:testDebugUnitTest` (scoring, lessons, progress, phrasebook, Gemini parsing, translation order)
- Source: `app/src/main/java/com/bolo/english/` (lesson, scoring, progress, speech, voice, ui)
- Design: `docs/superpowers/specs/2026-11-18-bolo-english-personal-app-design.md` and `2026-11-19-bolo-english-v2-design.md`
