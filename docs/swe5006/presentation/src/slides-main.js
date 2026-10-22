const { icon, mk, lockup, svg } = require('../../../presentation-kit/lib');
const C = require('./content');
// Accent fills never carry text: accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.
// Legibility (rubric V3): labels at least 18 pt, which is 24 px on the 1280 × 720 canvas. Diagram crops meet it 1:1
// (live, and on the D detail pages in the PDF); body and table text is 20 px where it fits and never under 18 px, and
// uppercase labels and chips 15 px or more (their cap height matches 18 px lower case).
// Density (review round 4): one point per line, never "A · B · C" runs; sparse slides over dense ones; detail moves into
// the captions of the 1:1 crops, the speaker notes or the backups.

// Placeholder for slides whose content a later drafting pass fills in.
const pending = n => () => {
  const s = C.SLIDES.find(x => x.n === n);
  return `<div style="margin-top:40px;font:400 20px/1.4 Inter">Rubric rows: <b>${s.rubric.join(', ')}</b></div><div style="margin-top:16px">${mk('FILL', 'slide ' + n + ' draft pending')}</div>`;
};

// ---------- shared helpers ----------
// Records that help the report but not the talk (issue, PR and ADR numbers, file names, course page references) stay
// off the slide face as data-ref attributes on the element they support. List them with
// grep -o 'data-ref="[^"]*"' dist/swe5006-presentation.html
const ref = t => ` data-ref="${String(t).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')}"`;
const th = (t, w, fs = 20) => `<th style="font-size:${fs}px;padding:8px 12px${w ? ';width:' + w + 'px' : ''}">${t}</th>`;
// rows: arrays of cells; a row may carry its record reference as a `ref` property.
// m (optional): [table, first row, step] in ms (Table with rows): the table fades in, each body row enters as a trow.
const tbl = (head, rows, { fs = 20, w = [], pad = '7px 12px', m } = {}) => `<table class="t${m ? ' a" data-a="fade' : ''}" style="font-size:${fs}px${m ? `;--d:${m[0]}ms` : ''}"><tr>${head.map((h, i) => th(h, w[i], fs)).join('')}</tr>
${rows.map((r, j) => `<tr${r.ref ? ref(r.ref) : ''}${m ? ` class="trow" style="--d:${Math.round(m[1] + m[2] * j)}ms"` : ''}>${r.map(c => `<td style="padding:${pad}">${c}</td>`).join('')}</tr>`).join('')}</table>`;
const withRef = (cells, t) => Object.assign(cells, { ref: t });
const mo = (t, sz = 18) => `<span class="mono" style="font-size:${sz}px;font-variant-ligatures:none">${t}</span>`;
// One point per line inside a cell or card.
const lines = (a, gap = 2) => a.map(t => `<div style="margin-bottom:${gap}px">${t}</div>`).join('');
// d (optional): the chip pops at d ms.
const chip = (t, kind, d) => {
  const k = { ok: ['#E3E8FB', '#3E5CD9'], chg: ['#FFEDC7', '#7A5C20'], part: ['#ECE6DC', '#1B1A18'], drop: ['#EFE9DF', '#8C2414'], open: ['#FBDFD9', '#B02E1B'] }[kind];
  return `<span${d === undefined ? '' : ' class="a" data-a="pop"'} style="display:inline-block;font:700 15px/1 Inter;letter-spacing:.03em;text-transform:uppercase;background:${k[0]};color:${k[1]};border-radius:999px;padding:5px 9px;margin-right:8px;white-space:nowrap${d === undefined ? '' : `;--d:${d}ms`}">${t}</span>`;
};
const kicker = (t, c = '#6B655C') => `<div style="font:700 16px Inter;letter-spacing:.06em;text-transform:uppercase;color:${c};margin-bottom:8px">${t}</div>`;
const report = t => `<div style="font:600 20px/1.3 Inter;color:#3E5CD9;margin-top:8px">${icon('book')} ${t}</div>`;
// The report section a slide's example belongs to (rubric G8); the final wrapper moves it into the header strip.
const repTop = t => `<!--rep${t}-->`;
// Review round 5: the report line and the detail-page note sit in the header, left of the speaker chip, so this deck
// raises the body by 20 px (568 px tall instead of 548). The kit draws the chip at right:56px, top:20px, 30 px high and
// as wide as the name; the strip ends just before it.
const BODY_TOP = 108, BODY_H = 568;
const DECK_CSS = `<style>.slide:not(.dark)>.body{top:${BODY_TOP}px}body.present .pdfonly{display:none}</style>`;
const chipRight = name => 30 + 8 + Math.ceil(name.length * 8.6) - 2 + 18;
const hdrStrip = (speaker, items) => `<div class="a" data-a="fade" style="--d:0ms;position:absolute;right:${chipRight(speaker)}px;top:${20 - BODY_TOP}px;height:30px;display:flex;gap:18px;align-items:center;white-space:nowrap">${items.join('')}</div>`;
// A labelled point: small uppercase label above its text.
const point = (k, v, fs = 22, mb = 16) => `<div style="margin-bottom:${mb}px"><div style="font:700 16px Inter;letter-spacing:.06em;text-transform:uppercase;color:#6B655C;margin-bottom:3px">${k}</div><div style="font:400 ${fs}px/1.32 Inter">${v}</div></div>`;

