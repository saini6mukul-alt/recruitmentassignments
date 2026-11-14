# Lehar v1: Design Spec

- **Date:** 2026-11-14
- **Status:** Approved in chat (concept, design sections 1-5, search source = Option 1). Awaiting written-spec review.
- **Owner:** personal project, single user, Android phone

## 1. Goal

A personal, dark and minimal Android music app that streams (not local files) and fixes the main
drawbacks of Spotify, Apple Music, YouTube Music, JioSaavn/Gaana, SoundCloud and Endel.

**Concept chosen: Hybrid** = *Mood Pad* home screen (concept A) + *One Screen Flow* full-screen player (concept B).
Concept E (Hook Swipe) is planned for v2 and concept D (Music Diary) for v3. Both are out of scope here.

### Success criteria for v1

1. The APK installs on the user's Android phone and opens to the Mood Pad home in under 2 seconds on a warm start.
2. Dragging the mood dot or tapping a chip produces a playable queue of at least 15 songs mixing Hindi, Punjabi and English.
3. Search returns songs and artists as separate sections while the user types.
4. Synced lyrics appear when LRCLIB has them, with a user-adjustable offset.
5. Playback continues with the screen locked or another app in front, and lock-screen/notification controls work.
6. Autoplay never repeats a song played in the last 3 hours or among the last 50 plays.
7. Library (liked songs, playlists, recent, most played) survives app restarts.

### Non-goals (v1)

- Accounts, login, cloud sync, multiple users
- Offline audio (not possible with the YouTube player)
- Play Store publishing (YouTube terms do not allow background playback, so this is a personal APK only)
- Hook Swipe discovery, Music Diary, daily stats, playlist import, crossfade (v2/v3)
- iOS

## 2. What the user said vs what is assumed

| Said by user | Assumption |
|---|---|
| Personal use and testing, on an Android phone | Single user. All data stays on the phone. |
| Music source should work like the NAUX project | YouTube embedded player inside an Android WebView, plus NAUX's background-playback fixes |
| Mixes Hindi, Punjabi and English | Default language mix of 40 / 30 / 30, editable in Settings |
| Dark and minimal | True-black (AMOLED) base with one marigold accent |
| Search via YouTube Music's internal API (Option 1) | No API key is needed, and the call can break if YouTube changes it |

## 3. Architecture

```
Android APK (Java, built without Gradle using aapt2 + javac + d8 + apksigner, like NAUX)
├── Native layer (Java)
│   ├── MainActivity        hosts BgWebView, JS bridge, battery-exemption prompt
│   ├── BgWebView           always reports VISIBLE to Chromium so media is not paused in background (from NAUX)
│   ├── AssetServer         serves bundled UI at https://appassets.androidplatform.net/ via shouldInterceptRequest
│   ├── PlaybackService     foreground service + MediaSession + MediaStyle notification (framework APIs, no androidx)
│   ├── AudioFocus          pauses on call / other app audio, resumes when it ends
│   ├── Net                 HTTP for YouTube Music internal API and LRCLIB (native side, so CORS does not apply)
│   └── Watchdog            resumes playback if the engine paused it, but never if the user paused it or other audio plays
└── UI layer (HTML/CSS/JS bundled in assets/www)
    ├── screens: home (Mood Pad), player, search, library, settings
    ├── engine:  mood.js (vibe mapping), queue.js (queue + repeat guard), ytm.js (API parsing), lyrics.js (LRC parsing)
    ├── player:  two hidden YouTube IFrame players (current + preloaded next)
    └── store:   IndexedDB (library, history, settings, caches)
```

### Why the UI is bundled and not a website
The UI loads instantly, the design is fully ours, and the app has no server to host. The YouTube IFrame player
throws an error when loaded from `file://`, so assets are served from the `https://appassets.androidplatform.net` origin
through a custom `shouldInterceptRequest` handler.

### JS ↔ native bridge (`window.LeharNative`)

