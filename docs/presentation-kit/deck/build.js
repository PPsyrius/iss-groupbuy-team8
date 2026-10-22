// Builds <deck>/dist/<outputs.deck>: 1280x720 slides from src/content.js (SLIDES, SEGMENTS, SPEAKERS),
// src/slides-main.js ({ n: () => html }) and optional src/slides-backup.js ({ list() }), plus the presenter runtime.
const fs = require('fs');
const path = require('path');
const { deck } = require('../lib/context');
const { fontCSS, md, esc, words } = require('../lib');
const CSS = require('./css');
const MOTION_CSS = require('./motion-css');
const runtime = require('./runtime');

// Key hint shown for 4 s when the deck opens (wording from the v3 deck).
const HINT = '→ / Space next · ← back · S speaker notes · O overview · F fullscreen · B backup index · . blank · number + Enter to jump';

module.exports = function buildDeck() {
  const { dir, cfg } = deck();
  const C = require(path.join(dir, 'src', 'content'));
  const MAIN = require(path.join(dir, 'src', 'slides-main'));
  // Backups are optional: only a missing slides-backup.js is skipped; any error inside an existing one fails the build.
  const backupFile = path.join(dir, 'src', 'slides-backup.js');
  const BACK = fs.existsSync(backupFile) ? require(backupFile) : {};

  const TOTAL = C.SLIDES.length;
  // motion: true adds the v3 entrance choreography to the chrome (header and speaker chip fade at 0 ms,
  // title rises at 60 ms) and appends deck/motion-css.js; the footer stays static, as in the v3 deck.
  const M = !!cfg.motion;
  const fade = M ? ' a" data-a="fade" style="--d:0ms' : '';
  const up = M ? ' a" data-a="up' : '';
  const ttlStyle = (long) => (long ? 'font-size:30.5px;top:62px' : '') + (M ? (long ? ';' : '') + '--d:60ms' : '');
  const SEGMIN = C.SEGMENTS.map(s => s.talk + s.qa);

  function footer(seg, label) {
    const bar = C.SEGMENTS.map((s, i) => {
      const cls = s.n === seg ? 'on' : (s.n < seg ? 'done' : '');
      return `<span class="${cls}" style="flex:${SEGMIN[i]}"></span>`;
    }).join('');
    const sg = C.SEGMENTS[seg - 1];
    return `<div class="foot"><div class="brand"><b>BlocBuy</b> ${cfg.brand}</div><div class="seg">${seg} · ${sg.short}</div><div class="bar">${bar}</div><div class="pg">${label}</div></div>`;
  }

  function mainSlide(s) {
    const seg = C.SEGMENTS[s.seg - 1];
    const sp = C.SPEAKERS[s.speaker];
    const body = MAIN[s.n]();
    if (cfg.darkSlides.includes(s.n)) return `<section class="slide dark" id="s${s.n}" data-n="${s.n}">${body}</section>`;
    return `<section class="slide" id="s${s.n}" data-n="${s.n}">
  <div class="hdr${fade}">${seg.n} · ${esc(seg.head)} <span class="it">${s.item}</span></div>
  <div class="who${fade}"><span class="av">${sp.init}</span>${esc(s.speaker)}</div>
  <div class="ttl${up}" style="${ttlStyle(s.title.length > 58)}">${esc(s.title)}</div>
  <div class="body">${body}</div>
  ${footer(s.seg, `${s.n}<small>/${TOTAL}</small>`)}
</section>`;
  }

  function backupSlide(b) {
    return `<section class="slide backup" id="${b.id}" data-n="${b.id}">
  <div class="hdr${fade}">Backup · Q&amp;A only <span class="it">${b.id}</span></div>
  ${b.speaker ? `<div class="who${fade}"><span class="av">${C.SPEAKERS[b.speaker].init}</span>${esc(b.speaker)}</div>` : ''}
  <div class="ttl${up}"${M ? ' style="--d:60ms"' : ''}>${esc(b.title)}</div>
  <div class="body">${b.body}</div>
  <div class="foot"><div class="brand"><b>BlocBuy</b> ${cfg.brand}</div><div class="seg" style="width:auto;color:var(--foreground)">Backup · not presented unless asked</div><div class="bar"><span style="flex:1"></span></div><div class="pg">${b.id}</div></div>
</section>`;
  }

  const notes = {};
  for (const s of C.SLIDES) notes[s.n] = { title: s.title, speaker: s.speaker, slot: s.slot, say: md(s.say).replace(/\[click\]/g, '<b class="clk">[click]</b>'), cues: (s.cues || []).map(md), handover: s.handover ? md(s.handover) : '', words: words(s.say) };

  const sections = C.SLIDES.map(mainSlide);
  if (BACK.list) sections.push(...BACK.list().map(backupSlide));

  const html = `<!doctype html><html lang="en-SG"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${cfg.title}</title>
<style>${fontCSS()}\n${CSS}${M ? MOTION_CSS : ''}</style></head>
<body><div class="deck">
${sections.join('\n')}
</div>
<div class="hint">${HINT}</div><div class="blank"></div>
<script>${runtime({ notes, total: TOTAL })}</script></body></html>`;

  fs.mkdirSync(path.join(dir, 'dist'), { recursive: true });
  fs.writeFileSync(path.join(dir, 'dist', cfg.outputs.deck), html);
  console.log('slides:', sections.length, 'characters:', html.length);
};