// ---------- motion helpers (patterns: docs/presentation-kit/MOTION.md) ----------
// Every helper above and below takes motion as an opt-in argument; leaving it out gives the static markup unchanged.
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
// Title lockup (MOTION.md, 5001 1): bag pop and wordmark up 200, tagline 320.
const lockupA = () => [
  ['<svg width="102" height="128" viewBox="34 20 132 164" style="display:block">', '<svg class="a" data-a="pop" width="102" height="128" viewBox="34 20 132 164" style="display:block;--d:200ms">'],
  ['<div style="font:900 104px', '<div class="a" data-a="up" style="--d:200ms;font:900 104px'],
  ['<div style="font:600 17px', '<div class="a" data-a="up" style="--d:320ms;font:600 17px'],
].reduce((s, [f, t]) => { if (!s.includes(f)) throw new Error(`lockup markup changed: ${f}`); return s.replace(f, t); }, lockup());
// hAt adds an entrance (class a, data-a, --d) to the first tag of a helper's markup, so shared helpers stay unchanged.
const hAt = (h, a, d) => {
  const i = h.indexOf('>'), r = h.slice(i);
  let t = h.slice(0, i);
  t = /class="/.test(t) ? t.replace(/class="([^"]*)"/, 'class="$1 a"') : t.replace(/^(\s*<\w+)/, '$1 class="a"');
  t = t.replace(/^(\s*<\w+)/, `$1 data-a="${a}"`);
  t = /style="/.test(t) ? t.replace(/style="([^"]*)"/, (m, s) => `style="${s.replace(/;?\s*$/, '')}${s.trim() ? ';' : ''}--d:${d}ms"`) : t + ` style="--d:${d}ms"`;
  return t + r;
};
// hIn does the same for the tag containing needle.
const hIn = (h, needle, a, d) => { const s = h.lastIndexOf('<', h.indexOf(needle)); return h.slice(0, s) + hAt(h.slice(s), a, d); };
// A: entrance attributes for an element without its own class: `<div ${A('up', 300, 'margin-top:8px;')}>`.
const A = (a, d, st = '') => `class="a" data-a="${a}" style="${st}--d:${d}ms"`;

// ---------- slide 1 ----------
// Title lockup (MOTION.md): kicker 213, bag and wordmark 200, tagline 320, headline 420, subline 520, roadmap 600,
// names 680, programme 740.
const s1 = () => `${DECK_CSS}
<div ${A('up', 213, 'position:absolute;left:80px;top:96px;font:600 18px Inter;letter-spacing:.1em;color:#FFB01F;')}>SWE5006 PRACTICE PROJECT · TEAM 8</div>
${lockupA()}
<div ${A('up', 420, 'position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff;')}>A community group-buying platform with pay-first teams</div>
<div ${A('up', 520, 'position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center;')}>Two prices: solo &amp; team <span style="color:#E8402A;font-size:20px">●</span> Pay first to team up <span style="color:#E8402A;font-size:20px">●</span> Full refund if the team size is not met</div>
<div ${A('fade', 600, 'position:absolute;left:80px;top:448px;font:500 19px Inter;color:#FFB01F;display:flex;gap:30px;')}>${['1 Introduction', '2 Analysis, design, patterns', '3 Testing', '4 CI/CD demo', '5 System demo'].map(t => `<span>${t}</span>`).join('')}</div>
<div ${A('fade', 680, 'position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff;display:flex;gap:28px;')}>${['Lim Jiechao', 'Liu Jin', 'Panpakorn Siripanich', 'Wang Ziang', 'Yu Qianshuo'].map(t => `<span>${t}</span>`).join('')}</div>
<div ${A('fade', 740, 'position:absolute;left:80px;top:552px;font:400 18px Inter;color:#B5ADA1;')}>Graduate Certificate in Designing Modern Software Systems, 26 October 2026</div>`;

// ---------- slide 2 (1.1) ----------
// m (optional): [pop, up] delays for the icon and its text (Icon + text row).
const row = (ic, txt, bg, col = 'var(--ink)', sz = 64, fs = 22, mb = 22, m) => `<div style="display:flex;align-items:center;gap:22px;margin-bottom:${mb}px">
  <div class="dot${m ? ' a' : ''}"${m ? ' data-a="pop"' : ''} style="width:${sz}px;height:${sz}px;background:${bg};font-size:${sz / 2}px;flex:none${m ? `;--d:${m[0]}ms` : ''}">${icon(ic)}</div>
  <div${m ? ' class="a" data-a="up"' : ''} style="font:400 ${fs}px/1.3 Inter;color:${col}${m ? `;--d:${m[1]}ms` : ''}">${txt}</div></div>`;
// Side columns with icon + text rows; the arrow draws on between the cards (MOTION.md: Side columns, Icon + text row,
// Connector): problem 150, left card 260 (rows 380 +90), right card 560 (rows 680 +70), arrow 700, note 1000.
const s2 = () => `
<div ${A('up', 150, 'position:absolute;left:0;top:0;width:1164px;border-left:6px solid #E8402A;padding:6px 0 6px 18px;font:600 25px/1.3 Inter;')}>Small merchants can discount only against committed volume, but no single buyer can reach it: demand exists, but no buyer commits first.</div>
<div class="card a" data-a="left" style="position:absolute;left:0;top:118px;width:520px;height:410px;padding:28px 32px;--d:260ms">
  <div class="serif a" data-a="up" style="font-size:28px;color:var(--muted);margin-bottom:24px;--d:300ms">Today</div>
  ${row('store', 'Merchants discount only when volume is predictable', '#6B655C', undefined, undefined, undefined, undefined, [380, 410])}
  ${row('user', 'One buyer can never reach a bulk price', '#6B655C', undefined, undefined, undefined, undefined, [470, 500])}
  ${row('bullhorn', 'Advertising is costly and not tied to real purchases', '#6B655C', 'var(--ink)', 64, 22, 0, [560, 590])}
</div>
${gSvg(gAh('s2ah', 'var(--orange)', 12, 14, 7.5) + gCn('M534,319.5 L588,319.5', 'var(--orange)', 3, 700, 's2ah'))}
<div class="card a" data-a="right" style="position:absolute;left:614px;top:118px;width:550px;height:410px;padding:26px 32px;background:var(--inverse);border:none;--d:560ms">
  <div class="serif a" data-a="up" style="font-size:28px;color:#E8402A;margin-bottom:20px;--d:600ms">BlocBuy: pay-first teams</div>
  ${row('tags', 'Two prices: solo and team', 'var(--orange)', '#fff', 54, 21, 18, [680, 710])}
  ${row('credit-card', 'Pay upfront to start a team, then share the invite link', 'var(--orange)', '#fff', 54, 21, 18, [750, 780])}
  ${row('users', 'Friends join by paying upfront too', 'var(--orange)', '#fff', 54, 21, 18, [820, 850])}
  ${row('arrows-rotate', 'Fills in time: team price. Expires: everyone refunded automatically', 'var(--orange)', '#fff', 54, 21, 0, [890, 920])}
  <div ${A('up', 1000, 'margin-top:14px;font:italic 400 18px Inter;color:#B5ADA1;')}${ref('proposal §6.1')}>Stripe test mode only</div>
</div>`;

// ---------- slide 3 (1.2) ----------
const ucs = [['UC1', 'Browse and view listings'], ['UC2', 'Register / authenticate'], ['UC3', 'Initiate a team (pay first)'], ['UC4', 'Join a team'], ['UC5', 'Team lifecycle resolution'], ['UC6', 'Refund on team expiry'], ['UC7', 'Share attribution'], ['UC8', 'View my orders and teams'], ['UC9', 'Admin listing management']];
const outScope = ['Merchant self-service', 'Analytics', 'Recommendations', 'Automatic team matching', 'Email, SMS, push', 'Native apps', 'Live payments'];
const qas = [
  ['Correctness under concurrency', ['Team capacity never exceeded', 'No payment double-charged or double-refunded'], ['§5 demo', '§3']],
  ['Performance', ['Interactive operations ≤ 2 s at 100 concurrent users', 'Expiry resolved ≤ 1 min after the window'], ['§5 k6 run', '§3']],
  ['Security', ['Google sign-in', 'Role-based admin', 'Signed Stripe webhooks', 'No credentials in code'], ['§5 demo', '§4']],
  ['Maintainability, extensibility', ['New pricing, payment provider or team consequence without editing domain logic'], ['§2 patterns', '§4', '§5']],
  ['Code quality', ['SonarCloud gate on every PR', 'Unit tests on the complex classes'], ['§3', '§4']],
  ['Auditability', ['Every payment, refund and transition traceable', 'Every artefact traceable to a commit'], ['§4']],
];
// Side columns (MOTION.md): left card 200, its list 260 +30; right kicker 420, table [450, 560, 38.4].
const s3 = () => `
<div class="card a" data-a="left" style="position:absolute;left:0;top:0;width:380px;height:568px;padding:14px 20px;--d:200ms">
 ${gA(kicker('In scope · proposal §6.1'), 'up', 230)}
 ${ucs.map((u, i) => `<div ${A('up', 260 + 30 * i, 'display:flex;gap:10px;font:400 20px/1.2 Inter;margin-bottom:5px;')}><b style="width:48px;flex:none">${u[0]}</b><span>${u[1]}</span></div>`).join('')}
 <div ${A('up', 530, 'font:italic 400 20px/1.25 Inter;margin:2px 0 12px;')}>+ Receive notifications (Sprint 4, SWE5001; off on dev)</div>
 ${gA(kicker('Out of scope · proposal §6.2', '#B02E1B'), 'up', 560)}
 <div ${A('up', 590, 'display:grid;grid-template-columns:1fr 1fr;gap:3px 14px;font:400 18px/1.25 Inter;color:var(--muted);')}>${outScope.map(t => `<span>${t}</span>`).join('')}</div>
</div>
<div style="position:absolute;left:398px;top:0;width:766px">
 ${gA(kicker('Quality attributes and targets · proposal §6.3'), 'up', 420)}
 ${tbl(['Attribute', 'Target', 'Shown in'], qas.map((q, i) => [`<b>${q[0]}</b>`, lines(q[1]), `<div style="${i < 3 ? 'color:#B02E1B;font-weight:700' : ''}">${lines(q[2])}</div>`]), { fs: 18, w: [186, null, 108], pad: '5px 10px', m: [450, 560, 38.4] })}
</div>`;

// ---------- slide 4 (1.3) ----------
// Status first, the eight changes on the click (rubric V1). Record references live in data-ref (review round 4).
const status4 = [
  ['Delivered', 'ok', 'UC1–UC9', 'All nine closed as completed', '#24–#32'],
  ['Open', 'open', 'Second buyer’s paid join on dev', 'Paid start and expiry refund shown on dev 23 Oct', '#664 · #590 · #517'],
  ['Dropped', 'drop', 'Gated prod, ECS circuit breaker', 'For SWE5006; built for SWE5001', '#226 · ADR 0008'],
  ['Partial', 'part', 'E2E, burndown, saved plans', 'Stubs; daily data, no chart; manual', 'F-ADR 0011 · #229'],
];
const changed4 = [
  ['UC9 admin API only, no GUI', 'Admin console, built in Sprint 3', 'F-ADR 0015 · #305–#307'],
  ['Adapter (UC6), Observer (UC7)', 'Gateway ports; Observer on UC5 → UC6', 'slides 10, 19 · no ADR'],
  ['State for the team lifecycle', 'Transition table; State on listings', 'design doc · no ADR (open item 7)'],
  ['Google sign-in with JWT', 'Sa-Token session, HttpOnly cookie', 'D6/D7, F-ADR 0002 · no backend ADR'],
  ['Spring Data JPA', 'MyBatis behind repository ports', 'D1/D9 · no ADR (open item 7)'],
  ['Dev on ECS Fargate and RDS', 'One EC2 host, Compose, database on the host', 'ADR 0008'],
  ['Secrets Manager, no static keys', 'SSM and a root-only env file; 2 IAM users, 1 key', 'operator guide §7 · no ADR'],
  ['Blocking OWASP and IaC scans', 'Trivy, not required; 3 per-job roles', 'security.yml, ruleset 22734681 · no ADR'],
];
const gaps4 = [['SSH port 22 open on the dev host', 'AWS read-only, 21 Oct'], ['CloudFront → ALB over HTTP until 16 Nov', '#227'], ['Dev volume unencrypted', 'AWS read-only, 19 Oct']];
// Side columns, Icon card (MOTION.md): card 200, status blocks 240 +70 with the chip popping +30, Outstanding 550,
// gaps 580 +35. The changes table is a plain fragment (its children cannot stagger after the click).
const s4 = () => `
<div class="card a" data-a="left" style="position:absolute;left:0;top:0;width:420px;padding:12px 20px 14px;--d:200ms">
 ${status4.map((r, i) => `<div ${A('up', 240 + 70 * i, 'margin-bottom:10px;')}${ref(r[4])}><div style="font:600 19px/1.25 Inter">${chip(r[0], r[1], 270 + 70 * i)}${r[2]}</div><div style="font:400 19px/1.28 Inter;margin-top:2px">${r[3]}</div></div>`).join('')}
 <div ${A('up', 550, 'margin-top:4px;')}>${kicker('Outstanding', '#B02E1B')}</div>
 ${gaps4.map((g, i) => `<div ${A('up', 580 + 35 * i, 'font:400 19px/1.28 Inter;margin-bottom:2px;')}${ref(g[1])}>${g[0]}</div>`).join('')}
</div>
<div class="frag" data-frag="1" style="position:absolute;left:438px;top:0;width:726px">
 ${tbl(['Changed: promised', 'Built'], changed4.map(r => withRef([`<b>${r[0]}</b>`, r[1]], r[2])), { fs: 20, w: [312], pad: '6px 12px' })}
 <div style="font:400 20px/1.3 Inter;color:#B02E1B;margin-top:10px">Six of these eight changes have no ADR yet.</div>
</div>`;

// ---------- slide 5 (1.4) ----------
const ms = [['27 Aug', 'Proposal submitted'], ['5 Sep', 'Backend pipeline live on dev'], ['14 Sep', 'Sprint 1 tag'], ['20 Sep', 'Stripe Sandbox acceptance (local)'], ['22 Sep', 'Web app live on dev'], ['27 Sep', 'Sprint 2 tag'], ['30 Sep', 'Admin console merged', '#305–#307'], ['11 Oct', 'Sprint 3 tag'], ['20 Oct', 'SWE5001 production release on ECS', 'promotion PR #574 · deploy run 39435280952 · three services since PR #534 (17 Oct)'], ['23 Oct', 'Paid start and refund on dev', 'PR #664 · stripe-dev-recovery-acceptance-20261023.md']];
const mem = [['Liu Jin', 51, 12.5], ['Yu Qianshuo', 48, 9], ['Wang Ziang', 46, 8.5], ['Panpakorn', 41.5, 11.5], ['Lim Jiechao', 38, 8.5]];
// Table with rows twice, then the lines and a trailing callout (MOTION.md): kicker 200, milestones [240, 340, 30];
// effort kicker 400, table [430, 520, 38.4]; lines 760, callout 860.
const s5 = () => `
<div style="position:absolute;left:0;top:0;width:480px">
 ${gA(kicker('Milestones achieved'), 'up', 200)}
 ${tbl(['Date', 'Milestone'], ms.map(m => withRef([`<b style="white-space:nowrap">${m[0]}</b>`, m[1]], m[2] || '')).concat([['—', `Sprint 4 tag ${mk('FILL', 'date, once cut (open item 6)')}`]]), { fs: 20, w: [96], pad: '8px 12px', m: [240, 340, 30] })}
</div>
<div style="position:absolute;left:510px;top:0;width:654px">
 <div ${A('up', 400, 'display:flex;align-items:baseline;gap:10px;')}>${kicker('Effort per member')}${mk('VERIFY', 'S4 split, PR #538')}</div>
 ${tbl(['Member', 'S1', 'S2', 'S3', 'S4', 'Planned h', 'Actual days'], mem.map(m => [`<b>${m[0]}</b>`, '—', '30', '30', m[1], `<b>${60 + m[1]}</b>`, `<b style="color:#3E5CD9">${m[2]}</b>`]), { fs: 20, w: [150], pad: '9px 11px', m: [430, 520, 38.4] })}
 <div ${A('up', 760, 'font:400 20px/1.4 Inter;margin-top:14px;')}>${lines(['Sprint 1 hours were not recorded', 'Team done: Sprint 2 134 of 150 h; Sprint 3 117 of 150 h', `<span${ref('proposal §7')}>Proposal budget: 10 person-days each</span>`], 4)}</div>
 <div ${A('up', 860, 'margin-top:12px;border-radius:8px;background:#E3E8FB;padding:12px 16px;font:400 20px/1.35 Inter;')}${ref('SWE5001 deck slide 6 (docs/swe5001/presentation/src/slides-main.js eff), presented 21 Oct')}><b style="color:#3E5CD9">Actual, ≈ 50 person-days:</b> the split the team agreed for the joint SWE5001 and SWE5006 project, estimated from the issues each person delivered; no one logged hours.</div>
</div>`;

// ---------- slide 6 (1.5) ----------
const own = [
  ['Liu Jin', 'UC9 admin listings and team terms', 'Listing lifecycle → <b>State</b> (full form)', 'slides 22–23 · was UC5 State, UC9'],
  ['Wang Ziang (Eric)', 'UC3 start, UC4 join', 'Team creation → <b>Factory Method</b>', 'slide 18 · unchanged; UC4 race is a transition strategy'],
  ['Yu Qianshuo (Yorik)', 'UC6 refund on expiry', 'Team outcome consequences → <b>Observer</b>', 'slide 19 · was Adapter'],
  ['Panpakorn (Pan)', 'Receive notifications', 'Outbox transports → <b>Template Method</b>', 'slide 20 · was UC1 Strategy, UC2'],
  ['Lim Jiechao', 'UC8 my orders and teams', 'Pricing → <b>Strategy</b>', 'slide 21 · was UC7 Observer, UC8'],
];
// Inline token strip (MOTION.md): kicker 150, tokens 200 +30, the CONFIRM chip pops 520; table [420, 540, 38];
// footnotes 800 / 860.
const s6 = () => `
${gA(kicker('Every use case has an owner · * proposed, not yet agreed'), 'up', 150)}
<div style="display:flex;flex-wrap:wrap;gap:8px 20px;align-items:center;font:400 20px/1.3 Inter;margin-bottom:16px">${[['UC1', 'Lim*'], ['UC2', 'Pan*'], ['UC3', 'Eric'], ['UC4', 'Eric'], ['UC5', 'Eric*'], ['UC6', 'Yorik'], ['UC7', 'Lim*'], ['UC8', 'Lim'], ['UC9', 'Liu Jin'], ['Notifications', 'Pan']].map((u, i) => `<span ${A('up', 200 + 30 * i)}><b>${u[0]}</b> ${u[1]}</span>`).join('')}${hAt(mk('CONFIRM', 'owners marked * (open decision 4)'), 'pop', 520)}</div>
${tbl(['Member', 'Use case', 'Design problem → GoF pattern'], own.map(o => withRef([`<b>${o[0]}</b>`, o[1], o[2]], `${o[3]} (proposal §6.1)`)), { fs: 20, w: [250, 380], pad: '11px 14px', m: [420, 540, 38] })}
<div ${A('up', 800, 'margin-top:14px;font:400 20px/1.4 Inter;')}>Each member presents their own design problem in section 2; the team statechart (UC4–UC6, UC8) goes with Eric’s UC4 design.</div>
<div ${A('up', 860, 'margin-top:6px;display:flex;gap:14px;align-items:center;')}>${report('Report: use case in §3.4, design problem in §3.5').replace('margin-top:8px', 'margin:0')} ${mk('FILL', 'section numbers (#463)')} ${mk('VERIFY', 'UC9 owner')}</div>`;

// ---------- slide 7 (1.6, 1.7) ----------
const steps = ['coded', 'tested', 'integrated', 'releasable', 'released'];
// Process chain (MOTION.md): label 150, pills pop 200 +70, each glyph arrow fades 30 ms after the pill it points to.
const ladder = () => `<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px"${ref('EPAT 01 Agile p26')}><span ${A('up', 150, 'font:600 18px Inter;color:var(--muted);margin-right:6px;')}>Definition of Done ladder</span>${steps.map((s, i) => `<span ${A('pop', 200 + 70 * i, `font:600 18px/1 Inter;padding:7px 12px;border-radius:999px;background:${[2, 3, 4].includes(i) ? '#E3E8FB' : '#ECE6DC'};color:${[2, 3, 4].includes(i) ? '#3E5CD9' : '#1B1A18'};`)}>${s}</span>${i < 4 ? `<span ${A('fade', 300 + 70 * i, 'color:#A69E92;')}>→</span>` : ''}`).join('')}</div>`;
const spr = [
  ['1 · 1–12 Sep', 'Walking skeleton: sign-in, browse, admin on dev', '<b>integrated</b>: backend on dev 5 Sep; no frontend, so not releasable'],
  ['2 · 15–26 Sep', 'Pay, join a team, see the order, replay-safe', '<b>integrated</b>: merged, tested, Checkout off on dev; 134 of 150 h'],
  ['3 · 28 Sep–9 Oct', 'Invite → pay → refund on deployed dev', '<b>integrated</b>; goal not met under our “on dev” rule; 117 of 150 h'],
  ['4 · 12–23 Oct', 'Complete system on dev; three services on ECS (SWE5001)', `<span${ref('PR #534 (17 Oct) · PR #574 · run 39435280952 · SSM /blocbuy/swe5001/release/* 20 Oct 22:32 SGT · PR #664 · #590')}><b>released</b>: SWE5001 production on ECS, 17 and 20 Oct, Stripe test mode, up per session; SWE5006 dev <b>releasable</b> except a second buyer’s join: paid start and refund shown 23 Oct</span>`],
];
const retro = [
  ['1 → 2', 'No burndown, no hours', 'Daily board snapshots, dated day logs'],
  ['2 → 3', 'A 10,000-line PR; cliff burndown', 'PRs under 1,500 lines, mid-sprint check, “on dev” rule in the DoD'],
  ['3 → 4', mk('FILL', 'retro #451'), 'Owners keep board estimates current, checked mid-sprint'],
];
const s7 = () => `
${ladder()}
${tbl(['Sprint', 'Goal', 'Definition of Done reached by the goal increment'], spr.map(s => [`<b style="white-space:nowrap">${s[0]}</b>`, s[1], s[2]]), { fs: 18, w: [160, 450], pad: '6px 11px', m: [560, 640, 38] })}
<div ${A('up', 860, 'font:400 18px/1.3 Inter;color:var(--muted);margin-top:6px;')}${ref('docs/agile/definition-of-done.md#done-ladder-per-sprint')}>Same basis every sprint: integrated = merged to dev with every required check green; releasable = shown working on deployed dev, our “on dev” rule; released = promoted to production and verified there</div>
<div class="frag" data-frag="1" style="margin-top:12px;border-radius:8px;background:#FDEFEC;padding:10px 16px">
 <div style="font:700 20px Inter;color:#B02E1B;margin-bottom:6px">Retro improvement → change in the next sprint</div>
 ${retro.map(r => `<div style="display:flex;gap:12px;font:400 20px/1.32 Inter;margin-bottom:4px"><b style="width:58px;flex:none">${r[0]}</b><span style="width:340px;flex:none">${r[1]}</span><span><span style="color:#E8402A;font-weight:700">→</span> ${r[2]}</span></div>`).join('')}
</div>`;

// ---------- slide 8 (1.8) ----------
function burndown(m) {
  // Both sprints planned 150 h, so one hours axis compares them; x is the sprint's working day (0 = start).
  const xs = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
  const ideal = [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0];
  const s2 = [150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16], s3 = [150, 132, 114, 90, 82, 76, 68, 68, 68, 66, 66];
  const W = 470, H = 230, L = 46, R = W - 10, T = 12, B = H - 32;
  const X = i => L + 12 + i * ((R - L - 20) / (xs.length - 1)), Y = v => B - (v / 150) * (B - T);
  // m (optional, Line chart): [ideal, [line, first dot, dot step] per sprint, legend]. pl's m: a dashed series fades in at
  // m[0]; a solid one draws on as a path (cline needs pathLength) and its dots pop after it.
  const pl = (d, c, dash, m) => (m && !dash ? `<path class="cline" pathLength="1" style="--d:${m[0]}ms" d="${d.map((v, i) => `${i ? 'L' : 'M'}${X(i)},${Y(v)}`).join(' ')}"` : `<polyline${m ? ` class="a" data-a="fade" style="--d:${m[0]}ms"` : ''} points="${d.map((v, i) => `${X(i)},${Y(v)}`).join(' ')}"`) + ` fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => `<circle${m ? ` class="cdot" style="--d:${m[1] + m[2] * i}ms"` : ''} cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 50, 100, 150].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 6}" font-size="18" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 24}" font-size="18" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${pl(ideal, '#A69E92', true, m && [m[0]])}${pl(s2, '#E8402A', false, m && m[1])}${pl(s3, '#3E5CD9', false, m && m[2])}</svg>
  <div ${m ? `class="a" data-a="up" ` : ''}style="display:flex;gap:16px;font:400 18px Inter;color:#6B655C;margin:0 0 4px 46px${m ? `;--d:${m[3]}ms` : ''}"><span><b style="color:#E8402A">●</b> Sprint 2</span><span><b style="color:#3E5CD9">●</b> Sprint 3</span><span>– – ideal · day of sprint</span></div>`;
}
const ac = ['Admin-only API: 403 for non-admins, 401 anonymous', 'Team size ≥ 2, lifetime in range, team price below individual; 400 with field errors', 'Stale version → 412; every write audited', 'A configured listing can start a team', 'Contract changes additive, merged before the controller', 'Unit, PostgreSQL and authorization tests; ArchUnit green'];
// Icon card with tick rows, then the burndown as a line chart and a trailing callout (MOTION.md): card 150, header 190,
// story 230, criteria 290 +60 (tick pop, text +30), field 650; chart kicker 300, chart 330, ideal 380, actual 450,
// dots 600 +40, comment 900, callout 1000.
const s8 = () => `
<div class="card a" data-a="up" style="position:absolute;left:0;top:0;width:660px;padding:14px 20px 14px;--d:150ms"${ref('#221 (US25) · PR #242 (29 Sep)')}>
 <div ${A('up', 190, 'display:flex;justify-content:space-between;align-items:baseline;')}>${kicker('Story card')}<span style="font:600 20px Inter;color:var(--muted)">Liu Jin</span></div>
 <div ${A('up', 230, 'font:600 20px/1.3 Inter;margin-bottom:8px;')}>As an admin, I can set a listing’s team size, lifetime and team price, so that buyers can start blocs on real listings.</div>
 ${ac.map((a, i) => `<div style="display:flex;gap:8px;font:400 20px/1.28 Inter;margin-bottom:5px"><span ${A('pop', 290 + 60 * i, 'color:#3E5CD9;flex:none;')}>${icon('square-check')}</span><span ${A('up', 320 + 60 * i)}>${a}</span></div>`).join('')}
 <div ${A('up', 650, 'font:400 20px Inter;color:var(--muted);margin-top:6px;')}>Design problem field: State, Optimistic Offline Lock</div>
</div>
<div style="position:absolute;left:684px;top:0;width:480px"${ref('S2: docs/sprint2-progress-report.typ, Daily burndown data (story PR merge dates) · S3: docs/agile/sprint-03/review.md planned hours, burned on issue close dates · docs/agile/metrics/*.csv')}>
 ${gA(kicker('Burndown · story hours left'), 'up', 300)}
 ${hAt(burndown([380, [450, 600, 30], [550, 700, 30], 800]), 'fade', 330)}
 <div ${A('up', 900, 'font:400 20px/1.32 Inter;margin-top:2px;')}>${lines(['Sprint 2: flat until the first story PR on 22 Sep, then a cliff', 'Sprint 3, PRs kept small: burns from day 1, then stalls on Checkout in dev', 'Reconstructed; no hours were logged'], 4)}</div>
</div>
<div ${A('up', 1000, 'position:absolute;left:0;right:0;top:490px;border-radius:8px;background:#E3E8FB;padding:10px 16px;font:400 20px/1.35 Inter;')}${ref('EPAT 03 TDD, EPAT 05 refactoring · sprint-03/log/2026-10-07.md · PR #424 · full set: backup B1')}><b style="color:#3E5CD9">Test first, then refactor:</b> two failing tests reproduced a price-quote bug before the fix; then team admission was refactored into shared checks.</div>`;

// ---------- diagram walk-through (rubric V1, V3, V5) ----------
// The whole diagram sits in a pane for orientation; each crop is then shown 1:1 (labels ≥ 24 px) as an opaque fragment
// layer stacked over the body in order, with a short caption. Presenting, a pane fills its box along one axis and scrolls
// along the other; screenshots, the overview and the PDF scale it to fit the box, with each crop outlined and numbered,
// and the PDF carries every crop 1:1 with its caption on a detail page (D<slide>-<step>, built from CROPS).
// Every layer a slide steps through is also recorded here, so slides-backup.js can print each one as a detail page
// (the PDF cannot step through fragments).
const CROPS = [];
const CROP_H = BODY_H;
// A window onto the diagram at 1:1, cut with the root viewBox so nothing overflows the slide.
const win = (name, x, y, w, h) => svg(name).replace(/<svg\b[^>]*>/, t => t.replace(/\s(width|height|viewBox)="[^"]*"/g, '')
  .replace('<svg', `<svg width="${w}" height="${h}" viewBox="${x} ${y} ${w} ${h}" style="display:block"`));
// `head` repeats the diagram's top `head` px (a sequence diagram's lifeline headers) as a strip above the cropped area,
// so every crop still names its lifelines.
const cropView = (name, x, y, head = 0) => {
  if (!(head > 0 && y >= head)) return win(name, x, y, 1164, CROP_H);
  return `${win(name, x, 0, 1164, head)}<div style="border-top:2px dashed #A69E92"></div>${win(name, x, y, 1164, CROP_H - head - 2)}`;
};
// A crop's caption: one point per line, each [label, text] or plain text. It takes the bottom of the layer, so the crop
// keeps its top-left anchor and loses only its bottom rows.
const caption = cap => (cap && cap.length ? `<div style="flex:none;border-top:2px solid #3E5CD9;background:#E3E8FB;padding:8px 18px;font:400 20px/1.35 Inter">${cap.map(l => `<div>${Array.isArray(l) ? `<b>${l[0]}:</b> ${l[1]}` : l}</div>`).join('')}</div>` : '');
const cropInner = (name, x, y, head, cap) => `<div style="flex:1;min-height:0;overflow:hidden">${cropView(name, x, y, head)}</div>${caption(cap)}`;
const frame = inner => `<div style="position:absolute;left:0;top:0;width:1164px;height:${CROP_H}px;background:#fff;overflow:hidden;border-radius:6px;box-shadow:0 0 0 2px #1B1A18;display:flex;flex-direction:column">${inner}</div>`;
// The step badge sits in the header row, left of the speaker chip, outside the crop, so it never covers the diagram;
// its cream band hides the header strip and the previous step's badge while stepping.
const badge = (t, i) => `<div class="frag zcrop" data-frag="${i + 1}" style="position:absolute;left:470px;right:__BR__;top:${20 - BODY_TOP}px;height:30px;line-height:30px;background:var(--background);text-align:right;z-index:11"><span style="display:inline-block;vertical-align:middle;font:700 16px/1 Inter;letter-spacing:.03em;background:#1B1A18;color:#fff;border-radius:999px;padding:5px 12px">${t}</span></div>`;
// A detail layer: step i of n, covering the body while presenting; recorded for its D page.
const layer = (inner, label, i, n) => {
  CROPS.push({ inner, label, i, n });
  return `<div class="frag zcrop" data-frag="${i + 1}" style="position:absolute;left:0;top:0;z-index:10">${frame(inner)}</div>
${badge(`${i + 1}/${n} · ${label}`, i)}`;
};
const pc = v => `${(v * 100).toFixed(3)}%`;
// Numbered dashed outlines for the crops a pane steps through (rubric V1: whole diagram first, then the part under
// discussion; V5: outline, not a laser pointer). Placed in percent, so they follow the pane at any scale.
// od, os (optional): outline k fades in at od + (k - 1) · os ms (Walked diagram).
const outlines = (crops, W, H, od, os) => crops.map(c => `<div${od === undefined ? '' : ' class="a" data-a="fade"'} style="${od === undefined ? '' : `--d:${od + (c.k - 1) * os}ms;`}position:absolute;left:${pc(c.x / W)};top:${pc(c.y / H)};width:${pc(Math.min(1164, W - c.x) / W)};height:${pc(Math.min(CROP_H, H - c.y) / H)};border:2px dashed #E8402A;border-radius:3px;box-sizing:border-box"><span style="position:absolute;left:-2px;top:-2px;font:700 13px/1 Inter;background:#E8402A;color:#fff;padding:3px 6px;border-radius:3px 0 3px 0">${c.k}</span></div>`).join('');
let paneN = 0;
// pane: the whole diagram in a box of box[0] × box[1] px at `at`. Presenting, axis 'y' fills the box width and scrolls
// vertically, axis 'x' fills its height and scrolls horizontally; otherwise the diagram is scaled to fit the box.
// Motion, all optional (Walked diagram): d = the pane's entrance delay, pa its data-a (default fade); od, os = the
// outline rhythm (see outlines); fx = a transform of this pane's copy of the SVG markup only (never the crops).
const pane = ({ name, W, H, box, at = 'left:0;top:0', axis = 'y', d, pa = 'fade', od, os, fx = x => x }, crops) => {
  const s = Math.min(box[0] / W, box[1] / H), fw = Math.round(W * s), fh = Math.round(H * s);
  const pw = axis === 'y' ? box[0] : Math.round(W * box[1] / H), ph = axis === 'y' ? Math.round(H * box[0] / W) : box[1];
  const id = `pane${++paneN}`, other = axis === 'y' ? 'x' : 'y';
  return `<style>.${id}{width:${fw}px;height:${fh}px;overflow:hidden}.${id}>div{width:${fw}px;height:${fh}px}
body.present .${id}{width:${Math.min(box[0], pw)}px;height:${Math.min(box[1], ph)}px;overflow-${axis}:auto;overflow-${other}:hidden}body.present .${id}>div{width:${pw}px;height:${ph}px}</style>
<div class="${id}${d === undefined ? '' : ' a'}"${d === undefined ? '' : ` data-a="${pa}"`} style="position:absolute;${at};background:#fff;border-radius:6px;box-shadow:0 0 0 1.5px #DDD5C8${d === undefined ? '' : `;--d:${d}ms`}"><div style="position:relative">${fx(svg(name)).replace('<svg ', '<svg style="display:block;width:100%;height:100%" ')}${outlines(crops, W, H, od, os)}</div></div>`;
};
// Diagrams on a slide: each view is a pane; crops [name, x, y, label, head, caption] step through any of them 1:1 in
// the order given.
const walk = (views, crops) => `<style>body:not(.present) .zcrop{display:none}</style>
${views.map(v => pane(v, crops.map(([n, x, y], k) => ({ n, x, y, k: k + 1 })).filter(c => c.n === v.name))).join('')}
${crops.map(([name, x, y, label, head, cap], i) => layer(cropInner(name, x, y, head, cap), label, i, crops.length)).join('')}`;
// Walked diagram (MOTION.md): the pane fades in at 200 ms (a second pane 110 ms later), then each crop outline fades
// in from 500 ms, 70 ms apart, numbered slide-wide; the crop layers and step badges stay plain fragments.
const WALK = { d: 200, od: 500, os: 70 };
const one = (name, W, H, box, crops, opts = {}) => walk([{ name, W, H, box, ...opts }], crops.map(c => [name, ...c]));

// ---------- slide 9 (2.1) ----------
const dec9 = [['Modular monolith: COLA layers, six contexts', 'skeleton plan D1, D4'], ['Framework-free domain; 17 ArchUnit rules', 'skeleton plan D1 · no ADR'], ['Consequences run before the commit', 'ADR 0002'], ['Cross-service events through an outbox', 'ADR 0007'], ['One codebase, two topologies', 'ADR 0008'], ['Router owns URL state; generated client', 'frontend ADR 0001, 0003']];
const s9 = () => `
${one('logical-layers', 2320, 1644, [764, 568], [
  [1160, 30, 'adapter · contract', 0, [['Adapter', 'REST controllers return one Result envelope against the committed OpenAPI contract']]],
  [1160, 548, 'application', 0, [['Application', 'services run the use cases, one package per bounded context']]],
  [640, 1116, 'infrastructure realises the domain ports', 0, [['Domain', 'depends on nothing; it names its needs as ports'], ['Infrastructure', 'implements those ports; 17 ArchUnit rules keep it that way']]],
], WALK)}
<div style="position:absolute;left:784px;top:0;width:380px"${ref('4+1 logical view · SAD 04a p24–29 · skeleton plan = backend-skeleton-plan.md D1–D20 (30 Aug), locked before numbered ADRs')}>
 ${gA(kicker('Decisions that shape it'), 'up', 260)}
 ${dec9.map((r, i) => `<div ${A('up', 300 + 50 * i, 'border-top:1.5px solid #DDD5C8;padding:5px 0 6px;')}><div style="font:400 19px/1.25 Inter">${r[0]}</div><div style="font:600 18px/1.25 Inter;color:#6B655C">${r[1]}</div></div>`).join('')}
 <div ${A('up', 620, 'margin-top:8px;')}>${kicker('Platform')}</div>
 <div ${A('up', 660, 'font:400 18px/1.32 Inter;')}>${lines(['Java 21, Spring Boot 4, MyBatis, Sa-Token', 'PostgreSQL 18, Redis 8, Stripe Checkout', 'React 19, TanStack, TypeScript', 'AWS EC2, S3, CloudFront'], 0)}</div>
</div>
${repTop('Report §3.1 · Liu Jin, presented by Eric')}`;

// ---------- slide 10 (2.2) ----------
const strat = [
  ['Language, framework', 'maintainability', '«control», «entity» · all', `Spring ${mo('@Service', 19)} by constructor injection; plain Java domain`, 'D1–D20, ArchUnit · no ADR'],
  ['Presentation', 'usability', 'GUI «boundary» · UC1–UC4, UC7–UC9', 'TanStack route + component + Query hook', 'F-ADR 0001, 0009'],
  ['Distribution', 'interoperability', '«control» · all', `${mo('@RestController', 19)} returning ${mo('Result', 19)}; generated client as stub`, 'D11–D12, F-ADR 0003'],
  ['Persistence', 'integrity', '«entity» · all', 'Aggregate + repository port, implemented with MyBatis', 'D1, D9 · no ADR'],
  ['External interface', 'provider isolation', 'system «boundary» · UC2–UC6', 'Gateway port + Stripe / Google adapter; signed webhook', 'ADR 0001, 0004, 0005'],
  ['Security', 'security', 'every «control» · all', `Login interceptor; ${mo('checkRole(ADMIN)', 19)}; CSRF header; rate limits`, 'D6–D7, F-ADR 0002'],
  ['Scheduling', 'expiry ≤ 1 min', 'Scheduler actor · UC5, UC6', `${mo('@Scheduled', 19)} job under a database lease, every minute`, 'ADR 0002 · job: no ADR'],
  ['Concurrency', 'no oversell', '«entity» with capacity · UC4', 'Row locks in a fixed order + guarded UPDATE + unique indexes', 'ADR 0001 · no ADR'],
  ['Domain events', 'decoupling', '«control» · UC5–UC7', 'Event record + transactional listener; outbox across services', 'ADR 0002, 0003, 0007'],
];
// Rows appear in three groups of three (rubric V1); the PDF shows all nine. Table with rows, then Fragment row group
// (MOTION.md): table 200, visible rows 320 +38; each revealed row is itself a fragment (frag a up), 0 / 40 / 80 ms
// after the click.
const s10 = () => `
<table class="t a" data-a="fade" style="font-size:20px;line-height:1.17;--d:200ms"${ref('Template: SAD 04a p36–37')}><tr>${th('Strategy · concern', 248)}${th('Object types · use cases', 318)}${th('Become in design')}</tr>
${strat.map((r, i) => `<tr${ref(r[4])}${i >= 3 ? ` class="frag a" data-a="up" data-frag="${Math.floor(i / 3)}" style="--d:${(i % 3) * 40}ms"` : ` class="trow" style="--d:${320 + 38 * i}ms"`}><td style="padding:3px 10px"><b>${r[0]}</b><br><span style="color:var(--muted)">${r[1]}</span></td><td style="padding:3px 10px">${r[2]}</td><td style="padding:3px 10px">${r[3]}</td></tr>`).join('')}</table>
${repTop('Report §3.2 · Yorik, Eric')}`;

// ---------- slide 11 (2.2) ----------
const typ = [['GUI «boundary»', 'Route, component and Query hook'], ['«control»', 'REST controller + application service, called through the generated client'], ['«entity»', 'Aggregate behind a repository port, implemented with MyBatis'], ['External «boundary»', 'Gateway port + adapter']];
// Mapping rows (MOTION.md): kicker 180; per row from 240, +80: label up, arrow fade +60, target left +100. The
// fragments stay plain.
const s11 = () => `
<div style="height:8px"></div>${gA(kicker('Class diagrams: each analysis type becomes design classes'), 'up', 180)}
${typ.map((t, i) => `<div style="display:flex;gap:16px;align-items:baseline;font:400 22px/1.3 Inter;margin-bottom:10px"><b ${A('up', 240 + 80 * i, 'width:250px;flex:none;')}>${t[0]}</b><span ${A('fade', 300 + 80 * i, 'color:#E8402A;font-weight:700;')}>→</span><span ${A('left', 340 + 80 * i)}>${t[1]}</span></div>`).join('')}
<div class="frag" data-frag="1" style="margin-top:22px">
 ${kicker('Sequence diagrams: a plain call becomes a guarded transaction')}
 <div style="display:flex;gap:16px;align-items:baseline;font:400 22px/1.4 Inter;margin-bottom:8px"><b style="width:250px;flex:none">Analysis</b>${mo('control → entity.update()', 21)}</div>
 <div style="display:flex;gap:16px;align-items:baseline;font:400 22px/1.4 Inter"><b style="width:250px;flex:none">Design</b><div>${lines([mo('service → repository.lockDeal()', 21) + ' <span style="color:var(--muted)">(SELECT … FOR UPDATE)</span>', mo('→ aggregate.joinPaid() → guarded UPDATE', 21), 'all in one transaction'], 2)}</div></div>
</div>
<div class="frag" data-frag="2" style="position:absolute;left:0;right:0;top:470px;border-radius:8px;background:#E3E8FB;padding:12px 18px;font:400 22px/1.35 Inter"><b style="color:#3E5CD9">Why:</b> the domain names its needs as interfaces and infrastructure supplies them, so persistence and providers can change without touching the rules.</div>
${repTop('Report §3.2 · transition strategies')}`;

// ---------- slide 12 (2.2) ----------
const s12 = () => `
${one('transition-pair', 2320, 2270, [1164, 568], [
  [0, 0, 'analysis: UC4', 0, [['Analysis classes', 'boundary, control and entity from slide 14; no platform yet']]],
  [1160, 20, 'strategy key', 0, [['Strategy key', 'each tag names the transition strategy that produced a design class']]],
  [600, 1684, 'design classes · persistence', 0, [['Design classes', 'the Team becomes the GroupDeal aggregate, reached through its repository port'], ['◁ means “realises”', 'MyBatis classes in infrastructure implement that port']]],
], WALK)}
${repTop('Report §3.2 · transition strategies')}`;

// ---------- slide 13 (2.3) ----------
const s13 = () => `
${one('use-case', 2320, 1096, [1164, 568], [
  [0, 364, 'Buyer and the money path', 0, [['In scope', 'start and join «include» paying upfront; invite attribution «extend»s join']]],
  [560, 40, 'resolution: Scheduler', 0, [['Scheduler', 'a time-driven actor resolves teams'], ['«extend»', 'refund extends resolution when a team expires']]],
  [560, 290, 'refund, payment: Stripe', 0, [['Stripe', 'a secondary actor of refund and of paying upfront']]],
  [1160, 40, 'out of scope', 0, [['Out of scope, greyed', 'as the proposal excluded']]],
  [1160, 640, 'delivery status', 0, [['Status badges', 'paid start and expiry refund on dev since 23 Oct; a second buyer’s join not yet']]],
], WALK)}
${repTop('Report §3.3 · use case diagram and specifications')}`;

// ---------- slide 14 (2.4) ----------
const s14 = () => `
${walk([{ name: 'uc4-analysis-class', W: 1160, H: 1070, box: [566, 568], at: 'left:0;top:0', ...WALK, d: 150 }, { name: 'uc4-analysis-seq', W: 2320, H: 2186, box: [582, 568], at: 'left:582px;top:0', ...WALK, d: 260 }],
  [['uc4-analysis-class', 0, 550, 'entities', 0, [['Entities only', 'attributes sit on entities: the Team holds its size and deadline']]],
   ['uc4-analysis-seq', 560, 1150, 'seat available: join', 160, [['Seat available', 'joining creates a membership while the window is open']]],
   ['uc4-analysis-seq', 1160, 1720, 'else: refund through the gateway', 160, [['Otherwise', 'the payment is refunded through the payment gateway']]]])}
${repTop('Report §3.4 · Eric (UC4 analysis model)')}`;

// ---------- slide 15 (2.4) ----------
const tagKey = [['Pres', 'presentation'], ['Dist', 'distribution'], ['Pers', 'persistence'], ['Ext', 'external interface'], ['Sec', 'security'], ['Conc', 'concurrency'], ['Sched', 'scheduling'], ['Evt', 'domain events'], ['Lang', 'language, framework']];
const s15 = () => `
${one('uc4-design-class', 2320, 2314, [944, 568], [
  [1160, 0, 'payment meets groupbuy through a port', 0, [['Context seam', 'payment reaches groupbuy only through a port']]],
  [1160, 1110, 'aggregate behind its repository port', 0, [['Persistence strategy', 'the GroupDeal aggregate sits behind its repository port']]],
  [1160, 1650, 'MyBatis implementation of the port', 0, [['Infrastructure', 'the repository implementation and its MyBatis mapper']]],
], WALK)}
<div style="position:absolute;left:964px;top:0;width:200px"${ref('Typed signatures, visibility and multiplicities: SAD 01b p14–16 · tags: slide 10')}>
 ${gA(kicker('Strategy tags'), 'up', 260)}
 ${tagKey.map((t, i) => `<div ${A('up', 290 + 30 * i, 'font:400 19px/1.4 Inter;')}><b>${t[0]}</b> ${t[1]}</div>`).join('')}
 <div ${A('up', 600, 'font:400 19px/1.35 Inter;margin-top:12px;color:var(--muted);')}>Typed signatures, visibility and multiplicities</div>
</div>
${repTop('Report §3.4 · Eric')}`;

// ---------- slide 16 (2.4) ----------
const s16 = () => `
${one('uc4-design-seq', 2320, 3306, [1164, 568], [
  [700, 772, 'team row locked first', 176, [['One transaction', 'after the webhook’s signature check; drawn as critical, as UML has no transaction fragment'], ['Lock order', 'team row, intent, then payment; a racing join waits on the team row']]],
  [1100, 1926, 'guarded update · last seat', 176, [['Guarded update', mo('UPDATE … WHERE current_size = expected AND status = open', 19)], ['Last seat', `the opt [teamCompleted] branch publishes ${mo('TeamSucceeded', 19)}`]]],
  [1100, 2500, 'loser: compensation, refund owed', 176, [['Seat gone', 'DEAL_FULL or DEAL_CLOSED: the receipt owes a refund, never an extra member'], ['Evidence', '20 paid buyers race for 1 seat: 19 refunds owed (local integration test)']]],
], WALK)}
<span${ref('StripeLocalIntegrationTest.twentyBuyersRaceForLastSeatAndNineteenRefundObligationsPersist (local, PostgreSQL in Testcontainers) · READ COMMITTED, 10 s')}></span>
${repTop('Report §3.4 · Eric')}`;

// ---------- slide 17 (2.4) ----------
// Inline state diagram in the pane's copy only (fx): the three transitions draw on at 250 / 320 / 390 ms, their labels
// fade in 60 ms later; the build fails if the drawing no longer has these elements.
const s17fx = g => {
  const sub = (re, fn, n) => { let k = 0; const r = g.replace(re, (...m) => { k++; return fn(...m); }); if (k !== n) throw new Error(`slide 17 fx: ${re} matched ${k}, expected ${n}`); g = r; };
  const cn = ms => `class="cn" pathLength="1" style="stroke-linecap:butt;--d:${ms}ms" `, fade = ms => `class="a" data-a="fade" style="--d:${ms}ms" `;
  sub(/<line (?=x1="57" y1="250")/g, () => `<line ${cn(250)}`, 1);
  sub(/<line (?=x1="1136" y1="118")/g, () => `<line ${cn(320)}`, 1);
  sub(/<line (?=x1="1136" y1="470")/g, () => `<line ${cn(390)}`, 1);
  sub(/<text (?=x="7[04]" )/g, () => `<text ${fade(310)}`, 4);
  sub(/<text (?=x="1168" y="(\d+)")/g, (m, y) => `<text ${fade(+y < 300 ? 380 : 450)}`, 9);
  return g;
};
const s17 = () => `
${one('team-statechart', 2320, 548, [1164, 275], [
  [0, 60, 'OPEN and its joins', 0, [['Guards', `price → new member → window → seat (${mo('GroupDeal.joinPaid', 19)})`], ['Code it matches', `${mo('DealStatus.canTransitionTo', 19)}: only OPEN moves, never to itself`], ['Greyed', 'FAILED and CANCELED are declared but never written']]],
  [1160, 20, 'succeeded or expired', 0, [['Time event', `${mo('at(expireTime)', 19)}, found by the expiry sweep every minute`]]],
], { ...WALK, fx: s17fx })}
<div style="position:absolute;left:0;top:300px;width:1164px"${ref('SAD 03c p4, p11')}>
 ${gA(point('Why a statechart', 'Team state gates four use cases: UC4 join, UC5 resolution, UC6 refund and UC8 my teams.', 24), 'up', 640)}
 ${gA(point('In code', `${mo('DealStatus', 22)} and the guards of ${mo('GroupDeal', 22)}`, 24), 'up', 700)}
</div>
${repTop('Report §3.4 · Eric (team statechart)')}`;

// ---------- slides 18–21 (2.5): one 20 s slot per member ----------
// Each slot: the problem, what varies and the pattern beside the after-diagram; the other SDP fields (SDP 06 design
// case study p10) move into the captions of its crops: crop 1 the class structure, crop 2 the collaboration.
const pslot = (rows, name, W, H, crops, rep, refs) => `
<div style="position:absolute;left:0;top:0;width:610px"${ref(refs)}>
 ${rows.map(r => point(r[0], r[1], 22, 16)).join('')}
</div>
${one(name, W, H, [530, 568], crops, { at: 'left:634px;top:0' })}
${repTop(rep)}`;
const s18 = () => pslot([
  ['Where it shows', 'The «create» of a team: the service once named the concrete creator itself'],
  ['Cause for redesign', 'Creating an object by specifying its class explicitly'],
  ['What varies', 'The class of the team that is created'],
  ['Pattern', '<b>Factory Method</b>'],
], 'fm-start', 1160, 1400, [
  [0, -24, 'Client · Creator', 0, [['Client', 'the service depends only on the abstract creator'], ['Needed because', 'the proposal makes new kinds of team, without editing the service, a design driver']]],
  [0, 520, 'ConcreteCreator · Product', 0, [['Participants', `Creator ${mo('PaidGroupDealCreator', 19)}, ConcreteCreator ${mo('StandardPaidGroupDealCreator', 19)}, Product ${mo('GroupDeal', 19)}`], ['Candidates', 'Factory Method, static factory, Builder, Abstract Factory']]],
  [0, 990, 'initiate → createGroupDeal', 0, [['Why', `the final ${mo('initiate', 19)} validates, then calls ${mo('createGroupDeal', 19)}: every creator keeps the rules`], ['Liability', 'one creator and one team class today'], ['Tested by', mo('PaidGroupDealServiceTest.startUsesTheInjectedCreator', 19)]]],
], 'Report §3.5 · Eric (UC3)', 'SDP 05a p13, p15, p35 · SDP 01a p34 · PR #424 · proposal §6.3');
const s19 = () => pslot([
  ['Where it shows', 'In the expiry sequence: it called the order and refund code inline'],
  ['Cause for redesign', 'Tight coupling: more and more objects depend on a team’s outcome'],
  ['What varies', 'The objects that depend on a team’s outcome'],
  ['Pattern', '<b>Observer</b>'],
], 'observer-expiry', 1160, 1870, [
  [0, 340, 'Subject · Observer · ConcreteObserver adapter', 0, [['Subject', `Spring’s ${mo('ApplicationEventPublisher', 19)} and its multicaster`], ['ConcreteObserver', 'Spring’s adapter, one per subscriber method'], ['Candidates', 'Observer, Mediator, direct calls, a message broker']]],
  [0, 1364, 'publish → before commit, in order', 0, [['Before commit, in order', `${mo('OrderLifecycleSubscriber', 19)} (10), then ${mo('TeamExpiryRefundSubscriber', 19)} (20)`], ['Liability', 'implicit control flow; a wrong phase is a silent bug'], ['Tested by', `${mo('TeamExpiryRefundSubscriberTest', 19)}: a failure rolls the expiry back`]]],
], 'Report §3.5 · Yorik (UC6)', 'ADR 0002 · SDP 05a p15 · proposal §6.3');
const s20 = () => pslot([
  ['Where it shows', 'In the class diagram: the SQS and SNS transports would each copy the batch loop'],
  ['Cause for redesign', 'Algorithmic dependencies: the same steps, two providers'],
  ['What varies', 'Steps of an algorithm'],
  ['Pattern', '<b>Template Method</b>'],
], 'template-method-outbox', 1160, 1724, [
  [0, 290, 'template method', 0, [['AbstractClass', `${mo('BatchingOutboxEventTransport&lt;E&gt;', 19)}: the final ${mo('publish', 19)} runs the loop`], ['Candidates', 'Template Method; Strategy (the whole algorithm does not vary); an adapter per SDK']]],
  [0, 1176, 'publish calls entry, send', 0, [['Primitive operations', `${mo('entry', 19)} and ${mo('send', 19)}: each transport implements only these`], ['Liability', 'a subclass cannot change the loop'], ['Tested by', `${mo('OutboxBatchFailureTest', 19)}, both providers`]]],
], 'Report §3.5 · Pan (notifications)', 'ADR 0007 · SDP 05a p12–14');
const s21 = () => pslot([
  ['Where it shows', 'In the class diagram: browse and the team quote each switch on the pricing mode'],
  ['Cause for redesign', 'Algorithmic dependencies: the arithmetic sits inline in both'],
  ['What varies', 'An algorithm: how a price is computed'],
  ['Pattern', '<b>Strategy</b>'],
], 'strategy-pricing', 1160, 1770, [
  [0, 330, 'resolver · Strategy', 0, [['Participants', `Strategy ${mo('PricingStrategy', 19)}, picked by a resolver`], ['Contexts', 'the product and team-price services'], ['Candidates', 'Strategy, Template Method (no shared skeleton), Decorator']]],
  [0, 1298, 'quote → resolve → strategy', 0, [['Delegation', `each context hands the arithmetic to the ConcreteStrategy ${mo('DualPricingStrategy', 19)}`], ['Liability', 'one strategy today; new pricing schemes are a design driver'], ['Tested by', `${mo('PricingStrategyResolverTest', 19)}, ${mo('DualPricingStrategyTest', 19)}`]]],
], 'Report §3.5 · Lim (UC1)', 'SDP 05a p35 · proposal §6.3 · team-price-quotes.md L55');

// ---------- slides 22–23 (2.5): the full-form problem ----------
const s22 = () => `
<div style="position:absolute;left:0;top:0;width:590px"${ref('RT 3.5 brute force · SDP 05a p4, p13 · admin-listings.md L67–68')}>
 ${point('Problem (UC9)', 'Publish, delist, revise and delete are legal in different states of a listing', 22, 14)}
 ${point('Why redesign', lines(['Hard to maintain: one rule in four places', 'Hard to extend: a new state edits every switch', 'Hard to reuse: the rule cannot be tested apart from Product']), 22, 14)}
 ${point('What varies', 'The states of an object', 22, 14)}
 ${point('Candidates', lines(['<b>State</b>', 'Strategy: nobody chooses; the state decides', 'Transition table, as on the team']), 22, 0)}
</div>
${one('state-before', 1160, 1120, [552, 568], [
  [0, 0, 'brute force: class', 0, [['Brute force', `the same ${mo('switch (status)', 19)} in four operations of ${mo('Product', 19)}, in red`]]],
  [0, 570, 'brute force: sequence', 0, [['In the sequence', `${mo('publish()', 19)} branches on the listing’s status`]]],
], { at: 'left:612px;top:0' })}
${repTop('Report §3.5 · Liu Jin (UC9)')}`;
const s23 = () => `
<div style="position:absolute;left:0;top:0;width:600px"${ref('GoF State implementation 2 · SDP 05a p35 · slide 4')}>
 ${point('Pattern', '<b>State</b>: the listing delegates to its state', 22, 16)}
 ${point('Implementation decisions', lines(['The state returns its successor; the listing applies it', 'Moves a state does not allow refuse with 409', 'Each move makes a new revision; a stale write gets 412']), 22, 16)}
 ${point('Why the team uses a table instead', 'Only OPEN has behaviour; SUCCEEDED and EXPIRED are final, so a transition table suffices', 22, 0)}
</div>
${one('state-after', 1160, 1424, [540, 568], [
  [0, 64, 'State · ConcreteStates', 0, [['Participants', `Context ${mo('Product', 19)}, State ${mo('ProductStatus', 19)}`], ['ConcreteStates', 'DRAFT, ON_SALE, OFF_SALE: enum constants with bodies, one subclass each']]],
  [0, 876, 'after: publish delegates', 0, [['No switch', 'each state answers publish its own way'], ['Liability', 'a new state still needs a constant, its overrides and tests']]],
], { at: 'left:624px;top:0' })}
${repTop('Report §3.5 · Liu Jin (UC9)')}`;

// ---------- slide 24 (2.6) ----------
const s24 = () => `
${one('core-erd', 2320, 1190, [1164, 568], [
  [580, 54, 'deal · membership', 0, [['Keys', 'UUIDv7 surrogate primary keys from PostgreSQL 18'], ['Race backstop', 'one membership per buyer per team, by a unique index']]],
  [1160, 600, 'payment · refund', 0, [['Race backstop', 'one refund per payment, by a unique key'], ['Associations', 'logical FK columns checked by the application: our DDL convention, not a course rule'], ['Aggregates', 'one table per aggregate root; nothing inherited, so no inheritance mapping']]],
])}
<span${ref('Mapping decisions: SAD 04a p53–61 · Flyway V1–V28 · all tables: backup B4')}></span>
${repTop('Report §3.6 · Liu Jin')}`;

// ---------- slide 25 (3.1, 3.3) ----------
// Agile Testing Quadrants, laid out as in the course: business-facing on top, supporting the team on the left. The
// overview names the test types; each click opens one quadrant in full (a detail layer, also a D page in the PDF).
const lvl = t => `<span style="display:inline-block;font:600 18px/1 Inter;border:1.5px solid #6B655C;color:#1B1A18;border-radius:999px;padding:3px 9px;margin-left:8px;vertical-align:2px">${t}</span>`;
const QUADS = [
  { q: 'Q1', axes: 'technology-facing · supporting the team', how: 'automated', at: 'left:0;top:282px', types: [['Unit, component', 'unit'], ['Integration', 'integration'], ['Contract, ArchUnit', 'integration']],
    detail: [
      ['Unit, component', 'unit', ['JUnit: 162 of 185 test classes', 'Vitest: 77 files']],
      ['Integration', 'integration', ['Testcontainers: 45 of 49 classes, on real PostgreSQL, Redis and ElasticMQ']],
      ['Contract, ArchUnit', 'integration', ['openapi-diff on the committed contract', '17 architecture rules']],
    ],
    runs: `${mo('build-quality', 19)}, ${mo('frontend-check', 19)} (required), ${mo('contract-check', 19)}`,
    caught: 'two red tests reproduced the join-quote defect before its fix',
    extra: `<div style="margin-top:12px;border-radius:8px;background:#E3E8FB;padding:8px 16px"><div style="font:700 20px Inter;color:#3E5CD9;margin-bottom:2px">Complex classes under test</div>
      ${[['Team state machine', `${mo('DealStatusTest', 19)}, ${mo('PaidGroupDealTest', 19)}`], ['Last-seat capacity', `${mo('StripeLocalIntegrationTest.twentyBuyersRaceForLastSeat…', 19)} (integration)`], ['Refund idempotency', mo('RefundServiceTest.lostResponseRetriesSameDurableKey', 19)]]
        .map(r => `<div style="display:flex;gap:12px;font:400 20px/1.32 Inter"><b style="width:224px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('')}</div>` },
  { q: 'Q2', axes: 'business-facing · supporting the team', how: 'automated', at: 'left:0;top:0', types: [['Story acceptance', 'system'], ['End-to-end', 'system']],
    detail: [
      ['Story acceptance', 'system', [`${mo('StripeRolloutAcceptanceIT', 19)}: real HTTP, scheduler, signed webhooks`]],
      ['End-to-end', 'system', ['Playwright: 8 specs, on the preview build and on dev after each deploy']],
    ],
    runs: `${mo('build-quality', 19)}, ${mo('e2e-preview', 19)}, ${mo('e2e-dev', 19)}`,
    caught: 'a 502 on e2e-dev during a deploy (3–4 Oct): one host' },
  { q: 'Q3', axes: 'business-facing · critiquing the product', how: 'manual', at: 'left:590px;top:0', types: [['Exploratory, acceptance', 'system']], note: 'Not done: outside users',
    detail: [
      ['Exploratory, acceptance', 'system', ['By hand, logged', 'Stripe Sandbox START and JOIN in a browser (20 Sep, local)', 'Sign-in cookie Secure and HttpOnly on dev (8 Oct)']],
    ],
    caught: 'no defect logged from these runs', gap: 'Not done: acceptance testing with outside users' },
  { q: 'Q4', axes: 'technology-facing · critiquing the product', how: 'tools', at: 'left:590px;top:282px', types: [['Performance, load', 'system'], ['Security scanning', 'types']],
    detail: [
      ['Performance, load', 'system', ['k6 on the SWE5001 ECS environment, by hand (next slide)']],
      ['Security scanning', 'types', ['Static analysis (SAST)', 'Dependencies (SCA)', 'Image and IaC configuration', 'Secrets', 'Dynamic, passive (DAST): OWASP ZAP on anonymous dev pages, advisory']],
    ],
    runs: `${mo('security.yml', 19)}, ${mo('build-quality', 19)}, ${mo('docker', 19)} gate the pipeline; ${mo('dast-baseline', 19)} runs weekly, advisory (slide 30)`,
    caught: 'full-history gitleaks: 24 matches, 4 badge tokens still open (slide 32); ZAP on dev: 2 Medium, 4 Low CSP and header findings (22 Oct)' },
];
const quadHead = (d, fs) => `<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:8px"><span style="font:700 ${fs}px Inter">${d.q} <span style="font-weight:500;color:var(--muted)">${d.axes}</span></span><span style="font:600 ${fs - 2}px Inter;color:var(--muted)">${d.how}</span></div>`;
const quadCard = d => `<div class="card" style="position:absolute;${d.at};width:574px;height:266px;padding:12px 18px">
 ${quadHead(d, 20)}
 ${d.types.map(t => `<div style="font:600 24px/1.3 Inter;margin-bottom:10px">${t[0]}${lvl(t[1])}</div>`).join('')}
 ${d.note ? `<div style="font:400 22px/1.3 Inter;color:#B02E1B">${d.note}</div>` : ''}
</div>`;
const quadDetail = d => `<div style="padding:12px 22px">
 ${quadHead(d, 22)}
 ${d.detail.map(r => `<div style="margin-bottom:8px"><div style="font:600 22px/1.3 Inter">${r[0]}${lvl(r[1])}</div><div style="font:400 20px/1.32 Inter;margin-top:2px">${lines(r[2], 0)}</div></div>`).join('')}
 ${d.runs ? `<div style="font:400 20px/1.32 Inter;color:var(--muted)">Runs in ${d.runs}</div>` : ''}
 <div style="font:400 20px/1.32 Inter;margin-top:4px"><b style="color:#B02E1B">Caught:</b> ${d.caught}</div>
 ${d.gap ? `<div style="font:400 20px/1.32 Inter;color:#B02E1B;margin-top:4px">${d.gap}</div>` : ''}
 ${d.extra || ''}
</div>`;
const s25 = () => `<style>body:not(.present) .zcrop{display:none}</style><div${ref('Quadrants: EPAT 03 TDD p7 · levels: EPAT 03 TDD p5')}>
${QUADS.map(quadCard).join('')}</div>
${QUADS.map((d, i) => layer(quadDetail(d), `${d.q} · ${d.axes}`, i, QUADS.length)).join('')}`;

// ---------- slide 26 (3.2) ----------
const kv = rows => rows.map(r => `<div style="display:flex;gap:14px;font:400 21px/1.35 Inter;margin-bottom:14px"><b style="width:130px;flex:none">${r[0]}</b><div>${Array.isArray(r[1]) ? lines(r[1], 0) : r[1]}</div></div>`).join('');
const s26 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:640px;height:568px;padding:18px 22px">
 ${kicker('The scripted test')}
 ${kv([
   ['Tool', `<span${ref('perf/k6/browse.js · perf/k6/README.md §V2')}>k6 1.6.1, image pinned by digest</span>`],
   ['Target', ['<b>SWE5001 ECS</b>, dev image of 6 Oct: CloudFront → ALB → Fargate', 'Never the shared SWE5006 dev host, which also runs its database']],
   ['Load', ['Open arrival rate 10 → 200 requests/s', 'Ten 60 s steps; 100 VUs preallocated, 400 max']],
   ['Mix', ['7 listing pages : 3 listing details, anonymous', 'No think time; 10 s timeout']],
   ['Pass, per step', ['p95 &lt; 500 ms', 'Errors &lt; 1%', 'Zero dropped requests']],
   ['Result', `<span${ref('docs/swe5001/evidence/20261016-http-baseline-dashboard/ (#670)')}>Run 20261016-v2-monolith-repeat, 16 Oct; shown on slide 36</span>`],
 ])}
</div>
<div style="position:absolute;left:660px;top:0;width:504px">
 <div class="card" style="padding:18px 22px;margin-bottom:14px">
  ${kicker('Proposal target (§6.3)', '#B02E1B')}
  <div style="font:600 22px/1.35 Inter">Interactive operations ≤ 2 s with up to 100 concurrent users</div>
  <div style="font:400 21px/1.4 Inter;margin-top:10px">Little’s law ${mo('N = X · (R + Z)', 20)}: 100 users, 0.5 s think, ~30 ms response → <b>X ≈ 190 requests/s</b></div>
  <div style="font:400 21px/1.4 Inter;margin-top:6px">Inside the 200 driven, with a p95 gate four times stricter than 2 s</div>
  <div style="font:400 20px/1.35 Inter;color:var(--muted);margin-top:6px">The 0.5 s think time is our assumption</div>
 </div>
 <div class="card" style="padding:18px 22px">
  ${kicker('Expiry ≤ 1 min after the window', '#B02E1B')}
  <div style="font:400 21px/1.4 Inter"${ref('docs/perf/sprint3-baseline.md')}>Measured <b>locally</b> under 100-user load (30 Sep): 30 teams, worst lag 58.2 s</div>
  <div style="font:400 20px/1.35 Inter;color:var(--muted);margin-top:6px">On ECS: not run, so this evidence is local only</div>
 </div>
</div>`;

// ---------- slides 27–28 (4.2) ----------
const tree = [['backend/', 'Maven COLA modules; SWE5001 services/'], ['frontend/', 'React SPA'], ['contracts/', 'openapi.json, the API contract'], ['deployment/', 'Compose, SSM bundle, deploy.sh'], ['infra/', 'Terraform: dev frontend, SWE5001'], ['perf/', 'k6 load tests'], ['docs/', 'ADRs, sprint logs'], ['.github/', 'workflows, CODEOWNERS, Dependabot']];
const s27 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:520px;height:568px;padding:16px 22px">
 ${kicker('One monorepo')}
 ${tree.map(t => `<div style="display:flex;gap:12px;font:400 21px/1.3 Inter;margin-bottom:9px"><span style="width:160px;flex:none">${mo(t[0], 21)}</span><span style="color:var(--muted)">${t[1]}</span></div>`).join('')}
</div>
<div style="position:absolute;left:544px;top:0;width:620px">
 <div class="card" style="padding:16px 22px;margin-bottom:14px"${ref('EPAT 07 CI p41–44')}>
  ${kicker('Branching: Branch by Feature')}
  <div style="font:400 21px/1.35 Inter">${lines(['One short branch per story', 'A pull request, then a squash merge onto the trunk, <b>dev</b>', 'Linear history since 12 Sep', 'Up to date with dev before merging, instead of merging the trunk daily'], 4)}</div>
 </div>
 <div class="card" style="padding:16px 22px"${ref('ruleset 22734681 · CODEOWNERS (payment and groupbuy owners: PR #669, merged 23 Oct) · docs/deploy/aws-account-governance/README.md')}>
  ${kicker('Who may change it')}
  <div style="font:400 21px/1.35 Inter">${lines([`GitHub: a <b>code owner</b> approves; six checks green`, 'Owners: backend Liu Jin, frontend Lim, CI/CD Pan', 'Payments, refunds Yorik; group buying Eric', 'AWS: IAM Identity Center, MFA enforced, one-hour sessions', 'CI: one OIDC role per job, no AWS keys in GitHub', '<span style="color:#B02E1B">Exception: two IAM users for SSM port forwarding, one with an access key</span>'], 4)}</div>
 </div>
</div>`;
const art = [
  ['Backend image', `${mo('git-&lt;sha&gt;-run-&lt;n&gt;', 20)}, deployed by its ${mo('@sha256', 20)} digest`, 'ECR, immutable tags, newest 30 kept'],
  ['API contract', `${mo('openapi.json', 20)} committed; drift fails CI`, 'Repository'],
  ['TypeScript client', 'Generated from the contract, gitignored', 'Build only', 'F-ADR 0003'],
  ['Frontend bundle', 'Content-hashed files, immutable cache', 'S3 behind CloudFront'],
  ['Database schema', 'Flyway migrations V1–V28', 'Repository'],
  ['Infrastructure', 'Terraform plan saved, applied by an operator; CI never applies', 'S3 state, DynamoDB lock'],
  ['SBOM + licences', 'CycloneDX source-dependency SBOMs and licence inventories', 'Actions artefacts, kept 90 days', 'sbom.yml · #609 · source inventories, not of the deployed JAR, image or bundle'],
];
const s28 = () => `
${tbl(['Artefact', 'Identity', 'Stored in'], art.map(a => withRef([`<b>${a[0]}</b>`, a[1], `<span style="color:var(--muted)">${a[2]}</span>`], a[3] || '')), { fs: 22, w: [230, 560], pad: '12px 14px' })}`;

// ---------- slide 29 (4.3) ----------
// A GitHub-Actions-style job graph (review of #605: "GitHub CI's own looks cleaner", "too much text"). It fits the body
// at 1:1, so the steps highlight a part of the whole graph and grey out the rest (rubric V5) instead of cropping it.
// The earlier step-by-step diagram (cicd-pr-vs-merge) stays as backup F16.
const CJ_W = 1164, CJ_H = 522;
const spot = (name, W, H, rects) => `<div style="position:relative;width:${W}px;height:${H}px">${svg(name)}
<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" style="position:absolute;left:0;top:0"><path fill-rule="evenodd" fill="#FFFFFF" fill-opacity=".74" d="M0,0 H${W} V${H} H0 Z ${rects.map(([x, y, w, h]) => `M${x},${y} h${w} v${h} h-${w} Z`).join(' ')}"/>
${rects.map(([x, y, w, h]) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" fill="none" stroke="#E8402A" stroke-width="2.5" stroke-dasharray="8 5"/>`).join('')}</svg></div>`;
const cj = [
  ['PR · backend', [[6, 34, 1152, 100]], ['Backend pull request', 'tests and ArchUnit; contract drift; the image boots and Trivy scans it']],
  ['PR · frontend, security', [[6, 134, 1152, 154]], ['Frontend and security', 'checks and Playwright; secrets, dependencies and policy as code']],
  ['merge gate', [[164, 294, 836, 50]], ['Merge gate', 'a code owner approves, the six blue checks are green, squash merge']],
  ['after merge', [[6, 350, 1152, 162]], ['After the merge', 'rebuild and deploy by digest with a health check; the frontend is smoke-tested']],
];
const s29 = () => `<style>body:not(.present) .zcrop{display:none}</style>
<div style="position:absolute;left:0;top:0;width:${CJ_W}px;height:${CJ_H}px">${svg('cicd-jobs')}</div>
<div style="position:absolute;left:0;top:${CJ_H + 12}px;font:400 18px Inter;color:var(--muted)"${ref('job names as in .github/workflows/ci-backend.yml, ci-frontend.yml, security.yml · required checks: ruleset dev-require-review')}>Real job names; every step, trigger and path filter: backups B8 and F16</div>
${cj.map(([label, rects, cap], i) => layer(`<div style="flex:1;min-height:0;overflow:hidden">${spot('cicd-jobs', CJ_W, CJ_H, rects)}</div>${caption([cap])}`, label, i, cj.length)).join('')}`;

// ---------- slide 30 (4.3, 3.3) ----------
// Stages as in DSOEA 07 slide 3 (pre-commit, commit, build, test, deploy).
function req2() { return '<span style="font:700 15px/1 Inter;color:#3E5CD9;border:1.5px solid #3E5CD9;border-radius:999px;padding:2px 6px;vertical-align:2px">required</span>'; }
const sec = [
  ['Pre-commit', ['Lefthook: format, lint, type-check (frontend)'], 'Style and type errors', ''],
  ['Commit · PR', [`<b>gitleaks</b> ${req2()}: the PR’s commits, full history weekly`, 'Code-owner review; CodeRabbit review, advisory', 'actionlint'], 'Secrets, unsafe workflows, review findings', ''],
  ['Build', [`SAST: <b>SonarCloud</b> ${req2()}, SpotBugs`, 'SCA: Trivy deps, Dependabot daily'], 'Code flaws, vulnerable libraries', ''],
  ['Test', [`<b>Trivy image</b> ${req2()} (HIGH, CRITICAL)`, 'Trivy config, deployment-bundle rules'], 'Image CVEs, IaC misconfiguration', ''],
  ['Deploy', ['OIDC role per job', 'Image by digest', 'Read-only container, all capabilities dropped', 'OWASP ZAP: passive, anonymous dev pages, advisory'], 'Stolen keys, swapped images, missing security headers', 'Dev delivery rebuilds the image without repeating the Trivy image gate; ZAP: no logins, payments or attacks'],
];
const s30 = () => `
${tbl(['Stage', 'Control', 'Catches', 'Gap'], sec.map(r => [`<b style="white-space:nowrap">${r[0]}</b>`, lines(r[1]), r[2], r[3] ? `<span style="color:#B02E1B">${r[3]}</span>` : '—']), { fs: 20, w: [140, 490, 250], pad: '5px 11px' })}
<div style="position:absolute;left:0;right:0;top:494px;border-radius:8px;background:#E3E8FB;padding:8px 16px;font:400 20px/1.32 Inter"${ref('Stages: DSOEA 07 slide 3 · ECR scans on push (operator guide)')}>${lines(['<b style="color:#3E5CD9">Types tested (slide 25, Q4):</b> static analysis, dependencies, image and IaC configuration, secrets, passive DAST', 'Every third-party action is pinned to a commit SHA'])}</div>`;

// ---------- slide 31 (4.4) ----------
const s31 = () => one('dev-deployment', 2320, 1096, [1164, 568], [
  [0, 20, 'request path', 0, [['One environment', 'dev; the SWE5006 production stack was planned, never applied'], ['Request path', 'the browser talks only to CloudFront: files from a private bucket, API calls to the load balancer']]],
  [940, 322, 'one host', 0, [['One host', 'the container runs by digest, beside PostgreSQL and Redis on loopback'], ['Trade-off', 'cheap and always on, but one point of failure']]],
  [1160, 636, 'deploy path', 0, [['Approver', 'the code owner on the pull request; the merge releases'], ['Practice', 'every merge deploys automatically to dev, not production; SWE5001 production is Continuous Delivery'], ['Verified by', 'on-host health check with automatic rollback, public health, Playwright smoke']]],
], { axis: 'x' }) + `<span${ref('#226 · EPAT 07 CI p34–35 · deploy.sh:101-141')}></span>`;

// ---------- slide 32 (4.5) ----------
const finds = [
  ['Early dev deploys failed (5–17 Sep)', ['The hand-built environment drifted from its design', 'Old OIDC subject format, a placeholder instance ID, port 8589 open wide'], ['Fixed on dev by hand', 'The prod stack renders each value in Terraform, so a plan shows it', 'No dev deploy job failed from 18 Sep to 22 Oct 13:54 UTC'], 'docs/deploy/prod/backend-prod-terraform-deployment.md L15–23 · 121a0733 · docs/ci/dora-metrics-20261022.md (delivery proxy, PR #629)'],
  ['build-quality red for two days (19–20 Oct)', ['Dependabot upgraded the SpotBugs engine without its Maven plugin'], ['Both now upgrade together', 'A POM policy test guards it'], 'docs/ci/ci-failure-audit-20261021.md · PR #580'],
  ['Weekly full-history gitleaks failing (since 21 Sep)', ['20 matches were file digests in a contract baseline, not secrets'], ['Each digest re-hashed, then fingerprinted exactly', '4 badge-token findings still open'], 'docs/ci/ci-failure-audit-20261021.md · PR #580'],
];
const s32 = () => `
${tbl(['Finding', 'Root cause', 'Fix'], finds.map(f => withRef([`<b>${f[0]}</b>`, lines(f[1], 4), lines(f[2], 4)], f[3])), { fs: 20, w: [250, 440], pad: '11px 12px' })}
<div style="position:absolute;left:0;right:0;top:472px;border-radius:8px;background:#FDEFEC;padding:12px 16px;font:400 20px/1.35 Inter"${ref('#664 · #590 · #517 · backend/docs/payment/stripe-dev-recovery-acceptance-20261023.md')}><b style="color:#B02E1B">Still open:</b> a second buyer’s paid join on dev. Paid start and the expiry refund were shown on dev on 23 Oct, after a webhook configuration repair.</div>`;

// ---------- slide 33 (4.1, 4.6) ----------
const live = [
  ['P1', 'The pull request: one frontend change, six required checks green', '60 s'],
  ['P2', 'Lim approves as the frontend code owner', '30 s'],
  ['P3', 'Squash merge: one commit on dev', '20 s'],
  ['P4', 'Delivery runs; the backend PR and deploy, triggered beforehand', '90 s'],
  ['P5', 'Verified: e2e-dev green, the change live on dev', '90 s'],
  ['P6', 'Merge to live in minutes; hand-over', '40 s'],
];
const s33 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 22, w: [76, null, 86], pad: '13px 14px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:18px 22px;margin-bottom:14px">${kicker('Why the backend is pre-run')}<div style="font:400 21px/1.4 Inter">Backend PR checks take about 16 min, longer than the segment, so that run was triggered before the talk and we walk through it finished.</div></div>
 <div class="card" style="padding:18px 22px">${kicker('Backup video', '#B02E1B')}<div style="font:400 21px/1.4 Inter">${lines(['Recorded on dev by 24 Oct, one clip per step', `In the cloud and on a USB drive ${mk('FILL', 'link')}`], 4)}</div></div>
</div>`;

