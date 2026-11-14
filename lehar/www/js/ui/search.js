// Search sheet: suggestions while typing, then Songs + Artists sections.
import * as app from '../app.js';
import * as api from '../engine/api.js';
import * as store from '../store.js';
import { $, esc, ICON, trackRow, gestures } from './dom.js';
import * as sheet from './sheet.js';
import { trackMenu } from './player.js';

let el, input, body;
let suggTimer = null, searchTimer = null, seq = 0, suggSeq = 0, shownQuery = '';
let results = [];

export function init() {
  el = $('#search');
  el.innerHTML = `
    <div class="sheet-head">
      <button class="icon-btn" id="s-back" aria-label="Wapas">${ICON.back}</button>
      <label class="search-box">${ICON.search}<input id="s-input" type="search" placeholder="Gaana, artist, ya film" autocomplete="off" enterkeyhint="search"></label>
    </div>
    <div class="sheet-body" id="s-body"></div>`;
  input = $('#s-input');
  body = $('#s-body');
  $('#s-back').addEventListener('click', () => sheet.close(el));
  gestures(el.querySelector('.sheet-head'), { onDown: () => sheet.close(el) });

  input.addEventListener('input', () => {
    const q = input.value.trim();
    clearTimeout(suggTimer); clearTimeout(searchTimer);
    shownQuery = '';
    if (!q) return renderRecent();
    suggTimer = setTimeout(() => suggest(q), 250);
    searchTimer = setTimeout(() => run(q), 700);
  });
  input.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    clearTimeout(searchTimer); clearTimeout(suggTimer);
    run(input.value.trim(), true);
    input.blur();
  });

  body.addEventListener('click', onClick);
  window.addEventListener('lehar:open', (e) => { if (e.detail === 'search') open(); });
}

export function open() {
  sheet.open(el);
  if (!input.value) renderRecent();
  setTimeout(() => input.focus(), 80);
}

async function renderRecent() {
  const recent = await store.getSetting('recentSearches', []);
  body.innerHTML = recent.length
    ? `<div class="sec-title">Haal hi mein dhoondha</div><div class="sugg">${recent.map((q) => `<button data-q="${esc(q)}">${ICON.clock}<span>${esc(q)}</span></button>`).join('')}</div>`
    : '<div class="empty">Koi bhi gaana, artist ya film ka naam likho.</div>';
}

async function suggest(q) {
  const my = ++suggSeq;
  const runAt = seq;
  const list = await api.suggest(q);
  // Suggestions never replace results: drop them if a search started or finished meanwhile.
  if (my !== suggSeq || runAt !== seq || shownQuery || !list.length) return;
  body.innerHTML = `<div class="sugg">${list.map((x) => `<button data-q="${esc(x)}">${ICON.search}<span>${esc(x)}</span></button>`).join('')}</div>`;
}

async function run(q, remember = false) {
  if (!q) return;
  const my = ++seq;
  shownQuery = q;
  body.innerHTML = `<div class="sec-title">Songs</div>${Array.from({ length: 6 }, () => '<div class="row"><div class="art skeleton" style="width:48px;height:48px;border-radius:10px"></div><div><div class="skeleton" style="height:12px;width:70%;border-radius:4px"></div></div></div>').join('')}`;
  try {
    const r = await api.search(q);
    if (my !== seq) return;
    results = r.tracks;
    results.query = q;
    app.hideBanner();
    if (remember || r.tracks.length) saveRecent(q);
    if (!r.tracks.length && !r.artists.length) { body.innerHTML = `<div class="empty">"${esc(q)}" ke liye kuch nahi mila. Spelling badal ke dekho.</div>`; return; }
    body.innerHTML = `
      ${r.tracks.length ? `<div class="sec-title">Songs</div><div class="list">${r.tracks.map((t) => trackRow(t)).join('')}</div>` : ''}
      ${r.artists.length ? `<div class="sec-title">Artists</div><div class="artists">${r.artists.map((a) => `<button class="artist" data-artist="${esc(a.name)}"><img src="${esc(a.thumb)}" alt="" loading="lazy"><div>${esc(a.name)}</div></button>`).join('')}</div>` : ''}`;
  } catch (e) {
    if (my !== seq) return;
    body.innerHTML = `<div class="empty">Search abhi kaam nahi kar raha. ${e.message === 'offline' || e.message === 'timeout' ? 'Internet check karo.' : 'Thodi der baad try karo.'}</div>`;
  }
}

async function saveRecent(q) {
  const recent = (await store.getSetting('recentSearches', [])).filter((x) => x.toLowerCase() !== q.toLowerCase());
  recent.unshift(q);
  store.setSetting('recentSearches', recent.slice(0, 8));
}

function onClick(e) {
  const q = e.target.closest('[data-q]')?.dataset.q;
  if (q) { input.value = q; clearTimeout(searchTimer); clearTimeout(suggTimer); run(q, true); return; }
  const artist = e.target.closest('[data-artist]')?.dataset.artist;
  if (artist) { input.value = `${artist} songs`; run(input.value, true); return; }
  const more = e.target.closest('[data-more]');
  const row = e.target.closest('[data-id]');
  if (!row) return;
  const t = results.find((x) => x.videoId === row.dataset.id);
  if (!t) return;
  if (more) { e.stopPropagation(); trackMenu(t); return; }
  // play the tapped song, then fill the queue with its radio
  app.playQueue([t], 0, { context: 'Search' }).then(() => app.moreLikeThis(t, { silent: true }));
  sheet.close(el);
}
