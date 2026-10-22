const { icon, md, mk, svg, lockup } = require('../../../presentation-kit/lib');
// Accent fills never carry text (design-screens.md §2.1): accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.

const todo = n => () => `<div style="margin-top:40px">${mk('FILL', 'slide ' + n + ' layout')}</div>`;

// ---------- motion helpers for slides 1–11 (patterns: docs/presentation-kit/MOTION.md) ----------
// gA: gives the root element of a shared helper's markup an entrance (class a, data-a, --d), leaving the helper unchanged.
const gA = (h, t, d) => {
  const r = h.replace(/^(\s*<\w+)(?: class="([^"]*)")?( style=")/, (m, o, cls, s) => `${o} class="${cls ? cls + ' ' : ''}a" data-a="${t}"${s}--d:${d}ms;`);
  if (r === h) throw new Error('gA: markup has no root style');
  return r;
};
// gAh: a CSS-border arrowhead (len along the path, h across) as a marker whose base sits on the path end.
const gAh = (id, c, len, h, refY = h / 2) => `<marker id="${id}" markerUnits="userSpaceOnUse" viewBox="0 0 ${len} ${h}" markerWidth="${len}" markerHeight="${h}" refX="0" refY="${refY}" orient="auto"><path d="M0,0 L${len},${h / 2} L0,${h} z" style="fill:${c}"/></marker>`;
// gCn: a connector that draws on (cn, pathLength 1, butt caps so the settled line matches the div it replaces).
const gCn = (d, c, w, ms, head) => `<path class="cn" pathLength="1" d="${d}" style="fill:none;stroke:${c};stroke-width:${w}px;stroke-linecap:butt;stroke-linejoin:miter;--d:${ms}ms"${head ? ` marker-end="url(#${head})"` : ''}/>`;
const gSvg = (inner, w = 1164, h = 560) => `<svg width="${w}" height="${h}" style="position:absolute;left:0;top:0;overflow:visible;pointer-events:none">${inner}</svg>`;
// gArr: dArr() redrawn as a connector at the same pixels (w px shaft, 2 px thick; 9 x 12 head).
const gArr = (id, d, c = '#6B655C', w = 14) => `<div style="flex:none;display:flex;align-items:center"><svg width="${w + 9}" height="12" style="display:block;overflow:visible"><defs>${gAh(id, c, 9, 12)}</defs>${gCn(`M0,6 L${w},6`, c, 2, d, id)}</svg></div>`;
// Attributes for an existing SVG element: gL draws a solid line on; gF fades an element (dashed lines, labels).
const gL = ms => ` class="cn" pathLength="1" style="stroke-linecap:butt;--d:${ms}ms"`;
const gF = ms => ` class="a" data-a="fade" style="--d:${ms}ms"`;

// ---------- slide 1 ----------
// Title lockup (MOTION.md, 5001 1): bag pop and wordmark up 200, kicker 213, tagline 320, headline 420, subline 520, names 650, programme 700.
const lockupA = () => [
  ['<svg width="102" height="128" viewBox="34 20 132 164" style="display:block">', '<svg class="a" data-a="pop" width="102" height="128" viewBox="34 20 132 164" style="display:block;--d:200ms">'],
  ['<div style="font:900 104px', '<div class="a" data-a="up" style="--d:200ms;font:900 104px'],
  ['<div style="font:600 17px', '<div class="a" data-a="up" style="--d:320ms;font:600 17px'],
].reduce((s, [f, t]) => { if (!s.includes(f)) throw new Error(`lockup markup changed: ${f}`); return s.replace(f, t); }, lockup());
const s1 = () => `
<div class="a" data-a="up" style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F;--d:213ms">SWE5006 PRACTICE PROJECT · TEAM 8</div>
${lockupA()}
<div class="a" data-a="up" style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff;--d:420ms">A scalable community group-buying platform</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center;--d:520ms">Two prices <span style="color:#E8402A;font-size:20px">●</span> pay first to team up <span style="color:#E8402A;font-size:20px">●</span> refunded if the team doesn’t fill</div>
<div class="a" data-a="fade" style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff;--d:650ms">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div class="a" data-a="fade" style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1;--d:700ms">Graduate Certificate in Designing Modern Software Systems · 26 October 2026</div>`;

// ---------- segment 1 helpers (patterns from docs/swe5001/presentation/src/slides-main.js) ----------
// m (optional): [pop, up] delays for the icon and its text (icon + text row).
const row = (ic, txt, bg, col = 'var(--ink)', sz = 80, fs = 25, mb = 30, m) => `<div style="display:flex;align-items:center;gap:26px;margin-bottom:${mb}px">
  <div class="dot${m ? ' a' : ''}"${m ? ' data-a="pop"' : ''} style="width:${sz}px;height:${sz}px;background:${bg};font-size:${sz / 2}px${m ? `;--d:${m[0]}ms` : ''}">${icon(ic)}</div>
  <div${m ? ' class="a" data-a="up"' : ''} style="font:400 ${fs}px/1.3 Inter;color:${col}${m ? `;--d:${m[1]}ms` : ''}">${txt}</div></div>`;
// d (optional): the icon pops at d, its text rises at d + 30.
const tick = (t, sub, kind = 'ok', d) => {
  const c = { ok: ['circle-check', 'var(--green)'], open: ['circle-exclamation', 'var(--orange)'], tbd: ['hourglass-half', '#6B655C'] }[kind];
  const [pc, ps] = d === undefined ? ['', ''] : [' class="a" data-a="pop"', `;--d:${d}ms`], [uc, us] = d === undefined ? ['', ''] : [' class="a" data-a="up"', `;--d:${d + 30}ms`];
  return `<div style="display:flex;gap:12px;margin-bottom:14px"><div${pc} style="color:${c[1]};font-size:24px;flex:none;margin-top:-1px${ps}">${icon(c[0])}</div>
  <div${uc} style="font:400 18px/1.3 Inter${us}">${t}${sub ? `<div style="font:italic 400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px">${sub}</div>` : ''}</div></div>`;
};

// ---------- slide 2 ----------
const s2 = () => `
<div class="a" data-a="up" style="position:absolute;left:0;top:4px;font:italic 400 18px Inter;color:var(--muted);--d:150ms">A market coordination failure, solved with a pay-first team purchase modelled on the early Pinduoduo playbook (proposal §4)</div>
<div class="card a" data-a="left" style="position:absolute;left:0;top:50px;width:520px;height:476px;padding:32px 34px;--d:260ms">
  <div class="serif a" data-a="up" style="font-size:30px;line-height:1.2;color:var(--muted);margin-bottom:26px;--d:290ms">Today</div>
  ${row('store', 'Merchants can discount only when volume is predictable', '#6B655C', 'var(--ink)', 76, 24, undefined, [380, 400])}
  ${row('user', 'One buyer can never reach a bulk price', '#6B655C', 'var(--ink)', 76, 24, undefined, [470, 490])}
  ${row('bullhorn', 'Advertising is costly and not tied to real purchases', '#6B655C', 'var(--ink)', 76, 24, undefined, [560, 580])}
</div>
${gSvg(`<defs>${gAh('s2ah', 'var(--orange)', 12, 14, 7.5)}</defs>${gCn('M534,289.5 L588,289.5', 'var(--orange)', 3, 700, 's2ah')}`)}
<div class="card a" data-a="right" style="position:absolute;left:614px;top:50px;width:550px;height:476px;padding:30px 34px;background:var(--inverse);border:none;--d:600ms">
  <div class="serif a" data-a="up" style="font-size:30px;color:#E8402A;margin-bottom:24px;--d:650ms">With BlocBuy: pay-first teams</div>
  ${row('tags', 'Two prices: individual and team', 'var(--orange)', '#fff', 62, 22, 22, [740, 770])}
  ${row('credit-card', 'Pay upfront to start a team, then share the invite link', 'var(--orange)', '#fff', 62, 22, 22, [830, 860])}
  ${row('users', 'Friends join by paying upfront too', 'var(--orange)', '#fff', 62, 22, 22, [920, 950])}
  ${row('arrows-rotate', 'Fills in time: team price.<br>Expires: everyone refunded automatically', 'var(--orange)', '#fff', 62, 22, 0, [1010, 1040])}
  <div class="a" data-a="up" style="margin-top:20px;font:italic 400 16px Inter;color:#B5ADA1;--d:1150ms">Stripe test mode only: no live money moves</div>
</div>`;

// ---------- slide 3 ----------
const s3 = () => `<div class="dia a" data-a="fade" style="margin-top:-8px;--d:200ms">${svg('use-case')}</div>
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:500px;font:400 16.5px/1.3 Inter;--d:750ms"><b>Out of scope:</b> merchant self-service GUI · analytics · recommendations · automatic team matching · email, SMS or push · native apps · live payments</div>`;

// ---------- slide 4 ----------
// Icon card rhythm from base d: card up d, title row +48, goal +100, result +140, stat +170.
const sprint = (n, dates, goal, result, stat, tone, d) => `<div class="card a" data-a="up" style="flex:1;height:196px;padding:14px 18px;border-top:5px solid ${tone};--d:${d}ms">
  <div class="a" data-a="up" style="display:flex;justify-content:space-between;align-items:baseline;--d:${d + 48}ms"><div style="font:700 21px Inter">Sprint ${n}</div><div style="font:600 16px Inter;color:var(--muted)">${dates}</div></div>
  <div class="a" data-a="up" style="font:400 17.5px/1.3 Inter;margin-top:8px;height:69px;--d:${d + 100}ms">${goal}</div>
  <div class="a" data-a="up" style="font:700 16px/1.3 Inter;margin-top:6px;color:var(--orange-d);--d:${d + 140}ms">${result}</div>
  <div class="a" data-a="up" style="font:400 15.5px Inter;color:var(--muted);margin-top:4px;--d:${d + 170}ms">${stat}</div></div>`;
// Icon + text: icon pops at d, its two lines rise at d + 30.
const cer = (ic, t, a, d) => `<div style="flex:1;display:flex;flex-direction:column;align-items:center;text-align:center"><div class="dot a" data-a="pop" style="width:46px;height:46px;background:var(--teal);font-size:22px;--d:${d}ms">${icon(ic)}</div>
  <div class="a" data-a="up" style="font:700 17px Inter;margin-top:6px;--d:${d + 30}ms">${t}</div><div class="a" data-a="up" style="font:400 15px/1.25 Inter;margin-top:2px;--d:${d + 30}ms">${a}</div></div>`;