// ---------- slide 34 (5.1, 5.2) ----------
// Environment per step follows the 23 Oct go/no-go (open item 1); the NO-GO wording is in the run book.
const env5 = (t, tone = 'ok') => {
  const k = { ok: ['#E3E8FB', '#3E5CD9'], rec: ['#FFEDC7', '#7A5C20'], loc: ['#ECE6DC', '#1B1A18'], ecs: ['#FBDFD9', '#B02E1B'] }[tone];
  return `<span style="display:inline-block;font:700 15px/1.15 Inter;background:${k[0]};color:${k[1]};border-radius:999px;padding:5px 10px;white-space:nowrap">${t}</span>`;
};
const ucList = a => `<div style="display:flex;flex-wrap:wrap;gap:0 8px">${a.map(u => `<span style="white-space:nowrap">${u}</span>`).join('')}</div>`;
const plan5 = [
  ['D1', 'Leader signs in, starts a bloc, pays in Stripe Sandbox, copies the invite', ['UC2', 'UC1', 'UC3', 'UC7'], 'Security: only a signed webhook confirms payment', env5('dev, if GO')],
  ['D2', 'Joiner opens the invite, joins and pays: 2 of 2, the team succeeds', ['UC4', 'UC5', 'UC7'], 'Correctness: guards re-checked after payment', env5('dev, if GO')],
  ['D3', 'My blocs and orders, filtered by the URL', ['UC8'], 'Functionality', env5('dev')],
  ['D4', 'The bloc started at 16:05 expired and refunded itself', ['UC5', 'UC6'], 'Unattended expiry and refund; ≤ 1 min measured locally (slide 36)', env5('dev, if GO')],
  ['D5', 'No session, a buyer on admin, a write without the marker', ['—'], 'Security: 401, 403, 400, HttpOnly', env5('dev')],
  ['D6', 'Twenty paid buyers race for one seat', ['UC4'], 'Correctness under concurrency', env5('local test', 'loc')],
  ['36', 'Scripted k6 load, 16 Oct run', ['UC1'], 'Performance: ≤ 2 s at 100 users', env5('SWE5001 ECS', 'ecs')],
  ['38', 'Back to the §4 pipeline run', ['—'], 'Maintainability', env5('dev')],
];
const s34 = () => `
${tbl(['Step', 'What you see', '<span style="white-space:nowrap">Use cases</span>', 'Quality attribute (slide 3)', 'Runs on'], plan5.map(r => [`<b>${r[0]}</b>`, r[1], ucList(r[2]), r[3], r[4]]), { fs: 20, w: [60, 400, 118, null, 138], pad: '5px 10px' })}
<div style="position:absolute;left:0;right:0;top:520px;font:400 18px/1.3 Inter;color:var(--muted)"${ref('#517 · #223')}>“dev, if GO”: live on dev after the 23 Oct go/no-go, otherwise its 24 Oct recording; a local result is never shown as dev</div>`;

