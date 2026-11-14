// IndexedDB storage. Everything stays on the phone.
const DB = 'lehar';
const VERSION = 1;
export const STORES = ['tracks', 'likes', 'playlists', 'history', 'moodTags', 'lyrics', 'settings'];
let db = null;

const wrap = (req) => new Promise((res, rej) => { req.onsuccess = () => res(req.result); req.onerror = () => rej(req.error); });

export async function open() {
  if (db) return;
  db = await new Promise((res, rej) => {
    const req = indexedDB.open(DB, VERSION);
    req.onupgradeneeded = () => {
      const d = req.result;
      for (const s of STORES) {
        if (d.objectStoreNames.contains(s)) continue;
        if (s === 'history') d.createObjectStore(s, { autoIncrement: true });
        else d.createObjectStore(s);
      }
    };
    req.onsuccess = () => res(req.result);
    req.onerror = () => rej(req.error);
  });
}

const tx = (name, mode = 'readonly') => db.transaction(name, mode).objectStore(name);

export const get = (name, key) => wrap(tx(name).get(key));
export const put = (name, value, key) => wrap(key === undefined ? tx(name, 'readwrite').put(value) : tx(name, 'readwrite').put(value, key));
export const del = (name, key) => wrap(tx(name, 'readwrite').delete(key));
export const all = (name) => wrap(tx(name).getAll());
export const keys = (name) => wrap(tx(name).getAllKeys());

export async function entries(name) {
  const [k, v] = await Promise.all([keys(name), all(name)]);
  return k.map((key, i) => [key, v[i]]);
}

export async function getSetting(key, fallback) {
  const v = await get('settings', key);
  return v === undefined ? fallback : v;
}
export const setSetting = (key, value) => put('settings', value, key);

/** Saves track metadata so library/history can render without the network. */
export async function saveTrack(t) {
  if (t?.videoId) await put('tracks', { videoId: t.videoId, title: t.title, artist: t.artist, album: t.album, durationSec: t.durationSec, thumb: t.thumb }, t.videoId);
}

export async function addHistory(entry) {
  await put('history', entry);
}

/** Newest last. */
export async function recentHistory(n = 200) {
  const h = await all('history');
  return h.slice(-n);
}

export async function exportAll() {
  const out = { app: 'lehar', version: 1, exportedAt: new Date().toISOString() };
  for (const s of STORES) if (s !== 'lyrics') out[s] = await entries(s);
  return out;
}

const VID = /^[\w-]{11}$/;
const okTrack = (t) => t && VID.test(t.videoId) && typeof t.title === 'string';

/** Validates the whole file before touching any data (review #8, minor 8). */
function validBackup(obj) {
  if (!obj || obj.app !== 'lehar') return false;
  const pairs = (n) => (obj[n] === undefined ? [] : Array.isArray(obj[n]) ? obj[n] : null);
  for (const n of STORES) if (n !== 'lyrics' && pairs(n) === null) return false;
  if (!pairs('tracks').every(([k, v]) => k === v?.videoId && okTrack(v))) return false;
  if (!pairs('likes').every(([k]) => VID.test(k))) return false;
  if (!pairs('moodTags').every(([k]) => VID.test(k))) return false;
  if (!pairs('history').every(([, v]) => VID.test(v?.videoId))) return false;
  if (!pairs('playlists').every(([, v]) => typeof v?.name === 'string' && Array.isArray(v.videoIds) && v.videoIds.every((x) => VID.test(x)))) return false;
  return true;
}

export async function importAll(obj) {
  if (!validBackup(obj)) throw new Error('Ye Lehar ki sahi backup file nahi hai');
  for (const s of STORES) {
    if (s === 'lyrics' || !Array.isArray(obj[s])) continue;
    await wrap(tx(s, 'readwrite').clear());
    for (const [k, v] of obj[s]) {
      if (s === 'history') await put(s, v); else await put(s, v, k);
    }
  }
}
