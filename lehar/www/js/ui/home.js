// Home: Mood Pad, chips, "Is mood ki queue" row, recently played, mini-player.
import * as app from '../app.js';
import * as store from '../store.js';
import { CHIPS, zoneOf, clampPoint } from '../engine/mood.js';
import { $, esc, img, trackRow, artistOf, gestures, artUrl } from './dom.js';
import { trackMenu } from './player.js';

let point = { energy: 0.5, valence: 0.5 };
let activeChip = null;

export function init() {
  const pad = $('#pad');
  const dot = $('#dot');

  // chips
  $('#chips').innerHTML = Object.keys(CHIPS).map((c) => `<button class="chip" data-chip="${c}">${c}</button>`).join('');
  $('#chips').addEventListener('click', (e) => {
    const c = e.target.closest('[data-chip]')?.dataset.chip;
    if (!c) return;
    activeChip = c;
    setPoint(CHIPS[c]);
    app.buildMoodQueue(CHIPS[c], `${c} mood`);
  });

  // pad dragging
  const toPoint = (e) => {
    const r = pad.getBoundingClientRect();
    return clampPoint({ energy: (e.clientX - r.left) / r.width, valence: 1 - (e.clientY - r.top) / r.height });
  };
  let dragging = false;
  pad.addEventListener('pointerdown', (e) => {
    dragging = true;
    pad.setPointerCapture(e.pointerId);
    pad.classList.add('dragging');
    activeChip = null;
    setPoint(toPoint(e));
  });
  pad.addEventListener('pointermove', (e) => { if (dragging) setPoint(toPoint(e)); });
  const end = (e) => {
    if (!dragging) return;
    dragging = false;
    pad.classList.remove('dragging');
    const p = toPoint(e);
    setPoint(p);
    app.buildMoodQueue(p, zoneOf(p).label);
  };
  pad.addEventListener('pointerup', end);
  pad.addEventListener('pointercancel', () => { dragging = false; pad.classList.remove('dragging'); });
  pad.addEventListener('keydown', (e) => {
    const step = 0.05;
    const d = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, step], ArrowDown: [0, -step] }[e.key];
    if (d) { e.preventDefault(); setPoint({ energy: point.energy + d[0], valence: point.valence + d[1] }); }
    if (e.key === 'Enter') app.buildMoodQueue(point, zoneOf(point).label);
  });

  // swipe up anywhere on home (outside the pad) opens search
  gestures($('#home'), { onUp: () => app_open('search'), threshold: 80 });

  $('#btn-search').addEventListener('click', () => app_open('search'));
  $('#btn-library').addEventListener('click', () => app_open('library'));
  $('#btn-settings').addEventListener('click', () => app_open('settings'));
  $('#btn-queue-all').addEventListener('click', () => app_open('queue'));

  // queue row + recent list
  $('#mood-row').addEventListener('click', (e) => {
    const i = e.target.closest('[data-i]')?.dataset.i;
    if (i !== undefined) app.jumpTo(Number(i));
  });
  $('#recent-list').addEventListener('click', onRecentClick);

  // mini player
  const mini = $('#mini');
  $('#mini-toggle').addEventListener('click', (e) => { e.stopPropagation(); app.toggle(); });
  $('#mini-next').addEventListener('click', (e) => { e.stopPropagation(); app.next(); });
  gestures(mini, { onTap: () => app_open('player'), onUp: () => app_open('player'), onLeft: () => app.next(), onRight: () => app.prev(), threshold: 40 });

  app.on('track', renderMini);
  app.on('state', renderMini);
  app.on('progress', ({ sec, dur }) => { $('#mini-bar').style.width = `${dur ? Math.min(100, (sec / dur) * 100) : 0}%`; });
  app.on('queue', renderRow);
  app.on('track', renderRow);
  app.on('history', renderRecent);
  app.on('moodLoading', ({ loading }) => { pad.classList.toggle('loading', loading); if (loading) renderRowSkeleton(); });
  app.on('point', (p) => setPoint(p));
  app.on('mix', renderMixHint);

  setPoint(point);
  renderMixHint(app.state.mix);
  renderRow();
}

