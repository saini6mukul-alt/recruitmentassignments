// Network glue: YouTube Music + LRCLIB over the native bridge.
import { requestJson, request } from '../bridge.js';
import * as ytm from './ytm.js';
import { cleanForLookup, pickMatch, getUrl, searchUrl } from './lyrics.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const post = (name, body) => requestJson(ytm.endpoint(name), { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) });

/** Songs filter first; regions where it is empty fall back to the Videos filter (Review Focus 1). */
export async function searchTracks(query) {
  try {
    const songs = ytm.parseSearch(await post('search', ytm.searchBody(query, 'songs')));
    if (songs.tracks.length) return songs.tracks;
  } catch (e) {
    if (e.message === 'offline') throw e;
  }
  return ytm.parseSearch(await post('search', ytm.searchBody(query, 'videos'))).tracks;
}

export async function search(query) {
  const [tracks, top] = await Promise.all([
    searchTracks(query),
    post('search', ytm.searchBody(query)).then(ytm.parseSearch).catch(() => ({ tracks: [], artists: [] })),
  ]);
  return { tracks, artists: top.artists };
}

export async function suggest(input) {
  if (!input.trim()) return [];
  try { return ytm.parseSuggest(await post('music/get_search_suggestions', ytm.suggestBody(input))).slice(0, 6); } catch { return []; }
}

export async function radio(videoId) {
  return ytm.parseNext(await post('next', ytm.nextBody(videoId)));
}

/**
 * Lyrics for a track. Uses the injected cache (`{get, put}`) so this module stays storage-agnostic.
 * @returns {Promise<{synced?:{t:number,text:string}[], plain?:string} | null>}
 */
export async function lyrics(track, cache) {
  const cached = await cache?.get(track.videoId);
  if (cached && (cached.synced || cached.plain || cached.none)) return cached.none ? null : cached;
  const q = cleanForLookup(track.title, track.artist);
  let found = null;
  const tryUrl = async (url) => {
    const r = await request(url, { headers: { 'Lrclib-Client': 'Lehar v1.0' } });
    if (r.status === 200) return pickMatch(JSON.parse(r.text), track.durationSec);
    if (r.status === 0) throw new Error('offline');
    return null;
  };
  try {
    if (q.artist) found = await tryUrl(getUrl({ ...q, durationSec: track.durationSec }));
    if (!found) found = await tryUrl(searchUrl(q));
    if (!found && q.artist) found = await tryUrl(searchUrl({ track: q.track, artist: '' }));
  } catch (e) {
    if (e.message === 'offline') return null; // do not cache "none" when offline
  }
  await cache?.put(track.videoId, found ? { ...found, offsetMs: cached?.offsetMs || 0 } : { none: true, offsetMs: 0 });
  return found;
}
