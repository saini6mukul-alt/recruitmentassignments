# Bolo English 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Hindi-to-English helper, Talk with Tara (Gemini), and unlock everything.
**Architecture:** New `translate` and `chat` packages with pure-Kotlin cores (normaliser, phrasebook, Gemini JSON, orchestrator) unit-tested; thin Android adapters (ML Kit, HTTP); new Compose screens.
**Tech Stack:** as v1 plus `com.google.mlkit:translate:17.0.3`, kotlinx-coroutines-android 1.8.1; HTTP via `HttpURLConnection`.
**Spec:** `docs/superpowers/specs/2026-11-19-bolo-english-v2-design.md`
**Execution:** native, inline (owner approved building straight through).

## Global Constraints
- versionCode 2, versionName "2.0", same signing key as 1.0.
- Permissions: RECORD_AUDIO, INTERNET only.
- Gemini endpoint `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=KEY`; model list `.../v1beta/models?key=KEY&pageSize=1000`.
- Never say "wrong" to the child.

## Review Focus
1. Hindi speech recognition output with different nukta/chandrabindu/punctuation -> still matches phrasebook.
2. Gemini replies wrapped in ```json fences or with extra text -> still parsed; garbage -> null (fallback).
3. No internet / bad key / quota error -> falls back to ML Kit without crashing; clear message for chat.
4. ML Kit model not yet downloaded and offline -> friendly message, phrasebook still works.
5. Saved sentences duplicates and cap -> de-duplicated, max 200.

### Task 1: Unlock everything
- [ ] Test: every lesson unlocked with empty progress (replace unlock tests). Remove `isUnlocked` rules (always true) or delete usage; remove ParentGate screen; Home ⚙️ opens Parent directly.

### Task 2: Hindi normaliser and phrasebook
- Files: `translate/HindiNormalizer.kt`, `translate/Phrasebook.kt`, `assets/phrasebook.json`, `tools/gen_phrasebook.py`; tests `translate/PhrasebookTest.kt`, `translate/PhrasebookContentTest.kt`.
- Interfaces: `object HindiNormalizer { fun normalize(text: String): String; fun tokens(text: String): List<String> }`; `@Serializable data class Phrase(val hi: List<String>, val en: String, val tip: String = "")`; `class Phrasebook(val phrases: List<Phrase>) { fun match(text: String): Phrase?; companion object { fun parse(json: String): Phrasebook } }`.
- [ ] Tests: punctuation/nukta/chandrabindu variants match; extra word still matches; unrelated sentence -> null; content parses, >= 120 entries, every English sentence non-blank, no duplicate Hindi variants.

### Task 3: Gemini JSON
- Files: `translate/Gemini.kt` (pure); test `translate/GeminiTest.kt`.
- Interfaces: `data class Translation(val hindi: String, val english: String, val explanation: String = "", val words: List<Pair<String,String>> = emptyList(), val source: Source)`, `enum class Source { PHRASEBOOK, GEMINI, MLKIT }`; `data class ChatTurn(val fromChild: Boolean, val text: String)`; `data class TaraReply(val english: String, val hindi: String, val correction: String)`; `object GeminiJson { fun translateRequest(hindi: String, age: String): String; fun chatRequest(history: List<ChatTurn>, age: String): String; fun parseTranslation(hindi: String, response: String): Translation?; fun parseChat(response: String): TaraReply?; fun pickModel(modelsJson: String): String? }`.
- [ ] Tests: request JSON contains text + responseMimeType; parse plain / fenced JSON; garbage -> null; pickModel prefers newest non-preview flash over lite/preview/image/tts.

### Task 4: Translation service
- Files: `translate/TranslationService.kt`; test `translate/TranslationServiceTest.kt`.
- Interfaces: `fun interface TextTranslator { suspend fun translate(hindi: String): Translation? }`; `class TranslationService(phrasebook, online: TextTranslator?, offline: TextTranslator) { suspend fun translate(hindi: String): Translation? }`.
- [ ] Tests: phrasebook first; online when no phrase; offline when online null/throws or absent; blank input -> null.

### Task 5: Progress additions
- `SavedSentence(hindi, english, stars, addedAt)`, `Progress.savedSentences`, `Settings.geminiKey`, `Settings.geminiModel`; `ProgressLogic.saveSentence(p, s)` de-dup by English (case-insensitive), newest first, cap 200; `ProgressLogic.updateSentenceStars`.
- [ ] Tests for de-dup, cap, stars keep best; repository round-trip with new fields; old v1 file still loads.

### Task 6: Android adapters
- `translate/MlKitTranslator.kt` (TextTranslator, downloads model), `translate/GeminiClient.kt` (HTTP, picks/caches model, translate + chat), `SpeechListener.start(language, onEvent)`; manifest INTERNET; deps. Compile check.

### Task 7: Screens
- Home cards, `ui/TranslateScreen.kt`, `ui/ChatScreen.kt`, `ui/MySentencesScreen.kt`, parent corner key section; view-model state for translate and chat. Build + tests.

### Task 8: Release 2.0
- versionCode 2; assembleRelease with existing key; verify signature/permissions; commit, push, send APK.
