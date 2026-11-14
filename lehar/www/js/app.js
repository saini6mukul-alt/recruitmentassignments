// Lehar app core: state, playback, mood queue, history, session restore, media controls.
import * as store from './store.js';
import * as api from './engine/api.js';
import * as bridge from './bridge.js';
import { buildPlan, DEFAULT_MIX, clampPoint } from './engine/mood.js';
import { buildQueue, createBuildGate, nearbyTagged, repeatGuard, dedupe } from './engine/queue.js';
import { createPlayer, STATE } from './ytplayer.js';
import { startWatchdog } from './watchdog.js';
import { $, toast } from './ui/dom.js';
import * as sheet from './ui/sheet.js';
import * as home from './ui/home.js';
import * as playerUi from './ui/player.js';
import * as searchUi from './ui/search.js';
import * as libraryUi from './ui/library.js';
import * as settingsUi from './ui/settings.js';

// ---------- event bus ----------
const handlers = {};
export const on = (evt, fn) => ((handlers[evt] ||= []).push(fn), fn);
export const emit = (evt, data) => (handlers[evt] || []).forEach((fn) => { try { fn(data); } catch (e) { console.error(evt, e); } });

// ---------- state ----------
const s = {
  queue: [],
  index: -1,
  point: null,          // active mood point (null when playing from search/library)
  context: '',          // label shown in the player ("Gym mood", "Search", "Liked songs")
  likes: new Set(),
  mix: { ...DEFAULT_MIX },
  repeat: 'off',        // off | all | one
  shuffle: false,
  playing: false,
  userPaused: false,
  otherAudio: false,
  resumeAfterFocus: false,
  lastUserAction: 0,
  position: 0,
  duration: 0,
  playedSec: 0,         // seconds actually listened for the current track
  sleep: null,          // { until: ms } | { endOfTrack: true }
  lyricsOn: true,
  refilling: false,
};
export const state = s;
export const current = () => s.queue[s.index] || null;

const gate = createBuildGate();
let player;

const lyricsCache = {
  get: (id) => store.get('lyrics', id),
  put: (id, v) => store.put('lyrics', v, id),
};
export const getLyrics = (t) => api.lyrics(t, lyricsCache);

export async function setLyricsOffset(videoId, offsetMs) {
  const v = (await store.get('lyrics', videoId)) || {};
  v.offsetMs = offsetMs;
  await store.put('lyrics', v, videoId);
}

const userAction = () => { s.lastUserAction = Date.now(); };

// ---------- playback ----------
export async function playQueue(tracks, index = 0, { point = null, context = '', autoplay = true, startSec = 0 } = {}) {
  if (!tracks.length) return;
  userAction();
  await finishCurrent();
  s.queue = tracks.slice();
  s.point = point;
  s.context = context;
  s.queue.forEach((t) => store.saveTrack(t));
  emit('queue', s.queue);
  await playAt(index, { autoplay, startSec });
}

async function playAt(i, { autoplay = true, startSec = 0 } = {}) {
  if (i < 0 || i >= s.queue.length) return;
  s.index = i;
  s.position = startSec;
  s.playedSec = 0;
  s.userPaused = !autoplay;
  const t = current();
  s.duration = t.durationSec || 0;
  emit('track', t);
  pushNowPlaying();
  await player.load(t, startSec, autoplay);
  player.cueNext(s.queue[i + 1]);
  saveSession();
  if (s.queue.length - i < 5) refill();
}

export async function next({ auto = false } = {}) {
  if (!auto) userAction();
  if (auto && s.sleep?.endOfTrack) { s.sleep = null; emit('sleep', null); pause(); bridge.native('stopService'); return; }
  if (auto && s.repeat === 'one') { player.seek(0); player.play(); return; }
  await finishCurrent();
  if (s.index + 1 < s.queue.length) return playAt(s.index + 1);
  if (s.repeat === 'all' && s.queue.length) return playAt(0);
  const added = await refill(true);
  if (added) return playAt(s.index + 1);
  toast('Queue khatam ho gayi');
  pause();
}

export async function prev() {
  userAction();
  if (player.currentTime > 3 || s.index === 0) { player.seek(0); return; }
  await finishCurrent();
  return playAt(s.index - 1);
}

