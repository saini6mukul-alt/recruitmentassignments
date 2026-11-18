# Supernova-style AI Spoken English Tutor for Kids: MVP Design

**Date:** 2026-11-18
**Status:** Approved in design review, awaiting written-spec review
**Working name:** Nova Kids (placeholder; final brand to be chosen)

## 1. Purpose and agreed understanding

Build a production mobile app, similar in concept to Supernova AI Spoken English, that helps
Indian children practise spoken English with an AI voice tutor ("Nova"). This document covers
the first sub-project: the MVP.

| Decision | Choice |
|---|---|
| Learners | Children aged 4-12; a parent account owns all data and settings |
| Tutor language | English, with Hindi as the support language (more languages later, no code change) |
| Platforms | Android and iOS from one codebase |
| MVP goal | Free closed beta with 50-200 families to prove children come back; billing deferred |
| Architecture | Turn-based "tap-to-talk" pipeline with swappable vendors (Approach A) |

### Beta success criteria

- At least 40% of children practise 3 or more times per week in week 4.
- Average pronunciation score rises over 4 weeks.
- Zero child-safety incidents.
- p90 tutor turn latency under 3 seconds, measured from India.

### Out of scope for the MVP

Payments and subscriptions, additional support languages, adult mode, free-chat mode,
leaderboards and social features, human mentor calls, and a content-editing admin panel.
Each becomes its own later sub-project with its own spec.

## 2. User experience

### Parent (account owner)

1. Signs up with phone number and OTP.
2. Gives explicit consent on a screen that explains voice processing, what is stored, retention,
   and that AI processing happens outside India. Consent is versioned; a new policy version
   requires fresh consent.
3. Creates 1-3 child profiles: nickname, age band (4-6, 7-9, 10-12) and avatar. No surname, date
   of birth, photo or school is collected.
4. Opens the parent dashboard behind a parental gate (a multiplication question). The dashboard
   shows minutes practised, lessons completed, pronunciation trend, words learned, safety
   flags, and controls to export or delete data.

### Child (voice-first, minimal reading)

1. Taps their avatar and sees a lesson map of about 30 lessons (5-8 minutes each) grouped into
   themes: Me & My Family, Animals, Food, At School, Feelings.
2. Each lesson mixes four activity types:
   - **Listen & Repeat:** Nova says a word or sentence; the child repeats it and earns 1-3 stars
     from the pronunciation score. No AI call is needed.
   - **Picture Talk:** Nova asks about a picture; the child answers; Nova recasts the answer
     correctly ("Yes! The cat *is* sleeping.").
   - **Mini role-play:** 3-4 guided turns (e.g. ordering ice-cream) with fixed goals.
   - **Help in Hindi:** after two failed attempts on a step, Nova explains in simple Hindi, then
     returns to English.
3. Stars and a daily streak (IST day boundaries) reward practice.

Large tap targets, pictures and audio instructions are used throughout because many 4-6 year
olds cannot read yet.

## 3. System architecture

```
supernova-kids/
├── apps/mobile/        Expo (React Native, TypeScript): child UI, parent UI, recording, playback
├── services/api/       Node.js + TypeScript (Fastify): the only backend the app talks to
│   ├── auth/           phone OTP via SMS provider (MSG91) -> short-lived JWT
│   ├── family/         consent records, child profiles, export, deletion
│   ├── lessons/        loads and validates lesson files, serves the lesson map
│   ├── tutor/          turn orchestrator
│   │   ├── SpeechAnalyzer  interface -> Azure AI Speech adapter (transcript + pronunciation)
│   │   ├── SafetyGuard     interface -> input and output checks
│   │   ├── TutorBrain      interface -> Claude adapter (structured feedback)
│   │   └── VoiceSynth      interface -> ElevenLabs or Azure TTS adapter
│   └── progress/       stars, streaks, scores, dashboard aggregates
├── packages/shared/    shared types and the lesson schema (Zod)
└── content/lessons/    lesson JSON files, human-reviewed before release
```

### Turn flow (`POST /turns`)

1. App uploads the audio clip with `childId`, `lessonId`, `stepId`.
2. `SpeechAnalyzer` returns transcript plus pronunciation scores. Listen & Repeat uses scripted
   assessment against the known target text; other steps use unscripted assessment.
