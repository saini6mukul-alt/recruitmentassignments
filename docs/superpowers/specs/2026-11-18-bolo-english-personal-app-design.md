# Bolo English: Free Personal Spoken-English App for a Child (Design)

**Date:** 2026-11-18
**Status:** Approved in chat
**Supersedes for personal use:** `2026-11-18-supernova-kids-mvp-design.md` (the commercial MVP design
remains valid for a future product; this app is a separate, free, personal build).

## 1. Goal and constraints

A native Android app (delivered as a signed APK) that lets the owner's child (age 4-12) practise
spoken English with a friendly parrot tutor, Tara.

- **Cost:** zero. No paid APIs, no server, no accounts, no API keys.
- **Use:** personal, sideloaded APK; not published to any store.
- **Privacy:** no INTERNET permission, no analytics, no ads; progress stored only on the phone.
- **Feedback engine:** scripted (no AI model). Lessons define expected answers; the app scores
  spoken words against them.

## 2. Features

### Child experience

- **First launch:** parent enters child's name and age band (4-6, 7-9, 10-12).
- **Lesson map:** 24 lessons in 6 themes (Me & My Family, Animals, Food, Colours & Shapes,
  At School, Feelings & Daily Routine), 4 lessons per theme, each with 5-8 steps.
- **Step types:**
  - `repeat`: Tara says a target word or sentence; child repeats it.
  - `picture`: large emoji picture plus a question; several accepted answers.
  - `question`: open question with a sentence pattern containing a slot
    (e.g. `my favourite colour is {any}`); any answer that matches the pattern passes.
  - `roleplay`: 3-4 scripted turns; Tara's next line is chosen by keywords in the child's answer,
    with a default branch.
- **Feedback per attempt:**
  - Recognised words shown with each expected word coloured green (heard) or orange (missed).
  - Stars: match ratio >= 0.9 gives 3, >= 0.7 gives 2, >= 0.4 gives 1, otherwise 0.
  - A step passes at 1 star or more. Tara praises and repeats the model sentence.
  - Never says "wrong"; uses encouraging phrases ("Good try! Listen again...").
  - After 2 failed attempts on a step, Tara gives the Hindi help line (Hindi TTS; text shown too)
    and a Skip button appears.
- **Rewards:** stars per lesson (best of the step scores), daily streak, one emoji sticker per
  completed lesson shown in a sticker book.

### Parent corner

Behind a parental gate (a two-digit multiplication question).

- Stars per lesson, days practised, total practice minutes.
- Words to practise: the 10 most frequently missed expected words.
- Settings: child name, age band, speech rate (slow, normal), Hindi help on/off, reset progress.

## 3. Architecture

Kotlin, Jetpack Compose, Material 3, single activity, `minSdk 26`, `targetSdk 34`.

| Unit | Responsibility | Depends on |
|---|---|---|
| `lesson` | Data classes, JSON parsing of `assets/lessons.json`, validation | kotlinx.serialization |
| `scoring` | Text normalisation, word matching (with small spelling tolerance and number words), pattern slot matching, stars, keyword branching | none (pure Kotlin) |
| `progress` | Progress model, streak logic, missed-word counts, persistence to a JSON file in app storage | kotlinx.serialization |
| `speech` | Wrapper over Android `SpeechRecognizer` (en-IN, prefer offline, partial results) | Android |
| `voice` | Wrapper over Android `TextToSpeech` (en-IN and hi-IN voices, rate) | Android |
| `ui` | Screens: onboarding, lesson map, lesson player, lesson complete, sticker book, parent gate, parent corner, settings | all of the above |

`scoring`, `lesson` parsing and `progress` logic are plain Kotlin with JVM unit tests.

### Permissions

`RECORD_AUDIO` only (requested at first lesson). A `<queries>` entry for the
`android.speech.RecognitionService` intent so speech recognition works on Android 11+.

### Error handling

- No speech recogniser on the device: friendly screen explaining the Google app is needed.
- Recognition error or silence: Tara says "I didn't hear you. Tap the mic and try again." and the
  attempt does not count as a failure.
- Hindi voice missing: Hindi help is shown as text only, and the parent corner explains how to
  install the Hindi voice.
- Corrupt progress file: start fresh rather than crash.

## 4. Testing

- JVM unit tests: scoring (normalisation, matching, stars, pattern slots, branching), lesson file
  validation (all lessons parse, ids unique, every step has text and at least one accepted answer
  where applicable), progress and streak logic.
- Build check: `assembleRelease` succeeds and produces a signed APK.
- Manual test on the owner's phone (microphone, voices, UI), since no device or emulator is
  available in the build environment.

## 5. Delivery

- Source in `apps/bolo-english/` on the working branch.
- Release APK signed with a key generated for this app; the keystore is kept with the source
  (personal-use project) so updates install over earlier versions without losing progress.
- APK sent directly to the owner with install instructions (allow "Install unknown apps", install
  the offline English speech pack and Hindi voice for best results).
