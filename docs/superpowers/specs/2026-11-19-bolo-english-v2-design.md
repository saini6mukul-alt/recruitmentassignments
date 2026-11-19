# Bolo English 2.0: Hindi-to-English Helper, Talk with Tara, Everything Unlocked (Design)

**Date:** 2026-11-19
**Status:** Approved in chat (owner chose "both" translation engines)
**Extends:** `2026-11-18-bolo-english-personal-app-design.md`

## Goals

1. A child says (or types) any Hindi sentence and gets proper, child-friendly English with its
   meaning, then practises saying it in English with word-level feedback.
2. A free conversation mode ("Tara से बातें") where the child talks to Tara in Hindi or English and
   Tara replies in simple English, gently correcting the child's English. Requires a Gemini key.
3. Nothing is locked: all lessons open; no parental maths gate.

## Translation pipeline (Hindi -> English)

1. **Phrasebook** (`assets/phrasebook.json`, ~150 curated child sentences with Hindi variants,
   English and a short Hindi tip). Matching uses a Hindi normaliser (punctuation, nukta,
   chandrabindu, zero-width characters, spacing) and a similarity threshold.
2. **Gemini** (only when a key is set and the device is online): JSON reply with `english`,
   `explanation_hi` and up to 5 key `words` (hi/en). Model chosen automatically from the account's
   model list (newest non-preview "flash" model supporting generateContent; fallbacks: flash-lite,
   then preview), cached in settings, re-chosen once if the call fails with a model error.
3. **ML Kit on-device translation** (`com.google.mlkit:translate:17.0.3`, Hindi -> English); model
   downloaded once (~30 MB), offline afterwards.

If all fail, Tara says she could not translate and asks the child to try a shorter sentence.

## Screens

- **Home:** three big cards at the top — "🗣️ हिंदी → English", "💬 Tara से बातें", "📚 मेरे वाक्य" —
  then all lessons (no locks) and the sticker book; a ⚙️ button opens the parent corner directly.
- **Hindi -> English:** mic in Hindi (`hi-IN`) or typing; shows Hindi heard, English answer, meaning
  and word tips; 🔊 hear again, 🐢 word-by-word; "अब तुम बोलो" mic in English scored with the
  existing scorer (green/orange words, stars, retries); saved to My Sentences.
- **Tara से बातें:** chat bubbles; two mics (English / हिंदी) plus typing; Tara's English reply is
  spoken, optional Hindi line spoken with the Hindi voice, correction shown highlighted. Without a key
  the screen explains how to get a free key.
- **मेरे वाक्य:** saved sentences (newest first, up to 200, de-duplicated by English), tap to practise.
- **Parent corner:** existing content plus Gemini API key field with a "Test key" button and
  instructions; "Reset progress" keeps its confirmation dialog.

## Child safety (chat)

System instruction: Tara is a friendly English tutor for an Indian child aged N; short replies
(max 2 sentences), simple words, child-appropriate topics only, never asks for personal details
(full name, address, school, phone), redirects unsafe or adult topics back to English practice,
encourages the child to talk to a parent if they sound upset. Replies are JSON
`{reply_en, reply_hi, correction}`; anything unparseable is replaced by a friendly fallback line.

## Permissions and privacy

Adds `INTERNET` (ML Kit model download, Gemini). Without a key nothing is sent to Gemini.
With a key, the child's sentences go to Google's Gemini API (free tier may be used by Google to
improve products); the parent corner states this next to the key field.

## Testing

JVM unit tests: Hindi normaliser and phrasebook matching; phrasebook content validity; Gemini
request building, response parsing and model picking; translation service order and fallbacks
(with fakes); saved sentences and new settings; lessons all unlocked. Manual on-device test by the
owner. Version 2.0 (versionCode 2) signed with the same key so it installs over 1.0.
