// End-to-end UI flows with a mocked Android bridge and fake YouTube player.
// Run: node test/ui/ui.spec.mjs   (serves www/ itself, writes screenshots to build/shots/)
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
let chromium;
try { ({ chromium } = require('playwright')); } catch { ({ chromium } = require(path.join(execSync('npm root -g').toString().trim(), 'playwright'))); }

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const www = path.join(root, 'www');
const shots = path.join(root, 'build/shots');
fs.mkdirSync(shots, { recursive: true });

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const p = path.join(www, decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/^\/$/, '/index.html'));
  if (!p.startsWith(www) || !fs.existsSync(p)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(p)] || 'application/octet-stream' });
  fs.createReadStream(p).pipe(res);
});
await new Promise((r) => server.listen(0, r));
const URL_ = `http://localhost:${server.address().port}/index.html`;

const fx = {};
for (const n of ['search-all', 'suggest', 'next', 'lrclib-get', 'lrclib-search']) fx[n] = fs.readFileSync(path.join(root, 'test/fixtures', `${n}.json`), 'utf8');
const mock = fs.readFileSync(path.join(here, 'mock-native.js'), 'utf8');

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2, hasTouch: false });
// Artwork stand-ins: a gradient per video id, so screenshots look like the real app (no network in tests).
await ctx.route(/ytimg\.com|googleusercontent/, (r) => {
  const id = r.request().url();
  let h = 0; for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const a = h % 360, b = (a + 40) % 360;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="270"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${a},55%,45%)"/><stop offset="1" stop-color="hsl(${b},45%,12%)"/></linearGradient></defs><rect width="480" height="270" fill="url(#g)"/></svg>`;
  return r.fulfill({ status: 200, contentType: 'image/svg+xml', body: svg });
});
await ctx.route(/youtube\.com/, (r) => r.fulfill({ status: 204, body: '' }));
await ctx.addInitScript(`window.__fx = ${JSON.stringify(fx)};`);
await ctx.addInitScript(mock);
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });

const results = [];
async function check(name, fn) {
  try { await fn(); results.push(['PASS', name]); console.log('PASS', name); }
  catch (e) { results.push(['FAIL', name, e.message]); console.log('FAIL', name, '-', e.message.split('\n')[0]); }
}
const assert = (c, m) => { if (!c) throw new Error(m); };

async function swipe(sel, dx, dy) {
  const box = await page.locator(sel).boundingBox();
  const x = box.x + box.width / 2, y = box.y + box.height / 2;
  await page.mouse.move(x, y); await page.mouse.down();
  await page.mouse.move(x + dx / 2, y + dy / 2, { steps: 4 }); await page.mouse.move(x + dx, y + dy, { steps: 4 });
  await page.mouse.up();
}
async function queueCount() {
  await page.click('#btn-queue-all');
  await page.waitForSelector('#popover:not([hidden]) .q-item');
  const n = await page.locator('#popover .q-item').count();
  await page.click('#scrim', { position: { x: 200, y: 40 } });
  await page.waitForTimeout(300);
  return n;
}

await page.goto(URL_);
await page.waitForTimeout(800);

await check('1. home renders pad, 6 chips and the queue row', async () => {
  assert(await page.locator('#pad').isVisible(), 'pad not visible');
  assert((await page.locator('.chip').count()) === 6, 'chip count');
  assert(await page.locator('#mood-row').isVisible(), 'queue row');
  await page.screenshot({ path: path.join(shots, '0-home-empty.png') });
});

await check('2. chip Gym builds a queue of >=15 and starts playing', async () => {
  await page.click('[data-chip="Gym"]');
  await page.waitForSelector('#mini:not([hidden])', { timeout: 8000 });
  await page.waitForTimeout(400);
  const n = await queueCount();
  assert(n >= 15, `queue only ${n}`);
  assert((await page.locator('#mini-title').textContent()).length > 0, 'mini title empty');
  const np = await page.evaluate(() => window.__np.length);
  assert(np > 0, 'setNowPlaying never called');
  await page.screenshot({ path: path.join(shots, '1-home.png') });
});

await check('3. player: lyrics show, swipe changes song, double-tap likes', async () => {
  await page.click('#mini-title');
  await page.waitForSelector('#player:not([hidden])');
  await page.waitForSelector('#p-lyrics .l.cur, #p-lyrics .plain', { timeout: 5000 });
  await page.waitForTimeout(1200);
  await page.screenshot({ path: path.join(shots, '2-player.png') });
  const before = await page.locator('#p-title').textContent();
  await swipe('#p-stage', -160, 0);
  await page.waitForTimeout(500);
  const after = await page.locator('#p-title').textContent();
  assert(before !== after, 'title did not change on swipe');
  const box = await page.locator('#p-stage').boundingBox();
  await page.mouse.dblclick(box.x + box.width / 2, box.y + box.height / 3);
  await page.waitForTimeout(400);
  assert(await page.locator('#p-like.on').count() === 1, 'like button not on');
  await page.click('#p-close');
  await page.waitForTimeout(400);
  await page.click('#btn-library');
  await page.waitForSelector('#library:not([hidden])');
  await page.waitForTimeout(300);
  assert((await page.locator('#l-body .row').count()) === 1, 'liked tab should list 1 song');
  await page.screenshot({ path: path.join(shots, '4-library.png') });
  await page.click('#l-back');
  await page.waitForTimeout(400);
});

await check('4. search shows suggestions, then Songs results', async () => {
  await page.click('#btn-search');
  await page.fill('#s-input', 'kesar');
  await page.waitForSelector('.sugg button', { timeout: 4000 });
  assert((await page.locator('.sugg button').count()) >= 3, 'few suggestions');
  await page.press('#s-input', 'Enter');
  await page.waitForSelector('#s-body .row .t', { timeout: 5000 });
  await page.waitForTimeout(300);
  assert((await page.locator('#s-body .sec-title', { hasText: 'Songs' }).count()) === 1, 'no Songs section');
  await page.screenshot({ path: path.join(shots, '3-search.png') });
  await page.click('#s-back');
  await page.waitForTimeout(400);
});

await check('5. reload restores the session (Review Focus 4)', async () => {
  const title = await page.locator('#mini-title').textContent();
  await page.waitForTimeout(4500); // let the periodic session save run
  await page.reload();
  await page.waitForSelector('#mini:not([hidden])', { timeout: 6000 });
  const restored = await page.locator('#mini-title').textContent();
  assert(restored === title, `restored "${restored}" vs "${title}"`);
});

await check('6. fast dot drags: only the last build wins (Review Focus 3)', async () => {
  const box = await page.locator('#pad').boundingBox();
  const at = (e, v) => [box.x + box.width * e, box.y + box.height * (1 - v)];
  for (const [e, v] of [[0.1, 0.1], [0.2, 0.15], [0.95, 0.95]]) {
    const [x, y] = at(e, v);
    await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.up();
    await page.waitForTimeout(80);
  }
  await page.waitForTimeout(3500); // slow (stale) builds finish after the fast one
  const title = await page.locator('#mini-title').textContent();
  assert(/party|dance|bhangra|club|wedding/i.test(title), `queue came from a stale build: "${title}"`);
});

await check('7. no page errors', async () => { assert(errors.length === 0, errors.join(' | ')); });

await browser.close();
server.close();
const failed = results.filter((r) => r[0] === 'FAIL').length;
console.log(`\n${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
