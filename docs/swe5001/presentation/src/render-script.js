const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('file://' + path.join(__dirname, '..', 'dist', 'swe5001-presentation-script-v3.html'));
  await p.evaluate(() => document.fonts.ready);
  await p.pdf({ path: path.join(__dirname, '..', 'dist', 'swe5001-presentation-script-v3.pdf'), format: 'A4', printBackground: true,
    displayHeaderFooter: true, margin: { top: '20mm', bottom: '18mm', left: '17mm', right: '17mm' },
    headerTemplate: '<div style="font:8px Helvetica;color:#6B655C;width:100%;padding:0 17mm;display:flex;justify-content:space-between"><span>Speaker script and demo run book · Team 8 BlocBuy · 21 Oct 2026, 16:15–16:55 SGT</span><span>v3 · BlocBuy tokens · 2026-10-18</span></div>',
    footerTemplate: '<div style="font:8px Helvetica;color:#1B1A18;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>' });
  await b.close(); console.log('script pdf ok');
})();