function app_open(name) {
  window.dispatchEvent(new CustomEvent('lehar:open', { detail: name }));
}

function setPoint(p) {
  point = clampPoint(p);
  const dot = $('#dot');
  dot.style.left = `${point.energy * 100}%`;
  dot.style.top = `${(1 - point.valence) * 100}%`;
  $('#zone-label').textContent = activeChip ? `${activeChip} · ${zoneOf(point).label}` : zoneOf(point).label;
  const warm = `rgba(242,181,68,${0.12 + point.energy * 0.28})`;
  const cool = `rgba(62,106,140,${0.12 + (1 - point.valence) * 0.3})`;
  $('.pad-glow').style.background = `radial-gradient(circle at ${point.energy * 100}% ${(1 - point.valence) * 100}%, ${warm}, transparent 55%), radial-gradient(circle at 15% 85%, ${cool}, transparent 55%)`;
  document.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c.dataset.chip === activeChip));
  $('#pad').setAttribute('aria-valuetext', `${zoneOf(point).label}: energy ${Math.round(point.energy * 100)}, mood ${Math.round(point.valence * 100)}`);
}

function renderMixHint(mix) {
  $('#mix-hint').textContent = `Hindi ${mix.hindi} · Punjabi ${mix.punjabi} · English ${mix.english}`;
}

function renderMini() {
  const t = app.current();
  $('#mini').hidden = !t;
  if (!t) return;
  const imgEl = $('#mini-art');
  const src = artUrl(t);
  if (imgEl.getAttribute('src') !== src) imgEl.src = src;
  $('#mini-title').textContent = t.title;
  $('#mini-artist').textContent = artistOf(t);
  $('#mini-toggle').classList.toggle('playing', app.state.playing);
}

function renderRowSkeleton() {
  $('#mood-row').innerHTML = Array.from({ length: 4 }, () => '<div class="card"><div class="art skeleton" style="width:128px;height:128px;border-radius:14px"></div></div>').join('');
}

function renderRow() {
  const { queue, index } = app.state;
  const row = $('#mood-row');
  if (!queue.length) {
    row.innerHTML = '<div class="empty">Dot ko pad par kheecho ya koi chip dabao. Lehar us mood ki queue bana dega.</div>';
    return;
  }
  const start = Math.max(0, index);
  row.innerHTML = queue.slice(start, start + 12).map((t, k) => `
    <button class="card${start + k === index ? ' now' : ''}" data-i="${start + k}">
      ${img(t)}
      <div class="t">${esc(t.title)}</div><div class="s">${esc(artistOf(t))}</div>
    </button>`).join('');
}

let recent = [];
async function renderRecent() {
  const [hist, tracks] = await Promise.all([store.recentHistory(80), store.all('tracks')]);
  const byId = Object.fromEntries(tracks.map((t) => [t.videoId, t]));
  const seen = new Set();
  recent = [];
  for (let i = hist.length - 1; i >= 0 && recent.length < 8; i--) {
    const t = byId[hist[i].videoId];
    if (t && !seen.has(t.videoId)) { seen.add(t.videoId); recent.push(t); }
  }
  $('#recent-list').innerHTML = recent.length
    ? recent.map((t) => trackRow(t)).join('')
    : '<div class="empty">Abhi kuch nahi suna. Jo bhi sunoge, yahan dikhega.</div>';
}

function onRecentClick(e) {
  const more = e.target.closest('[data-more]');
  const row = e.target.closest('[data-id]');
  if (!row) return;
  const i = recent.findIndex((t) => t.videoId === row.dataset.id);
  if (more) { e.stopPropagation(); trackMenu(recent[i]); return; }
  app.playQueue(recent, i, { context: 'Recently played' }).then(() => app.moreLikeThis(recent[i], { silent: true, keep: recent.length }));
}

export const _test = { setPoint, get point() { return point; } };
