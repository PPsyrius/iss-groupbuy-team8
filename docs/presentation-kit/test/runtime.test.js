// Presenter runtime regression tests: the runtime source runs in a minimal two-slide page in headless Chromium.
// Run with `npm --prefix docs/presentation-kit test`.
const { test, before, after } = require('node:test');
const assert = require('node:assert');
const { chromium } = require('playwright');
const runtime = require('../deck/runtime');

const page = `<!doctype html><style>.blank{display:none}.blank.on{display:block}</style><body>
<div class="slide" data-n="1"></div><div data-scroll style="position:fixed;right:0;top:0;width:200px;height:200px;overflow:auto"><div style="height:900px"></div></div><div class="slide" data-n="2"></div><div class="blank"></div>
<script>${runtime({ total: 2 })}</script></body>`;

let browser;
before(async () => { browser = await chromium.launch(); });
after(async () => { await browser.close(); });

async function open(hash = '') {
  const p = await browser.newPage();
  const errors = [];
  p.on('pageerror', e => errors.push(e.message));
  await p.goto('about:blank' + hash);
  await p.setContent(page);
  return { p, errors };
}
const state = p => p.evaluate(() => ({
  present: document.body.classList.contains('present'),
  overview: document.body.classList.contains('overview'),
  blank: document.querySelector('.blank').classList.contains('on'),
}));

test('entering the overview clears the blank screen, so it never sticks', async () => {
  const { p } = await open();
  await p.keyboard.press('.');
  assert.equal((await state(p)).blank, true);
  await p.keyboard.press('o');
  assert.deepEqual(await state(p), { present: false, overview: true, blank: false });
  await p.keyboard.press('o');
  assert.deepEqual(await state(p), { present: true, overview: false, blank: false });
});

test('a malformed hash still starts presenter mode', async () => {
  const { p, errors } = await open('#B6%');
  assert.equal((await state(p)).present, true);
  assert.deepEqual(errors, []);
});

test('a click inside a scrollable diagram does not step the deck', async () => {
  const { p } = await open();
  await p.waitForFunction(() => location.hash === '#1');
  await p.mouse.click(1100, 100);
  await p.waitForTimeout(100);
  assert.equal(await p.evaluate(() => location.hash), '#1');
  await p.mouse.click(1100, 600);
  await p.waitForFunction(() => location.hash === '#2');
});