// ---------- slide 35 (5.1, 5.2, 5.5) ----------
const live5 = [
  ['D1', 'Start a bloc and share the invite', '120 s'],
  ['D2', 'A friend joins and pays: the team succeeds', '120 s'],
  ['D3', 'My blocs and my orders', '75 s'],
  ['D4', 'The 16:05 bloc: expired, order cancelled, refunded', '90 s'],
  ['D5', 'Security: 401, 403, 400, HttpOnly cookie', '60 s'],
  ['D6', 'Last-seat race: the test, in the IDE', '45 s'],
];
const s35 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live5.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 22, w: [76, null, 86], pad: '13px 14px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:16px 22px;margin-bottom:12px">${kicker('Two buyers, one screen')}<div style="font:400 21px/1.38 Inter">${lines(['Two Chrome profiles, Leader and Joiner, both signed in with Google', 'Stripe Sandbox: a test card, no money moves'], 4)}</div></div>
 <div class="card" style="padding:16px 22px;margin-bottom:12px">${kicker('Started before the talk')}<div style="font:400 21px/1.38 Inter">A team of two on ${mo('US34-04', 20)} at 16:05: its 40-minute window, the shortest allowed, ends ≈ 16:45.</div></div>
 <div class="card" style="padding:16px 22px">${kicker('Backup video', '#B02E1B')}<div style="font:400 21px/1.38 Inter">${lines(['Recorded on dev by 24 Oct, one clip per step', `In the cloud and on a USB drive ${mk('FILL', 'link')}`], 4)}</div></div>