| Call (JS → native) | Purpose |
|---|---|
| `request(id, url, method, headersJson, body)` | Async HTTP. The result is delivered through `window.__lehar.resolve(id, status, text)` |
| `setNowPlaying(json)` | Title, artist, art URL, duration, isPlaying. Updates the MediaSession and notification |
| `setPosition(ms)` | Updates the notification/lock-screen seek bar |
| `stopService()` | Clears the notification after a sleep timer or when the queue ends |
| `toast(text)` | Shows a short native message |

| Event (native → JS, via `window.__lehar.onMedia(action)`) |
|---|
| `play`, `pause`, `next`, `prev`, `seek:<ms>`, `focusLoss`, `focusGain` |

## 4. Data sources

### 4.1 YouTube Music internal API (search, suggestions, radio)
- Base: `https://music.youtube.com/youtubei/v1/<endpoint>?prettyPrint=false`, POST JSON with
  `context.client = { clientName: "WEB_REMIX", clientVersion: <pinned>, hl: "en", gl: "IN" }`
- `search` with the songs filter param, which gives songs only. A call without the filter returns top results, from which artists are taken.
- `music/get_search_suggestions` returns typeahead suggestions
- `next` with `playlistId = "RDAMVM" + videoId` returns the radio (similar songs) used by autoplay
- Parsing lives in `ytm.js` as pure functions that turn a response into `Track[]`, unit-tested against saved fixture JSON.
- The client version is pinned in one constant. If the response shape changes, parsers return `[]` and the UI shows
  "Search abhi kaam nahi kar raha" instead of crashing.

### 4.2 Playback: YouTube IFrame API
- Two players: `A` (current) and `B` (cues the next track to remove the start-up gap).
- Errors `100`, `101` and `150` (unavailable or embedding disabled) skip to the next track and queue a search for
  `"<title> <artist> audio"` as an alternate version.

### 4.3 Lyrics: LRCLIB
- `GET https://lrclib.net/api/get?track_name&artist_name&duration`, falling back to `/api/search?q=`
- `syncedLyrics` (LRC) is parsed by `lyrics.js`. When only plain lyrics exist, they show statically. When nothing is found, art is shown alone.
- Results are cached in IndexedDB by videoId. The offset is adjustable per song in ±0.5s steps.

## 5. Screens

| Screen | Contents | Gestures |
|---|---|---|
| **Home (Mood Pad)** | Prompt text, a 2D pad (x: Shaant → Energy, y: Udaas → Khush) with a draggable glowing dot, chips (Gym, Drive, Focus, Sleep, Party, Sad), a "Is mood ki queue" row of covers, Recently played, mini-player, search icon | Drag dot (queue rebuilds on release), swipe up = search |
| **Player (One Screen)** | Full-bleed art with gradient; lyrics overlaid (previous line dim, current bright, next dim); title/artist; seek bar; prev/play/next; shuffle/repeat; queue sheet; ⋯ menu (lyrics offset, sleep timer, add to playlist) | ← → = prev/next, double-tap = like, swipe down = minimise |
| **Search** | Input with suggestions; sections Songs and Artists; tapping a song plays it and queues its radio | swipe down = close |
| **Library** | Tabs: Liked, Playlists, Recent, Most played; create/rename/delete playlists | — |
| **Settings** | Language mix sliders, default sleep timer, lyrics on/off, export/import backup (JSON) | — |

**Visual tokens:** background `#000`, surface `#111317`, line `#22252c`, text `#ECEDEF`, muted `#8A8F99`,
accent `#F2B544`. Fonts are Outfit (display) and Inter (body), bundled in the APK so no network is needed.

## 6. Mood engine and smart queue

### 6.1 Vibe mapping (`mood.js`)
- Pad point `(energy ∈ [0,1], valence ∈ [0,1])` maps to one of 9 zones (3×3 grid). Each zone has seed search
  phrases per language. Example: high energy + high valence = "party"; low energy + low valence = "sad slow".
