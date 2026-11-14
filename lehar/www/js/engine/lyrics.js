// LRC parsing and LRCLIB matching. Pure functions.

const MAX_DURATION_DIFF = 8; // seconds; larger gaps mean a different version (Review Focus 5)

/** @returns {{t:number,text:string}[]} sorted by time (ms) */
export function parseLrc(text) {
  if (!text || typeof text !== 'string') return [];
  const out = [];
  const stamp = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g;
  for (const raw of text.split(/\r?\n/)) {
    const stamps = [];
    let m;
    stamp.lastIndex = 0;
    let lastEnd = 0;
    while ((m = stamp.exec(raw)) && m.index === lastEnd) {
      const frac = m[3] ? Number(m[3].padEnd(3, '0')) : 0;
      stamps.push(Number(m[1]) * 60000 + Number(m[2]) * 1000 + frac);
      lastEnd = stamp.lastIndex;
    }
    if (!stamps.length) continue;
    const words = raw.slice(lastEnd).trim();
    for (const t of stamps) out.push({ t, text: words });
  }
  return out.sort((a, b) => a.t - b.t);
}

/** Index of the line active at `ms` (with the user's offset applied), or -1 before the first line. */
export function lineAt(lines, ms, offsetMs = 0) {
  const t = ms + offsetMs;
  let lo = 0;
  let hi = lines.length - 1;
  let ans = -1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    if (lines[mid].t <= t) { ans = mid; lo = mid + 1; } else hi = mid - 1;
  }
  return ans;
}

/** Turns a YouTube title/channel into something LRCLIB can match. */
export function cleanForLookup(title, artist) {
  let track = String(title || '');
  track = track.replace(/\s*[([{][^)\]}]*[)\]}]/g, ' ');     // (From "X"), [Official Video]
  track = track.split(/\s[|｜]\s|\s-\s|\s–\s/)[0];            // "Song | Film" / "Song - Film"
  track = track.replace(/\s+(ft\.?|feat\.?|featuring)\s.*$/i, '');
  track = track.replace(/\b(official|lyrical|lyrics?|video|audio|full song|4k|hd)\b/gi, '');
  track = track.replace(/\s+/g, ' ').trim();
  let a = String(artist || '').replace(/\s*-\s*Topic$/i, '').replace(/VEVO$/i, '').trim();
  return { track, artist: a };
}

/**
 * Picks the best lyrics from LRCLIB candidates (a `get` object or a `search` array).
 * @returns {{synced?: {t:number,text:string}[], plain?: string} | null}
 */
export function pickMatch(candidates, durationSec) {
  const list = Array.isArray(candidates) ? candidates : candidates ? [candidates] : [];
  const ok = list.filter((c) => c && !c.instrumental
    && (!durationSec || !c.duration || Math.abs(c.duration - durationSec) <= MAX_DURATION_DIFF)
    && (c.syncedLyrics || c.plainLyrics));
  if (!ok.length) return null;
  ok.sort((a, b) => Number(!!b.syncedLyrics) - Number(!!a.syncedLyrics)
    || Math.abs((a.duration || 0) - durationSec) - Math.abs((b.duration || 0) - durationSec));
  const best = ok[0];
  if (best.syncedLyrics) {
    const synced = parseLrc(best.syncedLyrics);
    if (synced.length) return best.plainLyrics ? { synced, plain: best.plainLyrics } : { synced };
  }
  return best.plainLyrics ? { plain: best.plainLyrics } : null;
}

const qs = (o) => new URLSearchParams(Object.entries(o).filter(([, v]) => v !== '' && v != null)).toString();

export function getUrl({ track, artist, durationSec }) {
  return `https://lrclib.net/api/get?${qs({ track_name: track, artist_name: artist, duration: Math.round(durationSec || 0) || '' })}`;
}

export function searchUrl({ track, artist }) {
  return `https://lrclib.net/api/search?${qs({ track_name: track, artist_name: artist })}`;
}
