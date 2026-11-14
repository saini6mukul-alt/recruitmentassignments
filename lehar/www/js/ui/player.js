// One Screen Flow player: full-bleed art, synced lyrics, gestures, queue, ⋯ menu, sleep timer.
import * as app from '../app.js';
import * as store from '../store.js';
import { lineAt } from '../engine/lyrics.js';
import { $, esc, fmtTime, artUrl, artistOf, ICON, gestures, trackRow, toast } from './dom.js';
import * as sheet from './sheet.js';

let el;
let lyr = null;        // {synced?, plain?} for the current track
let lyrFor = '';
let offsetMs = 0;
let lastLine = -2;
let seeking = false;

export function init() {
  el = $('#player');
  el.innerHTML = `
    <div class="player-bg" id="p-bg"></div>
    <div class="p-top">
      <button class="icon-btn" id="p-close" aria-label="Player band karo">${ICON.down}</button>
      <div class="ctx" id="p-ctx"></div>
      <button class="icon-btn" id="p-more" aria-label="Aur options">${ICON.dots}</button>
    </div>
    <div class="stage" id="p-stage">
      <img id="p-art" alt="">
      <div class="shade"></div>
      <div class="lyrics" id="p-lyrics" aria-live="polite"></div>
    </div>
    <div class="p-meta">
      <div style="min-width:0"><div class="t" id="p-title"></div><div class="s" id="p-artist"></div></div>
      <button class="icon-btn like-btn" id="p-like" aria-label="Like">${ICON.heart}</button>
    </div>
    <div class="seek" id="p-seek"><div class="rail"></div><div class="fill" id="p-fill"></div><div class="knob" id="p-knob"></div></div>
    <div class="times"><span id="p-pos">0:00</span><span id="p-dur">0:00</span></div>
    <div class="controls">
      <button class="icon-btn" id="p-shuffle" aria-label="Shuffle">${ICON.shuffle}</button>
      <button class="icon-btn" id="p-prev" aria-label="Pichla gaana">${ICON.prev}</button>
      <button class="main" id="p-toggle" aria-label="Play/Pause">${ICON.play}</button>
      <button class="icon-btn" id="p-next" aria-label="Agla gaana">${ICON.next}</button>
      <button class="icon-btn badge" id="p-repeat" aria-label="Repeat">${ICON.repeat}</button>
    </div>
    <div class="p-foot"><button id="p-queue">${'Queue'}</button><span id="p-sleep"></span><button id="p-lyrics-off"></button></div>`;

  $('#p-close').addEventListener('click', close);
  $('#p-more').addEventListener('click', () => trackMenu(app.current(), { inPlayer: true }));
  $('#p-like').addEventListener('click', () => app.toggleLike());
  $('#p-toggle').addEventListener('click', () => app.toggle());
  $('#p-prev').addEventListener('click', () => app.prev());
  $('#p-next').addEventListener('click', () => app.next());
  $('#p-shuffle').addEventListener('click', () => app.setShuffle());
  $('#p-repeat').addEventListener('click', () => app.setRepeat());
  $('#p-queue').addEventListener('click', openQueue);
  $('#p-lyrics-off').addEventListener('click', () => app.setLyricsOn(!app.state.lyricsOn));
  $('#p-sleep').addEventListener('click', sleepMenu);

  gestures($('#p-stage'), {
    onLeft: () => app.next(),
    onRight: () => app.prev(),
    onDown: close,
    onDoubleTap: () => { app.like(); burst(); },
  });
  initSeek();

  app.on('track', renderTrack);
  app.on('state', renderState);
  app.on('likes', renderLike);
  app.on('modes', renderModes);
  app.on('progress', renderProgress);
  app.on('sleep', renderSleep);
  app.on('lyricsOn', () => renderLyrics(true));
  window.addEventListener('lehar:open', (e) => {
    if (e.detail === 'player') open();
    if (e.detail === 'queue') openQueue();
  });
  renderModes();
  renderSleep();
}

export function open() {
  if (!app.current()) return;
  renderTrack(app.current());
  sheet.open(el);
}
export function close() { sheet.close(el); }

async function renderTrack(t) {
  if (!t) return;
  $('#p-title').textContent = t.title;
  $('#p-artist').textContent = artistOf(t);
  $('#p-ctx').textContent = app.state.context || 'Lehar';
  const art = $('#p-art');
  const lg = artUrl(t, 'lg');
  if (art.dataset.id !== t.videoId) {
    art.dataset.id = t.videoId;
    art.classList.remove('broken');
    art.onerror = () => { art.onerror = () => art.classList.add('broken'); art.src = `https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg`; };
    art.src = lg;
    $('#p-bg').style.backgroundImage = `url("${artUrl(t)}")`;
  }
  $('#p-dur').textContent = fmtTime(t.durationSec);
  renderLike();
  renderState();
  // lyrics
  lyr = null; lyrFor = t.videoId; lastLine = -2; offsetMs = 0;
  renderLyrics(true);
  const [found, cached] = await Promise.all([app.getLyrics(t), store.get('lyrics', t.videoId)]);
  if (lyrFor !== t.videoId) return;
  lyr = found;
  offsetMs = cached?.offsetMs || 0;
  renderLyrics(true);
}