// Stat strip: box up d, value +40, caption +46.
const stat = (big, unit, sub, d) => `<div class="card a" data-a="up" style="flex:1;padding:12px 16px;--d:${d}ms"><div class="a" data-a="up" style="--d:${d + 40}ms"><span class="serif" style="font-size:34px">${big}</span> <span style="font:700 17px Inter">${unit}</span></div><div class="a" data-a="up" style="font:400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px;--d:${d + 46}ms">${sub}</div></div>`;
const s4 = () => `
<div style="display:flex;gap:18px">
 ${sprint(1, '2–12 Sep', 'Walking skeleton: CI/CD and dev live on 5 Sep; sign-in, browse, admin', 'Pipeline live; features late', '35 team PRs · 3 issues closed', '#6B655C', 200)}
 ${sprint(2, '14–25 Sep', 'One idempotent chain: payment → team → order', '9 of 11 stories · 134 of 150 h', '64 team PRs · 47 issues closed', 'var(--teal)', 300)}
 ${sprint(3, '28 Sep–9 Oct', 'Invite → pay → refund on the deployed dev site', 'Not met on dev · 117 of 150 h', '126 team PRs · 67 issues closed', 'var(--orange)', 400)}
 ${sprint(4, '12–23 Oct', 'Complete system on dev; three services on ECS for SWE5001', mk('FILL', 'result, 23 Oct'), '38 team PRs · 17 closed so far', '#FFB01F', 500)}
</div>
<div class="a" data-a="fade" style="position:absolute;left:0;top:214px;width:640px;height:124px;border-radius:8px;background:#E3E8FB;padding:12px 10px;display:flex;--d:580ms">
 ${cer('calendar-check', 'Planning', 'plan.md, hours', 620)}${cer('clock', 'Day log', '35 dated logs', 670)}${cer('scale-balanced', 'Mid-sprint', 'check, S3–S4', 720)}${cer('magnifying-glass', 'Review', 'review, report', 770)}${cer('comments', 'Retro', 'in the report', 820)}
</div>
<div style="position:absolute;left:660px;top:214px;width:504px;display:flex;gap:12px">
 ${stat('159', 'cards', 'on the board: 132 Done, 7 in progress, 20 to do', 680)}
 ${stat('356', 'PRs', 'merged: 263 team, 70 Dependabot, 23 snapshot', 760)}
</div>
<div style="position:absolute;left:0;top:356px;width:640px;height:192px;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:14px 20px;--d:880ms" class="on-surface a" data-a="fade">
 <div class="serif a" data-a="up" style="font-size:21px;margin-bottom:8px;--d:890ms">Definition of Done, Sprints 3–4 (highlights)</div>
 <ul class="b a" data-a="up" style="font:400 17px/1.3 Inter;--d:950ms"><li style="margin-bottom:5px">Acceptance criteria met; contract before controller</li><li style="margin-bottom:5px">Every gate green: Maven verify, ArchUnit, Gitleaks, Trivy, SonarCloud, <span class="mono" style="font-size:15.5px">pnpm check</span></li><li style="margin-bottom:5px"><b>“On dev” means deployed</b>: a local run does not count</li><li style="margin-bottom:0">PR under ~1,500 lines; remaining hours on the board daily</li></ul>
</div>
<div class="a" data-a="fade" style="position:absolute;left:660px;top:356px;width:504px;height:192px;border-radius:8px;background:#FDEFEC;padding:14px 20px;--d:960ms">
 <div class="serif a" data-a="up" style="font-size:21px;margin-bottom:10px;color:var(--orange-d);--d:970ms">Was it met?</div>
 ${tick('Required gates block every merge', null, 'ok', 1040)}
 ${tick('Sprint 3 not counted: Checkout off on dev', null, 'open', 1120)}
 ${tick('Daily remaining hours not kept', null, 'open', 1200)}
</div>`;

