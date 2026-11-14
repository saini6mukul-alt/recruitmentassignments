// YouTube Music internal API: request bodies and response parsers.
// Everything here is pure, so it can be unit-tested against saved fixtures.
// If YouTube changes the response shape, parsers return empty results instead of throwing.

export const CLIENT = { clientName: 'WEB_REMIX', clientVersion: '1.20241111.01.00', hl: 'en', gl: 'IN' };

export const FILTER = {
  songs: 'EgWKAQIIAWoKEAkQBRAKEAMQBA==',
  videos: 'EgWKAQIQAWoKEAUQBBAVEBEQEA==',
};

const BASE = 'https://music.youtube.com/youtubei/v1/';

/** @typedef {{videoId:string,title:string,artist:string,album:string,durationSec:number,thumb:string}} Track */

/** YouTube video ids are exactly 11 chars of [A-Za-z0-9_-]; anything else is rejected (review #8). */
export const isVideoId = (v) => typeof v === 'string' && /^[\w-]{11}$/.test(v);

export function endpoint(name) {
  return `${BASE}${name}?prettyPrint=false`;
}

const ctx = () => ({ context: { client: { ...CLIENT } } });

export function searchBody(query, filter) {
  const body = { ...ctx(), query };
  if (filter) body.params = FILTER[filter];
  return body;
}

export function nextBody(videoId) {
  return { ...ctx(), videoId, playlistId: `RDAMVM${videoId}`, isAudioOnly: true };
}

export function suggestBody(input) {
  return { ...ctx(), input };
}

export function parseDuration(text) {
  if (!text || typeof text !== 'string') return 0;
  const m = text.trim().match(/^(\d+):(\d{2})(?::(\d{2}))?$/);
  if (!m) return 0;
  return m[3] !== undefined ? (+m[1]) * 3600 + (+m[2]) * 60 + (+m[3]) : (+m[1]) * 60 + (+m[2]);
}

export function bestThumb(thumbs) {
  if (!Array.isArray(thumbs) || thumbs.length === 0) return '';
  const best = [...thumbs].sort((a, b) => (b.width || 0) - (a.width || 0))[0];
  let url = best.url || '';
  if (url.startsWith('//')) url = 'https:' + url;
  if (url.includes('googleusercontent.com')) url = url.replace(/=w\d+-h\d+/, '=w544-h544');
  return url;
}

// ---- generic walking helpers ----

function collect(node, key, out = []) {
  if (Array.isArray(node)) {
    for (const v of node) collect(v, key, out);
  } else if (node && typeof node === 'object') {
    for (const k of Object.keys(node)) {
      if (k === key) out.push(node[k]);
      else collect(node[k], key, out);
    }
  }
  return out;
}

function findFirst(node, key) {
  return collect(node, key)[0];
}

const runsText = (t) => (t && Array.isArray(t.runs) ? t.runs.map((r) => r.text).join('') : '');

const pageType = (run) =>
  run?.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;

/** Splits a "A • B • 4:26" runs array into segments of runs. */
function segments(runs) {
  const segs = [[]];
  for (const r of runs || []) {
    if (r.text === ' • ') segs.push([]);
    else segs[segs.length - 1].push(r);
  }
  return segs.filter((s) => s.length);
}

function metaFromRuns(runs) {
  const segs = segments(runs);
  let artist = '';
  let album = '';
  let durationSec = 0;
  for (const seg of segs) {
    const text = seg.map((r) => r.text).join('');
    const pt = pageType(seg[0]);
    // Podcast-show / playlist pages are collections, not performers: never use them as the artist.
    if (pt && /PODCAST|PLAYLIST/.test(pt)) { segs.blocked = true; continue; }
    if (!durationSec && parseDuration(text)) { durationSec = parseDuration(text); continue; }
    if (!artist && (pt === 'MUSIC_PAGE_TYPE_ARTIST' || pt === 'MUSIC_PAGE_TYPE_USER_CHANNEL')) { artist = text; continue; }
    if (!album && pt === 'MUSIC_PAGE_TYPE_ALBUM') { album = text; continue; }
  }
  if (!artist && !segs.blocked) {
    const skip = new Set(['Song', 'Video', 'Single', 'Album', 'EP', 'Artist', 'Episode', 'Podcast']);
    const first = segs.map((s) => s.map((r) => r.text).join('')).find((t) => !skip.has(t) && !/views|plays|^\d{4}$/.test(t) && !parseDuration(t));
    artist = first || '';
  }
  return { artist, album, durationSec };
}

