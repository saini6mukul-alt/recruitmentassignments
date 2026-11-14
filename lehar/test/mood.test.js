import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as m from '../www/js/engine/mood.js';

const seq = () => { let i = 0; return () => ((i++ * 0.37) % 1); };
const LANGS = ['hindi', 'punjabi', 'english'];
const count = (p, l) => p.filter((x) => x.language === l).length;

test('zones cover corners and centre', () => {
  assert.equal(m.zoneOf({ energy: 1, valence: 1 }).id, 'high-high');
  assert.equal(m.zoneOf({ energy: 0, valence: 0 }).id, 'low-low');
  assert.equal(m.zoneOf({ energy: 0.5, valence: 0.5 }).id, 'mid-mid');
  assert.equal(m.zoneOf({ energy: 1, valence: 1 }).label, 'Party vibe');
  assert.ok(m.zoneOf({ energy: 0, valence: 0 }).label.length > 0);
});

test('every zone has at least 3 seeds for every language', () => {
  assert.equal(Object.keys(m.SEEDS).length, 9);
  for (const z of Object.values(m.SEEDS)) for (const l of LANGS) assert.ok(z[l].length >= 3);
});

test('chips sit at the spec points', () => {
  assert.deepEqual(m.CHIPS.Gym, { energy: 0.9, valence: 0.6 });
  assert.deepEqual(m.CHIPS.Sad, { energy: 0.25, valence: 0.1 });
  assert.deepEqual(Object.keys(m.CHIPS), ['Gym', 'Drive', 'Focus', 'Sleep', 'Party', 'Sad']);
});

test('buildPlan respects mix proportions', () => {
  const p = m.buildPlan({ energy: 0.9, valence: 0.9 }, { hindi: 50, punjabi: 50, english: 0 }, 6, seq());
  assert.equal(p.length, 6);
  assert.equal(count(p, 'hindi'), 3);
  assert.equal(count(p, 'english'), 0);
});

test('buildPlan with default 40/30/30 mix over 10 queries', () => {
  const p = m.buildPlan(m.CHIPS.Gym, m.DEFAULT_MIX, 10, seq());
  assert.deepEqual(LANGS.map((l) => count(p, l)), [4, 3, 3]);
});

test('no duplicate queries in a plan, even when asking for more than seeds', () => {
  const p = m.buildPlan(m.CHIPS.Party, { hindi: 100, punjabi: 0, english: 0 }, 50, seq());
  assert.equal(new Set(p.map((x) => x.query)).size, p.length);
  assert.ok(p.length >= 3);
});

test('all-zero mix falls back to default', () => {
  const p = m.buildPlan(m.CHIPS.Sad, { hindi: 0, punjabi: 0, english: 0 }, 5, seq());
  assert.equal(p.length, 5);
});

test('clampPoint and distance', () => {
  assert.deepEqual(m.clampPoint({ energy: 1.4, valence: -2 }), { energy: 1, valence: 0 });
  assert.equal(m.distance({ energy: 0, valence: 0 }, { energy: 0.3, valence: 0.4 }).toFixed(2), '0.50');
});

test('rebalanceMix keeps the total at 100 and the others proportional', () => {
  assert.deepEqual(m.rebalanceMix({ hindi: 40, punjabi: 30, english: 30 }, 'hindi', 70), { hindi: 70, punjabi: 15, english: 15 });
  assert.deepEqual(m.rebalanceMix({ hindi: 100, punjabi: 0, english: 0 }, 'hindi', 50), { hindi: 50, punjabi: 25, english: 25 });
  const r = m.rebalanceMix({ hindi: 50, punjabi: 40, english: 10 }, 'english', 35);
  assert.equal(r.hindi + r.punjabi + r.english, 100);
  assert.equal(r.english, 35);
  assert.ok(r.hindi > r.punjabi);
});
