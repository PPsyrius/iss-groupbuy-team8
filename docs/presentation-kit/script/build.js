// Builds the A4 speaker script as an in-memory HTML document (render.js prints it to <deck>/dist/<outputs.scriptPdf>).
// The deck's src/script.js takes the helpers below with require('<relative path>/presentation-kit/script/build').helpers()
// and exports the body HTML; the kit wraps it, inlines fonts and reports the marker counts.
const fs = require('fs');
const path = require('path');
const { deck } = require('../lib/context');
const { fontCSS, md, esc, words } = require('../lib');
const { hms, toS, est, slideClock } = require('../lib/schedule');
const CSS = require('./css');

function helpers(C, cfg) {
  const short = t => t.replace(/:00$/, '');
  const clock = slideClock(C.SEGMENTS, C.SLIDES);
  const markers = { FILL: 0, VERIFY: 0, CONFIRM: 0 };
  const countM = str => { for (const m of String(str).matchAll(/\{\{(FILL|VERIFY|CONFIRM):/g)) markers[m[1]]++; };

  const box = (cls, h, inner) => `<div class="box ${cls}"><b class="h">${h}</b>${inner}</div>`;
  const tbl = (head, rows, cls = '') => `<table class="${cls}"><tr>${head.map(h => `<th>${h}</th>`).join('')}</tr>${rows.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')}</table>`;
  const pre = s => `<pre>${esc(s)}</pre>`;
  const reqBox = seg => box('req', cfg.guidelineLabel, Object.entries(seg.items).map(([k, v]) => `<b>${k}</b> — ${esc(v)}`).join('<br>'));

  function slideCard(s) {
    countM(s.say); (s.cues || []).forEach(countM); if (s.handover) countM(s.handover);
    const [a, b] = clock[s.n];
    const time = s.slot ? `${hms(a)}–${hms(b)} · ${s.slot} s` : `${short(hms(a))} · Q&amp;A`;
    const w = words(s.say), e = est(s), over = s.slot && e > s.slot;
    const say = md(s.say).replace(/\[click\]/g, '<b class="clk">[click]</b>');
    return `<div class="card"><div class="hd"><span>Slide ${s.n} &nbsp; ${esc(s.title)}</span><small>${time}</small></div><div class="bd">
  <div class="row"><span class="lab">Speaker</span><span>${esc(s.speaker)} · covers guideline item <b>${s.item}</b></span></div>
  <div class="row"><span class="lab say">Say</span><span class="say">${say}</span></div>
  <div class="row"><span></span><span class="meter">≈ ${w} words · ≈ ${e} s at 130 wpm${s.slot ? ' · slot ' + s.slot + ' s' : ''}${over ? ` <span class="over">· over by ${e - s.slot} s: rehearse at pace or trim${s.trim ? ' — ' + esc(s.trim) : ''}</span>` : (s.trim ? ` · if short of time: ${esc(s.trim)}` : '')}</span></div>
  ${s.cues && s.cues.length ? `<div class="row"><span class="lab cue">Cues</span><ul>${s.cues.map(c => `<li>${md(c)}</li>`).join('')}</ul></div>` : ''}
  ${s.handover ? `<div class="row"><span class="lab ho">Hand-over</span><span>${/^\(/.test(s.handover) ? md(s.handover) : '<i>"' + md(s.handover) + '"</i>'}</span></div>` : ''}
  </div></div>`;
  }

  function qaTable(seg) {
    const q = C.QA[seg]; q.rows.forEach(r => countM(r[1]));
    return `<h3>Q&amp;A (${q.window})</h3>` + tbl(['Likely question', 'Answer: decision → reason → evidence or limitation (30–45 s)', 'Owner'], q.rows.map(r => [esc(r[0]), md(r[1]), esc(r[2])]));
  }

  // One chapter per segment; the demo segment (cfg.demoSegment) gets its Q&A table from the deck's own demo chapter.
  function segment(seg, extra = {}) {
    const sl = C.SLIDES.filter(s => s.seg === seg.n);
    const st = toS(seg.start), talkEnd = st + seg.talk * 60, end = st + (seg.talk + seg.qa) * 60;
    return `<section class="chap"><h1>${seg.n} ${esc(seg.name)} (${seg.talk}+${seg.qa} minutes)</h1>
  <div class="seghead"><table class="meta"><tr><td>Clock</td><td>${hms(st)} – ${hms(end)} (${seg.talk} min talk + ${seg.qa} min Q&amp;A; talk ends ${hms(talkEnd)})</td></tr><tr><td>Owner</td><td>${esc(seg.owner)}</td></tr><tr><td>Slides</td><td>${sl.map(s => s.n).join(', ')}</td></tr></table></div>
  ${reqBox(seg)}
  ${extra.tip ? box('tip', 'Tip', extra.tip) : ''}
  ${sl.map(slideCard).join('')}
  ${extra.warn ? box('warn', 'Watch out', extra.warn) : ''}
  ${C.QA[seg.n] && seg.n !== cfg.demoSegment ? qaTable(seg.n) : ''}
  </section>`;
  }

  const overs = C.SLIDES.filter(s => s.slot && est(s) > s.slot);

  // meta: [[label, html], ...]; after: html placed below the table (boxes, notes).
  const cover = ({ kicker, title, subtitle, meta, after }) => `<section>
<div style="height:4pt"></div>
<div style="color:var(--muted);font-size:11pt">${kicker}</div>
<h1 style="font-size:28pt;margin-top:18pt">${title}</h1>
<div style="font-size:15pt;color:var(--accent-soft-foreground);margin:10pt 0 20pt">${subtitle}</div>
<hr style="border:none;border-top:1px solid var(--border);margin-bottom:14pt">
<table class="meta">
${meta.map(([k, v]) => `<tr><td>${k}</td><td>${v}</td></tr>`).join('\n')}
</table>
${after}
</section>`;

  // Run sheet with per-segment budgets and the timing check; after: deck-specific coverage tables.
  function runSheet(after) {
    const runRows = C.SLIDES.map(s => { const e = est(s); return [short(hms(clock[s.n][0])), s.slot ? s.slot + ' s' : 'Q&amp;A', s.n, esc(s.title), s.speaker, s.item, s.slot ? (e > s.slot ? `<span class="over" style="white-space:nowrap">${e} s</span>` : `<span style="white-space:nowrap">${e} s</span>`) : '']; });
    const segTotals = C.SEGMENTS.map(seg => { const sl = C.SLIDES.filter(s => s.seg === seg.n && s.slot); return [seg.n, seg.talk * 60, sl.reduce((a, s) => a + s.slot, 0), sl.reduce((a, s) => a + est(s), 0)]; });
    const demo = cfg.demoSegment ? ` (§${cfg.demoSegment} talk is the demo itself)` : '';
    return `<section class="chap"><h1>Run sheet and coverage check</h1>
<h2>Slide-by-slide run sheet</h2>
${tbl(['Clock', 'Slot', 'Slide', 'Title', 'Speaker', 'Item', 'Est.'], runRows)}
<p class="small">Segment talk budget vs slots vs estimated speaking time: ${segTotals.map(([n, b, sl, e]) => `§${n} ${b} s / ${sl} s / ${e} s`).join(' · ')}${demo}.</p>
${overs.length ? box('warn', 'Timing check', `${overs.length} slides read longer than their slot at 130 wpm: ${overs.map(s => `slide ${s.n} (${est(s)} s / ${s.slot} s)`).join(', ')}. Each card lists what to trim if the rehearsal runs long.`) : ''}
${after}
</section>`;
  }

  return { C, cfg, md, esc, hms, toS, short, clock, est, markers, countM, box, tbl, pre, reqBox, slideCard, qaTable, segment, overs, cover, runSheet };
}

// One helper set per build, so markers counted by the deck's script.js are the ones reported here.
let shared = null;
function scriptHelpers() {
  if (!shared) { const { dir, cfg } = deck(); shared = helpers(require(path.join(dir, 'src', 'content')), cfg); }
  return shared;
}

// Returns the script HTML. SCRIPT_HTML=<file> also writes it there (outside dist/) to diff a kit change byte for byte.
function buildScript() {
  const { dir, cfg } = deck();
  const S = scriptHelpers();
  const body = require(path.join(dir, 'src', 'script'));
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${cfg.scriptTitle}</title><style>${fontCSS()}${CSS}</style></head><body>${body}</body></html>`;
  if (process.env.SCRIPT_HTML) {
    const file = path.resolve(process.env.SCRIPT_HTML);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, html);
  }
  console.log('markers', S.markers, 'over:', S.overs.map(s => `${s.n}:${S.est(s)}/${s.slot}`).join(' '));
  return html;
}

module.exports = { buildScript, helpers: scriptHelpers };
