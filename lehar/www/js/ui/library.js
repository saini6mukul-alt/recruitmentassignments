// Library: Liked, Playlists, Recent, Most played.
import * as app from '../app.js';
import * as store from '../store.js';
import { $, esc, ICON, trackRow, toast } from './dom.js';
import * as sheet from './sheet.js';
import { trackMenu, newPlaylist } from './player.js';

const TABS = [['liked', 'Liked'], ['playlists', 'Playlists'], ['recent', 'Recent'], ['most', 'Most played']];
let el, body, tab = 'liked';
let shown = [];         // tracks currently listed (for taps)
let openPlaylist = null;

export function init() {
  el = $('#library');
  el.innerHTML = `
    <div class="sheet-head"><button class="icon-btn" id="l-back" aria-label="Wapas">${ICON.back}</button><h2 id="l-title">Library</h2><button class="icon-btn" id="l-add" aria-label="Nayi playlist" hidden>${ICON.plus}</button></div>
    <div class="tabs" id="l-tabs">${TABS.map(([k, v]) => `<button class="tab" data-tab="${k}">${v}</button>`).join('')}</div>
    <div class="sheet-body" id="l-body"></div>`;
  body = $('#l-body');
  $('#l-back').addEventListener('click', () => { if (openPlaylist) { openPlaylist = null; render(); } else sheet.close(el); });
  $('#l-add').addEventListener('click', () => newPlaylist(null));
  $('#l-tabs').addEventListener('click', (e) => { const k = e.target.closest('[data-tab]')?.dataset.tab; if (k) { tab = k; openPlaylist = null; render(); } });
  body.addEventListener('click', onClick);
  body.addEventListener('contextmenu', (e) => e.preventDefault());
  let pressTimer = null;
  body.addEventListener('pointerdown', (e) => {
    const row = e.target.closest('[data-id]');
    if (!row || !openPlaylist) return;
    pressTimer = setTimeout(() => removeFromPlaylist(row.dataset.id), 600);
  });
  ['pointerup', 'pointercancel', 'pointermove'].forEach((ev) => body.addEventListener(ev, () => clearTimeout(pressTimer)));

  for (const ev of ['likes', 'history', 'playlists']) app.on(ev, () => { if (!el.hidden) render(); });
  window.addEventListener('lehar:open', (e) => { if (e.detail === 'library') open(); });
}

export function open() { sheet.open(el, { onClose: () => (openPlaylist = null) }); render(); }

async function tracksById() {
  return Object.fromEntries((await store.all('tracks')).map((t) => [t.videoId, t]));
}

async function render() {
  el.querySelectorAll('.tab').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
  $('#l-add').hidden = !(tab === 'playlists' && !openPlaylist);
  $('#l-title').textContent = openPlaylist ? openPlaylist.name : 'Library';
  const byId = await tracksById();
  let html = '';
  if (openPlaylist) {
    shown = openPlaylist.videoIds.map((id) => byId[id]).filter(Boolean);
    html = `<div class="stat"><span>${shown.length} gaane</span><span>Gaana hatane ke liye usse dabaye rakho</span></div>
      <div class="opts"><button class="btn primary" data-act="play">Chalao</button><button class="btn" data-act="shuffle">Shuffle</button><button class="btn" data-act="rename">Naam badlo</button><button class="btn danger" data-act="delete">Delete</button></div>
      ${list(shown, 'Is playlist mein abhi koi gaana nahi. Kisi bhi gaane ke ⋯ menu se jodo.')}`;
  } else if (tab === 'liked') {
    const likes = (await store.all('likes')).sort((a, b) => b.at - a.at);
    shown = likes.map((l) => byId[l.videoId]).filter(Boolean);
    html = (shown.length ? `<div class="opts"><button class="btn primary" data-act="play">Sab chalao</button><button class="btn" data-act="shuffle">Shuffle</button></div>` : '')
      + list(shown, 'Abhi koi liked gaana nahi. Player mein art par double-tap karo ya ♡ dabao.');
  } else if (tab === 'playlists') {
    const lists = (await store.all('playlists')).sort((a, b) => b.updatedAt - a.updatedAt);
    shown = [];
    html = lists.length ? lists.map((p) => `
      <button class="pl-row" data-pl="${esc(p.id)}">
        <div class="pl-cover">${p.videoIds.slice(0, 4).map((id) => `<img src="https://i.ytimg.com/vi/${esc(id)}/mqdefault.jpg" alt="">`).join('')}</div>
        <div style="min-width:0"><div class="t">${esc(p.name)}</div><div class="s">${p.videoIds.length} gaane</div></div><span></span>
      </button>`).join('') : '<div class="empty">Koi playlist nahi. Upar + dabake banao.</div>';
  } else if (tab === 'recent') {
    const hist = await store.recentHistory(300);
    const seen = new Set();
    shown = [];
    for (let i = hist.length - 1; i >= 0 && shown.length < 100; i--) {
      const t = byId[hist[i].videoId];
      if (t && !seen.has(t.videoId)) { seen.add(t.videoId); shown.push(t); }
    }
    html = list(shown, 'Abhi kuch nahi suna.');
  } else if (tab === 'most') {
    const since = Date.now() - 30 * 86400e3;
    const counts = {};
    for (const h of await store.all('history')) if (h.at >= since && h.playedSec >= 30) counts[h.videoId] = (counts[h.videoId] || 0) + 1;
    const top = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 50);
    shown = top.map(([id]) => byId[id]).filter(Boolean);
    html = shown.length
      ? `<div class="stat"><span>Pichle 30 din</span><span>kitni baar suna</span></div><div class="list">${top.filter(([id]) => byId[id]).map(([id, n]) => trackRow(byId[id], { more: false, meta: `${n}×` })).join('')}</div>`
      : '<div class="empty">Kuch din sunoge to yahan aapke sabse zyada sune gaane dikhenge.</div>';
  }
  body.innerHTML = html;
}