function renderLyrics(force) {
  const box = $('#p-lyrics');
  const stage = $('#p-stage');
  $('#p-lyrics-off').textContent = app.state.lyricsOn ? 'Lyrics: on' : 'Lyrics: off';
  if (!app.state.lyricsOn || !lyr) { box.innerHTML = ''; stage.classList.add('no-lyrics'); return; }
  stage.classList.remove('no-lyrics');
  if (lyr.synced?.length) {
    const i = lineAt(lyr.synced, app.state.position * 1000, offsetMs);
    if (!force && i === lastLine) return;
    lastLine = i;
    const line = (k) => (k >= 0 && k < lyr.synced.length ? lyr.synced[k].text || '♪' : '');
    box.innerHTML = `<div class="l">${esc(line(i - 1))}</div><div class="l cur">${esc(i < 0 ? '♪' : line(i))}</div><div class="l">${esc(line(i + 1))}</div>`;
  } else if (lyr.plain && force) {
    box.innerHTML = `<div class="plain">${esc(lyr.plain)}</div>`;
  }
}

function renderState() {
  $('#p-toggle').innerHTML = app.state.playing ? ICON.pause : ICON.play;
}

function renderLike() {
  const t = app.current();
  const on = t && app.isLiked(t.videoId);
  const b = $('#p-like');
  b.classList.toggle('on', !!on);
  b.innerHTML = on ? ICON.heartFill : ICON.heart;
}

function renderModes() {
  $('#p-shuffle').classList.toggle('on', app.state.shuffle);
  const r = $('#p-repeat');
  r.classList.toggle('on', app.state.repeat !== 'off');
  r.dataset.n = app.state.repeat === 'one' ? '1' : '';
}

function renderProgress({ sec, dur }) {
  if (!seeking) {
    const pct = dur ? Math.min(100, (sec / dur) * 100) : 0;
    $('#p-fill').style.width = `${pct}%`;
    $('#p-knob').style.left = `${pct}%`;
    $('#p-pos').textContent = fmtTime(sec);
    $('#p-dur').textContent = fmtTime(dur);
  }
  if (!el.hidden) renderLyrics(false);
}

function initSeek() {
  const bar = $('#p-seek');
  const pctAt = (e) => { const r = bar.getBoundingClientRect(); return Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)); };
  const show = (p) => {
    $('#p-fill').style.width = `${p * 100}%`;
    $('#p-knob').style.left = `${p * 100}%`;
    $('#p-pos').textContent = fmtTime(p * app.state.duration);
  };
  bar.addEventListener('pointerdown', (e) => { seeking = true; bar.setPointerCapture(e.pointerId); show(pctAt(e)); });
  bar.addEventListener('pointermove', (e) => { if (seeking) show(pctAt(e)); });
  bar.addEventListener('pointerup', (e) => { if (!seeking) return; seeking = false; app.seek(pctAt(e) * app.state.duration); lastLine = -2; });
  bar.addEventListener('pointercancel', () => { seeking = false; });
}

function burst() {
  const b = document.createElement('div');
  b.className = 'heart-burst';
  b.innerHTML = ICON.heartFill;
  $('#p-stage').appendChild(b);
  setTimeout(() => b.remove(), 750);
}

