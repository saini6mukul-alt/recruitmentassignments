// Resumes playback when the WebView engine pauses it in the background (NAUX fix, layer 5).
// Never fights the user (their own pause) or another app that holds the audio (calls, reels).
import { STATE } from './ytplayer.js';

export function createWatchdog({ player, isUserPaused, otherAudio, lastUserActionAt, now = () => Date.now() }) {
  let tries = [];
  return {
    tick() {
      if (player.state !== STATE.PAUSED) return false;
      if (isUserPaused() || otherAudio()) return false;
      if (now() - lastUserActionAt() < 1500) return false;
      const t = now();
      tries = tries.filter((x) => t - x < 30000);
      if (tries.length >= 3) return false;
      tries.push(t);
      player.play();
      return true;
    },
  };
}

export function startWatchdog(opts, intervalMs = 3000) {
  const w = createWatchdog(opts);
  return setInterval(() => w.tick(), intervalMs);
}