export function pause() { userAction(); s.userPaused = true; player.pause(); }
export function play() {
  userAction();
  s.userPaused = false;
  if (s.index < 0 && s.queue.length) return playAt(0);
  if (player.state === STATE.CUED || player.state === STATE.UNSTARTED) {
    const t = current();
    if (t && !player.track) return player.load(t, s.position, true);
  }
  player.play();
}
export const toggle = () => (s.playing ? pause() : play());
export function seek(sec) { userAction(); player.seek(sec); s.position = sec; pushNowPlaying(); }

export function jumpTo(i) { userAction(); finishCurrent().then(() => playAt(i)); }
export function removeFromQueue(i) {
  if (i === s.index || i < 0 || i >= s.queue.length) return;
  s.queue.splice(i, 1);
  if (i < s.index) s.index--;
  emit('queue', s.queue);
  if (i === s.index + 1) player.cueNext(s.queue[s.index + 1]);
  saveSession();
}
export function playNext(t) {
  if (!current()) return playQueue([t], 0, { context: 'Lehar' });
  s.queue = s.queue.filter((x, i) => i <= s.index || x.videoId !== t.videoId);
  s.queue.splice(s.index + 1, 0, t);
  store.saveTrack(t);
  emit('queue', s.queue);
  player.cueNext(t);
  toast('Agla yahi bajega');
  saveSession();
}
export function addToQueue(t) {
  if (!current()) return playQueue([t], 0, { context: 'Lehar' });
  s.queue.push(t);
  store.saveTrack(t);
  emit('queue', s.queue);
  toast('Queue mein jod diya');
  saveSession();
}

export function setRepeat() {
  s.repeat = { off: 'all', all: 'one', one: 'off' }[s.repeat];
  store.setSetting('repeat', s.repeat);
  emit('modes');
}
export function setShuffle() {
  s.shuffle = !s.shuffle;
  if (s.shuffle && s.index >= 0) {
    const rest = s.queue.slice(s.index + 1);
    for (let i = rest.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [rest[i], rest[j]] = [rest[j], rest[i]]; }
    s.queue = [...s.queue.slice(0, s.index + 1), ...rest];
    emit('queue', s.queue);
    player.cueNext(s.queue[s.index + 1]);
  }
  emit('modes');
}

/** Replaces everything after the current track with its radio ("Is gaane jaise aur"). */
export async function moreLikeThis(t = current(), { silent = false, keep = 0 } = {}) {
  if (!t) return;
  try {
    const history = await store.recentHistory();
    const radio = repeatGuard(dedupe(await api.radio(t.videoId)), history, Date.now()).filter((x) => x.videoId !== t.videoId);
    if (!radio.length) return toast('Abhi similar gaane nahi mile');
    if (current()?.videoId === t.videoId) {
      const kept = keep ? s.queue.slice(s.index + 1, keep) : [];
      const keptIds = new Set(kept.map((x) => x.videoId));
      s.queue = [...s.queue.slice(0, s.index + 1), ...kept, ...radio.filter((x) => !keptIds.has(x.videoId)).slice(0, 25)];
      s.queue.forEach((x) => store.saveTrack(x));
      emit('queue', s.queue);
      player.cueNext(s.queue[s.index + 1]);
      saveSession();
      if (!silent) toast('Queue badal di');
    } else {
      playQueue([t, ...radio.slice(0, 24)], 0, { context: 'Is gaane jaise' });
    }
  } catch { if (!silent) offline(); }
}

/** Adds the current track's radio when the queue is running low. */
async function refill(force = false) {
  if (s.refilling) return 0;
  const t = current();
  if (!t || (!force && s.queue.length - s.index >= 5)) return 0;
  s.refilling = true;
  try {
    const history = await store.recentHistory();
    const have = new Set(s.queue.map((x) => x.videoId));
    let radio = dedupe([...s.queue, ...(await api.radio(t.videoId))]).filter((x) => !have.has(x.videoId));
    radio = repeatGuard(radio, history, Date.now());
    if (!radio.length && s.point) radio = (await personalNear(s.point)).filter((x) => !have.has(x.videoId));
    const add = radio.slice(0, 15);
    s.queue.push(...add);
    add.forEach((x) => store.saveTrack(x));
    if (add.length) { emit('queue', s.queue); player.cueNext(s.queue[s.index + 1]); saveSession(); }
    return add.length;
  } catch {
    return 0;
  } finally {
    s.refilling = false;
  }
}