- Chips are fixed points on the pad: Gym (0.9, 0.6), Drive (0.65, 0.7), Focus (0.3, 0.5), Sleep (0.1, 0.4),
  Party (0.95, 0.9), Sad (0.25, 0.1).
- `buildPlan(point, languageMix)` returns a list of `{language, query}` sized in proportion to the mix (pure function).

### 6.2 Queue building (`queue.js`)
1. Run the plan's searches (songs filter) and collect candidates.
2. Add liked or played songs whose stored mood tag is within distance 0.25 of the point (personal learning).
3. Remove repeats (the repeat guard below), dedupe by videoId and by normalised title, and interleave languages to match the mix.
4. Target 25 tracks. When fewer than 5 remain, append the radio of the current track, passed through the same guard.

### 6.3 Repeat guard
A track is excluded if it was played in the last 3 hours **or** is among the last 50 history entries. If the guard
would empty the queue, the oldest history entries are relaxed first.

### 6.4 Mood tagging
When a track plays at least 50% while a mood point is active, the store saves `moodTag = point` (a running average) for that videoId.

## 7. Storage (IndexedDB `lehar`)

| Store | Key | Value |
|---|---|---|
| `tracks` | videoId | `{videoId, title, artist, album, durationSec, thumb}` |
| `likes` | videoId | `{videoId, at}` |
| `playlists` | id | `{id, name, videoIds[], updatedAt}` |
| `history` | auto | `{videoId, at, playedSec, point?}` |
| `moodTags` | videoId | `{energy, valence, n}` |
| `lyrics` | videoId | `{synced?, plain?, offsetMs}` |
| `settings` | key | value |

Backup export/import writes and reads one JSON file with all stores except the `lyrics` cache.

## 8. Background playback (carried over from NAUX v1.4)

1. `BgWebView.onWindowVisibilityChanged` always passes `VISIBLE`.
2. `setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)`.
3. A foreground service (`mediaPlayback` type) runs while playing, with a MediaStyle notification and MediaSession.
4. Battery-optimisation exemption is requested once.
5. A watchdog in JS checks every 3s: if the engine paused the player but the user did not, and no other audio holds focus, it calls `playVideo()`.

## 9. Error handling

| Case | Behaviour |
|---|---|
| Video unavailable / embed blocked | Skip to the next track and try an alternate version |
| No network | Banner with "Retry". Library and history still browsable |
| API shape change / non-200 | Empty results with a clear message, and the queue falls back to library + history |
| No lyrics | Art only, no error |
| Call / other app audio | Pause, then auto-resume when focus returns |
| Queue exhausted and radio fails | Fill from liked songs near the current mood |

## 10. Testing

- **Unit (Node, `node --test`):** `mood.js`, `queue.js` (repeat guard, interleave, dedupe), `ytm.js` (fixture parsing), `lyrics.js` (LRC parsing, offset).
- **UI (Playwright, Pixel-size viewport):** home renders, dot drag triggers a queue build (with a mocked bridge), player gestures, search sections, library persistence across reload. Screenshots are sent to the user.
- **Build checks:** `aapt2 dump badging`, `apksigner verify`, manifest permissions.
- **Not testable here:** real YouTube playback and background behaviour. These are verified by the user on the phone.

## 11. Delivery

- Source in `lehar/` on branch `ccr-383cae22-pt6xrn`.
- Signed APK sent to the user as a file. **The signing keystore is not committed** (the repo is public). It is sent to the user privately so future updates install over the old version.
- Version 1.0 (versionCode 1), package `app.lehar.music`, minSdk 26, targetSdk 34.

## 12. Risks

| Risk | Mitigation |
|---|---|
| YouTube Music internal API changes | Parsers isolated in `ytm.js` with fixtures, so a fix touches one file |
| Background playback still pauses on some phones | NAUX's 5-layer approach, plus the user's phone test and iteration |
| Mood seeds feel off | Seeds live in one table in `mood.js` and the language mix is user-editable |
