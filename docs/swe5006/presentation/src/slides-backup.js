const { svg } = require('../../../presentation-kit/lib');
const C = require('./content');

// Backup slides follow the last main slide; press B in the deck for the index (BX). Rows link with data-go.
function list() {
  const B = [];
  B.push({ id: 'BX', title: 'Backup slides: index', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 22px;margin-top:4px">
 ${C.BACKUPS.map(b => `<div data-go="${b[0]}" style="cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:8px 14px">
   <span class="tag" style="min-width:44px;text-align:center">${b[0]}</span><div><div style="font:600 17px Inter">${b[1]}</div><div style="font:400 14.5px Inter;color:var(--muted)">${b[2]}</div></div>
 </div>`).join('')}
</div>` });
  B.push({ id: 'B1', speaker: 'Yorik', title: 'Context map', body: `<div class="dia">${svg('context-map').replace('<svg ', '<svg style="width:470px;height:auto" ')}</div>` });
  return B;
}

module.exports = { list };
