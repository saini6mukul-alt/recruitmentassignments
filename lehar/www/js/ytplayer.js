// Two hidden YouTube IFrame players: one plays, the other pre-cues the next track for a gapless start.
const API_URL = 'https://www.youtube.com/iframe_api';
let apiReady = null;

function loadApi() {
  if (apiReady) return apiReady;
  apiReady = new Promise((resolve) => {
    if (window.YT && window.YT.Player) return resolve(window.YT);
    const prev = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => { prev?.(); resolve(window.YT); };
    const s = document.createElement('script');
    s.src = API_URL;
    s.async = true;
    s.onerror = () => { apiReady = null; };
    document.head.appendChild(s);
  });
  return apiReady;
}

const S = { UNSTARTED: -1, ENDED: 0, PLAYING: 1, PAUSED: 2, BUFFERING: 3, CUED: 5 };
export const STATE = S;

/**
 * @param {{onState?:(s:number)=>void, onEnded?:()=>void, onError?:(code:number, track:object)=>void, onProgress?:(sec:number,dur:number)=>void}} cb
 */
export function createPlayer(cb = {}) {
  const host = document.createElement('div');
  host.id = 'yt-host';
  host.setAttribute('aria-hidden', 'true');
  host.style.cssText = 'position:fixed;left:0;bottom:0;width:2px;height:2px;opacity:0.01;pointer-events:none;overflow:hidden;z-index:-1';
  document.body.appendChild(host);

  const slots = [mk('yt-a'), mk('yt-b')];
  let active = 0;
  let current = null; // track loaded in the active player
  let cued = null;    // track cued in the idle player
  let state = S.UNSTARTED;
  let retried = '';
  let ticker = null;
  let pendingLoad = null;

  function mk(id) {
    const d = document.createElement('div');
    d.id = id;
    host.appendChild(d);
    return { id, player: null, ready: false, readyP: null };
  }

  async function ensure(slot) {
    if (slot.readyP) return slot.readyP;
    slot.readyP = loadApi().then((YT) => new Promise((resolve) => {
      slot.player = new YT.Player(slot.id, {
        width: '2', height: '2',
        playerVars: { autoplay: 0, playsinline: 1, controls: 0, disablekb: 1, fs: 0, rel: 0, iv_load_policy: 3, origin: location.origin },
        events: {
          onReady: () => { slot.ready = true; resolve(slot.player); },
          onStateChange: (e) => onStateChange(slot, e.data),
          onError: (e) => onError(slot, e.data),
        },
      });
    }));
    return slot.readyP;
  }

  const isActive = (slot) => slots[active] === slot;

  function onStateChange(slot, s) {
    if (!isActive(slot)) return;
    state = s;
    cb.onState?.(s);
    if (s === S.PLAYING) startTicker(); else stopTicker();
    if (s === S.ENDED) cb.onEnded?.();
  }

  function onError(slot, code) {
    if (!isActive(slot)) { if (slot === slots[1 - active]) cued = null; return; }
    const t = current;
    // 2/5: transient player problems → retry once; 100/101/150: unavailable or embedding blocked
    if ((code === 2 || code === 5) && t && retried !== t.videoId) {
      retried = t.videoId;
      slot.player.loadVideoById(t.videoId, Math.floor(slot.player.getCurrentTime?.() || 0));
      return;
    }
    cb.onError?.(code, t);
  }

  function startTicker() {
    stopTicker();
    ticker = setInterval(() => {
      const p = slots[active].player;
      if (p?.getCurrentTime) cb.onProgress?.(p.getCurrentTime() || 0, p.getDuration?.() || current?.durationSec || 0);
    }, 500);
  }
  function stopTicker() { if (ticker) clearInterval(ticker); ticker = null; }

  async function load(track, startSec = 0, autoplay = true) {
    pendingLoad = track.videoId;
    retried = '';
    const idle = slots[1 - active];
    // The idle player already has this track cued: swap instantly.
    if (cued && cued.videoId === track.videoId && idle.ready) {
      slots[active].player?.stopVideo?.();
      active = 1 - active;
      current = track;
      cued = null;
      if (startSec) idle.player.seekTo(startSec, true);
      if (autoplay) idle.player.playVideo(); else cb.onState?.(S.CUED);
      return;
    }
    const slot = slots[active];
    const p = await ensure(slot);
    if (pendingLoad !== track.videoId) return; // a newer load won
    current = track;
    if (autoplay) p.loadVideoById({ videoId: track.videoId, startSeconds: startSec });
    else { p.cueVideoById({ videoId: track.videoId, startSeconds: startSec }); state = S.CUED; cb.onState?.(S.CUED); }
  }

  async function cueNext(track) {
    if (!track) return;
    const idle = slots[1 - active];
    const p = await ensure(idle);
    cued = track;
    p.mute?.();
    p.cueVideoById(track.videoId);
    p.unMute?.();
  }

  const pl = () => slots[active].player;
  return {
    load,
    cueNext,
    play: () => pl()?.playVideo?.(),
    pause: () => pl()?.pauseVideo?.(),
    seek: (sec) => pl()?.seekTo?.(sec, true),
    toggle() { state === S.PLAYING ? this.pause() : this.play(); },
    get state() { return state; },
    get currentTime() { return pl()?.getCurrentTime?.() || 0; },
    get duration() { return pl()?.getDuration?.() || current?.durationSec || 0; },
    get track() { return current; },
    warmup: () => ensure(slots[0]),
  };
}