// ---------- mood queue ----------
async function personalNear(point) {
  const [tagEntries, likeIds, tracks] = await Promise.all([store.entries('moodTags'), store.keys('likes'), store.all('tracks')]);
  const byId = Object.fromEntries(tracks.map((t) => [t.videoId, t]));
  const tags = Object.fromEntries(tagEntries);
  const near = nearbyTagged(tags, byId, point);
  const liked = likeIds.map((id) => byId[id]).filter(Boolean).filter((t) => !tags[t.videoId]);
  return [...near, ...liked.sort(() => Math.random() - 0.5).slice(0, 4)];
}

export async function buildMoodQueue(rawPoint, label) {
  const point = clampPoint(rawPoint);
  const token = gate.start();
  emit('moodLoading', { point, loading: true });
  try {
    const plan = buildPlan(point, s.mix, 6);
    const groups = { hindi: [], punjabi: [], english: [] };
    let failures = 0;
    await Promise.all(plan.map(async ({ language, query }) => {
      try { groups[language].push(...(await api.searchTracks(query))); } catch { failures++; }
    }));
    if (!gate.isCurrent(token)) return; // a newer drag won (Review Focus 3)
    for (const l of Object.keys(groups)) groups[l].sort(() => Math.random() - 0.5);
    const [personal, history] = await Promise.all([personalNear(point), store.recentHistory()]);
    let q = buildQueue({ groups, personal, history, now: Date.now(), mix: s.mix });
    if (!gate.isCurrent(token)) return;
    if (!q.length) {
      q = personal;
      if (!q.length) { if (failures) offline(() => buildMoodQueue(point, label)); else toast('Is mood ke gaane nahi mile'); return; }
    }
    hideBanner();
    await playQueue(q, 0, { point, context: label || 'Mood' });
  } finally {
    if (gate.isCurrent(token)) emit('moodLoading', { point, loading: false });
  }
}

// ---------- likes ----------
export const isLiked = (id) => s.likes.has(id);
export async function toggleLike(t = current()) {
  if (!t) return;
  if (s.likes.has(t.videoId)) { s.likes.delete(t.videoId); await store.del('likes', t.videoId); }
  else { s.likes.add(t.videoId); await store.saveTrack(t); await store.put('likes', { videoId: t.videoId, at: Date.now() }, t.videoId); }
  emit('likes', s.likes);
}
export async function like(t = current()) { if (t && !s.likes.has(t.videoId)) await toggleLike(t); }

// ---------- history + mood tags ----------
async function finishCurrent() {
  const t = current();
  if (!t || s.playedSec < 5) return;
  const played = s.playedSec;
  s.playedSec = 0;
  await store.addHistory({ videoId: t.videoId, at: Date.now(), playedSec: Math.round(played), point: s.point });
  const dur = s.duration || t.durationSec;
  if (s.point && dur && played >= dur * 0.5) {
    const old = (await store.get('moodTags', t.videoId)) || { energy: 0, valence: 0, n: 0 };
    const n = old.n + 1;
    await store.put('moodTags', {
      energy: (old.energy * old.n + s.point.energy) / n,
      valence: (old.valence * old.n + s.point.valence) / n,
      n,
    }, t.videoId);
  }
  emit('history');
}

// ---------- session (Review Focus 4) ----------
let lastSave = 0;
function saveSession(force = false) {
  if (!force && Date.now() - lastSave < 4000) return;
  lastSave = Date.now();
  if (s.index < 0) return;
  store.setSetting('session', {
    queue: s.queue.slice(Math.max(0, s.index - 20), s.index + 60),
    index: Math.min(s.index, 20),
    positionSec: Math.floor(player?.currentTime || s.position || 0),
    point: s.point,
    context: s.context,
  }).catch(() => {});
}

async function restoreSession() {
  const ses = await store.getSetting('session', null);
  if (!ses?.queue?.length) return false;
  s.queue = ses.queue;
  s.point = ses.point;
  s.context = ses.context || '';
  emit('queue', s.queue);
  await playAt(Math.min(ses.index || 0, s.queue.length - 1), { autoplay: false, startSec: ses.positionSec || 0 });
  if (s.point) emit('point', s.point);
  return true;
}

// ---------- native media notification ----------
function pushNowPlaying() {
  const t = current();
  if (!t) return;
  bridge.native('setNowPlaying', JSON.stringify({
    title: t.title, artist: t.artist || 'YouTube', thumb: `https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg`,
    durationSec: s.duration || t.durationSec || 0, positionSec: s.position || 0, isPlaying: s.playing,
  }));
}