3. `SafetyGuard.checkInput` removes personal details, detects distress words and off-topic
   speech (see section 4).
4. For Picture Talk, role-play and help steps, `TutorBrain` calls Claude and returns structured
   feedback. Listen & Repeat skips this and uses scripted praise chosen by score.
5. `SafetyGuard.checkOutput` validates the reply.
6. `VoiceSynth` produces audio for dynamic text; scripted lines use pre-generated audio.
7. API returns `{ text, hindiText?, stars, audioUrl, nextStep }` and records the turn.

Each stage is a small unit behind an interface so it can be unit-tested with fakes and replaced
without changing the orchestrator.

### Performance and cost techniques

- **Pre-generated scripted audio:** every fixed Nova line in lesson files is synthesised at build
  time and served from a CDN. Live synthesis is only for dynamic feedback.
- **Prompt caching:** the fixed tutor instructions are cached so each turn sends only the step
  context and the child's transcript.

### Hosting and data location

- API on Google Cloud Run, region asia-south1 (Mumbai); Postgres on Cloud SQL in Mumbai.
- Azure AI Speech in the Central India region.
- Child audio is processed in memory and never stored. Transcripts and scores are stored.
- Claude processing happens outside India; this is disclosed on the consent screen.

## 4. AI tutor and child safety

### Scope of the AI

Claude is only called inside a lesson step. There is no open chat. Each request includes:

- Tutor instructions: Nova's personality, child's age band, "never say wrong; correct by
  recasting", reply length cap (1-2 short sentences for ages 4-6), Hindi only on help steps.
- Step goal, target words and example good answers from the lesson file.
- The child's transcript and pronunciation scores.

### Structured output

Claude returns JSON validated against a strict schema (via `output_config.format`):

```json
{
  "replyEnglish": "string",
  "replyHindi": "string | null",
  "correctionShown": "boolean",
  "stars": "0 | 1 | 2 | 3",
  "stepOutcome": "advance | retry | help"
}
```

Nothing that fails the schema is ever spoken.

### Model configuration

- Model `claude-opus-5-5`, `output_config.effort: "low"`, adaptive thinking (the model default).
- Prompt caching on the fixed instruction prefix.
- Server-side refusal fallback enabled (`server-side-fallback-2026-07-01` beta,
  `fallbacks: "default"`); can be disabled by the product owner.
- `claude-haiku-5-5` is evaluated against Opus 5.5 on the tutor-quality eval set (section 6);
  the product owner decides the production model from quality and cost per turn.
- The `TutorBrain` adapter uses the official Anthropic TypeScript SDK (`@anthropic-ai/sdk`).

### Safety layers

1. **Scripted scoring where possible:** Listen & Repeat compares against known target text and
   does not depend on free-form transcription quality.