</div>`;

// ---------- slide 36 (5.3) ----------
// Repeat run 20261016-v2-monolith-repeat (v2-repeat-calibration.json): p95 ms per plateau; 0 errors, 0 dropped on every step.
const steps36 = [[10, 53], [20, 39], [40, 27], [60, 28], [80, 23], [100, 23], [125, 42], [150, 23], [175, 23], [200, 23]];
const s36 = () => `
<div style="position:absolute;left:0;top:0;width:690px">
 ${kicker('k6 run · SWE5001 ECS environment · not the dev host', '#B02E1B')}
 ${tbl(['Requests/s', 'p95 (ms)', 'Errors', 'Dropped', 'Step'], steps36.map(([r, p]) => [`<b>${r}</b>${r >= 190 ? ' <span style="color:#B02E1B">●</span>' : ''}`, p, '0 %', '0', 'pass']), { fs: 22, w: [150, 130, 120, 130], pad: '3px 14px' })}
 <div style="font:400 19px/1.35 Inter;color:var(--muted);margin-top:6px"><span style="color:#B02E1B">●</span> at or above ≈ 190 requests/s: the proposal’s 100 users, 0.5 s think time assumed (slide 26)</div>
 <div style="font:400 19px/1.35 Inter;margin-top:4px"${ref('docs/swe5001/evidence/20261016-http-baseline-dashboard/v2-repeat-summary.json')}>Run 20261016-v2-monolith-repeat, 16 Oct: 57,600 requests, 0 failed, 0 dropped</div>
