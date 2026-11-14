// Mood Pad engine: maps a point on the pad to a vibe zone and a plan of search queries.
// energy: 0 = Shaant (calm) → 1 = Energy;  valence: 0 = Udaas (sad) → 1 = Khush (happy).

export const CHIPS = {
  Gym: { energy: 0.9, valence: 0.6 },
  Drive: { energy: 0.65, valence: 0.7 },
  Focus: { energy: 0.3, valence: 0.5 },
  Sleep: { energy: 0.1, valence: 0.4 },
  Party: { energy: 0.95, valence: 0.9 },
  Sad: { energy: 0.25, valence: 0.1 },
};

export const DEFAULT_MIX = { hindi: 40, punjabi: 30, english: 30 };
const LANGS = ['hindi', 'punjabi', 'english'];

const LABELS = {
  'low-low': 'Udaas aur slow',
  'low-mid': 'Shaant aur halka',
  'low-high': 'Sukoon wala pyaar',
  'mid-low': 'Dil toota, beat chalu',
  'mid-mid': 'Chill vibe',
  'mid-high': 'Feel-good',
  'high-low': 'Gussa aur josh',
  'high-mid': 'Workout josh',
  'high-high': 'Party vibe',
};

export const SEEDS = {
  'low-low': {
    hindi: ['sad hindi songs', 'arijit singh sad songs', 'heartbreak bollywood songs', 'emotional hindi songs slow'],
    punjabi: ['sad punjabi songs', 'punjabi heartbreak songs', 'emotional punjabi songs', 'satinder sartaaj songs'],
    english: ['sad songs english', 'heartbreak ballads', 'slow sad pop songs', 'lewis capaldi songs'],
  },
  'low-mid': {
    hindi: ['soft hindi songs', 'hindi lofi songs', 'calm bollywood songs', 'acoustic hindi songs'],
    punjabi: ['soft punjabi songs', 'punjabi lofi', 'calm punjabi songs', 'punjabi acoustic songs'],
    english: ['chill acoustic songs', 'calm indie songs', 'soft pop songs', 'lofi chill songs'],
  },
  'low-high': {
    hindi: ['romantic hindi songs soft', 'bollywood love songs', 'sufi romantic songs', 'hindi romantic acoustic'],
    punjabi: ['romantic punjabi songs', 'punjabi love songs soft', 'punjabi romantic acoustic', 'b praak romantic songs'],
    english: ['romantic acoustic songs', 'love songs english soft', 'ed sheeran love songs', 'feel good acoustic love'],
  },
  'mid-low': {
    hindi: ['hindi breakup songs', 'bollywood breakup songs upbeat', 'hindi sad songs beat', 'yo yo honey singh sad'],
    punjabi: ['punjabi breakup songs', 'punjabi sad songs beat', 'ap dhillon sad songs', 'punjabi bewafa songs'],
    english: ['breakup songs upbeat', 'moody pop songs', 'the weeknd songs', 'sad dance songs'],
  },
  'mid-mid': {
    hindi: ['hindi chill songs', 'bollywood indie songs', 'prateek kuhad songs', 'hindi indie pop'],
    punjabi: ['punjabi chill songs', 'ap dhillon songs', 'punjabi indie songs', 'punjabi vibe songs'],
    english: ['chill pop songs', 'indie pop hits', 'chill vibes playlist songs', 'easy listening pop'],
  },
  'mid-high': {
    hindi: ['happy hindi songs', 'feel good bollywood songs', 'road trip hindi songs', 'bollywood upbeat songs'],
    punjabi: ['happy punjabi songs', 'punjabi road trip songs', 'diljit dosanjh songs', 'feel good punjabi songs'],
    english: ['feel good songs', 'happy pop songs', 'road trip songs english', 'upbeat indie songs'],
  },
  'high-low': {
    hindi: ['hindi rap songs', 'desi hip hop hard', 'divine songs', 'aggressive hindi rap'],
    punjabi: ['punjabi gangster songs', 'sidhu moose wala songs', 'karan aujla songs', 'punjabi rap hard'],
    english: ['aggressive rap songs', 'eminem songs', 'hard rock workout', 'phonk songs'],
  },
  'high-mid': {
    hindi: ['hindi gym songs', 'bollywood workout songs', 'hindi motivational songs', 'desi workout hip hop'],
    punjabi: ['punjabi gym songs', 'punjabi workout songs', 'punjabi motivational songs', 'bhangra workout'],
    english: ['gym workout songs', 'workout motivation songs', 'edm workout', 'hip hop workout songs'],
  },
  'high-high': {
    hindi: ['bollywood party songs', 'hindi dance songs', 'bollywood dance hits', 'hindi wedding dance songs'],
    punjabi: ['punjabi party songs', 'bhangra hits', 'punjabi dance songs latest', 'punjabi wedding songs'],
    english: ['party songs english', 'dance pop hits', 'edm party songs', 'club hits'],
  },
};

const band = (v) => (v < 1 / 3 ? 'low' : v < 2 / 3 ? 'mid' : 'high');

export function clampPoint(p) {
  const c = (v) => Math.min(1, Math.max(0, Number.isFinite(v) ? v : 0.5));
  return { energy: c(p?.energy), valence: c(p?.valence) };
}

export function distance(a, b) {
  return Math.hypot(a.energy - b.energy, a.valence - b.valence);
}

export function zoneOf(point) {
  const p = clampPoint(point);
  const id = `${band(p.energy)}-${band(p.valence)}`;
  return { id, label: LABELS[id] };
}

/** Splits n slots across languages in proportion to the mix (largest remainder). */
function allocate(mix, n) {
  const weights = LANGS.map((l) => Math.max(0, Number(mix?.[l]) || 0));
  const total = weights.reduce((a, b) => a + b, 0);
  const w = total > 0 ? weights : LANGS.map((l) => DEFAULT_MIX[l]);
  const sum = w.reduce((a, b) => a + b, 0);
  const exact = w.map((x) => (x / sum) * n);
  const base = exact.map(Math.floor);
  let left = n - base.reduce((a, b) => a + b, 0);
  const order = exact.map((x, i) => [x - Math.floor(x), i]).sort((a, b) => b[0] - a[0] || a[1] - b[1]);
  for (const [, i] of order) { if (left <= 0) break; base[i]++; left--; }
  return Object.fromEntries(LANGS.map((l, i) => [l, base[i]]));
}

/**
 * Plan of search queries for a mood point.
 * @returns {{language:string, query:string}[]}
 */
export function buildPlan(point, mix = DEFAULT_MIX, n = 6, rand = Math.random) {
  const seeds = SEEDS[zoneOf(point).id];
  const counts = allocate(mix, n);
  const plan = [];
  for (const language of LANGS) {
    const pool = [...seeds[language]];
    for (let i = 0; i < counts[language] && pool.length; i++) {
      const idx = Math.floor(rand() * pool.length) % pool.length;
      plan.push({ language, query: pool.splice(idx, 1)[0] });
    }
  }
  return plan;
}

/** Moves one language slider and rebalances the other two (in steps of 5) so the total stays 100. */
export function rebalanceMix(mix, lang, value) {
  const others = LANGS.filter((k) => k !== lang);
  const rest = 100 - value;
  const oldRest = others.reduce((a, k) => a + (mix[k] || 0), 0);
  const out = { ...mix, [lang]: value };
  const first = oldRest === 0 ? rest / 2 : (mix[others[0]] / oldRest) * rest;
  out[others[0]] = Math.round(first / 5) * 5;
  out[others[1]] = rest - out[others[0]];
  return out;
}
