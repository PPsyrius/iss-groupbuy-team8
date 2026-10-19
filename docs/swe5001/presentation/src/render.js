// node src/render.js [pdf|png] [filter]
const { chromium } = require('playwright');
const path = require('path'), fs = require('fs');
(async () => {
  const mode = process.argv[2] || 'both', filter = process.argv[3];
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  await p.addInitScript(() => { window.__PRINT__ = true; });
  await p.goto('file://' + path.join(__dirname, '..', 'dist', 'swe5001-presentation.html'));
  await p.evaluate(() => document.fonts.ready);
  if (mode !== 'png') {
    await p.pdf({ path: path.join(__dirname, '..', 'dist', 'swe5001-presentation.pdf'), width: '1280px', height: '720px', printBackground: true, preferCSSPageSize: true });
    console.log('pdf ok');
  }
  if (mode !== 'pdf') {
    fs.mkdirSync(path.join(__dirname, '..', 'png'), { recursive: true });
    const ids = await p.$$eval('.slide', ss => ss.map(s => s.id));
    // report overflow: elements whose box exceeds the slide
    const over = await p.$$eval('.slide', ss => ss.map(s => { const r = s.getBoundingClientRect(); const bad = [];
      s.querySelectorAll('.body *').forEach(e => { const q = e.getBoundingClientRect(); if (q.width && (q.right > r.right - 40 || q.bottom > r.bottom - 38) && !e.closest('svg')) bad.push((e.className && e.className.baseVal === undefined ? e.className : e.tagName) + ':' + Math.round(q.right - r.left) + 'x' + Math.round(q.bottom - r.top)); });
      return s.id + ' ' + bad.slice(0, 4).join(' | '); }));
    console.log(over.filter(l => l.includes(':')).join('\n'));
    for (const id of ids) {
      if (filter && !new RegExp(filter).test(id)) continue;
      const el = await p.$('#' + id);
      await el.screenshot({ path: path.join(__dirname, '..', 'png', id + '.png') });
    }
  }
  await b.close();
})();