</div>
<div style="position:absolute;left:714px;top:0;width:450px">
 <div class="card" style="padding:12px 20px;margin-bottom:10px">
  ${kicker('Target (proposal §6.3)', '#B02E1B')}
  <div style="font:600 21px/1.35 Inter">≤ 2 s with up to 100 concurrent users</div>
  <div style="font:400 20px/1.4 Inter;margin-top:8px">${lines(['Our gate per step:', 'p95 &lt; 500 ms', 'Errors &lt; 1 %', 'No dropped requests'], 0)}</div>
  <div style="font:700 20px/1.4 Inter;margin-top:8px">Verdict: pass on every step</div><div style="font:400 20px/1.4 Inter">Worst p95 53 ms, 0 % errors, 0 dropped</div>
 </div>
 <div class="card" style="padding:12px 20px;margin-bottom:10px">
  ${kicker('Expiry ≤ 1 min')}
  <div style="font:400 20px/1.35 Inter">Worst lag 58.2 s for 30 teams under 100-user load (<b>local</b>, 30 Sep)</div>
 </div>
 <div class="card" style="padding:12px 20px">
  ${kicker('Not claimed')}
  <div style="font:400 20px/1.4 Inter">${lines(['A capacity limit: 200/s is the last step', 'Authenticated paths under load', 'Anything about the dev host', 'Scaling (one task)'], 0)}</div>
 </div>