function onMedia(action) {
  if (action === 'play') return play();
  if (action === 'pause') return pause();
  if (action === 'next') return next();
  if (action === 'prev') return prev();
  if (action.startsWith('seek:')) return seek(Number(action.slice(5)) / 1000);
  if (action === 'focusLoss') {
    s.otherAudio = true;
    s.resumeAfterFocus = s.playing;
    player.pause();
    return;
  }
  if (action === 'focusGain') {
    s.otherAudio = false;
    if (s.resumeAfterFocus && !s.userPaused) player.play();
    s.resumeAfterFocus = false;
  }
}

// ---------- sleep timer ----------
let sleepTimer = null;
export function setSleep(minutes) {
  clearTimeout(sleepTimer);
  if (minutes === 'end') s.sleep = { endOfTrack: true };
  else if (minutes > 0) {
    s.sleep = { until: Date.now() + minutes * 60000 };
    sleepTimer = setTimeout(() => { s.sleep = null; emit('sleep', null); pause(); bridge.native('stopService'); toast('Sleep timer: gaana band'); }, minutes * 60000);
  } else s.sleep = null;
  emit('sleep', s.sleep);
}

// ---------- offline banner ----------
let retryFn = null;
export function offline(retry) {
  retryFn = retry || null;
  $('#banner-text').textContent = 'Internet nahi hai. Library aur history phir bhi dekh sakte ho.';
  $('#banner-retry').hidden = !retry;
  $('#banner').hidden = false;
}
export function hideBanner() { $('#banner').hidden = true; }

export function setMix(mix) {
  s.mix = mix;
  store.setSetting('mix', mix);
  emit('mix', mix);
}
export function setLyricsOn(v) { s.lyricsOn = v; store.setSetting('lyricsOn', v); emit('lyricsOn', v); }

// ---------- boot ----------
async function boot() {
  await store.open();
  s.mix = await store.getSetting('mix', { ...DEFAULT_MIX });
  s.repeat = await store.getSetting('repeat', 'off');
  s.lyricsOn = await store.getSetting('lyricsOn', true);
  s.likes = new Set(await store.keys('likes'));

  player = createPlayer({
    onState: (st) => {
      const was = s.playing;
      s.playing = st === STATE.PLAYING;
      if (s.playing) s.userPaused = false;
      if (was !== s.playing || st === STATE.CUED) { emit('state', st); pushNowPlaying(); }
    },
    onEnded: () => next({ auto: true }),
    onError: (code, t) => handleUnplayable(code, t),
    onProgress: (sec, dur) => {
      if (sec > s.position && sec - s.position < 2) s.playedSec += sec - s.position;
      s.position = sec;
      if (dur) s.duration = dur;
      emit('progress', { sec, dur: s.duration });
      bridge.native('setPosition', Math.floor(sec * 1000));
      saveSession();
    },
  });
  startWatchdog({ player, isUserPaused: () => s.userPaused, otherAudio: () => s.otherAudio, lastUserActionAt: () => s.lastUserAction });

  sheet.initScrim();
  home.init();
  playerUi.init();
  searchUi.init();
  libraryUi.init();
  settingsUi.init();

  bridge.onMedia(onMedia);
  bridge.onBack(() => sheet.closeTop());
  $('#banner-retry').addEventListener('click', () => { hideBanner(); retryFn?.(); });
  document.addEventListener('visibilitychange', () => saveSession(true));
  window.addEventListener('pagehide', () => saveSession(true));
  window.addEventListener('online', hideBanner);

  emit('history');
  const restored = await restoreSession().catch(() => false);
  if (!restored) player.warmup?.();
  emit('ready');
}

/** Skip unplayable tracks (embed blocked / removed) and try to find another upload of the same song. */
async function handleUnplayable(code, t) {
  if (!t) return;
  const at = s.queue.findIndex((x) => x.videoId === t.videoId);
  try {
    const alts = await api.searchTracks(`${t.title} ${t.artist || ''} audio`.trim());
    const alt = alts.find((x) => x.videoId !== t.videoId && !s.queue.some((q) => q.videoId === x.videoId));
    if (alt && at >= 0) { s.queue.splice(at + 1, 0, alt); emit('queue', s.queue); }
  } catch { /* offline: just skip */ }
  if (at === s.index) next({ auto: false });
}

boot().catch((e) => { console.error(e); toast('App shuru nahi ho paya'); });
