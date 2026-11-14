// Small in-memory diagnostic log, shown in Settings → Problem report (no network, nothing leaves the phone).
const MAX = 150;
const lines = [];
const t0 = Date.now();

export function log(...parts) {
  const s = ((Date.now() - t0) / 1000).toFixed(1).padStart(7) + 's ' + parts.map((p) => (typeof p === 'string' ? p : JSON.stringify(p))).join(' ');
  lines.push(s);
  if (lines.length > MAX) lines.shift();
  if (globalThis.__ytDebug) console.log('[lehar]', s);
}

export const recent = (n = 60) => lines.slice(-n);
