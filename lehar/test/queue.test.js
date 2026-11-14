import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as q from '../www/js/engine/queue.js';

const T = (id, title = id, artist = 'a') => ({ videoId: id.padEnd(11, 'x'), title, artist, album: '', durationSec: 200, thumb: 'https://x' });
const H = 3600e3;
const now = 10 * 24 * H;

test('normTitle merges official/lyrical/film variants (Review Focus 2)', () => {
  assert.equal(q.normTitle('Kesariya (From "Brahmastra") | Official Video'), q.normTitle('Kesariya - Lyrical 4K'));
  assert.equal(q.normTitle('KESARIYA Full Song'), q.normTitle('Kesariya'));
});

test('normTitle keeps slowed/remix variants distinct', () => {
  assert.notEqual(q.normTitle('Kesariya'), q.normTitle('Kesariya [Slowed + Reverb]'));
  assert.notEqual(q.normTitle('Kesariya'), q.normTitle('Kesariya (Dance Mix)'));
  assert.equal(q.normTitle('Kesariya [Slowed + Reverb]'), q.normTitle('Kesariya (Reverb Slowed) | Lofi'.replace(' | Lofi', '')));
});

test('dedupe by id and normalised title, keeping first', () => {
  const out = q.dedupe([T('a', 'Song (Official Video)'), T('b', 'Song | Lyrical'), T('a'), T('c', 'Other')]);
  assert.deepEqual(out.map((t) => t.videoId[0]), ['a', 'c']);
});

test('repeatGuard excludes tracks played in the last 3 hours', () => {
  const hist = [{ videoId: T('a').videoId, at: now - 2 * H }];
  assert.deepEqual(q.repeatGuard([T('a'), T('c')], hist, now).map((t) => t.videoId[0]), ['c']);
});

test('repeatGuard excludes tracks among the last 50 plays even if old', () => {
  const hist = [{ videoId: T('b').videoId, at: now - 30 * H }];
  assert.deepEqual(q.repeatGuard([T('b'), T('c')], hist, now).map((t) => t.videoId[0]), ['c']);
});

test('repeatGuard allows a track older than 3h and outside last 50', () => {
  const hist = [{ videoId: T('b').videoId, at: now - 30 * H }, ...Array.from({ length: 50 }, (_, i) => ({ videoId: 'z' + i, at: now - 20 * H }))];
  assert.equal(q.repeatGuard([T('b')], hist, now).length, 1);
});

test('repeatGuard relaxes instead of returning empty', () => {
  const hist = [T('a'), T('b')].map((t, i) => ({ videoId: t.videoId, at: now - i * 60e3 }));
  assert.ok(q.repeatGuard([T('a'), T('b')], hist, now).length >= 1);
  assert.deepEqual(q.repeatGuard([], hist, now), []);
});

test('interleave follows mix and starts with the heaviest language', () => {
  const g = { hindi: [T('h1'), T('h2'), T('h3'), T('h4')], punjabi: [T('p1'), T('p2')], english: [T('e1'), T('e2')] };
  const out = q.interleave(g, { hindi: 50, punjabi: 25, english: 25 });
  assert.equal(out.length, 8);
  assert.equal(out[0].videoId[0], 'h');
  assert.ok(out.slice(0, 4).some((t) => t.videoId[0] === 'p'));
  assert.ok(out.slice(0, 4).some((t) => t.videoId[0] === 'e'));
});

test('interleave drains remaining groups when one runs out', () => {
  const out = q.interleave({ hindi: [T('h1')], punjabi: [T('p1'), T('p2'), T('p3')], english: [] }, { hindi: 90, punjabi: 10, english: 0 });
  assert.equal(out.length, 4);
});

test('buildQueue fills to size with unique tracks', () => {
  const groups = {
    hindi: Array.from({ length: 20 }, (_, i) => T('h' + i, 'H song ' + i)),
    punjabi: Array.from({ length: 20 }, (_, i) => T('p' + i, 'P song ' + i)),
    english: [],
  };
  const out = q.buildQueue({ groups, personal: [], history: [], now, mix: { hindi: 50, punjabi: 50, english: 0 } });
  assert.equal(out.length, 25);
  assert.equal(new Set(out.map((t) => t.videoId)).size, 25);
});

test('buildQueue weaves personal picks into every third slot', () => {
  const groups = { hindi: Array.from({ length: 30 }, (_, i) => T('h' + i, 'H song ' + i)), punjabi: [], english: [] };
  const personal = [T('m1', 'Mine one'), T('m2', 'Mine two')];
  const out = q.buildQueue({ groups, personal, history: [], now, mix: { hindi: 100, punjabi: 0, english: 0 } });
  assert.equal(out[2].videoId[0], 'm');
  assert.equal(out[5].videoId[0], 'm');
});

test('build gate drops stale builds (Review Focus 3)', () => {
  const g = q.createBuildGate();
  const a = g.start();
  const b = g.start();
  assert.equal(g.isCurrent(a), false);
  assert.equal(g.isCurrent(b), true);
});

test('nearbyTagged uses radius 0.25', () => {
  const tags = { [T('a').videoId]: { energy: 0.9, valence: 0.9, n: 2 }, [T('b').videoId]: { energy: 0.1, valence: 0.1, n: 1 } };
  const byId = { [T('a').videoId]: T('a'), [T('b').videoId]: T('b') };
  assert.deepEqual(q.nearbyTagged(tags, byId, { energy: 0.8, valence: 0.8 }).map((t) => t.videoId[0]), ['a']);
});
