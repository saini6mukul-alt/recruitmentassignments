import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { parseLrc, lineAt, cleanForLookup, pickMatch, getUrl, searchUrl } from '../www/js/engine/lyrics.js';

const fx = (n) => JSON.parse(fs.readFileSync(new URL(`./fixtures/${n}.json`, import.meta.url)));

test('parseLrc handles multiple stamps, centiseconds, tags and blank lines', () => {
  const l = parseLrc('[00:01.50]Hello\n[00:03.00][00:10.00]Again\n[ar:x]\n[00:05.2]');
  assert.deepEqual(l.map((x) => x.t), [1500, 3000, 5200, 10000]);
  assert.equal(l[0].text, 'Hello');
  assert.equal(l[2].text, '');
  assert.deepEqual(parseLrc(''), []);
  assert.deepEqual(parseLrc(null), []);
});

test('lineAt respects offset', () => {
  const l = parseLrc('[00:01.00]a\n[00:02.00]b');
  assert.equal(lineAt(l, 500), -1);
  assert.equal(lineAt(l, 1500), 0);
  assert.equal(lineAt(l, 1500, 600), 1);
  assert.equal(lineAt(l, 99999), 1);
  assert.equal(lineAt([], 1000), -1);
});

test('cleanForLookup strips video noise', () => {
  assert.deepEqual(cleanForLookup('Kesariya (From "Brahmastra") | Official Video', 'Arijit Singh - Topic'),
    { track: 'Kesariya', artist: 'Arijit Singh' });
  assert.deepEqual(cleanForLookup('Kesariya - Brahmāstra | Ranbir Kapoor', ''), { track: 'Kesariya', artist: '' });
  assert.deepEqual(cleanForLookup('Lover [Official Lyric Video] ft. Someone', 'Taylor Swift'),
    { track: 'Lover', artist: 'Taylor Swift' });
});

test('pickMatch rejects wrong-length versions (Review Focus 5)', () => {
  const c = [
    { duration: 400, syncedLyrics: '[00:01.00]x' },
    { duration: 268, syncedLyrics: '[00:01.00]y', plainLyrics: 'y' },
  ];
  assert.equal(pickMatch(c, 270).synced[0].text, 'y');
  assert.equal(pickMatch([{ duration: 300, plainLyrics: 'z' }], 200), null);
});

test('pickMatch falls back to plain lyrics and skips instrumentals', () => {
  assert.deepEqual(pickMatch([{ duration: 200, plainLyrics: 'p' }], 201), { plain: 'p' });
  assert.equal(pickMatch([{ duration: 200, instrumental: true }], 200), null);
  assert.equal(pickMatch([], 200), null);
});

test('pickMatch accepts a single LRCLIB get object and real search results', () => {
  const got = pickMatch(fx('lrclib-get'), 268);
  assert.ok(got && (got.synced || got.plain));
  const s = pickMatch(fx('lrclib-search'), 268);
  assert.ok(s && (s.synced?.length > 5 || s.plain));
});

test('urls are encoded', () => {
  assert.equal(getUrl({ track: 'Tum Hi Ho', artist: 'Arijit Singh', durationSec: 262 }),
    'https://lrclib.net/api/get?track_name=Tum+Hi+Ho&artist_name=Arijit+Singh&duration=262');
  assert.equal(searchUrl({ track: 'Tum Hi Ho', artist: '' }), 'https://lrclib.net/api/search?track_name=Tum+Hi+Ho');
});