function trackFromListItem(r) {
  const videoId = r.playlistItemData?.videoId || findFirst(r.overlay, 'watchEndpoint')?.videoId;
  if (!isVideoId(videoId)) return null;
  const cols = (r.flexColumns || []).map((c) => c.musicResponsiveListItemFlexColumnRenderer?.text);
  const title = runsText(cols[0]);
  if (!title) return null;
  const meta = metaFromRuns(cols[1]?.runs);
  const fixed = (r.fixedColumns || []).map((c) => runsText(c.musicResponsiveListItemFixedColumnRenderer?.text));
  const durationSec = meta.durationSec || parseDuration(fixed.find((t) => parseDuration(t)) || '');
  return {
    videoId, title, artist: meta.artist, album: meta.album, durationSec,
    thumb: bestThumb(r.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails),
  };
}

function artistFromListItem(r) {
  const be = r.navigationEndpoint?.browseEndpoint;
  const pt = be?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType;
  if (pt !== 'MUSIC_PAGE_TYPE_ARTIST') return null;
  const name = runsText(r.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text);
  if (!name) return null;
  return { name, browseId: be.browseId, thumb: bestThumb(r.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails) };
}

function fromCardShelf(c) {
  const titleRun = c.title?.runs?.[0];
  const videoId = titleRun?.navigationEndpoint?.watchEndpoint?.videoId;
  const thumb = bestThumb(c.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails);
  if (isVideoId(videoId)) {
    const meta = metaFromRuns(c.subtitle?.runs);
    return { track: { videoId, title: titleRun.text, artist: meta.artist, album: meta.album, durationSec: meta.durationSec, thumb } };
  }
  const be = titleRun?.navigationEndpoint?.browseEndpoint;
  if (pageType(titleRun) === 'MUSIC_PAGE_TYPE_ARTIST') return { artist: { name: titleRun.text, browseId: be.browseId, thumb } };
  return {};
}

/** @returns {{tracks: Track[], artists: {name:string,browseId:string,thumb:string}[], empty: boolean}} */
export function parseSearch(json) {
  const tracks = [];
  const artists = [];
  const seenT = new Set();
  const seenA = new Set();
  const addT = (t) => { if (t && !seenT.has(t.videoId)) { seenT.add(t.videoId); tracks.push(t); } };
  const addA = (a) => { if (a && !seenA.has(a.browseId)) { seenA.add(a.browseId); artists.push(a); } };
  try {
    for (const c of collect(json, 'musicCardShelfRenderer')) {
      const r = fromCardShelf(c);
      addT(r.track); addA(r.artist);
    }
    for (const r of collect(json, 'musicResponsiveListItemRenderer')) {
      addT(trackFromListItem(r));
      addA(artistFromListItem(r));
    }
  } catch {
    return { tracks: [], artists: [], empty: true };
  }
  return { tracks, artists, empty: tracks.length === 0 && artists.length === 0 };
}

const bylineBlocked = (t) => (t?.runs || []).some((r) => /PODCAST|PLAYLIST/.test(pageType(r) || ''));

/** Radio / up-next tracks. @returns {Track[]} */
export function parseNext(json) {
  const out = [];
  const seen = new Set();
  try {
    for (const r of collect(json, 'playlistPanelVideoRenderer')) {
      if (!isVideoId(r?.videoId) || seen.has(r.videoId)) continue;
      const meta = metaFromRuns(r.longBylineText?.runs);
      const t = {
        videoId: r.videoId,
        title: runsText(r.title),
        artist: meta.artist || (bylineBlocked(r.longBylineText) ? '' : runsText(r.shortBylineText)),
        album: meta.album,
        durationSec: parseDuration(runsText(r.lengthText)) || meta.durationSec,
        thumb: bestThumb(r.thumbnail?.thumbnails),
      };
      if (!t.title) continue;
      seen.add(r.videoId);
      out.push(t);
    }
  } catch {
    return [];
  }
  return out;
}

export function parseSuggest(json) {
  try {
    const out = [];
    for (const r of collect(json, 'searchSuggestionRenderer')) {
      const q = r.navigationEndpoint?.searchEndpoint?.query || runsText(r.suggestion);
      if (q && !out.includes(q)) out.push(q);
    }
    return out;
  } catch {
    return [];
  }
}