// ---------- slide 5 ----------
const ms = [['27 Aug', 'Proposal'], ['5 Sep', 'Backend live on dev'], ['14 Sep', 'Sprint 1 tag'], ['22 Sep', 'Web app live on dev'], ['27 Sep', 'Sprint 2 tag'], ['8 Oct', 'Startable listings on dev'], ['11 Oct', 'Sprint 3 tag'], ['15 Oct', 'Three services merged'], ['23 Oct', 'Sprint 4 tag']];
const bdIdeal = [224.5, 202.1, 179.6, 157.2, 134.7, 112.3, 89.8, 67.4, 44.9, 22.5, 0], bdAct = [224.5, 224.5, 218.5, 184.5, 123.5, 123.5, 106.5];
const bdX = ['start', '12', '13', '14', '15', '16', '19', '20', '21', '22', '23'];
// Line chart (MOTION.md, 5001 14): chart fade 660, dashed ideal line fade 700, actual line cline 760, dots cdot 860 + 60, label 1280.
function burndown() {
  const W = 560, H = 196, L = 44, R = 548, T = 10, B = 168;
  const X = i => L + 14 + i * ((R - L - 24) / 10), Y = v => B - (v / 240) * (B - T);
  const line = (d, c, dash) => `<polyline${dash ? gF(700) : ' class="cline" pathLength="1" style="--d:760ms"'} points="${d.map((v, i) => `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => `<circle class="cdot" style="--d:${860 + i * 60}ms" cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg class="a" data-a="fade" style="--d:660ms" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 60, 120, 180, 240].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14.5" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${bdX.map((x, i) => `<text x="${X(i)}" y="${B + 22}" font-size="14.5" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${line(bdIdeal, '#A69E92', true)}${line(bdAct, '#3E5CD9')}
   <text${gF(1280)} x="${X(6) + 10}" y="${Y(106.5) - 10}" font-size="15" font-weight="700" fill="#3E5CD9">106.5 h left</text></svg>`;
}
const mem = [['Liu Jin', 51, 39], ['Yu Qianshuo', 48, 8], ['Wang Ziang', 46, 17], ['Panpakorn', 41.5, 29], ['Lim Jiechao', 38, 29]];
const hrs = [['S1', null, null], ['S2', 150, 134], ['S3', 150, 117], ['S4', 224.5, 118]];
const s5 = () => `
${gSvg(gCn('M10,49.5 L1154,49.5', 'var(--teal)', 3, 150))}
${ms.map((m, i) => {
  const x = 52 + i * 132.5, last = i === ms.length - 1, d = 280 + i * 70;
  return `<div class="a" data-a="up" style="position:absolute;left:${x - 66}px;width:132px;top:0;text-align:center;font:700 18px Inter;--d:${d}ms">${m[0]}</div>
  <div class="a" data-a="pop" style="position:absolute;left:${x - 12}px;top:38px;width:24px;height:24px;border-radius:50%;border:2px solid var(--teal);background:#fff;padding:4px;--d:${d}ms"><div style="width:100%;height:100%;border-radius:50%;background:${last ? '#ECE6DC' : 'var(--teal)'}"></div></div>
  <div class="a" data-a="up" style="position:absolute;left:${x - 66}px;width:132px;top:70px;text-align:center;font:400 15.5px/1.2 Inter;--d:${d + 40}ms">${m[1]}${last ? `<div style="margin-top:2px">${mk('FILL', 'date')}</div>` : ''}</div>`;
}).join('')}
<div style="position:absolute;left:0;top:150px;width:560px">
 <div class="a" data-a="up" style="display:flex;align-items:center;gap:10px;margin-bottom:8px;--d:520ms"><div class="serif" style="font-size:21px">Planned hours per member</div>${mk('VERIFY', 'S4 split, PR #538')}</div>
 <table class="t a" data-a="fade" style="font-size:17px;--d:560ms"><tr><th>Member</th><th style="text-align:right">S2</th><th style="text-align:right">S3</th><th style="text-align:right">S4</th><th style="text-align:right">Board items Done</th></tr>
 ${mem.map((m, i) => `<tr class="trow" style="--d:${Math.round(680 + i * 38.4)}ms"><td style="font-weight:600">${m[0]}</td><td style="text-align:right">30</td><td style="text-align:right">30</td><td style="text-align:right">${m[1]}</td><td style="text-align:right">${m[2]}</td></tr>`).join('')}</table>
 <div class="a" data-a="up" style="font:italic 400 15.5px/1.35 Inter;margin-top:8px;--d:900ms">S1 hours were not recorded. Board items are delivery evidence, not effort. Planned S2–S4: 98–111 h each; guideline ≈ 10 man-days each (proposal §7)</div>
 <div class="a" data-a="up" style="margin-top:8px;--d:950ms">${mk('CONFIRM', 'actual effort per member, agreed by all five')}</div>
</div>
<div style="position:absolute;left:600px;top:150px;width:564px">
 <div class="serif a" data-a="up" style="font-size:21px;margin-bottom:2px;--d:620ms">Sprint 4 burndown to 19 Oct: plan hours not Done</div>
 ${burndown()}
 <div class="a" data-a="up" style="display:flex;gap:22px;font:400 15px Inter;margin:-2px 0 8px 44px;--d:1000ms"><span><i style="display:inline-block;width:22px;height:0;border-top:3px dashed #A69E92;vertical-align:4px"></i> Ideal (plan §7)</span><span><i style="display:inline-block;width:22px;height:3px;background:var(--teal);vertical-align:4px"></i> Daily board snapshot</span></div>
 ${hrs.map((h, i) => { const d = 1060 + i * 90; return `<div style="display:flex;align-items:center;height:30px;gap:10px"><div class="a" data-a="up" style="width:30px;font:700 16px Inter;--d:${d}ms">${h[0]}</div>
   <div style="position:relative;width:220px;height:18px">${h[1] ? `<div class="a" data-a="grow" style="position:absolute;left:0;top:0;height:18px;width:${h[1] / 224.5 * 220}px;background:#ECE6DC;border-radius:4px;--d:${d + 60}ms"></div>` : ''}${h[2] ? `<div class="a" data-a="grow" style="position:absolute;left:0;top:0;height:18px;width:${h[2] / 224.5 * 220}px;background:var(--teal);border-radius:4px;--d:${d + 100}ms"></div>` : ''}</div>
   <div class="a" data-a="up" style="font:400 16px Inter;--d:${d + 160}ms">${!h[1] ? mk('FILL', 'S1 hours') : h[0] === 'S4' ? `<b>${h[2]}</b> of ${h[1]} h ${mk('FILL', 'final')}` : `<b>${h[2]}</b> of ${h[1]} h`}</div></div>`; }).join('')}
</div>`;

// ---------- slide 6 ----------
// d: the sprint label rises at d, its text at d + 40.
const rc = (s, was, now, d) => `<div style="margin-bottom:12px"><div class="a" data-a="up" style="font:700 16px Inter;color:var(--orange-d);--d:${d}ms">${s}</div><div class="a" data-a="up" style="font:400 16.5px/1.3 Inter;--d:${d + 40}ms">${was}${now ? ` <span style="color:var(--orange)">→</span> ${now}` : ''}</div></div>`;
// Side columns (MOTION.md): left 200, middle 250, right 320; heading +30…+40; tick rows +80, text +30; retro rows +90.
const s6 = () => `
<div style="display:flex;gap:18px;height:540px;margin-top:-6px">
 <div class="a" data-a="left" style="flex:1;background:#E3E8FB;border-radius:8px;padding:20px 20px;--d:200ms">
   <div class="serif a" data-a="up" style="font-size:24px;color:var(--green);margin-bottom:6px;--d:230ms">Live on dev · 19 Oct</div>
   <div class="a" data-a="up" style="margin-bottom:12px;--d:260ms">${mk('VERIFY', 're-check on 25 Oct')}</div>
   ${tick('API health UP; web app loads', 'checked 19 Oct, 14:14 SGT', 'ok', 320)}
   ${tick('14 listings in the catalog', 'startable listings set through the admin API (8 Oct)', 'ok', 400)}
   ${tick('Google sign-in, Secure session cookie', 'NFR-01 verified on dev, 8 Oct', 'ok', 480)}
   ${tick('Stripe Checkout opens and returns', 'Sandbox, 16 Oct (#517)', 'ok', 560)}
 </div>
 <div class="a" data-a="up" style="flex:1;background:#FDEFEC;border-radius:8px;padding:20px 20px;--d:250ms">
   <div class="serif a" data-a="up" style="font-size:24px;color:var(--orange-d);margin-bottom:16px;--d:290ms">Still open</div>
   ${tick('Webhook never confirms: payments stay “waiting”', '#517 · Yorik', 'open', 420)}
   ${tick('Scheduling and expiry refunds off on dev', '#223 · Yorik · go/no-go for the live payment demo', 'open', 500)}
   ${tick('Payment hardening in progress', '#539, #548, #550 · Eric', 'open', 580)}
   ${tick('Sprint 3 review and retro not yet written', '#451 · Lim', 'open', 660)}
 </div>
 <div class="card a" data-a="right" style="flex:1.12;padding:20px 20px;--d:320ms">
   <div class="serif a" data-a="up" style="font-size:24px;margin-bottom:14px;--d:360ms">Retro → next sprint</div>
   ${rc('Sprint 1 → 2', 'No burndown kept', 'daily board snapshots, hours per story', 520)}
   ${rc('Sprint 2 → 3', 'A 10,000-line PR, cliff burndown, owners drifted', 'PRs under 1,500 lines, mid-sprint check, hours daily', 610)}
   ${rc('Sprint 3 → 4', mk('FILL', 'retro, #451'), 'review: goal not met on dev, so #223 became the critical path', 700)}
   ${rc('Sprint 4', mk('FILL', 'retro on 23 Oct'), '', 790)}
 </div>
</div>`;

// ---------- segment 2 helpers (design; d* names) ----------
// Role colours follow docs/presentation-kit/diagrams/STYLE.md: [stroke, soft fill, text].
const dTone = {
  biz: ['#E8402A', '#FDEFEC', '#B02E1B'], plat: ['#3E5CD9', '#F1F4FD', '#3E5CD9'], ev: ['#7A5C20', '#FFF6E2', '#7A5C20'],
  n: ['#6B655C', '#FFFFFF', '#1B1A18'], bad: ['#8C2414', '#EFE9DF', '#8C2414'],
};
const cn = (t, sz = 15.5) => `<span class="mono" style="font-size:${sz}px;font-weight:500">${t}</span>`;
const dK = (t, c = '#6B655C') => `<div style="font:700 14.5px Inter;letter-spacing:.05em;text-transform:uppercase;color:${c};margin-bottom:5px">${t}</div>`;
const dTo = '<span style="color:#E8402A;font-weight:700"> → </span>';
const dArr = (c = '#6B655C', w = 14) => `<div style="flex:none;display:flex;align-items:center"><div style="width:${w}px;height:2px;background:${c}"></div><div style="border-left:9px solid ${c};border-top:6px solid transparent;border-bottom:6px solid transparent"></div></div>`;
const dBox = (kind, top, main, sub, style = '', msz = 15) => {
  const [b, bg, fg] = dTone[kind];
  return `<div style="border-radius:8px;border:1.4px solid ${b};background:${bg};padding:7px 11px;${style}">${top ? `<div style="font:700 14.5px/1.25 Inter;color:${fg}">${top}</div>` : ''}${main ? `<div style="font:500 ${msz}px/1.35 'JetBrains Mono',monospace;margin-top:2px">${main}</div>` : ''}${sub ? `<div style="font:400 14.5px/1.3 Inter;color:#6B655C;margin-top:3px">${sub}</div>` : ''}</div>`;
};
const dBand = (uc, a2d, prob) => `<div style="position:absolute;left:0;top:0;width:1164px;height:124px;display:flex;gap:12px">
 <div class="card" style="width:322px;padding:10px 16px">${dK('Use case · analysis')}<div style="font:400 17.5px/1.3 Inter">${uc}</div></div>
 <div class="card" style="width:432px;padding:10px 16px">${dK('Analysis → design')}<div style="font:400 15.5px/1.55 Inter">${a2d}</div></div>
 <div style="flex:1;border-radius:8px;background:#FDEFEC;padding:10px 16px">${dK('Design problem', '#B02E1B')}<div style="font:500 17.5px/1.3 Inter">${prob}</div></div></div>`;
const dPat = (name, kind, src) => `<div style="display:flex;align-items:baseline;gap:10px;margin-bottom:10px"><div style="font:700 21px Inter;white-space:nowrap">${name}</div><div style="font:500 15px Inter;color:#6B655C;white-space:nowrap">${kind}</div>${src ? `<div style="margin-left:auto;white-space:nowrap">${cn(src, 14.5)}</div>` : ''}</div>`;
const dTag = t => `<span style="display:inline-block;font:600 14.5px/1 Inter;color:#1B1A18;background:#ECE6DC;border-radius:999px;padding:5px 10px;white-space:nowrap">${t}</span>`;
const dNote = (ic, t, c = '#6B655C') => `<div style="display:flex;gap:10px;align-items:flex-start;font:400 16px/1.35 Inter"><span style="color:${c};font-size:18px;flex:none">${icon(ic)}</span><span>${t}</span></div>`;

// ---------- slide 7 ----------
// d (optional, motion): module rows and context pills rise or pop at d; rule cards rise at d with their arrow line +40.
const dMod = (m, t, d) => `<div${d === undefined ? '' : ' class="a" data-a="up"'} style="display:flex;align-items:center;height:37px;border-top:1px solid #EFE9DF${d === undefined ? '' : `;--d:${d}ms`}"><div style="width:230px;flex:none">${cn(m, 15.5)}</div><div style="font:400 16.5px Inter">${t}</div></div>`;
const dCtx = (t, k, d) => { const [b, bg, fg] = dTone[k]; return `<span${d === undefined ? '' : ' class="a" data-a="pop"'} style="display:inline-block;font:600 16px/1 Inter;color:${fg};background:${bg};border:1.4px solid ${b};border-radius:999px;padding:6px 12px${d === undefined ? '' : `;--d:${d}ms`}">${t}</span>`; };
const dRule = (a, d, ms) => `<div${ms === undefined ? '' : ' class="a" data-a="up"'} style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:8px 12px${ms === undefined ? '' : `;--d:${ms}ms`}"><div style="font:600 16.5px/1.25 Inter">${a}</div><div${ms === undefined ? '' : ` class="a" data-a="up"`} style="font:400 16px/1.3 Inter;color:#B02E1B;margin-top:3px${ms === undefined ? '' : `;--d:${ms + 40}ms`}">→ ${d}</div></div>`;
// Card columns: left card 200 (header 230, tag 280, module rows 300 + 50, pills 600 + 45, note 900);
// right card 320 (header 350, tag 400, dev box 460, ECS boxes 560 + 50, events 720, rule panel 800); rule band 950, cards 1000 + 70.
const s7 = () => `
<div class="card a" data-a="up" style="position:absolute;left:0;top:0;width:566px;height:398px;padding:14px 20px;--d:200ms">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif a" data-a="up" style="font-size:21px;--d:230ms">One codebase: five COLA modules</div>${gA(dTag('modular monolith'), 'pop', 280)}</div>
 <div style="margin-top:8px">
  ${dMod('blocbuy-adapter', 'REST controllers, Result envelope', 300)}
  ${dMod('blocbuy-application', 'use-case services, subscribers', 350)}
  ${dMod('blocbuy-domain', 'aggregates, events, ports; no Spring', 400)}
  ${dMod('blocbuy-infrastructure', 'MyBatis, Stripe, Redis, SQS, SNS', 450)}
  ${dMod('blocbuy-client', 'DTOs, integration event contracts', 500)}
 </div>
 <div class="a" data-a="up" style="font:600 16.5px Inter;margin:12px 0 8px;--d:560ms">Each bounded context is a package in every module</div>
 <div style="display:flex;flex-wrap:wrap;gap:8px">${dCtx('identity', 'plat', 600)}${dCtx('catalog', 'biz', 645)}${dCtx('groupbuy', 'biz', 690)}${dCtx('order', 'biz', 735)}${dCtx('payment', 'biz', 780)}${dCtx('notification', 'plat', 825)}</div>
 <div class="a" data-a="up" style="margin-top:14px;--d:900ms">${dNote('shield-halved', '<b>18 ArchUnit rules</b> fail the build on a wrong dependency', '#3E5CD9')}</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:584px;top:0;width:580px;height:398px;padding:14px 20px;--d:320ms">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif a" data-a="up" style="font-size:21px;--d:350ms">Two topologies from one build</div>${gA(dTag('ADR 0008'), 'pop', 400)}</div>
 <div class="a" data-a="up" style="margin-top:12px;--d:460ms">${dK('dev · SWE5006 · one EC2 host, Docker Compose')}
  ${dBox('biz', 'Monolith: all six contexts in one process', null, 'events in-process, no broker')}</div>
 <div style="margin-top:14px">${gA(dK('prod · SWE5001 · Amazon ECS'), 'up', 540)}
  <div style="display:flex;gap:8px">${gA(dBox('plat', 'identity-svc', null, null, 'flex:1'), 'pop', 560)}${gA(dBox('biz', 'commerce-svc', null, 'catalog · groupbuy · order · payment', 'flex:1.9'), 'pop', 610)}${gA(dBox('plat', 'notification-svc', null, null, 'flex:1.15'), 'pop', 660)}</div>
  <div class="a" data-a="up" style="margin-top:8px;--d:720ms">${dBox('ev', 'Events: outbox → SNS → SQS (ADR 0007)', null, null)}</div></div>
 <div class="a" data-a="up" style="margin-top:14px;border-radius:8px;background:#FDEFEC;padding:9px 14px;font:400 16.5px/1.3 Inter;--d:800ms"><b style="color:#B02E1B">Split only where no transaction crosses:</b> pay, join, order and stock commit together, so they stay in commerce</div>
</div>
<div style="position:absolute;left:0;top:414px;width:1164px">
 <div class="a" data-a="up" style="display:flex;align-items:baseline;justify-content:space-between;margin-bottom:8px;--d:950ms"><div class="serif" style="font-size:20px">Analysis → design: our transition rule</div><div style="font:400 15.5px Inter;color:#6B655C">use cases: slide 3 · worked examples: B7</div></div>
 <div style="display:flex;gap:10px">${dRule('Bounded context', 'package in each module', 1000)}${dRule('Analysis entity', 'aggregate + repository port', 1070)}${dRule('External actor (Google, Stripe)', 'gateway port + adapter (ACL)', 1140)}${dRule('Use case', 'application service + endpoint', 1210)}</div>
</div>`;

// ---------- slide 8 ----------
// gBand: dBand() with its three cards rising at d, d + 80, d + 160 (card row).
const gBand = (uc, a2d, prob, d = 150) => [
  ['<div class="card" style="width:322px;', `<div class="card a" data-a="up" style="--d:${d}ms;width:322px;`],
  ['<div class="card" style="width:432px;', `<div class="card a" data-a="up" style="--d:${d + 80}ms;width:432px;`],
  ['<div style="flex:1;border-radius:8px;background:#FDEFEC;', `<div class="a" data-a="up" style="--d:${d + 160}ms;flex:1;border-radius:8px;background:#FDEFEC;`],
].reduce((s, [f, t]) => { if (!s.includes(f)) throw new Error(`dBand markup changed: ${f}`); return s.replace(f, t); }, dBand(uc, a2d, prob));
// State diagram: the SVG fades with its nodes (440), then each transition draws on (540, 620, 700, 760) with its label +60.
const dState = () => {
  const node = (x, t) => `<rect x="${x}" y="22" width="150" height="46" rx="23" fill="url(#s8g)" stroke="#E8402A" stroke-width="1.4"/><text x="${x + 75}" y="51" text-anchor="middle" font-family="JetBrains Mono, monospace" font-size="15.5" font-weight="700" fill="#B02E1B">${t}</text>`;
  const lab = (x, y, t, ms) => `<text${gF(ms)} x="${x}" y="${y}" text-anchor="middle" font-family="JetBrains Mono, monospace" font-size="14.5" fill="#1B1A18">${t}</text>`;
  return `<svg class="a" data-a="fade" style="--d:440ms" width="700" height="96" viewBox="0 0 700 96" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><linearGradient id="s8g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FBDFD9"/></linearGradient>
  <marker id="s8ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker></defs>
  <circle cx="8" cy="45" r="7" fill="#1B1A18"/><line${gL(540)} x1="15" y1="45" x2="38" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>
  ${node(40, 'DRAFT')}${node(300, 'ON_SALE')}${node(548, 'OFF_SALE')}
  <line${gL(620)} x1="190" y1="45" x2="298" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(245, 37, 'publish()', 680)}
  <line${gL(700)} x1="450" y1="36" x2="546" y2="36" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(499, 28, 'delist()', 760)}
  <line${gL(760)} x1="548" y1="56" x2="452" y2="56" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(499, 76, 'publish()', 820)}
  <text x="115" y="90" text-anchor="middle" font-size="14.5" fill="#6B655C">only state that may be deleted</text></svg>`;
};
const dSchema = [
  ['identity', 'plat', 'sys_user, sys_role +4'], ['catalog', 'biz', 'biz_product +3'], ['groupbuy', 'biz', 'biz_group_deal +4'],
  ['order', 'biz', 'biz_order, biz_purchase_intent'], ['payment', 'biz', 'biz_payment, biz_refund +4'],
  ['notification', 'plat', 'biz_notification, sys_consumed_message'], ['jobs', 'biz', 'sys_outbox +3'],
];
// Band 150 / 230 / 310; State card 400 (diagram above, caption 860); lock card 500, process chain boxes pop 600 / 680 / 760 / 840 + 40,
// arrows draw after each pair (720 / 800 / 900), caption 980; schema card 640, rows 740 + 50, footnote 1110.
const s8 = () => `
${gBand('UC9 · An admin drafts, publishes and delists listings and sets team terms',
  `listing${dTo}${cn('Product')} «aggregate»<br>listing status${dTo}${cn('ProductStatus')} (State)<br>listing store${dTo}${cn('ProductRepository')} port`,
  'Legal moves depend on the status, and two admins can edit the same listing')}
<div class="card a" data-a="up" style="position:absolute;left:0;top:138px;width:744px;height:200px;padding:12px 20px;--d:400ms">
 ${gA(dPat('State', 'GoF · behavioural', 'ProductStatus.java'), 'up', 430)}
 ${dState()}
 <div class="a" data-a="up" style="font:400 16px/1.3 Inter;margin-top:2px;--d:860ms">Each constant overrides only its legal moves; every other move is refused with <b>409</b></div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:0;top:350px;width:744px;height:198px;padding:12px 20px;--d:500ms">
 ${gA(dPat('Optimistic Offline Lock', 'Fowler, PoEAA', 'Product.requireVersion'), 'up', 540)}
 <div style="display:flex;align-items:center;gap:6px">
  ${gA(dBox('n', 'Admin reads', 'version 4', null, 'width:128px', 14.5), 'pop', 600)}${gArr('s8a1', 720)}
  ${gA(dBox('n', 'Write sends', 'expectedVersion=4', null, 'width:180px', 14.5), 'pop', 680)}${gArr('s8a2', 800)}
  ${gA(dBox('biz', 'Guarded UPDATE', 'WHERE version=4', null, 'width:162px', 14.5), 'pop', 760)}${gArr('s8a3', 900)}
  <div style="display:flex;flex-direction:column;gap:6px;flex:1">${gA(dBox('plat', null, '1 row: v5', null, '', 14.5), 'pop', 840)}${gA(dBox('bad', null, '0 rows: 412', null, '', 14.5), 'pop', 880)}</div>
 </div>
 <div class="a" data-a="up" style="font:400 16px/1.3 Inter;margin-top:10px;--d:980ms">A stale write changes nothing: <b>412</b>, and the console offers Reload (frontend ADR 0015)</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:760px;top:138px;width:404px;height:410px;padding:12px 18px;--d:640ms">
 <div class="serif a" data-a="up" style="font-size:20px;--d:680ms">Database schema</div>
 <div class="a" data-a="up" style="font:400 15px Inter;color:#6B655C;margin:2px 0 8px;--d:700ms">29 tables · 28 Flyway migrations</div>
 ${dSchema.map(([g, k, t], i) => `<div class="a" data-a="up" style="display:flex;gap:10px;align-items:baseline;padding:4px 0 4px 10px;border-left:4px solid ${dTone[k][0]};margin-bottom:5px;--d:${740 + i * 50}ms"><div style="width:90px;flex:none;font:700 15px Inter;color:${dTone[k][2]}">${g}</div><div class="mono" style="font-size:14.5px;line-height:1.3">${t}</div></div>`).join('')}
 <div class="a" data-a="up" style="font:400 14.5px/1.35 Inter;color:#6B655C;margin-top:8px;--d:1110ms">UUID keys, timestamptz, numeric money; UNIQUE keys make retries safe · full list B5</div>
</div>`;

// ---------- slide 9 ----------
const dFactory = () => {
  const cls = (x, y, w, name, stereo, ops) => {
    const h = 46 + (stereo ? 18 : 0) + (ops ? ops.length * 22 + 8 : 0), ny = y + (stereo ? 40 : 29);
    return `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="url(#s9g)" stroke="#E8402A" stroke-width="1.4"/>
    ${stereo ? `<text x="${x + w / 2}" y="${y + 19}" text-anchor="middle" font-size="14.5" fill="#1B1A18">${stereo}</text>` : ''}
    <text x="${x + w / 2}" y="${ny}" text-anchor="middle" font-family="JetBrains Mono, monospace" font-size="15" font-weight="700" fill="#B02E1B"${stereo ? ' font-style="italic"' : ''}>${name}</text>
    ${ops ? `<line x1="${x}" y1="${ny + 12}" x2="${x + w}" y2="${ny + 12}" stroke="#E8402A" stroke-width="1.2"/>` + ops.map((o, i) => `<text x="${x + 12}" y="${ny + 34 + i * 22}" font-family="JetBrains Mono, monospace" font-size="14.5" fill="#1B1A18">${o}</text>`).join('') : ''}`;
  };
  return `<svg class="a" data-a="fade" style="--d:480ms" width="556" height="224" viewBox="0 0 556 224" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><linearGradient id="s9g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FBDFD9"/></linearGradient>
  <marker id="s9ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker>
  <marker id="s9op" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#1B1A18" stroke-width="1.4"/></marker>
  <marker id="s9tri" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="14" markerHeight="14" orient="auto-start-reverse"><path d="M0,0 L12,6 L0,12 z" fill="#fff" stroke="#1B1A18" stroke-width="1.2"/></marker></defs>
  ${cls(0, 22, 206, 'PaidGroupDealService', null, null)}
  ${cls(282, 0, 274, 'PaidGroupDealCreator', '«abstract»', ['+ initiate()  {final}', '# createGroupDeal()'])}
  ${cls(270, 170, 286, 'StandardPaidGroupDealCreator', null, null)}
  ${cls(0, 170, 150, 'GroupDeal', null, null)}
  <line${gL(600)} x1="206" y1="45" x2="280" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s9ah)"/><text${gF(660)} x="243" y="70" text-anchor="middle" font-size="14.5" fill="#6B655C">injected</text>
  <line${gL(680)} x1="419" y1="170" x2="419" y2="124" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s9tri)"/>
  <line${gF(760)} x1="270" y1="193" x2="152" y2="193" stroke="#1B1A18" stroke-width="1.4" stroke-dasharray="6 4" marker-end="url(#s9op)"/><text${gF(800)} x="211" y="185" text-anchor="middle" font-size="14.5" fill="#6B655C">creates</text></svg>`;
};
// Band 150 / 230 / 310; Factory card 400 (header 430, diagram 480, lines 600 / 680 / 760, text 860, note 920);
// guards card 520 (header 550, before 610, boxes pop 650 / 700, bad box 760, after 840, boxes 880 / 920, GroupDeal 960, arrow 1020, caption 1100).
const s9 = () => `
${gBand('UC3/UC4 · A buyer pays first to start a team, or pays to join an open one',
  `team${dTo}${cn('GroupDeal')} «aggregate»<br>accepted join${dTo}${cn('PaidAdmission')}<br>team store${dTo}${cn('PaidGroupDealRepository')} port`,
  'Join rules were written twice, for the quote and after payment, and drifted: a buyer could pay, then be refunded')}
<div class="card a" data-a="up" style="position:absolute;left:0;top:138px;width:596px;height:410px;padding:12px 20px;--d:400ms">
 ${gA(dPat('Factory Method', 'GoF · creational'), 'up', 430)}
 ${dFactory()}
 <div class="a" data-a="up" style="font:400 16px/1.35 Inter;margin-top:10px;--d:860ms">${cn('initiate()', 15)} is final: it checks terms and price, then calls ${cn('createGroupDeal()', 15)}, so it is also a Template Method</div>
 <div class="a" data-a="up" style="margin-top:8px;--d:920ms">${dNote('circle-info', 'One creator, wired at build time by <span class="mono" style="font-size:14.5px">GroupDealCreationConfig</span> (#421): a seam, not runtime-pluggable')}</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:612px;top:138px;width:552px;height:410px;padding:12px 20px;--d:520ms">
 ${gA(dPat('Shared guards on the aggregate', 'domain methods, not GoF'), 'up', 550)}
 ${gA(dK('Before PR #424'), 'up', 610)}
 <div style="display:flex;gap:8px">${gA(dBox('n', 'Quote', 'lockJoinTerms', 'own inline checks', 'flex:1'), 'pop', 650)}${gA(dBox('n', 'After payment', 'joinPaid', 'own inline checks', 'flex:1'), 'pop', 700)}</div>
 <div class="a" data-a="up" style="margin-top:6px;--d:760ms">${dBox('bad', 'Team with 0 members: quote OK, DEAL_FULL after payment, refund', null, null)}</div>
 <div class="a" data-a="up" style="margin-top:12px;--d:840ms">${dK('After (#422, merged 13 Oct)')}</div>
 <div style="display:flex;align-items:center;gap:6px">
  <div style="display:flex;flex-direction:column;gap:6px;width:150px">${gA(dBox('n', null, 'lockJoinTerms', null, '', 14.5), 'pop', 880)}${gA(dBox('n', null, 'joinPaid', null, '', 14.5), 'pop', 920)}</div>${gArr('s9a1', 1020)}
  ${gA(dBox('biz', 'GroupDeal', 'requireJoinWindow()<br>requireAvailableSeat()<br>requireNewMember()', null, 'flex:1', 14.5), 'pop', 960)}
 </div>
 <div class="a" data-a="up" style="font:400 15px/1.3 Inter;color:#6B655C;margin-top:6px;--d:1100ms">Each path keeps its own lock and check order</div>
</div>`;

// ---------- slide 10 ----------
const dCrash = () => {
  const xs = { w: 64, s: 240, d: 380 };
  // ms: a solid message draws on at ms (a dashed one fades), its label fades at ms + 60.
  const msg = (x1, x2, y, t, c = '#1B1A18', dash = false, ms = 0) => `<line${dash ? gF(ms) : gL(ms)} x1="${x1}" y1="${y}" x2="${x2 + (x2 > x1 ? -2 : 2)}" y2="${y}" stroke="${c}" stroke-width="${c === '#1B1A18' ? 1.4 : 2}"${dash ? ' stroke-dasharray="6 4"' : ''} marker-end="url(#s10${c === '#1B1A18' ? 'a' : 'r'})"/><text${gF(ms + 60)} x="${(x1 + x2) / 2}" y="${y - 8}" text-anchor="middle" font-size="15" fill="${c}">${t}</text>`;
  const head = (x, t) => `<rect x="${x - 58}" y="0" width="116" height="36" fill="#fff" stroke="#6B655C" stroke-width="1.4"/><text x="${x}" y="24" text-anchor="middle" font-size="15.5" font-weight="700" fill="#1B1A18">${t}</text><line x1="${x}" y1="36" x2="${x}" y2="300" stroke="#6B655C" stroke-width="1.2" stroke-dasharray="5 4"/>`;
  return `<svg class="a" data-a="fade" style="--d:480ms" width="440" height="300" viewBox="0 0 440 300" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><marker id="s10a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker>
  <marker id="s10r" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#E8402A"/></marker></defs>
  ${head(xs.w, 'Worker')}${head(xs.s, 'Stripe')}${head(xs.d, 'Database')}
  ${msg(xs.w, xs.s, 76, '1 create refund', '#1B1A18', false, 600)}${msg(xs.s, xs.w, 116, '2 accepted', '#1B1A18', true, 700)}
  <path${gF(800)} d="M${xs.w - 9},${139} l18,18 M${xs.w + 9},${139} l-18,18" stroke="#8C2414" stroke-width="3"/><text${gF(830)} x="${xs.w + 18}" y="${153}" font-size="15" font-weight="700" fill="#8C2414">3 crash, result not saved</text>
  ${msg(xs.d, xs.w, 200, '4 restart: still pending', '#1B1A18', true, 900)}
  ${msg(xs.w, xs.s, 246, '5 create refund again', '#E8402A', false, 1000)}
  <text${gF(1100)} x="${xs.s + 10}" y="${280}" font-size="15" font-weight="700" fill="#B02E1B">6 a second refund</text></svg>`;
};
// d: the solution row rises at d.
const dSol = (name, body, d) => `<div class="a" data-a="up" style="display:flex;gap:14px;padding:8px 0;border-top:1px solid #EFE9DF;--d:${d}ms"><div style="width:150px;flex:none;font:700 16.5px/1.3 Inter">${name}</div><div style="font:400 16px/1.4 Inter">${body}</div></div>`;
// Band 150 / 230 / 310; crash card 400 (kicker 440, sequence 480, messages 600 + 100 with labels +60, caption 1160);
// solution card 520 (kicker 560, rows 620 + 70), trailing callout 980 / 990.
const s10 = () => `
${gBand('UC6 · When a team expires, every paid member is refunded automatically',
  `refund owed${dTo}${cn('biz_refund')} + ${cn('RefundWork')}<br>payment provider${dTo}${cn('RefundGateway')} port<br>Stripe${dTo}${cn('StripeRefundGateway')} adapter`,
  'Stripe accepts a refund and the process dies before saving it: a naive retry refunds twice')}
<div class="card a" data-a="up" style="position:absolute;left:0;top:138px;width:476px;height:410px;padding:12px 18px;--d:400ms">
 ${gA(dK('The crash window', '#B02E1B'), 'up', 440)}
 ${dCrash()}
 <div class="a" data-a="up" style="font:400 15.5px/1.3 Inter;margin-top:6px;--d:1160ms">Without a stable request identity, the retry is a new refund</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:492px;top:138px;width:672px;height:410px;padding:12px 20px;--d:520ms">
 ${gA(dK('Solution'), 'up', 560)}
 ${dSol('Idempotency Key', `${cn('RefundWork.providerKey()', 15)} = "blocbuy-refund-" + id, sent by ${cn('StripeRefundGateway', 15)}: a retry gets the same refund back`, 620)}
 ${dSol('Lease + version', 'A claim leases the row for 2 min and bumps its version; the result is saved only if the version still matches', 690)}
 ${dSol('Fixed retry', 'A failed call is due again after a fixed 30 s, not exponential backoff', 760)}
 ${dSol('Human review', 'Payment mismatch, permanent provider error, or no result after 23 h: parked for review, never a new refund', 830)}
 ${dSol('Observer', `${cn('TeamExpired', 15)} → order and refund subscribers, before the same commit (ADR 0002)`, 900)}
 <div class="a" data-a="fade" style="margin-top:6px;border-radius:8px;background:#FFEDC7;padding:8px 14px;--d:980ms">${gA(dNote('flask', 'Proven locally with a Stripe substitute (3 Oct); no refund on dev yet (#223)', '#7A5C20'), 'up', 990)}</div>
</div>`;

// ---------- slide 11 ----------
// Process chain: band 150 / 230 / 310; chain card 400, stages up 440 + 90, each arrow 60 after the stage it points to, note 920;
// SQL card 960 (kicker 1000, code 1040, text 1090); placement card 1040 (kicker 1080, rows 1120 + 60).
const s11 = () => `
${gBand('Receive notifications · A buyer is told when their team succeeds or expires, or a payment succeeds',
  `notice${dTo}${cn('Notification')} «aggregate»<br>commerce fact${dTo}${cn('TeamExpiredV1')} contract<br>queue message${dTo}${cn('NotificationEventHandler')} port`,
  'Tell buyers without commerce calling notification, and never lose or repeat a notice')}
<div class="card a" data-a="up" style="position:absolute;left:0;top:138px;width:1164px;height:196px;padding:12px 18px;--d:400ms">
 <div style="display:flex;align-items:stretch;gap:6px">
  <div class="a" data-a="up" style="width:160px;--d:440ms">${dK('Observer', '#B02E1B')}${dBox('ev', 'domain event', 'TeamExpired', 'published in-process', 'height:112px', 14.5)}</div>${gArr('s11a1', 590)}
  <div class="a" data-a="up" style="width:240px;--d:530ms">${dK('Transactional Outbox', '#B02E1B')}${dBox('biz', 'BEFORE_COMMIT', 'IntegrationOutboxWriter', 'same transaction → sys_outbox', 'height:112px', 14.5)}</div>${gArr('s11a2', 680)}
  <div class="a" data-a="up" style="width:184px;--d:620ms">${dK('Relay')}${dBox('biz', 'every 2 s, leased', 'OutboxRelayJob', 'batches of 100, 10 tries', 'height:112px', 14.5)}</div>${gArr('s11a3', 770)}
  <div class="a" data-a="up" style="width:170px;--d:710ms">${dK('Transport')}${dBox('ev', 'at least once', 'SNS → SQS', 'DLQ after 5 receives', 'height:112px', 14.5)}</div>${gArr('s11a4', 860)}
  <div class="a" data-a="up" style="flex:1;--d:800ms">${dK('Idempotent Receiver', '#B02E1B')}${dBox('plat', 'one transaction', 'markConsumed', 'then biz_notification rows', 'height:112px', 14.5)}</div>
 </div>
 <div class="a" data-a="up" style="margin-top:12px;--d:920ms">${dNote('shield-halved', 'Commerce never calls notification: the ArchUnit rule <span class="mono" style="font-size:14.5px">nothing_depends_on_notification</span> fails the build', '#3E5CD9')}</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:0;top:350px;width:574px;height:198px;padding:12px 20px;--d:960ms">
 ${gA(dK('Idempotent Receiver in SQL'), 'up', 1000)}
 <div class="a" data-a="fade" style="background:#1B1A18;color:#F6F2EC;border-radius:8px;padding:10px 14px;font:500 15px/1.5 'JetBrains Mono',monospace;--d:1040ms">INSERT INTO sys_consumed_message<br>&nbsp;&nbsp;(event_id, consumer, …)<br>ON CONFLICT (event_id, consumer) DO NOTHING</div>
 <div class="a" data-a="up" style="font:400 16px/1.3 Inter;margin-top:8px;--d:1090ms">0 rows inserted = a duplicate: nothing written, message deleted</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:590px;top:350px;width:574px;height:198px;padding:12px 20px;--d:1040ms">
 ${gA(dK('Where it runs'), 'up', 1080)}
 ${[['dev · EC2', 'monolith: writer, relay and consumer off; no broker (ADR 0007)'], ['prod · ECS', 'commerce-svc → SNS → SQS → notification-svc'], ['local, CI', 'SQS on ElasticMQ: OutboxRelayElasticMqIT, NotificationConsumerElasticMqIT']].map(([a, b], i) => `<div class="a" data-a="up" style="display:flex;gap:12px;padding:6px 0;border-top:1px solid #EFE9DF;--d:${1120 + i * 60}ms"><div style="width:96px;flex:none;font:700 16px Inter">${a}</div><div style="font:400 15.5px/1.35 Inter">${b}</div></div>`).join('')}
</div>`;

// ---------- motion helpers for slides 12–22 (h* names; see docs/presentation-kit/MOTION.md) ----------
// hAt adds an entrance (class a, data-a, --d) to the first tag of a helper's markup, so shared helpers stay unchanged.
const hAt = (h, a, d) => {
  const i = h.indexOf('>'), r = h.slice(i);
  let t = h.slice(0, i);
  t = /class="/.test(t) ? t.replace(/class="([^"]*)"/, 'class="$1 a"') : t.replace(/^(\s*<\w+)/, '$1 class="a"');
  t = t.replace(/^(\s*<\w+)/, `$1 data-a="${a}"`);
  t = /style="/.test(t) ? t.replace(/style="([^"]*)"/, (m, s) => `style="${s.replace(/;?\s*$/, '')}${s.trim() ? ';' : ''}--d:${d}ms"`) : t + ` style="--d:${d}ms"`;
  return t + r;
};
// hIn does the same for the tag containing needle (e.g. one card inside dBand).
const hIn = (h, needle, a, d) => { const s = h.lastIndexOf('<', h.indexOf(needle)); return h.slice(0, s) + hAt(h.slice(s), a, d); };
// hArr is dArr drawn as an SVG connector (cn) with the same shaft and head geometry.
const hArr = (d, id, c = '#6B655C', w = 14) => `<div style="flex:none;display:flex;align-items:center"><svg width="${w + 9}" height="12" style="display:block;overflow:visible"><defs><marker id="${id}" markerUnits="userSpaceOnUse" viewBox="0 0 9 12" markerWidth="9" markerHeight="12" refX="0" refY="6" orient="auto"><path d="M0,0 L9,6 L0,12 Z" style="fill:${c}"/></marker></defs><path class="cn" pathLength="1" d="M0,6 L${w},6" marker-end="url(#${id})" style="stroke:${c};stroke-width:2px;stroke-linecap:butt;--d:${d}ms"/></svg></div>`;

// hM returns [class/data-a attributes, ;--d style] for an optional entrance, or empty strings when d is undefined.
const hM = (a, d) => d === undefined ? ['', ''] : [` data-a="${a}"`, `;--d:${d}ms`];

// ---------- slide 12 ----------
const s12 = () => `
${[['width:322px', 200], ['width:432px', 280], ['flex:1;border-radius:8px;background:#FDEFEC', 360]].reduce((h, [k, d]) => hIn(h, k, 'up', d), dBand('UC8 · A buyer sees their blocs (Active, Completed, Refunded) and orders on /account',
  `My blocs${dTo}${cn('GET /group-deals/me')}, latest 100<br>My orders${dTo}${cn('GET /orders/me')}, paged<br>view state${dTo}URL ${cn('?blocs=&amp;page=&amp;size=')}`,
  `A filter and a pager must survive reload, Back and a shared link; the API has no status filter <span style="white-space:nowrap">${mk('CONFIRM', 'Lim picks the problem')}</span>`))}
<div class="card a" data-a="fade" style="position:absolute;left:0;top:138px;width:1164px;height:180px;padding:12px 18px;--d:420ms">
 ${hAt(dPat('URL as the single source of view state', 'router owns URL state, Query owns server state (frontend ADR 0001)'), 'up', 440)}
 <div style="display:flex;align-items:stretch;gap:6px">
  ${hAt(dBox('n', 'URL', '/account?<br>blocs=completed&amp;page=2', null, 'width:226px', 14.5), 'up', 480)}${hArr(600, 's12a1')}
  ${hAt(dBox('plat', 'validateSearch', 'accountSearchSchema', 'bad value → fallback; defaults stripped', 'flex:1', 14.5), 'up', 570)}${hArr(690, 's12a2')}
  ${hAt(dBox('plat', 'loader', 'orders + blocs', 'prefetched in parallel; past the last page → redirect', 'flex:1', 14.5), 'up', 660)}${hArr(780, 's12a3')}
  ${hAt(dBox('plat', 'chip click', '?blocs=', 'not a loader dependency: filters the cache, no refetch', 'flex:1', 14.5), 'up', 750)}
 </div>
</div>
<div class="card a" data-a="fade" style="position:absolute;left:0;top:332px;width:574px;height:216px;padding:12px 20px;--d:760ms">
 ${hAt(dPat('Adapter at one boundary', 'frontend ADR 0009'), 'up', 780)}
 <div style="display:flex;align-items:center;gap:6px">${hAt(dBox('n', 'generated client', 'Result&lt;T&gt;', null, 'width:150px', 14.5), 'up', 820)}${hArr(940, 's12a4')}${hAt(dBox('biz', 'select:', 'unwrapResult', null, 'width:160px', 14.5), 'up', 910)}${hArr(1030, 's12a5')}${hAt(dBox('plat', 'components', 'T or ApiError', null, 'flex:1', 14.5), 'up', 1000)}</div>
 <div class="a" data-a="up" style="font:400 15.5px/1.35 Inter;margin-top:10px;--d:1080ms">One function knows the envelope; the client is generated from the contract (ADR 0003)</div>
</div>
<div class="card a" data-a="fade" style="position:absolute;left:590px;top:332px;width:574px;height:216px;padding:12px 20px;--d:820ms">
 ${hAt(dK('Evidence · account.test.tsx'), 'up', 840)}
 <ul class="b" style="font:400 15.5px/1.35 Inter"><li class="a" data-a="up" style="--d:890ms">switches chips through ?blocs= without refetching</li><li class="a" data-a="up" style="--d:950ms">falls back to Active for an unknown ?blocs= value</li><li class="a" data-a="up" style="--d:1010ms">sends a page past the last one to the last page</li></ul>
 <div class="a" data-a="up" style="font:400 14.5px/1.35 Inter;color:#6B655C;margin-top:6px;--d:1120ms">Limit: only the latest 100 blocs; a closed bloc reads “Closed”, as this endpoint carries no refund data</div>
</div>`;

// ---------- segment 3 helpers (testing; t* names) ----------
// Pyramid rows: [kind, tone, bar width, count, count unit, tools, runs, example class, behaviour]
const tRun = t => `<span style="display:inline-block;font:600 14.5px/1 Inter;color:#1B1A18;background:#ECE6DC;border-radius:999px;padding:4px 9px;white-space:nowrap;margin-right:6px">${t}</span>`;
const tRows = [
  ['Performance', 'ev', 150, '3', 'load scenarios', 'Node workload · k6', ['by hand'], 'backend/perf/workload.mjs', '100 users each, p95 24–57 ms (local, 30 Sep)'],
  ['End-to-end', 'plat', 175, '7', 'specs · 18 in code', 'Playwright', ['every PR', 'after deploy'], 'buyer-journey.spec.ts', 'listing → quote → Stripe (stubbed) → Paid in My blocs'],
  ['Component', 'plat', 200, '76', 'files · 799 tests', 'Vitest · Testing Library · MSW', ['every PR'], 'account.test.tsx', 'a chip switches ?blocs= without refetching'],
  ['API contract', 'n', 225, '1', 'OpenAPI file', 'export · git diff · openapi-diff', ['every PR'], 'contracts/openapi.json', 'drift or a breaking change fails the build'],
  ['Architecture', 'biz', 250, '18', 'ArchUnit rules', 'ArchUnit', ['every PR'], 'ArchitectureTest', 'domain_is_framework_free, nothing_depends_on_notification'],
  ['Integration', 'biz', 275, '49', '*IT classes', 'Spring Boot · Testcontainers', ['every PR'], 'OrderRepositoryIT · StripeRolloutAcceptanceIT', 'PostgreSQL 18 rejects a duplicate payment; paid start to refund'],
  ['Unit', 'biz', 300, '182', '*Test classes', 'JUnit Jupiter · Mockito', ['every PR'], 'RefundServiceTest · PaidGroupDealTest', 'a lost Stripe response reuses its key; 24 paid-team rules'],
];
const tRow = ([k, tone, w, n, unit, tool, runs, cls, why], i) => {
  const [b, bg, fg] = dTone[tone];
  return `<div style="display:flex;align-items:center;height:68px;border-top:1px solid #EFE9DF">
  <div style="width:300px;flex:none;display:flex;justify-content:center"><div style="width:${w}px;height:54px;border-radius:6px;border:1.4px solid ${b};background:${bg};display:flex;align-items:center;justify-content:center;font:700 17px Inter;color:${fg}">${k}</div></div>
  <div style="width:150px;flex:none;padding-left:18px"><div style="font:700 26px/1 Inter">${n}</div><div style="font:400 14.5px/1.2 Inter;color:#6B655C;margin-top:3px">${unit}</div></div>
  <div style="width:270px;flex:none"><div style="font:600 16px/1.25 Inter">${tool}</div><div style="margin-top:5px">${runs.map(tRun).join('')}</div></div>
  <div style="flex:1;min-width:0"><div>${cn(cls, 14.5)}</div><div style="font:400 15.5px/1.3 Inter;margin-top:3px">${why}</div></div></div>`;
};
const tHead = t => `<div style="font:700 14.5px Inter;letter-spacing:.05em;text-transform:uppercase;color:#6B655C">${t}</div>`;

// ---------- slide 13 ----------
const s13 = () => `
<div class="a" data-a="fade" style="display:flex;align-items:flex-end;height:24px;margin-top:-6px;--d:200ms">
 <div style="width:300px;flex:none;text-align:center">${tHead('Kind of test')}</div><div style="width:150px;flex:none;padding-left:18px">${tHead('Count')}</div>
 <div style="width:270px;flex:none">${tHead('Tool · when it runs')}</div><div style="flex:1">${tHead('Example: complex behaviour under test (3b)')}</div></div>
<div class="frag a" data-frag="1" data-a="up" style="--d:0ms">${tRows.slice(0, 3).map(tRow).join('')}</div>
<div>${tRows.slice(3).map((r, i) => hAt(tRow(r, i + 3), 'up', 300 + i * 70)).join('')}</div>
<div class="a" data-a="up" style="display:flex;gap:22px;align-items:center;border-top:1px solid #EFE9DF;padding-top:8px;font:400 15px Inter;color:#6B655C;--d:640ms">
 <span><i style="display:inline-block;width:14px;height:14px;border-radius:3px;background:#FDEFEC;border:1.4px solid #E8402A;vertical-align:-2px"></i> backend</span>
 <span><i style="display:inline-block;width:14px;height:14px;border-radius:3px;background:#F1F4FD;border:1.4px solid #3E5CD9;vertical-align:-2px"></i> frontend</span>
 <span style="margin-left:auto">Counted in the repository, 19 Oct · security scans (gitleaks, Trivy): Pan’s segment</span></div>`;

// ---------- slide 14 ----------
const tReq = t => `<div style="display:flex;gap:9px;align-items:center;font:500 16.5px Inter"><span style="color:var(--green);font-size:19px">${icon('circle-check')}</span>${t}</div>`;
const tCov = (side, nw, all, when) => `<div style="flex:1;border-radius:8px;background:#E3E8FB;padding:10px 14px"><div style="font:700 14.5px Inter;letter-spacing:.05em;text-transform:uppercase;color:#3E5CD9">${side}</div>
  <div style="margin-top:2px"><span class="serif" style="font-size:32px">${nw}</span> <span style="font:600 15.5px Inter">on new code</span></div><div style="font:400 15px/1.3 Inter;color:#6B655C">${all} overall · ${when}</div></div>`;
const tCaught = (ic, tone, kind, head, body, src) => {
  const [b, bg, fg] = dTone[tone];
  return `<div class="card" style="height:146px;padding:12px 18px;display:flex;gap:14px">
  <div style="width:46px;height:46px;flex:none;border-radius:50%;background:${bg};border:1.4px solid ${b};color:${fg};font-size:20px;display:flex;align-items:center;justify-content:center">${icon(ic)}</div>
  <div style="flex:1"><div style="font:700 14.5px Inter;letter-spacing:.05em;text-transform:uppercase;color:${fg}">${kind}</div>
   <div style="font:700 17px/1.3 Inter;margin-top:2px">${head}</div><div style="font:400 15.5px/1.35 Inter;margin-top:3px">${body}</div>
   <div style="font:400 14.5px Inter;color:#6B655C;margin-top:4px">${src}</div></div></div>`;
};
const s14 = () => `
<div class="card a" data-a="up" style="position:absolute;left:0;top:0;width:556px;height:196px;padding:12px 20px;--d:200ms">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif" style="font-size:21px">Required to merge into dev</div>${hAt(dTag('ruleset, strict'), 'pop', 300)}</div>
 <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 16px;margin-top:12px">
  ${['build-quality: every test', 'SonarCloud Backend', 'docker: image boots', 'SonarCloud Frontend', 'gitleaks', 'frontend-check: Vitest'].map((t, i) => hAt(tReq(t), 'up', 300 + i * 45)).join('')}</div>
 <div class="a" data-a="up" style="font:400 15px/1.3 Inter;color:#6B655C;margin-top:12px;--d:600ms">Branch up to date · 1 approval · threads resolved · squash only</div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:0;top:208px;width:556px;height:196px;padding:12px 20px;--d:340ms">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif" style="font-size:21px">SonarCloud gate on dev, 19 Oct</div>${hAt(dTag('NFR-06'), 'pop', 440)}</div>
 <div style="font:400 15.5px Inter;margin:4px 0 10px">Fails under <b>80%</b> coverage or over <b>3%</b> duplication on new code</div>
 <div style="display:flex;gap:12px">${hAt(tCov('Backend', '99.2%', '99.4%', 'JaCoCo, 16 Oct'), 'up', 500)}${hAt(tCov('Frontend', '96.2%', '96.1%', 'Vitest V8, 19 Oct'), 'up', 570)}</div>
</div>
<div style="position:absolute;left:0;top:416px;width:556px;height:74px;display:flex;gap:12px">
 <div class="a" data-a="up" style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:8px 14px;--d:680ms"><div><span class="serif" style="font-size:26px">1,931</span> <span style="font:700 15.5px Inter">backend tests</span></div><div style="font:400 14.5px Inter;color:#6B655C">0 failed, 1 opt-in skip · log 16 Oct</div></div>
 <div class="a" data-a="up" style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:8px 14px;--d:760ms"><div><span class="serif" style="font-size:26px">799</span> <span style="font:700 15.5px Inter">frontend tests</span></div><div style="font:400 14.5px Inter;color:#6B655C">e2e run 19 Oct: 17 pass, 5 skip</div></div>
</div>
<div class="frag a" data-frag="1" data-a="up" style="position:absolute;left:576px;top:0;width:588px;--d:0ms">
 ${dK('What tests caught before production', '#B02E1B')}
 <div style="display:flex;flex-direction:column;gap:10px">
 ${tCaught('vial', 'biz', 'Unit · red first', 'A team with no members passed the quote', 'The buyer could pay, then be refunded. Two red tests reproduced it before the fix (slide 9).', 'GroupDealPurchaseSupportImplTest · 7 Oct · #422')}
 ${tCaught('shield-halved', 'plat', 'Architecture', 'A new controller skipped input validation', '<span class="mono" style="font-size:14.5px">controllers_are_validated</span> failed the build until it had <span class="mono" style="font-size:14.5px">@Validated</span>.', 'ArchitectureTest · 13 Oct · #468')}
 ${tCaught('globe', 'ev', 'End-to-end after deploy', 'Dev answered 502 during a deploy', 'The smoke test ran while the single backend host restarted: dev deploys are not zero-downtime.', 'e2e-dev · 3 and 4 Oct · runs 37102432667, 37182996807')}
 </div>
</div>
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:502px;font:italic 400 15.5px/1.35 Inter;color:#6B655C;--d:860ms"><b style="font-style:normal">Limits:</b> test-first only for selected changes · no automated mutation testing (hand probes only) · load tests by hand, not in CI · CodeQL wired but off (ENABLE_CODEQL=false) · contract, e2e and Trivy run on PRs but are not required</div>`;

// ---------- segment 4 helpers (CI/CD; p* names) ----------
const pChip = (t, k = 'n') => { const [b, bg, fg] = dTone[k]; return `<span style="display:inline-block;font:600 15px/1 Inter;color:${fg};background:${bg};border:1.4px solid ${b};border-radius:999px;padding:5px 11px;white-space:nowrap">${t}</span>`; };
const pRule = t => `<div style="display:flex;gap:9px;align-items:flex-start;font:400 17px/1.3 Inter;margin-bottom:11px"><span style="color:var(--green);font-size:17px;flex:none;margin-top:1px">${icon('circle-check')}</span><span>${t}</span></div>`;
const pArt = (ic, head, body, d) => {
  const ia = d === undefined ? ['', ''] : [' class="a" data-a="pop"', `;--d:${d}ms`], ta = d === undefined ? ['', ''] : [' class="a" data-a="up"', ` style="--d:${d + 30}ms"`];
  return `<div style="display:flex;gap:14px;padding:10px 0;border-top:1px solid #EFE9DF">
  <div${ia[0]} style="width:42px;height:42px;flex:none;border-radius:50%;background:#E3E8FB;border:1.4px solid #3E5CD9;color:#3E5CD9;font-size:18px;display:flex;align-items:center;justify-content:center${ia[1]}">${icon(ic)}</div>
  <div${ta[0]}${ta[1]}><div style="font:700 17px/1.3 Inter">${head}</div><div style="font:400 15.5px/1.35 Inter;margin-top:2px">${body}</div></div></div>`;
};

// ---------- slide 15 ----------
const pBranch = () => `<svg width="610" height="172" viewBox="0 0 610 172" font-family="Inter, Helvetica, Arial, sans-serif">
 <defs><marker id="s15a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#3E5CD9"/></marker>
 <marker id="s15g" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#6B655C"/></marker>
 <marker id="s15r" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#E8402A"/></marker></defs>
 <text x="0" y="18" font-size="15" fill="#6B655C">feature/*, fix/*, docs/* · one pull request each</text>
 ${[110, 230, 350, 470].map((x, i) => `<path class="cn" pathLength="1" d="M${x - 40},30 Q${x - 6},34 ${x},62" fill="none" stroke="#6B655C" stroke-width="1.6" marker-end="url(#s15g)" style="stroke-linecap:butt;--d:${380 + i * 60}ms"/>`).join('')}
 <text x="0" y="74" font-size="20" font-weight="700" fill="#3E5CD9">dev</text>
 <line class="cn" pathLength="1" x1="46" y1="68" x2="596" y2="68" stroke="#3E5CD9" stroke-width="4" marker-end="url(#s15a)" style="stroke-linecap:butt;--d:320ms"/>
 ${[[150, 'sprint1'], [320, 'sprint2'], [490, 'sprint3']].map(([x, t], i) => `<circle class="cdot" style="--d:${520 + i * 80}ms" cx="${x}" cy="68" r="6" fill="#fff" stroke="#3E5CD9" stroke-width="2"/><text x="${x}" y="96" text-anchor="middle" font-size="14.5" font-family="JetBrains Mono, monospace" fill="#1B1A18">v0.1.0-${t}</text>`).join('')}
 <text x="0" y="146" font-size="20" font-weight="700" fill="#B02E1B">prod</text>
 <line class="cn dashed" x1="56" y1="140" x2="596" y2="140" stroke="#E8402A" stroke-width="2" stroke-dasharray="7 5" style="stroke-dasharray:7 5;stroke-linecap:butt;--d:640ms"/>
 ${[420, 570].map((x, i) => `<line class="cn" pathLength="1" x1="${x}" y1="73" x2="${x}" y2="132" stroke="#E8402A" stroke-width="1.8" marker-end="url(#s15r)" style="stroke-linecap:butt;--d:${700 + i * 80}ms"/>`).join('')}
 <text x="56" y="166" font-size="15" fill="#B02E1B">SWE5001 only: changed by a promotion PR from dev</text>
</svg>`;
const s15 = () => `
<div class="card a" data-a="left" style="position:absolute;left:0;top:0;width:650px;height:548px;padding:14px 20px;--d:200ms">
 <div class="a" data-a="up" style="display:flex;align-items:baseline;justify-content:space-between;--d:240ms"><div class="serif" style="font-size:21px">Source: one trunk, squash only</div>${hAt(dTag('ruleset dev-require-review'), 'pop', 300)}</div>
 <div class="a" data-a="fade" style="margin-top:8px;--d:280ms">${pBranch()}</div>
 <div style="display:grid;grid-template-columns:1fr 1fr;gap:0 18px;margin-top:14px">
  <div>${['Squash merge only: linear history', '1 approval by a code owner; stale approvals dismissed', 'Review threads resolved'].map((t, i) => hAt(pRule(t), 'up', 760 + i * 40)).join('')}</div>
  <div>${['6 required checks, branch up to date', 'No force push, no branch deletion', 'Conventional Commits; PR template asks for evidence'].map((t, i) => hAt(pRule(t), 'up', 880 + i * 40)).join('')}</div>
 </div>
 <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:8px"><span class="a" data-a="up" style="font:700 15px Inter;margin-right:2px;--d:1000ms">CODEOWNERS</span>${hAt(pChip('.github/ → Pan', 'n'), 'pop', 1040)}${hAt(pChip('backend/ → Liu Jin', 'biz'), 'pop', 1090)}${hAt(pChip('frontend/ → Lim', 'plat'), 'pop', 1140)}</div>
 <div class="a" data-a="up" style="margin-top:16px;--d:1200ms">${dNote('robot', '<b>Dependabot</b>: Maven, npm, Docker, Actions · daily, 7-day cooldown · 70 PRs merged', '#6B655C')}</div>
</div>
<div class="card a" data-a="right" style="position:absolute;left:666px;top:0;width:498px;height:548px;padding:14px 20px;--d:300ms">
 <div class="serif a" data-a="up" style="font-size:21px;margin-bottom:6px;--d:340ms">Artefacts</div>
 ${pArt('cubes', 'Backend image in ECR <span class="mono" style="font-size:15px">dev/blocbuy</span>', 'Immutable tags, scanned on push, newest 30 kept; deployed as <span class="mono" style="font-size:14.5px">repo@sha256:…</span>, never by tag', 420)}
 ${pArt('window-maximize', 'Frontend <span class="mono" style="font-size:15px">dist/</span>', 'One static build, no per-environment settings (ADR 0004); hashed assets immutable, <span class="mono" style="font-size:14.5px">index.html</span> no-cache', 520)}
 ${pArt('file-code', 'API contract', '<span class="mono" style="font-size:14.5px">contracts/openapi.json</span> regenerated from code; drift fails CI; the client is generated, not committed (ADR 0003)', 620)}
 ${pArt('tag', 'Release tags', '<span class="mono" style="font-size:14.5px">v0.1.0-sprint1…3</span>; a tag runs the full gate chain', 720)}
 <div class="a" data-a="up" style="margin-top:8px;border-radius:8px;background:#EFE9DF;padding:8px 12px;font:400 15px/1.35 Inter;--d:840ms">After merge, the jar, image and <span class="mono" style="font-size:14.5px">dist/</span> are rebuilt from the merged commit, then deployed (slide 16)</div>
</div>`;

// ---------- slide 16 ----------
const s16 = () => `<div class="dia a" data-a="fade" style="margin-top:-12px;--d:200ms">${svg('cicd-pipeline')}</div>`;

// ---------- slide 17 ----------
const pCtl = [
  ['gitleaks', 'secrets in commits', 'PR + dev push', 'req'],
  ['SonarCloud', 'bugs, hotspots, coverage', 'PR + dev', 'req'],
  ['SpotBugs, ArchUnit', 'Java bugs, layering', 'every PR', 'req'],
  ['Trivy image', 'fixable HIGH/CRITICAL', 'PR; ECR on push', 'req'],
  ['Trivy deps, config', 'Maven, pnpm, IaC', 'on change, weekly', 'opt'],
  ['actionlint, ShellCheck', 'workflows, scripts', 'on change', 'opt'],
  ['Pinned actions', 'all 28 on commit SHAs', 'Dependabot daily', 'none'],
];
const pGate = k => ({ req: pChip('required', 'plat'), opt: '<span style="color:#6B655C">fails run</span>', none: '<span style="color:#6B655C">review</span>' })[k];
const pRole = (name, trust, scope) => `<div style="padding:7px 0;border-top:1px solid #EFE9DF"><div>${cn(name, 15)}</div><div style="font:400 15.5px/1.3 Inter;margin-top:2px"><b>trust:</b> ${trust} · ${scope}</div></div>`;
const s17 = () => `
<div class="card a" data-a="up" style="position:absolute;left:0;top:0;width:700px;height:490px;padding:12px 18px;--d:180ms">
 <div class="a" data-a="up" style="display:flex;align-items:baseline;justify-content:space-between;margin-bottom:6px;--d:200ms"><div class="serif" style="font-size:21px">Shift-left controls</div>${hAt(dTag('security.yml · ci-backend.yml'), 'pop', 230)}</div>
 <table class="t a" data-a="fade" style="font-size:16px;table-layout:fixed;width:100%;--d:246ms"><tr><th style="font-size:15px;padding:6px 10px;width:186px">Control</th><th style="font-size:15px;padding:6px 10px">Catches</th><th style="font-size:15px;padding:6px 10px;width:156px">Runs</th><th style="font-size:15px;padding:6px 10px;width:104px">Gate</th></tr>
 ${pCtl.map(([c, w, r, g], i) => `<tr class="trow" style="--d:${Math.round(368 + i * 38.4)}ms"><td style="padding:10px 10px;font-weight:600">${c}</td><td style="padding:10px 10px">${w}</td><td style="padding:10px 10px">${r}</td><td style="padding:10px 10px">${pGate(g)}</td></tr>`).join('')}</table>
 <div class="a" data-a="up" style="font:400 15px/1.35 Inter;color:#6B655C;margin-top:8px;--d:680ms">Trivy writes SARIF reports; path-filtered scans also run on tags and every Monday</div>
</div>
<div class="card a" data-a="right" style="position:absolute;left:716px;top:0;width:448px;height:262px;padding:12px 18px;--d:300ms">
 <div class="a" data-a="up" style="display:flex;align-items:baseline;justify-content:space-between;--d:340ms"><div class="serif" style="font-size:21px">No AWS keys in GitHub</div>${hAt(dTag('OIDC'), 'pop', 380)}</div>
 <div style="margin-top:6px">
 ${hAt(pRole('BlocBuyGitHubEcrPush', 'dev branch', 'push to dev/blocbuy'), 'up', 420)}
 ${hAt(pRole('BlocBuyGitHubBackendDeploy', 'backend-dev env', 'one SSM document, one instance'), 'up', 480)}
 ${hAt(pRole('BlocBuyGitHubFrontendDeployDev', 'frontend-dev env', 'one bucket, one distribution'), 'up', 540)}
 </div>
</div>
<div class="card a" data-a="right" style="position:absolute;left:716px;top:276px;width:448px;height:214px;padding:12px 18px;--d:440ms">
 <div class="serif a" data-a="up" style="font-size:21px;margin-bottom:8px;--d:480ms">Runtime and account</div>
 ${hAt(dNote('box', 'Container: read-only root, <span class="mono" style="font-size:14.5px">cap_drop: ALL</span>, no-new-privileges', '#3E5CD9'), 'up', 560)}
 <div style="height:8px"></div>${hAt(dNote('key', 'App secrets only in a root-only file on the host (0600)', '#3E5CD9'), 'up', 620)}
 <div style="height:8px"></div>${hAt(dNote('building', 'AWS Organization SCPs pin the region and cap costs; Identity Center, 1-hour sessions', '#3E5CD9'), 'up', 680)}
</div>
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:504px;font:italic 400 15.5px/1.35 Inter;color:#6B655C;--d:900ms"><b style="font-style:normal">Not yet:</b> CodeQL wired but off (ENABLE_CODEQL=false) · CloudFront → ALB hop is HTTP (NFR-37, accepted on dev to 16 Nov) · dev host: SSH open to the internet, EBS not encrypted, secrets in an env file · full list B14</div>`;

// ---------- slide 18 ----------
const s18 = () => `<div class="dia a" data-a="fade" style="margin-top:-12px;--d:200ms">${svg('dev-deployment')}</div>`;

// ---------- slide 19 ----------
const pFind = (ic, tone, scope, head, what, fix, open, src, d) => {
  const [b, bg, fg] = dTone[tone], on = d !== undefined, A = on ? ' a' : '', m = (a, k) => hM(a, on ? d + k : undefined);
  const [cA, cD] = m('up', 0), [iA, iD] = m('pop', 40), [hA, hD] = m('up', 70), [wA, wD] = m('up', 110), [fA, fD] = m('fade', 170), [sA, sD] = m('up', 210);
  return `<div class="card${A}"${cA} style="height:266px;padding:12px 18px;display:flex;flex-direction:column${cD}">
  <div style="display:flex;gap:12px;align-items:center"><div${on ? ' class="a"' : ''}${iA} style="width:42px;height:42px;flex:none;border-radius:50%;background:${bg};border:1.4px solid ${b};color:${fg};font-size:18px;display:flex;align-items:center;justify-content:center${iD}">${icon(ic)}</div>
   <div${on ? ` class="a"${hA} style="${hD.slice(1)}"` : ''}><div style="font:700 14.5px Inter;letter-spacing:.05em;text-transform:uppercase;color:${fg}">${scope}</div><div style="font:700 18px/1.25 Inter">${head}</div></div></div>
  <div${on ? ' class="a"' : ''}${wA} style="font:400 17.5px/1.4 Inter;margin-top:10px${wD}">${what}</div>
  <div${on ? ' class="a"' : ''}${fA} style="margin-top:auto;border-radius:8px;background:${open ? '#FDEFEC' : '#E3E8FB'};padding:8px 12px;font:500 16px/1.35 Inter${fD}"><b style="color:${open ? '#B02E1B' : '#3E5CD9'}">${open === true ? 'Open' : open || 'Fix'}:</b> ${fix}</div>
  <div${on ? ' class="a"' : ''}${sA} style="font:400 14.5px Inter;color:#6B655C;margin-top:6px${sD}">${src}</div></div>`;
};
const s19 = () => `<div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">
 ${pFind('credit-card', 'biz', 'dev · pipeline green, feature not', 'Payments never confirm on dev', 'Stripe Checkout opens and returns, but the webhook never moves the payment out of “waiting”. Deploys and health checks stay green.', 'find the endpoint, secret or event mismatch; go/no-go for the payment demo on 23 Oct', true, '#517, #223 · 16 Oct · Yorik', 200)}
 ${pFind('server', 'ev', 'dev · single host', 'A deploy answered 502', 'The e2e-dev smoke hit the API while the only backend container was being replaced.', 'dev deploys are not zero-downtime: one host, container replaced in place', 'Accepted', 'runs 37102432667, 37182996807 · 3–4 Oct', 300)}
 ${pFind('building-shield', 'plat', 'dev · operations', 'Guardrails also block operators', 'Organisation SCPs denied Access Analyzer and bucket-list views; scoped deploys still worked.', 'IAM changes through the CLI; four SCPs consolidated into three (#530)', false, 'day log 23 Sep · log observability guide · 16 Oct', 400)}
 ${pFind('cloud', 'n', 'SWE5001 · real cloud ≠ mocks', 'Tests passed, the real run failed', 'Terraform tests on mocks passed, then AWS refused a tag permission; a read-only root on Fargate left Tomcat no writable /tmp.', 'scoped TagResource grant (#527); <span class="mono" style="font-size:14.5px">VOLUME /tmp</span> in both Dockerfiles', false, 'PR #527 · 16 Oct · day log 6 Oct', 500)}
</div>`;

// ---------- slide 20 ----------
const pStep = (n, ic, t, s, d) => `<div${d === undefined ? '' : ` class="a" data-a="up"`} style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:16px 12px;text-align:center${hM('up', d)[1]}">
  <div${d === undefined ? '' : ` class="a" data-a="pop"`} style="width:44px;height:44px;margin:0 auto;border-radius:50%;background:#E3E8FB;border:1.4px solid #3E5CD9;color:#3E5CD9;font-size:19px;display:flex;align-items:center;justify-content:center${hM('pop', d === undefined ? d : d + 40)[1]}">${icon(ic)}</div>
  <div style="font:700 17px/1.25 Inter;margin-top:8px">${n}. ${t}</div><div style="font:400 15px/1.3 Inter;color:#6B655C;margin-top:3px">${s}</div></div>`;
const pBig = (big, unit, sub, tone = '#3E5CD9', bg = '#E3E8FB', d) => `<div${d === undefined ? '' : ' class="a" data-a="up"'} style="flex:1;border-radius:8px;background:${bg};padding:16px 20px${hM('up', d)[1]}"><div><span class="serif" style="font-size:52px;color:${tone};white-space:nowrap">${big}</span> <span style="font:700 18px Inter">${unit}</span></div><div style="font:400 16px/1.35 Inter;margin-top:4px">${sub}</div></div>`;
const s20 = () => `
<div style="display:flex;gap:6px;align-items:stretch">
 ${pStep(1, 'code-pull-request', 'Open PR', 'a small frontend change', 200)}${hArr(300, 's20a1')}
 ${pStep(2, 'list-check', 'Checks', 'required checks, e2e-preview', 290)}${hArr(390, 's20a2')}
 ${pStep(3, 'user-check', 'Approve', 'code owner', 380)}${hArr(480, 's20a3')}
 ${pStep(4, 'code-merge', 'Squash merge', 'into dev', 470)}${hArr(570, 's20a4')}
 ${pStep(5, 'cloud-arrow-up', 'Deliver', 'S3, CloudFront, smoke test', 560)}${hArr(660, 's20a5')}
 ${pStep(6, 'eye', 'See it', 'on the dev site', 650)}
</div>
<div style="display:flex;gap:14px;margin-top:30px">
 ${pBig('≈ 5', 'min', 'frontend PR checks (median 4.8 min, 19 runs, 16–19 Oct)', undefined, undefined, 820)}
 ${pBig('≈ 2.7', 'min', 'merge → live and smoke-tested (median, 94 runs)', undefined, undefined, 890)}
 ${pBig('≈ 3.1', 'min', 'backend merge → deployed (median, 88 runs); shown recorded', '#1B1A18', '#ECE6DC', 960)}
</div>
<div style="display:flex;gap:14px;margin-top:30px">
 <div class="a" data-a="fade" style="flex:1;border-radius:8px;background:#FDEFEC;padding:16px 20px;--d:1060ms"><div class="a" data-a="up" style="font:700 19px Inter;color:#B02E1B;--d:1080ms">${icon('video')} Recorded fallback ready</div><div class="a" data-a="up" style="font:400 16px/1.35 Inter;margin-top:4px;--d:1110ms">If GitHub or AWS is slow, we switch to the recording (item 4e) ${mk('FILL', 'link, recorded by 25 Oct')}</div></div>
 <div class="a" data-a="fade" style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:14px 20px;--d:1120ms"><div class="a" data-a="up" style="font:700 19px Inter;--d:1140ms">Watch for</div><div class="a" data-a="up" style="font:400 16px/1.35 Inter;margin-top:4px;--d:1170ms">the required checks turning green, the merge, then the new version on <span class="mono" style="font-size:15px">d1b3np88z657cp.cloudfront.net</span> ${mk('CONFIRM', 'the demo change')}</div></div>
</div>`;

// ---------- slide 21 (pattern: SWE5001 slide 23) ----------
// Steps 1–4 run live on dev (cobalt, platform); step 5 shows labelled evidence (accent soft). Accent fills carry no text.
const dStep = (n, t, uc, env, qa, tone = 'live', d) => {
  const [bg, fg] = tone === 'live' ? ['#E3E8FB', '#3E5CD9'] : ['var(--orange-l)', 'var(--orange-d)'];
  const on = d !== undefined, A = on ? ' a' : '', C = on ? ' class="a"' : '', m = (a, k) => hM(a, on ? d + k : undefined);
  const [cA, cD] = m('up', 0), [nA, nD] = m('pop', 40), [tA, tD] = m('up', 70), [uA, uD] = m('up', 110), [eA, eD] = m('up', 160), [qA, qD] = m('up', 190);
  return `<div class="card${A}"${cA} style="flex:1;height:352px;padding:20px 16px;display:flex;flex-direction:column${cD}">
 <div class="dot${A}"${nA} style="width:56px;height:56px;background:${bg};color:${fg};border:1.4px solid ${fg};font:700 25px Inter${nD}">${n}</div>
 <div${C}${tA} style="font:700 20.5px/1.25 Inter;margin-top:14px${tD}">${t}</div>
 <div${C}${uA} style="font:400 15.5px/1.3 Inter;color:var(--muted);margin-top:6px${uD}">${uc}</div>
 <div style="flex:1"></div>
 <div${C}${eA} style="font:600 13.5px Inter;letter-spacing:.04em;color:${fg};margin-bottom:8px${eD}">${env}</div>
 <div${C}${qA} style="border-radius:8px;background:var(--default);padding:8px 10px;font:600 16.5px/1.3 Inter${qD}">${qa}</div></div>`;
};
const s21 = () => `
<div style="display:flex;gap:6px;align-items:stretch;margin-top:4px">
 ${dStep(1, 'Start a bloc and share the invite', 'UC2 sign in · UC3 start · UC7 invite · Stripe Sandbox', 'LIVE ON DEV', 'Functionality, security', 'live', 200)}${hArr(370, 's21a1')}
 ${dStep(2, 'A friend joins through the invite and pays', 'UC4 join · UC7 credit to the sharer · Stripe Sandbox', 'LIVE ON DEV', 'Consistency', 'live', 310)}${hArr(480, 's21a2')}
 ${dStep(3, 'My blocs and my orders', 'UC8 · Active, Completed, Refunded', 'LIVE ON DEV', 'Functionality', 'live', 420)}${hArr(590, 's21a3')}
 ${dStep(4, 'The bloc started at 16:05 expires and refunds', 'UC5 resolve · UC6 refund · receipt', 'LIVE ON DEV', 'Reliability, consistency', 'live', 530)}${hArr(700, 's21a4')}
 ${dStep(5, 'Quality attributes', 'one refund per payment · ECS scale-out · load tests · the pipeline', 'LABELLED EVIDENCE', 'Scalability, performance, maintainability', 'evidence', 640)}
</div>
<div style="display:flex;justify-content:space-between;margin-top:22px;font:400 17px/1.35 Inter;color:var(--muted)">
 <span class="a" data-a="up" style="--d:900ms"><b style="color:#3E5CD9">Live on dev:</b> one EC2 host, Stripe Sandbox test cards, no real money</span>
 <span class="a" data-a="up" style="--d:960ms"><b style="color:var(--orange-d)">Labelled:</b> SWE5001 prod on ECS or a local run, named on screen</span>
</div>
<div class="a" data-a="up" style="margin-top:18px;border-radius:8px;background:#FBDFD9;padding:12px 22px;font:700 19px Inter;display:flex;align-items:center;gap:12px;flex-wrap:wrap;--d:1100ms"><span class="a" data-a="pop" style="color:var(--red);font-size:14px;--d:1150ms">●</span> Live on dev; recorded steps are marked ${mk('CONFIRM', 'go/no-go for the live payment steps on 23 Oct (#223, #517)')}</div>`;

// ---------- slide 22 ----------
const s22 = () => `
<div class="a" data-a="up" style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff;--d:150ms">Thank you</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff;--d:300ms">Questions?</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:304px;font:700 16px Inter;letter-spacing:.14em;color:#FFB01F;--d:420ms">WHAT WE HOPE YOU REMEMBER</div>
<div style="position:absolute;left:80px;top:338px;width:1060px;display:grid;grid-template-columns:1fr 1fr;gap:14px 20px">
 ${['Pay first; refunded automatically if the team fails', 'A refund is never paid twice, even after a crash', 'One codebase, two topologies: EC2 and ECS', 'Pull request to dev in minutes, every gate green'].map((t, i) => `<div class="a" data-a="pop" style="height:52px;border-radius:26px;background:#fff;color:var(--teal-d);font:700 19px/52px Inter;text-align:center;--d:${600 + i * 110}ms">${t}</div>`).join('')}
</div>
<div style="position:absolute;left:80px;top:486px;width:1060px;display:flex;gap:20px">
 ${[['99.2%', 'backend coverage on new code (SonarCloud, 19 Oct)'], ['1,931', 'backend tests, 0 failed (16 Oct)'], ['≈ 2.7 min', 'frontend merge → live and smoke-tested (median)']].map((n, i) => `<div class="a" data-a="up" style="flex:1;border-radius:8px;border:1.5px solid rgba(255,255,255,.35);background:rgba(255,255,255,.08);padding:10px 18px;--d:${1000 + i * 100}ms"><div class="a" data-a="up" style="font:700 30px/1.2 Inter;color:#fff;--d:${1040 + i * 100}ms">${n[0]}</div><div class="a" data-a="up" style="font:400 15.5px Inter;color:#E6DFD3;--d:${1046 + i * 100}ms">${n[1]}</div></div>`).join('')}
</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center;--d:1400ms"><span class="a" data-a="pop" style="font-size:20px;--d:1400ms">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8 · d1b3np88z657cp.cloudfront.net</div>`;

const S = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 13: s13, 14: s14, 15: s15, 16: s16, 17: s17, 18: s18, 19: s19, 20: s20, 21: s21, 22: s22 };
for (let n = 2; n <= 21; n++) if (!S[n]) S[n] = todo(n);
module.exports = S;
