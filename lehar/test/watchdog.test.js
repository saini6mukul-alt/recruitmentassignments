import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createWatchdog } from '../www/js/watchdog.js';

function setup({ state = 2, userPaused = false, other = false, lastAction = 0 } = {}) {
  let t = 100000;
  const calls = [];
  const player = { state, play: () => calls.push(t) };
  const w = createWatchdog({ player, isUserPaused: () => userPaused, otherAudio: () => other, lastUserActionAt: () => lastAction, now: () => t });
  return { w, calls, adv: (ms) => (t += ms), player };
}

test('resumes when the engine paused it', () => {
  const s = setup();
  assert.equal(s.w.tick(), true);
  assert.equal(s.calls.length, 1);
});

test('never resumes a user pause', () => { assert.equal(setup({ userPaused: true }).w.tick(), false); });
test('waits while another app holds the audio', () => { assert.equal(setup({ other: true }).w.tick(), false); });
test('ignores a pause right after a user action', () => { assert.equal(setup({ lastAction: 99500 }).w.tick(), false); });
test('does nothing while playing', () => { assert.equal(setup({ state: 1 }).w.tick(), false); });

test('gives up after 3 tries in 30s, then tries again later', () => {
  const s = setup();
  for (let i = 0; i < 5; i++) { s.w.tick(); s.adv(3000); }
  assert.equal(s.calls.length, 3);
  s.adv(30000);
  assert.equal(s.w.tick(), true);
});
