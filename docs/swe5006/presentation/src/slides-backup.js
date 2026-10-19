const { icon, md, mk, svg } = require('../../../presentation-kit/lib');
const C = require('./content');

const tbl = (head, rows, opts = {}) => `<table class="t" style="font-size:${opts.fs || 16}px">
<tr>${head.map((h, i) => `<th style="${opts.w && opts.w[i] ? 'width:' + opts.w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map(r => `<tr>${r.map((c, i) => `<td class="${i === 0 && opts.id ? 'id' : ''}">${c}</td>`).join('')}</tr>`).join('')}</table>`;

// ---- B11: Sprint 2 and 3 burndowns (segment 1) ----
function lineChart({ xs, series, max, step, W = 560, H = 250 }) {
  const L = 44, R = W - 12, T = 12, B = H - 30;
  const X = i => L + 14 + i * ((R - L - 24) / (xs.length - 1)), Y = v => B - (v / max) * (B - T);
  const ticks = []; for (let v = 0; v <= max; v += step) ticks.push(v);
  const pl = (d, c, dash) => `<polyline points="${d.map((v, i) => v == null ? '' : `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => v == null ? '' : `<circle cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${ticks.map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14.5" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 22}" font-size="14.5" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${series.map(s => pl(s.d, s.c, s.dash)).join('')}</svg>`;
}
const key = (c, t, dash) => `<span><i style="display:inline-block;width:22px;${dash ? `height:0;border-top:3px dashed ${c}` : `height:3px;background:${c}`};vertical-align:4px"></i> ${t}</span>`;
const b11 = () => `
<div style="position:absolute;left:0;top:0;width:560px">
 <div class="serif" style="font-size:21px">Sprint 2 · remaining hours, 15–26 Sep</div>
 <div style="font:400 15.5px Inter;margin:2px 0 6px">150 h planned · 16 h left · reconstructed from merge dates</div>
 ${lineChart({ xs: ['start', '15', '16', '17', '18', '19', '22', '23', '24', '25', '26'], max: 150, step: 50, series: [
   { d: [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0], c: '#A69E92', dash: true },
   { d: [150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16], c: '#3E5CD9' }] })}
 <div style="display:flex;gap:22px;font:400 15px Inter;margin-left:44px">${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Actual remaining')}</div>
 <div class="card" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter">Flat for a week, then a cliff: work merged late in very large pull requests. Retro answer: PRs under 1,500 lines and hours on the board daily.</div>
 <div style="font:italic 400 15px Inter;margin-top:8px">Source: docs/sprint2-progress-report.typ, “Daily burndown data”</div>
</div>
<div style="position:absolute;left:604px;top:0;width:560px">
 <div class="serif" style="font-size:21px">Sprint 3 · open sprint cards, 29 Sep–9 Oct</div>
 <div style="font:400 15.5px Inter;margin:2px 0 6px">21 cards · 150 h planned · 33 h left at freeze (review)</div>
 ${lineChart({ xs: ['29', '30', '1', '2', '5', '6', '7', '8', '9'], max: 21, step: 7, series: [
   { d: [21, 18.4, 15.8, 13.1, 10.5, 7.9, 5.3, 2.6, 0], c: '#A69E92', dash: true },
   { d: [19, 16, 13, 12, 7, 7, 7, 7, 8], c: '#3E5CD9' }] })}
 <div style="display:flex;gap:22px;font:400 15px Inter;margin-left:44px">${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Cards not Done (daily snapshot)')}</div>
 <div class="card" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter">Steadier burn, then stuck at 7 from 5 Oct: #223, #224 and #229 waited for Checkout on dev, #226 for the NFR-37 decision. Hours come from the review.</div>
 <div style="font:italic 400 15px Inter;margin-top:8px">Source: docs/agile/metrics/*.csv, sprint-03/review.md</div>
</div>`;

// Backup slides follow the last main slide; press B in the deck for the index (BX). Rows link with data-go.
// Each segment adds its backups to BACKUPS (content.js) and a matching entry below, in index order.
function list() {
  const B = [];
  B.push({ id: 'BX', title: 'Backup slides: index', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 22px;margin-top:4px">
 ${C.BACKUPS.map(b => `<div data-go="${b[0]}" style="cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:8px 14px">
   <span class="tag" style="min-width:44px;text-align:center">${b[0]}</span><div><div style="font:600 17px Inter">${b[1]}</div><div style="font:400 14.5px Inter;color:var(--muted)">${b[2]}</div></div>
 </div>`).join('')}
</div><div style="margin-top:12px;font:italic 400 15px Inter;color:var(--muted)">Click a row (or type its number in the URL as #B6). Slide 22 stays up during Q&amp;A; open a backup only when a question needs it.</div>` });
  B.push({ id: 'B11', title: 'Sprint 2 and 3 burndowns', speaker: 'Lim', body: b11() });
  return B;
}

module.exports = { list, tbl };
