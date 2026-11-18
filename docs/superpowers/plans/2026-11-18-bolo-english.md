# Bolo English Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a free, offline-first native Android app (signed APK) where a child practises spoken English with Tara the parrot using scripted lessons and word-level scoring.

**Architecture:** Single-module Android app. Pure-Kotlin units (`lesson`, `scoring`, `progress`) carry all logic and are JVM unit-tested; thin Android wrappers (`speech`, `voice`) adapt `SpeechRecognizer` and `TextToSpeech`; Compose screens driven by one `AppViewModel` with a simple screen-state enum (no navigation library).

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.14.3, JDK 21, Jetpack Compose (BOM 2024.09.03), Material 3, kotlinx.serialization-json 1.7.3, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-18-bolo-english-personal-app-design.md`

**Execution:** The owner approved proceeding straight to build and delivery; executed natively (inline) in this session.

## Global Constraints

- Package `com.bolo.english`; app name "Bolo English"; tutor "Tara" the parrot.
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`.
- Permissions: `RECORD_AUDIO` only. No `INTERNET` permission. No analytics or ads.
- Stars: ratio >= 0.9 -> 3, >= 0.7 -> 2, >= 0.4 -> 1, else 0. A step passes at >= 1 star.
- Hindi help after 2 failed attempts on a step; Skip button appears at the same time.
- Never display or speak the word "wrong".
- 24 lessons, 6 themes x 4 lessons, 5-8 steps each.
- Progress stored as JSON in app-private storage; corrupt file -> fresh defaults.

## Review Focus

1. Recogniser returns digits ("I am 7") where lessons expect words ("seven") -> numbers 0-20 normalised to words, both directions match.
2. Spelling and regional variants ("color"/"colour", "mom"/"mummy"/"mommy", "it's"/"it is") -> treated as equal.
3. Child adds filler or extra words ("umm it is a cat yes") -> extra words ignored; only expected words count.
4. Recogniser returns nothing or errors -> attempt not counted as a failure; Tara asks to try again.
5. Missing day(s) and month/year boundaries in streak calculation -> streak resets correctly; today-or-yesterday continuity.

---

### Task 1: Project scaffold and build

**Files:**
- Create: `apps/bolo-english/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/bolo/english/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, launcher icon (adaptive vector), `.gitignore`

- [ ] Gradle files with versions from Tech Stack; Compose + serialization plugins; `<queries>` for `android.speech.RecognitionService`; `RECORD_AUDIO`.
- [ ] Run `gradle :app:assembleDebug` -> BUILD SUCCESSFUL.
- [ ] Commit.

### Task 2: Lesson model, parser, validator

**Files:** `app/src/main/java/com/bolo/english/lesson/Lesson.kt`, `LessonValidator.kt`; test `app/src/test/java/com/bolo/english/lesson/LessonParserTest.kt`

**Interfaces (Produces):**
```kotlin
@Serializable enum class StepType { @SerialName("repeat") REPEAT, @SerialName("picture") PICTURE, @SerialName("question") QUESTION, @SerialName("roleplay") ROLEPLAY }
@Serializable data class Branch(val keywords: List<String>, val reply: String)
@Serializable data class Step(val id: String, val type: StepType, val say: String, val picture: String? = null,
    val answer: String, val accepted: List<String> = emptyList(), val hindi: String,
    val branches: List<Branch> = emptyList(), val reply: String? = null)
