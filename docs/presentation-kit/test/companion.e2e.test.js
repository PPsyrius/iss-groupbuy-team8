// End-to-end test of the speaker companion: builds the fixture deck into a temp folder, opens it from file:// (as it is
// presented), presses S and drives the deck from the companion window and back.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { pathToFileURL } = require('url');
const { chromium } = require('playwright');

const KIT = path.join(__dirname, '..');

test('the companion follows the deck and drives it', async t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'kit-companion-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  fs.cpSync(path.join(__dirname, 'fixture'), dir, { recursive: true });
  execFileSync(process.execPath, [path.join(KIT, 'bin', 'build.js'), dir, 'deck'], { stdio: 'pipe' });

  let browser;
  try { browser = await chromium.launch(); } catch {
    t.skip('Chromium is not installed: npm --prefix docs/presentation-kit exec -- playwright install chromium');
    return;
  }
  t.after(() => browser.close());
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const deck = await context.newPage();
  await deck.goto(pathToFileURL(path.join(dir, 'dist', 'fixture.html')).href);
  await deck.waitForFunction(() => location.hash === '#1');
  await deck.keyboard.press('t'); // the talk timer starts in the deck before the companion exists

  const [notes] = await Promise.all([context.waitForEvent('page'), deck.keyboard.press('s')]);
  await notes.waitForLoadState();
  assert.match(notes.url(), /\?notes$/);
  const text = id => notes.locator('#' + id).innerText();
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Slide 1 / 3');
  assert.equal(await notes.locator('.slide').count(), 0, 'the companion shows no slides');
  assert.equal(await text('n-title'), 'Fixture title');
  assert.equal(await text('n-script'), 'Hello </script> & welcome.');
  await notes.waitForFunction(() => document.getElementById('n-elapsed').textContent !== 'press T');
  assert.equal(await notes.locator('#n-cues-box').isHidden(), true);

  // companion → deck: arrow key and the Next button
  await notes.keyboard.press('ArrowRight');
  await deck.waitForFunction(() => location.hash === '#2');
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Slide 2 / 3');
  assert.equal(await text('n-frag'), 'Reveal 0 / 1 — next click reveals more');
  assert.equal(await text('n-slot'), ' · 16:00:20–16:01:00 · 40 s');
  await notes.locator('#n-nextb').click();
  await notes.waitForFunction(() => document.getElementById('n-frag').textContent === 'Reveal 1 / 1');
  assert.equal(await deck.evaluate(() => location.hash), '#2');

  // deck → companion: stepping in the deck, the demo box and the timer
  await deck.keyboard.press('ArrowRight');
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Slide 3 / 3');
  assert.equal(await notes.locator('#n-demo').isVisible(), true);
  assert.match(await text('n-demo'), /Step 1 · Open the app/);
  assert.equal(await text('n-next'), 'End of the talk; backups follow');
  // a running timer restarts from zero on T in either window and on the button; it never stops
  const restartsFrom = async press => {
    await notes.waitForFunction(() => !['press T', '00:00'].includes(document.getElementById('n-elapsed').textContent));
    await press();
    await notes.waitForFunction(() => document.getElementById('n-elapsed').textContent === '00:00');
  };
  await restartsFrom(() => notes.keyboard.press('t'));
  await restartsFrom(() => deck.keyboard.press('t'));
  await restartsFrom(() => notes.locator('#n-timer').click());
  assert.match(await text('n-delta'), /^ahead 0[23]:\d\d$/);

  // backups and the Back button; B in the companion opens the backup index, as the hand-over says
  await notes.keyboard.press('b');
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Backup BX');
  assert.equal(await text('n-delta'), '—');
  await notes.locator('#n-nextb').click();
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Backup B1');
  assert.equal(await text('n-script'), 'Because it is simpler.');
  await notes.locator('#n-prev').click();
  await deck.waitForFunction(() => location.hash === '#BX');

  // a reloaded deck picks the companion up again
  await deck.reload();
  await deck.waitForFunction(() => location.hash === '#BX');
  // the deck adopts the companion on its next hello (every 1 s); B1 is the last slide, so repeated presses cannot overshoot
  for (let attempt = 0; attempt < 10 && (await deck.evaluate(() => location.hash)) !== '#B1'; attempt++) {
    await notes.keyboard.press('ArrowRight');
    await deck.waitForFunction(() => location.hash === '#B1', null, { timeout: 500 }).catch(() => {});
  }
  assert.equal(await deck.evaluate(() => location.hash), '#B1');
  await notes.waitForFunction(() => document.getElementById('n-label').textContent === 'Backup B1');
  assert.equal(await text('n-status'), '');

  // the backup hand-over's way back works from the companion: End to the last main slide, Home to the first
  assert.equal(await text('n-hand'), 'Back to slide 3: press End, or B for the index.');
  await notes.keyboard.press('End');
  await deck.waitForFunction(() => location.hash === '#3');
  await notes.keyboard.press('Home');
  await deck.waitForFunction(() => location.hash === '#1');
});
