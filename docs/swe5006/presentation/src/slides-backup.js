const { icon, md, mk, svg } = require('../../../presentation-kit/lib');
const C = require('./content');

const tbl = (head, rows, opts = {}) => `<table class="t" style="font-size:${opts.fs || 16}px">
<tr>${head.map((h, i) => `<th style="${opts.w && opts.w[i] ? 'width:' + opts.w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map(r => `<tr>${r.map((c, i) => `<td class="${i === 0 && opts.id ? 'id' : ''}">${c}</td>`).join('')}</tr>`).join('')}</table>`;

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
  return B;
}

module.exports = { list, tbl };