@Serializable data class Lesson(val id: String, val title: String, val emoji: String, val sticker: String, val steps: List<Step>)
@Serializable data class Theme(val id: String, val title: String, val emoji: String, val lessons: List<Lesson>)
@Serializable data class LessonBook(val themes: List<Theme>) { val allLessons: List<Lesson> }
object LessonParser { fun parse(json: String): LessonBook }
object LessonValidator { fun validate(book: LessonBook): List<String> } // empty list = valid
```
`answer` is the model sentence Tara repeats back; `accepted` are extra patterns; `{any}` is a slot.

- [ ] Tests: parses a minimal book; validator reports duplicate ids, empty `say`/`answer`/`hindi`, lesson with <5 or >8 steps, unknown `{slot}` syntax.
- [ ] Implement, run `gradle :app:testDebugUnitTest`, commit.

### Task 3: Scoring engine

**Files:** `scoring/TextNormalizer.kt`, `scoring/Scorer.kt`; tests `scoring/TextNormalizerTest.kt`, `scoring/ScorerTest.kt`

**Interfaces (Produces):**
```kotlin
object TextNormalizer { fun words(text: String): List<String> }  // lowercase, strip punctuation, expand contractions, digits 0-20 -> words, variant map
data class WordMark(val word: String, val heard: Boolean)
data class ScoreResult(val stars: Int, val ratio: Double, val marks: List<WordMark>) { val passed: Boolean get() = stars >= 1 }
object Scorer {
  fun score(heard: List<String>, step: Step): ScoreResult   // heard = recogniser alternatives
  fun starsFor(ratio: Double): Int
  fun chooseReply(heard: List<String>, step: Step): String? // first branch whose keyword appears, else step.reply
}
```
Matching: for each alternative x each pattern (`answer` + `accepted`), ratio = matched expected units / expected units; word matches exactly or by edit distance <= 1 when length >= 4; `{any}` unit matches when the alternative contains a word not in the pattern. Best ratio wins; marks are for `answer` words against the best alternative.

- [ ] Tests: exact match 3 stars; one missing word of five -> 2 stars (0.8); filler words ignored; "I am 7" matches "I am seven"; "color" matches "colour"; "its a cat" matches "it is a cat"; `{any}` slot; edge distance tolerance ("elefant"); empty input -> 0 stars; thresholds 0.9/0.7/0.4; `chooseReply` keyword and default.
- [ ] Implement, run tests, commit.

### Task 4: Progress logic and storage

**Files:** `progress/Progress.kt`, `progress/ProgressRepository.kt`; tests `progress/ProgressTest.kt`, `progress/ProgressRepositoryTest.kt`

**Interfaces (Produces):**
```kotlin
@Serializable data class Settings(val slowSpeech: Boolean = false, val hindiHelp: Boolean = true)
@Serializable data class Progress(val childName: String = "", val ageBand: String = "4-6",
  val lessonStars: Map<String, Int> = emptyMap(), val practiceDays: Set<String> = emptySet(),
  val practiceSeconds: Long = 0, val missedWords: Map<String, Int> = emptyMap(), val settings: Settings = Settings()) {
  val isOnboarded: Boolean; val totalStars: Int
}
object ProgressLogic {
  fun completeLesson(p: Progress, lessonId: String, stars: Int, today: LocalDate, seconds: Long): Progress // keeps best stars
  fun recordMisses(p: Progress, words: List<String>): Progress
  fun topMissed(p: Progress, n: Int = 10): List<Pair<String, Int>>
  fun streak(p: Progress, today: LocalDate): Int
  fun isUnlocked(book: LessonBook, p: Progress, lessonId: String): Boolean // first lesson of each theme open; next opens when previous completed
}
class ProgressRepository(private val file: File) { fun load(): Progress; fun save(p: Progress) }
```
- [ ] Tests: best stars kept; streak today/yesterday/gap/month boundary; topMissed ordering; unlock rules; repository round-trip; corrupt file -> default; missing file -> default.
- [ ] Implement, run tests, commit.

### Task 5: Lesson content

**Files:** `app/src/main/assets/lessons.json`; test `lesson/LessonContentTest.kt` (reads `src/main/assets/lessons.json` from disk)

- [ ] Write 6 themes x 4 lessons, 5-8 steps each, age-appropriate simple English, Hindi help lines in Devanagari, emoji pictures, roleplay branches.
- [ ] Test: real file parses, validator returns no errors, exactly 24 lessons, every `answer` scores 3 stars against itself.
- [ ] Run tests, commit.

### Task 6: Speech and voice wrappers

**Files:** `speech/SpeechListener.kt`, `voice/TaraVoice.kt`

**Interfaces (Produces):**
```kotlin
sealed interface SpeechEvent { object Ready; data class Partial(val text: String); data class Results(val alternatives: List<String>); data class Error(val noMatch: Boolean, val code: Int) }
class SpeechListener(context: Context) { val isAvailable: Boolean; fun start(onEvent: (SpeechEvent) -> Unit); fun stop(); fun destroy() }
class TaraVoice(context: Context) { fun speak(text: String, hindi: Boolean = false, onDone: () -> Unit = {}); fun setSlow(slow: Boolean); val hindiAvailable: Boolean; fun shutdown() }
```
`SpeechListener` uses `en-IN`, `EXTRA_PREFER_OFFLINE`, `EXTRA_MAX_RESULTS = 5`, `EXTRA_PARTIAL_RESULTS`. Compile check only (Android classes; manual test on device).

- [ ] Implement, `gradle :app:assembleDebug`, commit.

### Task 7: UI and view model

**Files:** `AppViewModel.kt`, `ui/Theme.kt`, `ui/OnboardingScreen.kt`, `ui/HomeScreen.kt`, `ui/LessonScreen.kt`, `ui/LessonDoneScreen.kt`, `ui/StickerBookScreen.kt`, `ui/ParentGate.kt`, `ui/ParentScreen.kt`, `MainActivity.kt`; test `lesson/AttemptLogicTest.kt`

**Interfaces:**
```kotlin
object AttemptLogic { fun next(failedAttempts: Int, result: ScoreResult?, hindiHelpOn: Boolean): AttemptOutcome }
enum class AttemptOutcome { PASS, RETRY, HINDI_HELP, NOT_HEARD }  // null result -> NOT_HEARD (not a failure)
```
- [ ] Test `AttemptLogic`: pass; first fail -> RETRY; second fail -> HINDI_HELP (or RETRY if Hindi help off, skip still shown); null -> NOT_HEARD without incrementing.
- [ ] Screens per spec; mic permission request; no-recogniser screen; Hindi text fallback; parental gate with random two-digit multiplication.
- [ ] `gradle :app:testDebugUnitTest :app:assembleDebug`, commit.

### Task 8: Release signing and delivery

- [ ] Generate `release.jks` (keytool), signing config reading `keystore.properties`; `gradle :app:assembleRelease`.
- [ ] Verify with `apksigner verify` and `aapt dump badging` (package, permissions, sdk levels).
- [ ] Copy APK to `apps/bolo-english/dist/BoloEnglish-1.0.apk`; write `apps/bolo-english/README.md` with install steps; commit and push; send APK to owner.