// ---------- sleep timer ----------
function renderSleep() {
  const s = app.state.sleep;
  const el2 = $('#p-sleep');
  if (!s) { el2.textContent = 'Sleep timer'; el2.classList.remove('sleep-on'); return; }
  el2.classList.add('sleep-on');
  el2.textContent = s.endOfTrack ? 'Gaana khatam hone par band' : `Band: ${new Date(s.until).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

export function sleepMenu() {
  sheet.menu('Sleep timer', [
    ...[15, 30, 45, 60].map((m) => ({ label: `${m} minute`, onClick: () => { app.setSleep(m); toast(`${m} minute baad band hoga`); } })),
    { label: 'Ye gaana khatam hone par', onClick: () => { app.setSleep('end'); toast('Gaana khatam hone par band hoga'); } },
    { label: 'Timer band karo', onClick: () => app.setSleep(0) },
  ]);
}

// ---------- queue sheet ----------
export function openQueue() {
  const pop = $('#popover');
  const render = () => {
    const { queue, index } = app.state;
    pop.innerHTML = `<div class="grabber"></div><div class="sheet-head"><h2>Queue</h2><span class="note">${Math.max(0, queue.length - index - 1)} aage</span></div>
      <div class="sheet-body" id="q-body">${queue.map((t, i) => (i < index ? '' : `<div class="q-item" data-i="${i}">${trackRow(t, { now: i === index, more: false, meta: i === index ? 'Abhi' : fmtTime(t.durationSec) })}</div>`)).join('') || '<div class="empty">Queue khaali hai</div>'}</div>`;
    const body = pop.querySelector('#q-body');
    body.querySelectorAll('.q-item').forEach((item) => {
      const i = Number(item.dataset.i);
      gestures(item, {
        onTap: () => { sheet.close(pop); app.jumpTo(i); },
        onLeft: () => { if (i !== app.state.index) { app.removeFromQueue(i); render(); } },
        threshold: 50,
      });
    });
  };
  render();
  sheet.open(pop, { pop: true });
}

// ---------- ⋯ menu for any track ----------
export function trackMenu(t, { inPlayer = false } = {}) {
  if (!t) return;
  const liked = app.isLiked(t.videoId);
  const items = [];
  if (!inPlayer) {
    items.push({ label: 'Abhi chalao', icon: ICON.play, onClick: () => app.playQueue([t], 0, { context: 'Lehar' }).then(() => app.moreLikeThis(t, { silent: true })) });
    items.push({ label: 'Agla yahi bajao', icon: ICON.next, onClick: () => app.playNext(t) });
    items.push({ label: 'Queue mein jodo', icon: ICON.queue, onClick: () => app.addToQueue(t) });
  }
  items.push({ label: liked ? 'Liked se hatao' : 'Like karo', icon: liked ? ICON.heartFill : ICON.heart, onClick: () => app.toggleLike(t) });
  items.push({ label: 'Playlist mein jodo', icon: ICON.plus, onClick: () => addToPlaylist(t) });
  items.push({ label: 'Is gaane jaise aur', icon: ICON.wave, onClick: () => app.moreLikeThis(t) });
  if (inPlayer) {
    if (lyr?.synced?.length) items.push({ label: 'Lyrics timing', sub: `${offsetMs >= 0 ? '+' : ''}${(offsetMs / 1000).toFixed(1)}s`, icon: ICON.clock, onClick: () => offsetMenu(t) });
    items.push({ label: 'Sleep timer', icon: ICON.clock, onClick: sleepMenu });
  }
  sheet.menu(esc(t.title), items);
}

function offsetMenu(t) {
  const pop = $('#popover');
  const show = () => (pop.querySelector('#off-v').textContent = `${offsetMs >= 0 ? '+' : ''}${(offsetMs / 1000).toFixed(1)}s`);
  pop.innerHTML = `<div class="grabber"></div><div class="sheet-head"><h2>Lyrics timing</h2></div>
    <div class="sheet-body"><p class="note">Lyrics gaane se aage chal rahi hain to − dabao, peeche chal rahi hain to +.</p>
      <div class="menu-item"><span>Offset</span><div class="stepper"><button id="off-m" aria-label="Kam karo">−</button><span id="off-v"></span><button id="off-p" aria-label="Badhao">+</button></div></div>
      <div class="opts"><button class="btn" id="off-r">Reset</button></div></div>`;
  const set = (v) => { offsetMs = v; show(); lastLine = -2; renderLyrics(true); app.setLyricsOffset(t.videoId, v); };
  pop.querySelector('#off-m').addEventListener('click', () => set(offsetMs - 500));
  pop.querySelector('#off-p').addEventListener('click', () => set(offsetMs + 500));
  pop.querySelector('#off-r').addEventListener('click', () => set(0));
  show();
  sheet.open(pop, { pop: true });
}

export async function addToPlaylist(t) {
  const lists = (await store.all('playlists')).sort((a, b) => b.updatedAt - a.updatedAt);
  sheet.menu('Playlist chuno', [
    { label: 'Nayi playlist banao', icon: ICON.plus, onClick: () => newPlaylist(t) },
    ...lists.map((p) => ({
      label: esc(p.name), sub: `${p.videoIds.length} gaane`,
      onClick: async () => {
        if (!p.videoIds.includes(t.videoId)) p.videoIds.push(t.videoId);
        p.updatedAt = Date.now();
        await store.saveTrack(t);
        await store.put('playlists', p, p.id);
        toast(`"${p.name}" mein jod diya`);
        app.emit('playlists');
      },
    })),
  ]);
}

export function newPlaylist(t) {
  const pop = $('#popover');
  pop.innerHTML = `<div class="grabber"></div><div class="sheet-head"><h2>Nayi playlist</h2></div>
    <div class="sheet-body"><form id="np-form" style="display:flex;flex-direction:column;gap:12px">
      <input class="field" id="np-name" maxlength="60" placeholder="Naam, jaise: Raat ke gaane" autocomplete="off">
      <button class="btn primary" type="submit">Banao</button></form></div>`;
  sheet.open(pop, { pop: true });
  const input = pop.querySelector('#np-name');
  setTimeout(() => input.focus(), 50);
  pop.querySelector('#np-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = input.value.trim();
    if (!name) { input.focus(); return; }
    const p = { id: `pl${Date.now()}`, name, videoIds: t ? [t.videoId] : [], updatedAt: Date.now() };
    if (t) await store.saveTrack(t);
    await store.put('playlists', p, p.id);
    sheet.close(pop);
    toast(t ? `"${name}" bani aur gaana jud gaya` : `"${name}" ban gayi`);
    app.emit('playlists');
  });
}
