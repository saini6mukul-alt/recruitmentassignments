import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as ytm from '../www/js/engine/ytm.js';

const fx = (n) => JSON.parse(fs.readFileSync(new URL(`./fixtures/${n}.json`, import.meta.url)));

test('parseDuration handles m:ss, h:mm:ss and empty', () => {
  assert.equal(ytm.parseDuration('3:45'), 225);
  assert.equal(ytm.parseDuration('1:02:03'), 3723);
  assert.equal(ytm.parseDuration(''), 0);
  assert.equal(ytm.parseDuration(undefined), 0);
});

test('videos-filter search yields playable tracks', () => {
  const r = ytm.parseSearch(fx('search-videos'));
  assert.equal(r.empty, false);
  assert.ok(r.tracks.length >= 10, `got ${r.tracks.length}`);
  for (const t of r.tracks) {
    assert.match(t.videoId, /^[\w-]{11}$/);
    assert.ok(t.title.length > 0);
    assert.ok(t.thumb.startsWith('https://'));
  }
  const k = r.tracks.find((t) => t.videoId === 'W1S9AbHpWFY');
  assert.equal(k.artist, 'Arijit Singh');
  assert.equal(k.durationSec, 266);
});

test('all search yields artists with browse ids', () => {
  const r = ytm.parseSearch(fx('search-all'));
  assert.ok(r.artists.length >= 1);
  assert.ok(r.artists.some((a) => a.name === 'Arijit Singh' && a.browseId.startsWith('UC')));
});

test('no-results response is empty, not a crash (Review Focus 1)', () => {
  const r = ytm.parseSearch(fx('search-noresults'));
  assert.deepEqual(r.tracks, []);
  assert.equal(r.empty, true);
});

test('garbage input returns empty structures', () => {
  assert.deepEqual(ytm.parseSearch({}).tracks, []);
  assert.deepEqual(ytm.parseSearch(null).artists, []);
  assert.deepEqual(ytm.parseNext(null), []);
  assert.deepEqual(ytm.parseSuggest(undefined), []);
});

test('parseNext returns radio tracks with durations and no duplicates', () => {
  const t = ytm.parseNext(fx('next'));
  assert.ok(t.length >= 20, `got ${t.length}`);
  assert.ok(t.every((x) => x.durationSec > 0));
  assert.equal(new Set(t.map((x) => x.videoId)).size, t.length);
});

test('parseSuggest returns query strings', () => {
  assert.ok(ytm.parseSuggest(fx('suggest')).includes('kesariya'));
});

test('searchBody carries filter params only when asked', () => {
  assert.equal(ytm.searchBody('x', 'videos').params, ytm.FILTER.videos);
  assert.equal(ytm.searchBody('x').params, undefined);
  assert.equal(ytm.searchBody('x').context.client.clientName, 'WEB_REMIX');
  assert.equal(ytm.nextBody('abc').playlistId, 'RDAMVMabc');
  assert.equal(ytm.endpoint('search'), 'https://music.youtube.com/youtubei/v1/search?prettyPrint=false');
});

test('bestThumb picks the largest and upsizes googleusercontent', () => {
  assert.equal(ytm.bestThumb([{ url: 'https://a/s', width: 60 }, { url: 'https://a/l', width: 400 }]), 'https://a/l');
  assert.equal(ytm.bestThumb([{ url: 'https://lh3.googleusercontent.com/x=w60-h60-l90-rj', width: 60 }]),
    'https://lh3.googleusercontent.com/x=w544-h544-l90-rj');
  assert.equal(ytm.bestThumb([]), '');
});

test('radio artist is never a playlist name', () => {
  const t = ytm.parseNext(fx('next'));
  assert.ok(t.every((x) => !/Official Playlist/i.test(x.artist)), t.map((x) => x.artist).join(' | '));
});