2. **Input guard (before the AI):**
   - Redact personal details (phone numbers, addresses, school names).
   - Distress or safety words bypass the AI completely: Nova plays a fixed line ("That sounds
     important. Let's tell a grown-up!") and a `safety_events` row flags the parent dashboard.
   - Off-topic speech gets a scripted redirect back to the lesson.
3. **Output guard (after the AI):** schema validation, blocked-word list, length limit, language
   check. Any failure replaces the reply with a scripted encouraging line.
4. **No cross-session memory:** each lesson starts fresh; the AI only sees nickname and age band.
5. **Human oversight:** every lesson file is reviewed before release; a random sample of AI turns
   is reviewed weekly during the beta; transcripts are kept 90 days (parents can delete sooner).

## 5. Data model and API

### Postgres tables

| Table | Key fields | Notes |
|---|---|---|
| `parents` | id, phone_hash, phone_encrypted, created_at | Phone is the only identifier |
| `consents` | parent_id, policy_version, granted_at, revoked_at | New version requires re-consent |
| `children` | id, parent_id, nickname, age_band, avatar, deleted_at | No surname, DOB or photo |
| `lesson_progress` | child_id, lesson_id, status, stars, completed_at | One row per child per lesson |
| `turns` | id, child_id, lesson_id, step_id, transcript, pron_scores, ai_reply, safety_flags, latency_ms, model, created_at | Purged after 90 days by nightly job |
| `safety_events` | id, child_id, turn_id, kind, created_at, reviewed_by | Dashboard flag and review queue |
| `streaks` | child_id, current, longest, last_active_date | IST day boundaries |

Lessons are versioned JSON files validated against the shared Zod schema at API start-up. A step
has: `id`, `type` (`listen_repeat` | `picture_talk` | `roleplay`), scripted Nova lines with audio
references, target text or goal, example answers, Hindi help text, and an image reference.

### Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /auth/otp/request`, `POST /auth/otp/verify` | Parent login; rate-limited; 6-digit OTP, 5-minute expiry |
| `POST /consent` | Record consent for the current policy version |
| `GET`, `POST`, `PATCH`, `DELETE /children` | Child profiles; `DELETE` erases all of the child's data |
| `GET /lessons?childId=` | Lesson map with the child's progress |
| `GET /lessons/:id` | Lesson steps and scripted audio URLs |
| `POST /turns` | Core tutor turn (section 3) |
| `GET /dashboard?childId=` | Parent dashboard aggregates; app requires the parental gate |
| `GET /export`, `DELETE /account` | Data export and full account deletion (DPDP rights) |

### Error handling

- Every external call (speech, Claude, voice, SMS) has a timeout and one retry.
- A failed turn never shows an error screen to the child: Nova plays a scripted line ("Oops, I
  didn't hear that. Can you say it again?"), and the failure is logged with cause and latency.
- Turns over 2 seconds show a "Nova is thinking" animation.

## 6. Testing

1. **Unit tests (test-first)** for every module; tutor stages tested with fakes, offline.
2. **Adapter tests** against recorded vendor responses, plus manually triggered live smoke tests.
3. **Evaluation sets:**
   - Kids' speech set: about 100 recordings from consenting beta families to measure
     transcription accuracy on young Indian voices (largest technical risk).
   - Tutor quality set: Claude replies scored on a rubric (encouraging, correct, length, Hindi use).
     Used to choose between Opus 5.5 and Haiku 5.5.
   - Safety set: about 50 adversarial inputs (personal details, distress, rude words, attempts to
     leave the lesson). Must pass 100% before any release.
4. **End-to-end tests** with Maestro on an Android emulator: signup, consent, full lesson, dashboard.
5. **Latency check:** p90 turn latency under 3 seconds from India.

## 7. Cost estimate (AI model only)

Assumptions per AI turn: about 3,000 cached instruction tokens, 500 fresh input tokens, 350 output
tokens. About 5 AI turns per lesson because Listen & Repeat steps do not call Claude.

| Model | Per AI turn | Per lesson | Per child per month (1 lesson/day) |
|---|---|---|---|
| Opus 5.5 | ~$0.01 | ~$0.05 | ~$1.50 (about ₹125) |
| Haiku 5.5 | ~$0.0003 | ~$0.0015 | ~$0.05 (about ₹4) |

At Opus pricing, AI cost alone would be roughly 40% of a ₹299 monthly subscription before speech
and voice costs, so the Haiku evaluation is a required step before the paid phase. Azure Speech and
ElevenLabs costs will be measured during the beta.

## 8. Build order

1. Foundation: repo, CI, API skeleton, OTP login, consent, child profiles.
2. Lesson engine and Listen & Repeat with pronunciation stars and pre-generated audio.
3. AI turns: Picture Talk, role-play, Hindi help, all safety layers, eval sets.
4. Parent dashboard, streaks, export and deletion.
5. Content: 30 lessons, Hindi review, audio generation.
6. Internal testing, then closed beta via Google Play internal testing and TestFlight.

## 9. Dependencies the product owner must provide

- Accounts and API keys: Anthropic, Azure AI Speech, ElevenLabs, MSG91, Google Cloud.
- Apple Developer Program ($99/year) and Google Play Console ($25 one-time).
- Lawyer-reviewed privacy policy and consent text for children's data (DPDP Act 2023, Apple Kids
  Category, Google Families Policy).
- A Hindi-speaking reviewer for lesson content.
- Beta families, including consent for the kids' speech evaluation recordings.

## 10. Open risks

- Speech-to-text accuracy on young children's Indian-accented English may be too low for
  free-speech steps; mitigation is the kids' speech eval in milestone 3, with a fallback to more
  scripted steps if accuracy is poor.
- App store review for children's apps can reject third-party analytics or external links; the
  MVP uses no third-party analytics SDKs in the app.
- Unit economics depend on the Opus vs Haiku decision and measured speech and voice costs.