</div>`;

// ---------- slide 37 (5.2) ----------
const s37 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:560px;height:300px;padding:20px 24px"${ref('AWS read-only, 21 Oct')}>
 ${kicker('Dev, the system you saw')}
 ${[['Compute', 'One EC2 t3.large, no Auto Scaling group'], ['Data', 'PostgreSQL 18 and Redis 8 on the same host'], ['Effect', 'One point of failure; a deploy restarts the API']].map(r => `<div style="display:flex;gap:14px;font:400 22px/1.35 Inter;margin-bottom:16px"><b style="width:118px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('')}
</div>
<div class="card" style="position:absolute;left:584px;top:0;width:580px;height:300px;padding:20px 24px"${ref('proposal §6.3 · backend ADR 0008')}>
 ${kicker('Why, deliberately', '#B02E1B')}
 <ul class="b" style="font:400 22px/1.4 Inter;margin:0">
  <li style="margin-bottom:12px">One complete system, always on, at low cost</li>
  <li style="margin-bottom:12px">The proposal lists no scalability target</li>
  <li>One codebase, two topologies</li>
 </ul>
</div>
<div style="position:absolute;left:0;right:0;top:324px;border-radius:8px;background:#ECE6DC;padding:16px 20px;font:400 22px/1.4 Inter"${ref('backup B10')}><b>Scale path, not claimed here:</b> the same code on ECS in our SWE5001 topology, commerce 2–6 tasks on requests per target, notification 1–4 on queue backlog.</div>`;

// ---------- slide 38 (5.4) ----------
const seams = [
  ['New pricing mode', `One more ${mo('PricingStrategy', 20)} and its ${mo('PricingMode', 20)} constant`, ['Strategy', 'slide 21']],
  ['New consequence of a team’s outcome', `One more subscriber to ${mo('TeamSucceeded', 20)} / ${mo('TeamExpired', 20)}`, ['Observer', 'slide 19']],
  ['New payment provider', `One more adapter behind ${mo('CheckoutGateway', 20)}, ${mo('RefundGateway', 20)}`, ['Gateway port (external-interface strategy)', 'slide 12']],
];
const s38 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:420px;padding:20px 24px">
 ${kicker('Shown in §4 (slide 33)')}
 <div style="font:400 22px/1.45 Inter">${lines(['One change, live on dev', 'Six required checks, a code-owner review, an automatic deploy and a smoke test', '17 ArchUnit rules hold the module boundaries on every build'], 10)}</div>
</div>
<div style="position:absolute;left:444px;top:0;width:720px"${ref('proposal §6.3: the primary design driver')}>
 ${kicker('Primary design driver: extensibility', '#B02E1B')}
 ${tbl(['Change', 'Added as', 'Pattern'], seams.map(r => [`<b>${r[0]}</b>`, r[1], lines([r[2][0], `<span style="color:var(--muted)">${r[2][1]}</span>`], 0)]), { fs: 21, w: [230, null, 190], pad: '12px 12px' })}
 <div style="font:400 21px/1.35 Inter;color:var(--muted);margin-top:12px">Existing rules stay unedited: each change adds classes; a pricing mode also adds one enum constant.</div>
</div>`;

