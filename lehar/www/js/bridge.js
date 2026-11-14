// Bridge between the UI and the Android shell (window.LeharNative).
// Without the shell (browser, tests) requests fall back to fetch() and native calls are no-ops.

const g = globalThis;
g.__lehar = g.__lehar || {};
const pending = new Map();
let seq = 0;

g.__lehar.resolve = (id, status, text) => {
  const p = pending.get(id);
  if (!p) return;
  pending.delete(id);
  clearTimeout(p.timer);
  p.resolve({ status, text: text ?? '' });
};

const hasNative = () => !!(g.LeharNative && typeof g.LeharNative.request === 'function');

/** @returns {Promise<{status:number, text:string}>} status 0 = network failure */
export function request(url, { method = 'GET', headers = {}, body = null, timeoutMs = 12000 } = {}) {
  if (!hasNative()) {
    const ctl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timer = setTimeout(() => ctl?.abort(), timeoutMs);
    return fetch(url, { method, headers, body, signal: ctl?.signal })
      .then(async (r) => ({ status: r.status, text: await r.text() }))
      .catch((e) => { if (e?.name === 'AbortError') throw new Error('timeout'); return { status: 0, text: '' }; })
      .finally(() => clearTimeout(timer));
  }
  return new Promise((resolve, reject) => {
    const id = `r${++seq}`;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('timeout')); }, timeoutMs);
    pending.set(id, { resolve, timer });
    g.LeharNative.request(id, url, method, JSON.stringify(headers), body ?? '');
  });
}

export async function requestJson(url, opts) {
  const r = await request(url, opts);
  if (r.status < 200 || r.status >= 300) {
    const err = new Error(r.status === 0 ? 'offline' : `http ${r.status}`);
    err.status = r.status;
    throw err;
  }
  return JSON.parse(r.text);
}

/** Calls a native method if the shell provides it. */
export function native(name, ...args) {
  try {
    if (g.LeharNative && typeof g.LeharNative[name] === 'function') return g.LeharNative[name](...args);
  } catch (e) {
    console.warn('native call failed', name, e);
  }
  return undefined;
}

export const isNative = hasNative;

/** Media actions from the notification / lock screen / headset / audio focus. */
export function onMedia(fn) {
  g.__lehar.onMedia = (action) => { try { fn(String(action)); } catch (e) { console.error(e); } };
}

/** Android back button: return true if the UI handled it. */
export function onBack(fn) {
  g.__lehar.back = () => { try { return !!fn(); } catch { return false; } };
}
