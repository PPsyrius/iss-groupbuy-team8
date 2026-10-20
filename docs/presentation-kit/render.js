// Prints with Playwright Chromium: deck PDF from the built HTML deck (1280x720 pages), script PDF from the in-memory script (A4 with header/footer),
// and per-slide PNG screenshots with an overflow report (elements reaching into the slide's bottom/right margin).
const path = require('path'), fs = require('fs');
const { pathToFileURL } = require('url');
const { chromium } = require('playwright');
const { deck } = require('./lib/context');

async function deckPage(b) {
  const { dir, cfg } = deck();
  const p = await b.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  await p.addInitScript(() => { window.__PRINT__ = true; });
  await p.goto(pathToFileURL(path.join(dir, 'dist', cfg.outputs.deck)).href);
  await p.evaluate(() => document.fonts.ready);
  return p;
}

async function deckPdf() {
  const { dir, cfg } = deck();
  const b = await chromium.launch();
  const p = await deckPage(b);
  await p.pdf({ path: path.join(dir, 'dist', cfg.outputs.deckPdf), width: '1280px', height: '720px', printBackground: true, preferCSSPageSize: true });
  console.log('pdf ok');
  await b.close();
}

// filter: optional regex on slide ids (s1, s2 … B1, BX); PNGs go to <deck>/png/ (git-ignored).
async function screenshots(filter) {
  const { dir } = deck();
  const b = await chromium.launch();
  const p = await deckPage(b);
  // Print media lays out every slide, like the PDF; a deck built outside the kit ignores __PRINT__ and shows only the current slide on screen.
  await p.emulateMedia({ media: 'print' });
  fs.mkdirSync(path.join(dir, 'png'), { recursive: true });
  const ids = await p.$$eval('.slide', ss => ss.map(s => s.id));
  // report overflow: elements whose box exceeds the slide
  const over = await p.$$eval('.slide', ss => ss.map(s => { const r = s.getBoundingClientRect(); const bad = [];
    s.querySelectorAll('.body *').forEach(e => { const q = e.getBoundingClientRect(); if (q.width && (q.right > r.right - 40 || q.bottom > r.bottom - 38) && !e.closest('svg')) bad.push((e.className && e.className.baseVal === undefined ? e.className : e.tagName) + ':' + Math.round(q.right - r.left) + 'x' + Math.round(q.bottom - r.top)); });
    return s.id + ' ' + bad.slice(0, 4).join(' | '); }));
  console.log(over.filter(l => l.includes(':')).join('\n'));
  for (const id of ids) {
    if (filter && !new RegExp(filter).test(id)) continue;
    const el = await p.$('#' + id);
    await el.screenshot({ path: path.join(dir, 'png', id + '.png') });
  }
  await b.close();
}

// html: the script document from script/build.js; it is self-contained (fonts inlined), so no file is needed.
async function scriptPdf(html) {
  const { dir, cfg } = deck();
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent(html, { waitUntil: 'load' });
  await p.evaluate(() => document.fonts.ready);
  fs.mkdirSync(path.join(dir, 'dist'), { recursive: true });
  await p.pdf({ path: path.join(dir, 'dist', cfg.outputs.scriptPdf), format: 'A4', printBackground: true,
    displayHeaderFooter: true, margin: { top: '20mm', bottom: '18mm', left: '17mm', right: '17mm' },
    headerTemplate: `<div style="font:8px Helvetica;color:#6B655C;width:100%;padding:0 17mm;display:flex;justify-content:space-between"><span>${cfg.scriptHeader.left}</span><span>${cfg.scriptHeader.right}</span></div>`,
    footerTemplate: '<div style="font:8px Helvetica;color:#1B1A18;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>' });
  await b.close(); console.log('script pdf ok');
}

module.exports = { deckPdf, scriptPdf, screenshots };
