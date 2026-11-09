// Unit tests for the speaker companion data (deck/notes-data.js) and the shared slide clock (lib/schedule.js).
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const context = require('../lib/context');

const FIXTURE = path.join(__dirname, 'fixture');
context.use(FIXTURE); // md() reads the deck's fillLabel
const { buildNotesData, jsonForScript, chips } = require('../deck/notes-data');
const { slideClock, hms, toS } = require('../lib/schedule');
const C = require(path.join(FIXTURE, 'src', 'content'));
const backups = require(path.join(FIXTURE, 'src', 'slides-backup')).list();

const data = buildNotesData(C, backups);
const byId = id => data.slides.find(s => s.id === id);

test('slides run back to back from their segment start; a Q&A slot sits at start + talk', () => {
  const clock = slideClock(C.SEGMENTS, C.SLIDES);
  assert.deepEqual(clock[1], [toS('16:00:00'), toS('16:00:20')]);
  assert.deepEqual(clock[2], [toS('16:00:20'), toS('16:01:00')]);
  assert.deepEqual(clock[3], [toS('16:03:00'), null]);
  assert.equal(hms(toS('9:05:07')), '9:05:07');
});

test('one entry per slide in deck order: main slides, then backups', () => {
  assert.deepEqual(data.slides.map(s => s.id), ['1', '2', '3', 'BX', 'B1']);
  assert.equal(data.total, 3);
  assert.equal(data.talkStart, '16:00:00');
});

test('a main slide carries its plan, meta, script and the next slide', () => {
  const s = byId('2');
  assert.equal(s.label, 'Slide 2 / 3');
  assert.equal(s.meta, '1 · Opening · item 1a · Ann');
  assert.equal(s.slot, '16:00:20–16:01:00 · 40 s');
  assert.equal(s.secs, 40);
  assert.equal(s.due, 20);
  assert.match(s.script, /<b class="clk">\[click\]<\/b>/);
  assert.match(s.cues[0], /class="mk mk-verify"/);
  assert.equal(s.handover, '(pause) Bo takes over.');
  assert.equal(s.next, 'Next · 3 — Live demo (Bo)');
});

test('content text is escaped and a spoken hand-over is quoted', () => {
  const s = byId('1');
  assert.equal(s.script, 'Hello &lt;/script&gt; &amp; welcome.');
  assert.equal(s.handover, '"Over to the problem."');
});

test('the Q&A slide is due at its segment start + talk, and the last main slide points at the backups', () => {
  const s = byId('3');
  assert.equal(s.slot, '16:03:00 · Q&A');
  assert.equal(s.secs, 0);
  assert.equal(s.due, 180);
  assert.equal(s.next, 'End of the talk; backups follow');
});

test('the demo slide lists its steps; other slides have none', () => {
  assert.match(byId('3').demo, /^<h3>Live demo steps<\/h3>/);
  assert.match(byId('3').demo, /16:01:00 – 16:02:00 · Step 1 · Open the app/);
  assert.match(byId('3').demo, /<code>\/home<\/code>/);
  assert.equal(byId('2').demo, '');
});

test('a backup joins its BACKUPS row; one without a row still gets title, speaker and the way back', () => {
  const b1 = byId('B1');
  assert.equal(b1.label, 'Backup B1');
  assert.equal(b1.meta, 'Q&A only · Bo');
  assert.equal(b1.script, 'Because it is simpler.');
  assert.deepEqual(b1.cues, ['Answers: Why this design?']);
  assert.equal(b1.due, null);
  assert.equal(b1.handover, 'Back to slide 3: press End, or B for the index.');
  const bx = byId('BX');
  assert.equal(bx.meta, 'Q&A only');
  assert.equal(bx.script, '');
  assert.deepEqual(bx.cues, []);
});

test('chips() renders markers inside HTML and leaves the HTML alone', () => {
  assert.equal(chips('<b>x</b> {{FILL:y}}'), '<b>x</b> <span class="mk mk-fill">[FILL: y]</span>');
  assert.equal(chips(undefined), '');
});

test('jsonForScript cannot end its script element early and round-trips', () => {
  const v = { a: '</script><!-- & \u2028 \u2029' };
  const out = jsonForScript(v);
  assert.doesNotMatch(out, /[<>&\u2028\u2029]/);
  assert.deepEqual(JSON.parse(out), v);
});

test('a numbered click mark is highlighted and not counted as words', () => {
  const { clicks, words } = require('../lib/text');
  assert.equal(clicks('Intro. [click 2/3] Next.'), 'Intro. <b class="clk">[click 2/3]</b> Next.');
  assert.equal(clicks('[click] x'), '<b class="clk">[click]</b> x');
  assert.equal(words('One [click 1/2] two [click] three.'), 3);
});