const list = (tracks, emptyText) => (tracks.length
  ? `<div class="list">${tracks.map((t) => trackRow(t, { now: app.current()?.videoId === t.videoId })).join('')}</div>`
  : `<div class="empty">${emptyText}</div>`);

async function onClick(e) {
  const pl = e.target.closest('[data-pl]')?.dataset.pl;
  if (pl) { openPlaylist = await store.get('playlists', pl); render(); return; }
  const act = e.target.closest('[data-act]')?.dataset.act;
  if (act) return action(act);
  const row = e.target.closest('[data-id]');
  if (!row) return;
  const i = shown.findIndex((t) => t.videoId === row.dataset.id);
  if (i < 0) return;
  if (e.target.closest('[data-more]')) { e.stopPropagation(); trackMenu(shown[i]); return; }
  const ctx = openPlaylist ? openPlaylist.name : TABS.find(([k]) => k === tab)[1];
  app.playQueue(shown, i, { context: ctx });
}

async function action(act) {
  if (act === 'play' && shown.length) return app.playQueue(shown, 0, { context: openPlaylist?.name || 'Liked songs' });
  if (act === 'shuffle' && shown.length) {
    const s = [...shown].sort(() => Math.random() - 0.5);
    return app.playQueue(s, 0, { context: openPlaylist?.name || 'Liked songs' });
  }
  if (act === 'rename' && openPlaylist) return renamePlaylist();
  if (act === 'delete' && openPlaylist) {
    sheet.menu(`"${esc(openPlaylist.name)}" delete karein?`, [
      { label: 'Haan, delete karo', onClick: async () => { await store.del('playlists', openPlaylist.id); toast('Playlist delete ho gayi'); openPlaylist = null; render(); } },
      { label: 'Rehne do' },
    ]);
  }
}

function renamePlaylist() {
  const pop = $('#popover');
  pop.innerHTML = `<div class="grabber"></div><div class="sheet-head"><h2>Naam badlo</h2></div>
    <div class="sheet-body"><form id="rn-form" style="display:flex;flex-direction:column;gap:12px">
    <input class="field" id="rn-name" maxlength="60" value="${esc(openPlaylist.name)}" autocomplete="off"><button class="btn primary" type="submit">Save</button></form></div>`;
  sheet.open(pop, { pop: true });
  pop.querySelector('#rn-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = pop.querySelector('#rn-name').value.trim();
    if (!name) return;
    openPlaylist.name = name;
    openPlaylist.updatedAt = Date.now();
    await store.put('playlists', openPlaylist, openPlaylist.id);
    sheet.close(pop);
    render();
  });
}

async function removeFromPlaylist(id) {
  const t = shown.find((x) => x.videoId === id);
  sheet.menu(`"${esc(t?.title || 'Gaana')}" hatayein?`, [
    { label: 'Playlist se hatao', onClick: async () => {
      openPlaylist.videoIds = openPlaylist.videoIds.filter((x) => x !== id);
      openPlaylist.updatedAt = Date.now();
      await store.put('playlists', openPlaylist, openPlaylist.id);
      render();
    } },
    { label: 'Rehne do' },
  ]);
}
