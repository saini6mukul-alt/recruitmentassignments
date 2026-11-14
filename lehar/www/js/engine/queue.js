// Smart queue: repeat guard, title normalisation, dedupe, language interleave, build gate. Pure.
import { distance } from './mood.js';

const VARIANTS = [
  ['slowed', /\bslowed\b/], ['reverb', /\breverb\b/], ['lofi', /\blo-?fi\b/], ['remix', /\bremix\b/],
  ['unplugged', /\bunplugged\b/], ['acoustic', /\bacoustic\b/], ['mashup', /\bmash-?up\b/], ['cover', /\bcover\b/],
  ['live', /\blive\b/], ['dance mix', /\bdance mix\b/], ['reprise', /\breprise\b/], ['female', /\bfemale\b/],
  ['male', /\bmale\b/], ['instrumental', /\binstrumental\b/], ['karaoke', /\bkaraoke\b/], ['sped up', /\bsped[ -]?up\b/],
];
const NOISE = /\b(official|music video|video song|video|audio|lyrical|lyrics?|full song|full video|4k|hd|hq|1080p|the film|ost|visualizer)\b/g;

const simple = (x) => String(x || '').toLowerCase()
  .replace(/\s*-\s*topic$/, '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9\u0900-\u0a7f ]/g, ' ').replace(/\s+/g, ' ').trim();

/**
 * Canonical title used to detect the same song uploaded under different names.
 * "Song - Film" keeps the song; "Artist - Song" keeps the song when the artist is known (review #7).
 */
export function normTitle(title, artist = '') {
  const raw = String(title || '').toLowerCase();
  const variants = VARIANTS.filter(([, re]) => re.test(raw)).map(([v]) => v).sort();
  const base = raw.replace(/\s*[([{][^)\]}]*[)\]}]/g, ' ').split(/\s[|｜]\s/)[0];
  const parts = base.split(/\s-\s|\s–\s|\s:\s/).map((x) => x.trim()).filter(Boolean);
  const who = simple(artist);
  let pick = parts[0] || '';
  if (who && parts.length > 1) {
    const i = parts.findIndex((p) => simple(p) === who || simple(p).startsWith(who + ' '));
    if (i >= 0) pick = parts[i === 0 ? 1 : 0];
  }
  let t = simple(pick.replace(/\s+(ft\.?|feat\.?|featuring)\s.*$/, '').replace(NOISE, ' '));
  if (!t) t = simple(raw);
  return variants.length ? `${t} ~${variants.join('+')}` : t;
}

export function dedupe(tracks) {
  const ids = new Set();
  const titles = new Set();
  const out = [];
  for (const t of tracks) {
    if (!t?.videoId || ids.has(t.videoId)) continue;
    const n = normTitle(t.title, t.artist);
    if (n && titles.has(n)) continue;
    ids.add(t.videoId);
    if (n) titles.add(n);
    out.push(t);
  }
  return out;
}

/**
 * Removes tracks heard recently. history: [{videoId, at}] oldest first (newest last).
 * Relaxes the rules rather than returning nothing when every candidate was heard.
 */
export function repeatGuard(tracks, history, now, { hours = 3, lastN = 50 } = {}) {
  if (!tracks.length) return [];
  const apply = (h, n) => {
    const recentById = new Set(history.slice(-n).map((x) => x.videoId));
    const cutoff = now - h * 3600e3;
    for (const x of history) if (x.at >= cutoff) recentById.add(x.videoId);
    return tracks.filter((t) => !recentById.has(t.videoId));
  };
  let h = hours;
  let n = lastN;
  while (h >= 0.25 || n >= 1) {
    const out = apply(h, n);
    if (out.length) return out;
    h /= 2;
    n = Math.floor(n / 2);
  }
  const last5 = new Set(history.slice(-5).map((x) => x.videoId));
  const out = tracks.filter((t) => !last5.has(t.videoId));
  return out.length ? out : tracks.slice();
}

/** Weighted round-robin across language groups (smooth weighted round-robin). */
export function interleave(groups, mix) {
  const langs = Object.keys(groups).filter((l) => groups[l]?.length);
  const queues = Object.fromEntries(langs.map((l) => [l, [...groups[l]]]));
  const weight = Object.fromEntries(langs.map((l) => [l, Math.max(0, Number(mix?.[l]) || 0)]));
  if (langs.every((l) => weight[l] === 0)) langs.forEach((l) => (weight[l] = 1));
  const current = Object.fromEntries(langs.map((l) => [l, 0]));
  const out = [];
  for (;;) {
    const live = langs.filter((l) => queues[l].length);
    if (!live.length) break;
    const liveW = live.map((l) => (weight[l] > 0 ? weight[l] : 0.0001));
    const total = liveW.reduce((a, b) => a + b, 0);
    live.forEach((l, i) => (current[l] += liveW[i]));
    let pick = live[0];
    for (const l of live) if (current[l] > current[pick]) pick = l;
    current[pick] -= total;
    out.push(queues[pick].shift());
  }
  return out;
}

export function nearbyTagged(moodTags, tracksById, point, radius = 0.25) {
  const out = [];
  for (const [id, tag] of Object.entries(moodTags || {})) {
    if (tracksById[id] && distance(tag, point) <= radius) out.push({ t: tracksById[id], d: distance(tag, point) });
  }
  return out.sort((a, b) => a.d - b.d).map((x) => x.t);
}

/** Builds the mood queue: interleave by language, weave personal picks in every 3rd slot, dedupe, guard, cut. */
export function buildQueue({ groups, personal = [], history = [], now = Date.now(), mix, size = 25 }) {
  const base = interleave(groups, mix);
  const maxPersonal = Math.floor(size * 0.3);
  const mine = personal.slice(0, maxPersonal);
  const woven = [];
  let bi = 0;
  let pi = 0;
  while (bi < base.length || pi < mine.length) {
    if ((woven.length + 1) % 3 === 0 && pi < mine.length) woven.push(mine[pi++]);
    else if (bi < base.length) woven.push(base[bi++]);
    else woven.push(mine[pi++]);
  }
  return repeatGuard(dedupe(woven), history, now).slice(0, size);
}

/** Lets only the most recent async build replace the queue (Review Focus 3). */
export function createBuildGate() {
  let latest = 0;
  return {
    start: () => ++latest,
    isCurrent: (token) => token === latest,
  };
}
