// Small DOM helpers shared by the screens.
export const $ = (sel, root = document) => root.querySelector(sel);

export const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export function fmtTime(sec) {
  const s = Math.max(0, Math.floor(sec || 0));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${r}` : `${m}:${r}`;
}

/** Square-friendly artwork URL. YouTube video thumbs come in 16:9; mq/maxres have no letterbox bars. */
export function artUrl(track, size = 'sm') {
  if (!track) return '';
  const thumb = track.thumb || '';
  if (!thumb || /i\.ytimg\.com\/vi\//.test(thumb) || !thumb.includes('googleusercontent')) {
    return `https://i.ytimg.com/vi/${track.videoId}/${size === 'lg' ? 'maxresdefault' : 'mqdefault'}.jpg`;
  }
  return size === 'lg' ? thumb.replace(/=w\d+-h\d+/, '=w1080-h1080') : thumb;
}

/** <img> that falls back from maxres → hq when YouTube has no large thumb. */
export function img(track, cls = 'art', size = 'sm') {
  const fallback = `https://i.ytimg.com/vi/${esc(track?.videoId)}/hqdefault.jpg`;
  return `<img class="${cls}" loading="lazy" alt="" src="${esc(artUrl(track, size))}" onerror="this.onerror=null;this.src='${fallback}'">`;
}

export const artistOf = (t) => t?.artist || 'YouTube';

export function trackRow(t, { now = false, meta = '', more = true } = {}) {
  return `<button class="row${now ? ' now' : ''}" data-id="${esc(t.videoId)}">
    ${img(t)}
    <div><div class="t">${esc(t.title)}</div><div class="s">${esc(artistOf(t))}</div></div>
    ${more ? `<span class="icon-btn more" data-more="${esc(t.videoId)}" role="button" aria-label="Aur options"><svg viewBox="0 0 24 24"><circle class="fill" cx="12" cy="5" r="1.6"/><circle class="fill" cx="12" cy="12" r="1.6"/><circle class="fill" cx="12" cy="19" r="1.6"/></svg></span>` : `<span class="meta">${esc(meta)}</span>`}
  </button>`;
}

export const ICON = {
  back: '<svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
  down: '<svg viewBox="0 0 24 24"><path d="M5 9l7 7 7-7"/></svg>',
  close: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  dots: '<svg viewBox="0 0 24 24"><circle class="fill" cx="5" cy="12" r="1.7"/><circle class="fill" cx="12" cy="12" r="1.7"/><circle class="fill" cx="19" cy="12" r="1.7"/></svg>',
  heart: '<svg viewBox="0 0 24 24"><path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z"/></svg>',
  heartFill: '<svg viewBox="0 0 24 24" class="fill"><path d="M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.3a4.3 4.3 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z"/></svg>',
  play: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg>',
  next: '<svg viewBox="0 0 24 24"><path class="fill" d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>',
  prev: '<svg viewBox="0 0 24 24"><path class="fill" d="M6 6h2v12H6zM9.5 12l8.5 6V6z"/></svg>',
  shuffle: '<svg viewBox="0 0 24 24"><path d="M4 7h3c4 0 6 10 10 10h3M17 14l3 3-3 3M4 17h3c1.5 0 2.7-1.4 3.7-3.2M13.3 10.2C14.3 8.4 15.5 7 17 7h3M17 4l3 3-3 3"/></svg>',
  repeat: '<svg viewBox="0 0 24 24"><path d="M5 11V9a3 3 0 0 1 3-3h11M16 3l3 3-3 3M19 13v2a3 3 0 0 1-3 3H5M8 21l-3-3 3-3"/></svg>',
  queue: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h10M4 18h10M18 14v6M15 17h6"/></svg>',
  search: '<svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/></svg>',
  clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M12 8v4l3 2"/></svg>',
  plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  wave: '<svg viewBox="0 0 24 24"><path d="M3 13c2-6 4-6 6 0s4 6 6 0 4-5 6-2"/></svg>',
  trash: '<svg viewBox="0 0 24 24"><path d="M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13"/></svg>',
};

let toastTimer = null;
export function toast(text) {
  const el = $('#toast');
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.hidden = true), 2200);
}

/** Horizontal/vertical swipe + double-tap detection on an element. */
export function gestures(el, { onLeft, onRight, onDown, onUp, onDoubleTap, onTap, threshold = 60 }) {
  let sx = 0, sy = 0, st = 0, active = false, lastTap = 0, tapTimer = null;
  el.addEventListener('pointerdown', (e) => { active = true; sx = e.clientX; sy = e.clientY; st = Date.now(); });
  el.addEventListener('pointercancel', () => { active = false; });
  el.addEventListener('pointerup', (e) => {
    if (!active) return;
    active = false;
    const dx = e.clientX - sx, dy = e.clientY - sy;
    const ax = Math.abs(dx), ay = Math.abs(dy);
    if (ax > threshold && ax > ay * 1.3) return dx < 0 ? onLeft?.() : onRight?.();
    if (ay > threshold + 20 && ay > ax * 1.3) return dy > 0 ? onDown?.() : onUp?.();
    if (ax < 12 && ay < 12 && Date.now() - st < 300) {
      const now = Date.now();
      if (now - lastTap < 300) { clearTimeout(tapTimer); lastTap = 0; onDoubleTap?.(e); }
      else { lastTap = now; tapTimer = setTimeout(() => onTap?.(e), 300); }
    }
  });
}