const out = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 13: s13, 14: s14, 15: s15, 16: s16, 17: s17, 18: s18, 19: s19, 20: s20, 21: s21, 22: s22, 23: s23, 24: s24, 25: s25, 26: s26, 27: s27, 28: s28, 29: s29, 30: s30, 31: s31, 32: s32, 33: s33, 34: s34, 35: s35, 36: s36, 37: s37, 38: s38 };
for (const s of C.SLIDES) if (!out[s.n]) out[s.n] = s.slot === null ? (() => '') : pending(s.n);
// Rubric G8: every example slide names the report section holding the full set. §2 slides carry it with repTop; the
// others get it here. Our section numbers follow #463 and stay FILL until it is final; the report template section
// (RT) each slide answers is kept as metadata.
const repSec = {
  3: ['scope, quality attributes', 'RT 1.4.1–1.4.3'], 4: ['status, promised vs delivered', 'RT 1.4, 2.2'], 5: ['milestones, effort', 'RT 2.3'],
  6: ['ownership', 'RT 2.1, 3.4, 3.5'], 7: ['sprints, DoD, retrospectives', ''], 8: ['appendix: agile artefacts', ''],
  25: ['types of testing', ''], 26: ['performance test', ''], 27: ['source control strategy', 'RT 4.1'], 28: ['artefacts', 'RT 4.1'],
  29: ['continuous integration', 'RT 4.2'], 30: ['pipeline security', 'RT 4.2'], 31: ['continuous delivery', 'RT 4.3'], 32: ['technical findings', ''],
};
for (const [n, [t, rt]] of Object.entries(repSec)) {
  const body = out[n];
  out[n] = () => body() + repTop(`<span${rt ? ref(rt) : ''}>Report ${mk('FILL', '§ (#463)')} · ${t}</span>`);
}
// Closing slide (dark, full bleed; MOTION.md, shortened): headline 150, sub 300, footer up and its icon pop 520.
out[39] = () => `
<div ${A('up', 150, 'position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff;')}>Thank you</div>
<div ${A('up', 300, 'position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff;')}>Questions?</div>
<div ${A('up', 520, "position:absolute;left:80px;top:604px;font:400 18px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:28px;align-items:center;")}><span style="display:flex;gap:10px;align-items:center"><span ${A('pop', 520, 'font-size:20px;')}>${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8</span><span>d1b3np88z657cp.cloudfront.net</span></div>`;

// Header strip per slide: the report line, and the detail pages a reader of the PDF needs (hidden while presenting,
// where the crops themselves step in).
for (const s of C.SLIDES) {
  if (s.slot === null) continue;
  const f = out[s.n];
  CROPS.length = 0;
  f();
  const k = CROPS.length;
  out[s.n] = () => {
    let rp = '';
    const html = f().replace(/<!--rep([\s\S]*?)-->/, (m, r) => { rp = r; return ''; }).replace(/__BR__/g, `${chipRight(s.speaker)}px`);
    const items = [];
    if (k) items.push(`<span class="pdfonly" style="font:600 13px Inter;color:#6B655C;display:flex;gap:5px;align-items:center">${icon('magnifying-glass')} Details D${s.n}-1 to D${s.n}-${k} in the backups</span>`);
    if (rp) items.push(`<span style="font:600 16px Inter;color:#3E5CD9;display:flex;gap:6px;align-items:center">${icon('book')} ${rp}</span>`);
    return html + (items.length ? hdrStrip(s.speaker, items) : '');
  };
}

// Speaker notes number every fragment step as [click k/n] (review round 4); the build fails when a slide's steps and
// its notes disagree.
for (const s of C.SLIDES) {
  if (s.slot === null) continue;
  const html = out[s.n]();
  const n = Math.max(0, ...[...html.matchAll(/data-frag="(\d+)"/g)].map(m => +m[1]));
  const marks = [...String(s.say).matchAll(/\[click(?: (\d+)\/(\d+))?\]/g)];
  if (marks.length !== n || marks.some((m, i) => +m[1] !== i + 1 || +m[2] !== n)) {
    throw new Error(`slide ${s.n}: ${n} fragment steps, but its say has ${marks.map(m => m[0]).join(' ') || 'no click marks'}`);
  }
}

// Detail pages for the PDF (rubric V3): every detail layer of every slide, in slide order, as a static backup body.
// Non-enumerable, so the kit, which looks slides up by number, never sees it.
Object.defineProperty(out, 'details', { value: () => C.SLIDES.filter(s => s.slot !== null).flatMap(s => {
  CROPS.length = 0;
  out[s.n]();
  return CROPS.map(c => ({
    id: `D${s.n}-${c.i + 1}`, n: s.n, k: c.i + 1, of: c.n, label: c.label, speaker: s.speaker, slideTitle: s.title,
    body: frame(c.inner),
  }));
}) });

module.exports = out;
