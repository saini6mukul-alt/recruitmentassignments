// Settings: language mix, lyrics, default sleep timer, backup export/import.
import * as app from '../app.js';
import * as store from '../store.js';
import * as bridge from '../bridge.js';
import { $, ICON, toast } from './dom.js';
import * as sheet from './sheet.js';
import { sleepMenu } from './player.js';
import { rebalanceMix } from '../engine/mood.js';

const LANGS = [['hindi', 'Hindi'], ['punjabi', 'Punjabi'], ['english', 'English']];
let el;

export function init() {
  el = $('#settings');
  el.innerHTML = `
    <div class="sheet-head"><button class="icon-btn" id="st-back" aria-label="Wapas">${ICON.back}</button><h2>Settings</h2></div>
    <div class="sheet-body">
      <div class="set-group">
        <h3>Language mix</h3>
        <p class="note">Mood queue mein kis language ke kitne gaane aayein. Teeno milke hamesha 100 rehte hain.</p>
        ${LANGS.map(([k, v]) => `<div class="slider-row"><label for="mix-${k}">${v}</label><input type="range" id="mix-${k}" min="0" max="100" step="5" data-lang="${k}"><output id="mix-${k}-o"></output></div>`).join('')}
      </div>
      <div class="set-group">
        <h3>Player</h3>
        <button class="menu-item" id="st-lyrics"><span>Synced lyrics dikhao</span><span class="switch" id="st-lyrics-sw"></span></button>
        <button class="menu-item" id="st-sleep"><span>Sleep timer</span><small id="st-sleep-v">Band</small></button>
      </div>
      <div class="set-group">
        <h3>Backup</h3>
        <p class="note">Liked songs, playlists aur history ek file mein save karo, aur naye phone par wapas lao.</p>
        <div class="opts"><button class="btn" id="st-export">Backup banao</button><label class="btn" for="st-import">Backup se wapas lao</label>
        <input type="file" id="st-import" accept="application/json,.json" hidden></div>
      </div>
      <div class="set-group">
        <h3>Lehar</h3>
        <p class="note">Version 1.0 · Gaane YouTube se, lyrics LRCLIB se. Sirf personal use ke liye.</p>
      </div>
    </div>`;

  $('#st-back').addEventListener('click', () => sheet.close(el));
  for (const [k] of LANGS) $(`#mix-${k}`).addEventListener('input', (e) => onMix(k, Number(e.target.value)));
  $('#st-lyrics').addEventListener('click', () => app.setLyricsOn(!app.state.lyricsOn));
  $('#st-sleep').addEventListener('click', sleepMenu);
  $('#st-export').addEventListener('click', exportBackup);
  $('#st-import').addEventListener('change', importBackup);
  app.on('lyricsOn', render);
  app.on('sleep', render);
  window.addEventListener('lehar:open', (e) => { if (e.detail === 'settings') { sheet.open(el); render(); } });
}

function render() {
  const mix = app.state.mix;
  for (const [k] of LANGS) { $(`#mix-${k}`).value = mix[k]; $(`#mix-${k}-o`).textContent = mix[k]; }
  $('#st-lyrics-sw').classList.toggle('on', app.state.lyricsOn);
  const s = app.state.sleep;
  $('#st-sleep-v').textContent = !s ? 'Band' : s.endOfTrack ? 'Gaana khatam hone par' : new Date(s.until).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function onMix(lang, value) {
  app.setMix(rebalanceMix(app.state.mix, lang, value));
  render();
}

async function exportBackup() {
  const data = JSON.stringify(await store.exportAll());
  const name = `lehar-backup-${new Date().toISOString().slice(0, 10)}.json`;
  if (bridge.isNative()) {
    const ok = bridge.native('saveFile', name, data);
    toast(ok ? `Downloads mein save hua: ${name}` : 'Backup save nahi ho paya');
  } else {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([data], { type: 'application/json' }));
    a.download = name;
    a.click();
  }
}

async function importBackup(e) {
  const f = e.target.files?.[0];
  e.target.value = '';
  if (!f) return;
  try {
    await store.importAll(JSON.parse(await f.text()));
    app.state.likes = new Set(await store.keys('likes'));
    app.state.mix = await store.getSetting('mix', app.state.mix);
    app.emit('likes'); app.emit('history'); app.emit('playlists'); app.emit('mix', app.state.mix);
    render();
    toast('Backup wapas aa gaya');
  } catch (err) {
    toast(err.message?.startsWith('Ye Lehar') ? err.message : 'Ye file padh nahi paye');
  }
}
