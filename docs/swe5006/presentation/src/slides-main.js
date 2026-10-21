const { icon, mk, lockup, svg } = require('../../../presentation-kit/lib');
const C = require('./content');
// Accent fills never carry text: accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.
// Legibility (rubric V3): labels at least 18 pt, which is 24 px on the 1280 × 720 canvas. Diagram crops meet it 1:1;
// body and table text (15.5–19 px) does not yet, and the final layout pass raises it.

// Placeholder for slides whose content a later drafting pass fills in.
const pending = n => () => {
  const s = C.SLIDES.find(x => x.n === n);
  return `<div style="margin-top:40px;font:400 20px/1.4 Inter">Rubric rows: <b>${s.rubric.join(', ')}</b></div><div style="margin-top:16px">${mk('FILL', 'slide ' + n + ' draft pending')}</div>`;
};

// ---------- shared helpers ----------
const th = (t, w) => `<th style="font-size:16px;padding:8px 12px${w ? ';width:' + w + 'px' : ''}">${t}</th>`;
const tbl = (head, rows, { fs = 17, w = [], pad = '7px 12px' } = {}) => `<table class="t" style="font-size:${fs}px"><tr>${head.map((h, i) => th(h, w[i])).join('')}</tr>
${rows.map(r => `<tr>${r.map(c => `<td style="padding:${pad}">${c}</td>`).join('')}</tr>`).join('')}</table>`;
const mo = (t, sz = 16) => `<span class="mono" style="font-size:${sz}px;font-variant-ligatures:none">${t}</span>`;
const chip = (t, kind) => {
  const k = { ok: ['#E3E8FB', '#3E5CD9'], chg: ['#FFEDC7', '#7A5C20'], part: ['#ECE6DC', '#1B1A18'], drop: ['#EFE9DF', '#8C2414'], open: ['#FBDFD9', '#B02E1B'] }[kind];
  return `<span style="display:inline-block;font:700 14.5px/1 Inter;letter-spacing:.03em;text-transform:uppercase;background:${k[0]};color:${k[1]};border-radius:999px;padding:5px 9px;margin-right:6px;white-space:nowrap">${t}</span>`;
};
const kicker = (t, c = '#6B655C') => `<div style="font:700 15px Inter;letter-spacing:.06em;text-transform:uppercase;color:${c};margin-bottom:8px">${t}</div>`;

// ---------- slide 1 ----------
const s1 = () => `
<div style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F">SWE5006 PRACTICE PROJECT · TEAM 8</div>
${lockup()}
<div style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff">A community group-buying platform with pay-first teams</div>
<div style="position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center">Two prices <span style="color:#E8402A;font-size:20px">●</span> pay first to team up <span style="color:#E8402A;font-size:20px">●</span> refunded if the team doesn’t fill</div>
<div style="position:absolute;left:80px;top:448px;font:500 19px Inter;color:#FFB01F">1 Introduction · 2 Analysis, design, patterns · 3 Testing · 4 CI/CD demo · 5 System demo</div>
<div style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1">Graduate Certificate in Designing Modern Software Systems · 26 October 2026</div>`;

// ---------- slide 2 (1.1) ----------
const row = (ic, txt, bg, col = 'var(--ink)', sz = 64, fs = 22, mb = 22) => `<div style="display:flex;align-items:center;gap:22px;margin-bottom:${mb}px">
  <div class="dot" style="width:${sz}px;height:${sz}px;background:${bg};font-size:${sz / 2}px;flex:none">${icon(ic)}</div>
  <div style="font:400 ${fs}px/1.3 Inter;color:${col}">${txt}</div></div>`;
const s2 = () => `
<div style="position:absolute;left:0;top:0;width:1164px;border-left:6px solid #E8402A;padding:6px 0 6px 18px;font:600 25px/1.3 Inter">Small merchants can discount only against committed volume, and no single buyer can reach it: demand exists, but nobody commits first.</div>
<div class="card" style="position:absolute;left:0;top:118px;width:520px;height:410px;padding:28px 32px">
  <div class="serif" style="font-size:28px;color:var(--muted);margin-bottom:24px">Today</div>
  ${row('store', 'Merchants discount only when volume is predictable', '#6B655C')}
  ${row('user', 'One buyer can never reach a bulk price', '#6B655C')}
  ${row('bullhorn', 'Advertising is costly and not tied to real purchases', '#6B655C', 'var(--ink)', 64, 22, 0)}
</div>
<div style="position:absolute;left:534px;top:318px;width:62px;height:3px;background:var(--orange)"></div>
<div style="position:absolute;left:588px;top:312px;border-left:12px solid var(--orange);border-top:7px solid transparent;border-bottom:7px solid transparent"></div>
<div class="card" style="position:absolute;left:614px;top:118px;width:550px;height:410px;padding:26px 32px;background:var(--inverse);border:none">
  <div class="serif" style="font-size:28px;color:#E8402A;margin-bottom:20px">BlocBuy: pay-first teams</div>
  ${row('tags', 'Two prices: individual and team', 'var(--orange)', '#fff', 54, 21, 18)}
  ${row('credit-card', 'Pay upfront to start a team, then share the invite link', 'var(--orange)', '#fff', 54, 21, 18)}
  ${row('users', 'Friends join by paying upfront too', 'var(--orange)', '#fff', 54, 21, 18)}
  ${row('arrows-rotate', 'Fills in time: team price. Expires: everyone refunded automatically', 'var(--orange)', '#fff', 54, 21, 0)}
  <div style="margin-top:14px;font:italic 400 18px Inter;color:#B5ADA1">Stripe test mode only (proposal §6.1)</div>
</div>`;

// ---------- slide 3 (1.2) ----------
const ucs = [['UC1', 'Browse and view listings'], ['UC2', 'Register / authenticate'], ['UC3', 'Initiate a team (pay first)'], ['UC4', 'Join a team'], ['UC5', 'Team lifecycle resolution'], ['UC6', 'Refund on team expiry'], ['UC7', 'Share attribution'], ['UC8', 'View my orders and teams'], ['UC9', 'Admin listing management']];
const qas = [
  ['Correctness under concurrency', 'team capacity never exceeded; no payment double-charged or double-refunded', '§5 demo · §3'],
  ['Performance', 'interactive operations ≤ 2 s at 100 concurrent users; expiry resolved ≤ 1 min after the window', '§5 k6 run · §3'],
  ['Security', 'Google sign-in; role-based admin; signed Stripe webhooks; no credentials in code', '§5 demo · §4'],
  ['Maintainability, extensibility', 'new pricing, payment provider or team consequence without editing domain logic', '§2 patterns · §4'],
  ['Code quality', 'SonarCloud gate on every PR; unit tests on the complex classes', '§3 · §4'],
  ['Auditability', 'every payment, refund and transition traceable; every artefact traceable to a commit', '§4'],
];
const s3 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:372px;height:548px;padding:16px 20px">
 ${kicker('In scope · proposal §6.1')}
 ${ucs.map(u => `<div style="display:flex;gap:10px;font:400 18px/1.25 Inter;margin-bottom:7px"><b style="width:44px;flex:none">${u[0]}</b><span>${u[1]}</span></div>`).join('')}
 <div style="font:400 17px/1.3 Inter;color:var(--muted);margin:4px 0 12px">+ Receive notifications (Sprint 4, SWE5001; off on dev)</div>
 ${kicker('Out of scope · proposal §6.2', '#B02E1B')}
 <div style="font:400 17px/1.35 Inter">Merchant self-service · analytics · recommendations · automatic team matching · email, SMS, push · native apps · live payments</div>
</div>
<div style="position:absolute;left:392px;top:0;width:772px">
 ${kicker('Quality attributes and targets · proposal §6.3')}
 ${tbl(['Attribute', 'Target', 'Shown in'], qas.map((q, i) => [`<b>${q[0]}</b>`, q[1], `<span style="white-space:nowrap${i < 3 ? ';color:#B02E1B;font-weight:700' : ''}">${q[2]}</span>`]), { fs: 17, w: [200, null, 140], pad: '7px 11px' })}
</div>`;

// ---------- slide 4 (1.3) ----------
const pvd = [
  ['UC1–UC9', chip('Delivered', 'ok') + 'all nine closed as completed', '#24–#32'],
  ['UC9 admin API only, no GUI', chip('Changed', 'chg') + 'admin console built in Sprint 3', 'F-ADR 0015 · #305–#307'],
  ['Adapter (UC6), Observer (UC7)', chip('Changed', 'chg') + 'gateway ports; Observer on UC5 → UC6', 'slides 10, 18 · <i>no ADR</i>'],
  ['Pay-first START, JOIN, refund on dev', chip('Open', 'open') + 'Checkout returns; webhook never confirms', '#517 · #223'],
  ['State for the team lifecycle', chip('Changed', 'chg') + 'transition table; State on listings', 'design doc · <i>no ADR (item 7)</i>'],
  ['Google sign-in with JWT', chip('Changed', 'chg') + 'Sa-Token session, HttpOnly cookie', 'D6/D7, F-ADR 0002 · <i>no backend ADR</i>'],
  ['Spring Data JPA', chip('Changed', 'chg') + 'MyBatis behind repository ports', 'D1/D9 · <i>no ADR (item 7)</i>'],
  ['Dev on ECS Fargate and RDS', chip('Changed', 'chg') + 'one EC2 host, Compose, DB on host', 'ADR 0008'],
  ['Secrets Manager, no static keys', chip('Changed', 'chg') + 'SSM and a root-only env file; 2 IAM users, 1 key', 'operator guide §7 · <i>no ADR</i>'],
  ['Blocking OWASP and IaC scans', chip('Changed', 'chg') + 'Trivy deps and config, not required; 3 per-job roles', 'security.yml, ruleset · <i>no ADR</i>'],
  ['Gated prod, ECS circuit breaker', chip('Dropped', 'drop') + 'for SWE5006; built for SWE5001', '#226 · ADR 0008'],
  ['E2E, burndown, saved plans', chip('Partial', 'part') + 'stubs; daily data, no chart; manual', 'F-ADR 0011 · #229'],
];
const s4 = () => `
${tbl(['Promised (proposal)', 'Status on 21 Oct', 'Record'], pvd.map(r => [`<b>${r[0]}</b>`, r[1], `<span style="color:var(--muted)">${r[2]}</span>`]), { fs: 16.5, w: [330, null, 250], pad: '5px 10px' })}
<div style="position:absolute;left:0;right:0;top:488px;border-radius:8px;background:#FDEFEC;padding:8px 16px;font:400 17px/1.3 Inter;display:flex;gap:10px;align-items:baseline">
 <b style="color:#B02E1B;white-space:nowrap">Outstanding</b><span>#517 and #223 (go/no-go 23 Oct) · SSH 22 open on the dev host · CodeQL off · CloudFront → ALB over HTTP until 16 Nov (#227) · dev volume unencrypted</span>
</div>`;

// ---------- slide 5 (1.4) ----------
const ms = [['27 Aug', 'Proposal submitted'], ['5 Sep', 'Backend pipeline live on dev'], ['14 Sep', 'Sprint 1 tag'], ['20 Sep', 'Stripe Sandbox acceptance (local)'], ['22 Sep', 'Web app live on dev'], ['27 Sep', 'Sprint 2 tag'], ['30 Sep', 'Admin console merged (#305–#307)'], ['11 Oct', 'Sprint 3 tag']];
const mem = [['Liu Jin', 51], ['Yu Qianshuo', 48], ['Wang Ziang', 46], ['Panpakorn', 41.5], ['Lim Jiechao', 38]];
const s5 = () => `
<div style="position:absolute;left:0;top:0;width:470px">
 ${kicker('Milestones achieved')}
 ${tbl(['Date', 'Milestone'], ms.map(m => [`<b style="white-space:nowrap">${m[0]}</b>`, m[1]]).concat([[`<b>23 Oct</b>`, `Sprint 4 tag ${mk('FILL', 'date')}`], ['—', `<span style="color:#B02E1B">Paid start, join, refund on dev: not yet</span>`]]), { fs: 17, w: [92], pad: '4px 11px' })}
</div>
<div style="position:absolute;left:500px;top:0;width:664px">
 <div style="display:flex;align-items:baseline;gap:10px">${kicker('Effort per member · planned hours')}${mk('VERIFY', 'S4 split, PR #538')}</div>
 ${tbl(['Member', 'S1', 'S2', 'S3', 'S4', 'Planned', 'Actual'], mem.map(m => [`<b>${m[0]}</b>`, '—', '30', '30', m[1], `<b>${60 + m[1]}</b>`, mk('CONFIRM', 'agreed')]), { fs: 17.5, w: [150], pad: '7px 11px' })}
 <div style="font:400 17px/1.4 Inter;margin-top:12px">Sprint 1 hours were not recorded. Team done: S2 134 of 150 h, S3 117 of 150 h. Budget in the proposal: 10 man-days each (§7).</div>
 <div style="margin-top:10px;border-radius:8px;background:#FDEFEC;padding:10px 14px;font:400 17px/1.35 Inter"><b style="color:#B02E1B">Planned, not actual:</b> no member logged actual hours, and the progress reports carry none. ${mk('CONFIRM', 'actual effort agreed by all five')}</div>
</div>`;

// ---------- slide 6 (1.5) ----------
const own = [
  ['Liu Jin', 'UC9 admin listings and team terms', 'Listing lifecycle → <b>State</b> (full form)', '21–22', 'was UC5 State, UC9'],
  ['Wang Ziang (Eric)', 'UC3 start, UC4 join', 'Team creation → <b>Factory Method</b>', '17', 'unchanged; UC4 race is a transition strategy'],
  ['Yu Qianshuo (Yorik)', 'UC6 refund on expiry', 'Team outcome consequences → <b>Observer</b>', '18', 'was Adapter'],
  ['Panpakorn (Pan)', 'Receive notifications', 'Outbox transports → <b>Template Method</b>', '19', 'was UC1 Strategy, UC2'],
  ['Lim Jiechao', 'UC8 my orders and teams', 'Pricing → <b>Strategy</b>', '20', 'was UC7 Observer, UC8'],
];
const s6 = () => `
${tbl(['Member', 'Use case', 'Design problem → GoF pattern', 'Slide', 'Change from proposal §6.1'], own.map(o => [`<b>${o[0]}</b>`, o[1], o[2], o[3], `<span style="color:var(--muted)">${o[4]}</span>`]), { fs: 18, w: [196, 270, 330, 70], pad: '12px 12px' })}
<div style="margin-top:14px">${kicker('Every use case has an owner · * proposed, not yet agreed')}</div>
<div style="display:flex;flex-wrap:wrap;gap:8px 18px;font:400 18px/1.3 Inter">${[['UC1', 'Lim*'], ['UC2', 'Pan*'], ['UC3', 'Eric'], ['UC4', 'Eric'], ['UC5', 'Eric*'], ['UC6', 'Yorik'], ['UC7', 'Lim*'], ['UC8', 'Lim'], ['UC9', 'Liu Jin'], ['Notifications', 'Pan']].map(u => `<span><b>${u[0]}</b> ${u[1]}</span>`).join('')}${mk('CONFIRM', 'owners marked * (open decision 4)')}</div>
<div style="margin-top:10px;font:400 17px/1.4 Inter">Each member presents their own design problem in section 2; the team statechart (UC4–UC6) goes with Eric’s UC4 design.</div>
<div style="margin-top:12px;display:flex;gap:16px;align-items:center;font:400 17px Inter;color:var(--muted)">Report: use case in §3.4, design problem in §3.5 ${mk('FILL', 'section numbers (#463)')} ${mk('VERIFY', 'UC9 owner')}</div>`;

// ---------- slide 7 (1.6, 1.7) ----------
const steps = ['coded', 'tested', 'integrated', 'releasable', 'released'];
const ladder = () => `<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px"><span style="font:600 16px Inter;color:var(--muted);margin-right:6px">DoD ladder (EPAT 01 Agile p26)</span>${steps.map((s, i) => `<span style="font:600 17px/1 Inter;padding:7px 12px;border-radius:999px;background:${i === 2 ? '#E3E8FB' : '#ECE6DC'};color:${i === 2 ? '#3E5CD9' : '#1B1A18'}">${s}</span>${i < 4 ? '<span style="color:#A69E92">→</span>' : ''}`).join('')}</div>`;
const spr = [
  ['1 · 2–12 Sep', 'Walking skeleton: sign-in, browse, admin on dev', '<b>integrated</b>: backend on dev 5 Sep; no frontend, so not releasable'],
  ['2 · 14–25 Sep', 'Pay, join a team, see the order, replay-safe', '<b>integrated</b>: merged, tested, Checkout off on dev; 134 of 150 h'],
  ['3 · 28 Sep–9 Oct', 'Invite → pay → refund on deployed dev', '<b>integrated</b>; goal not met under our “on dev” rule; 117 of 150 h'],
  ['4 · 12–23 Oct', 'Complete system on dev; three services on ECS (SWE5001)', mk('FILL', 'step as of the 25 Oct rehearsal')],
];
const retro = [
  ['1 → 2', 'No burndown, no hours', 'daily board snapshots, dated day logs'],
  ['2 → 3', 'A 10,000-line PR; cliff burndown', 'PRs under 1,500 lines, mid-sprint check, “on dev” rule in the DoD'],
  ['3 → 4', mk('FILL', 'retro #451'), 'owners keep board estimates current, checked mid-sprint'],
];
const s7 = () => `
${ladder()}
${tbl(['Sprint', 'Goal', 'Definition of Done reached by the goal increment'], spr.map(s => [`<b style="white-space:nowrap">${s[0]}</b>`, s[1], s[2]]), { fs: 17, w: [160, 450], pad: '6px 11px' })}
<div style="font:400 15.5px/1.3 Inter;color:var(--muted);margin-top:6px">Same basis every sprint: integrated = merged to dev with every check green; releasable = shown working on deployed dev, our “on dev” rule</div>
<div class="frag" data-frag="1" style="margin-top:12px;border-radius:8px;background:#FDEFEC;padding:10px 16px">
 <div style="font:700 17px Inter;color:#B02E1B;margin-bottom:6px">Retro improvement → change in the next sprint</div>
 ${retro.map(r => `<div style="display:flex;gap:12px;font:400 17px/1.35 Inter;margin-bottom:3px"><b style="width:52px;flex:none">${r[0]}</b><span style="width:330px;flex:none">${r[1]}</span><span><span style="color:#E8402A;font-weight:700">→</span> ${r[2]}</span></div>`).join('')}
</div>`;

// ---------- slide 8 (1.8) ----------
function burndown() {
  const xs = ['start', '15', '16', '17', '18', '19', '22', '23', '24', '25', '26'];
  const ideal = [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0], act = [150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16];
  const W = 600, H = 250, L = 48, R = W - 12, T = 12, B = H - 32;
  const X = i => L + 14 + i * ((R - L - 24) / (xs.length - 1)), Y = v => B - (v / 150) * (B - T);
  const pl = (d, c, dash) => `<polyline points="${d.map((v, i) => `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => `<circle cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 50, 100, 150].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 6}" font-size="17" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 24}" font-size="17" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${pl(ideal, '#A69E92', true)}${pl(act, '#3E5CD9')}</svg>`;
}
const ac = ['Admin-only API; 403 non-admin, 401 anonymous', 'Size ≥ 2, lifetime in range, team price below individual; 400 with field errors', 'Stale expectedVersion → 412; writes audited', 'Configured listing is startable; START integration test', 'Contract changes additive, merged before the controller', 'Unit, PostgreSQL IT, authorization tests; ArchUnit green'];
const s8 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:540px;padding:14px 20px 16px">
 <div style="display:flex;justify-content:space-between;align-items:baseline">${kicker('Story card · #221 (US25)')}<span style="font:600 16px Inter;color:var(--muted)">Liu Jin · PR #242</span></div>
 <div style="font:600 18px/1.3 Inter;margin-bottom:8px">As an admin, I can set a listing’s team size, lifetime and team price, so that buyers can start blocs on real listings.</div>
 ${ac.map(a => `<div style="display:flex;gap:8px;font:400 16.5px/1.3 Inter;margin-bottom:5px"><span style="color:#3E5CD9;flex:none">${icon('square-check')}</span><span>${a}</span></div>`).join('')}
 <div style="font:400 16px Inter;color:var(--muted);margin-top:6px">Design problem field: State, Optimistic Offline Lock</div>
</div>
<div style="position:absolute;left:564px;top:0;width:600px">
 ${kicker('Sprint 2 burndown · hours left · report period 15–26 Sep')}
 ${burndown()}
 <div style="font:400 16.5px/1.35 Inter;margin-top:2px">Flat for a week, then a cliff: work merged late in large PRs. Reconstructed from merge dates.</div>
</div>
<div style="position:absolute;left:0;right:0;top:450px;border-radius:8px;background:#E3E8FB;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#3E5CD9">Practice (EPAT 03, 05):</b> two red tests reproduced a quote defect (7 Oct log), then PR #424 refactored admission into shared guards and injected the Factory Method’s creator. Full set of artefacts: backup B1.</div>`;

// ---------- slide 24 (3.1, 3.3) ----------
// Agile Testing Quadrants (EPAT 03 TDD p7), laid out as in the deck: business-facing on top, supporting the team on the left.
const lvl = t => `<span style="display:inline-block;font:600 14.5px/1 Inter;border:1.5px solid #6B655C;color:#1B1A18;border-radius:999px;padding:3px 8px;margin-left:6px;vertical-align:2px">${t}</span>`;
const qrow = (name, level, rest) => `<div style="font:400 18px/1.3 Inter;margin-bottom:6px"><b>${name}</b>${lvl(level)} <span style="color:var(--muted)">·</span> ${rest}</div>`;
const caught = t => `<div style="font:400 17px/1.3 Inter;margin-top:2px"><b style="color:#B02E1B">Caught:</b> ${t}</div>`;
const runs = t => `<div style="font:400 17px/1.3 Inter;color:var(--muted);margin-bottom:2px">Runs in ${t}</div>`;
const quad = (q, axes, how, body, style, frag) => `<div class="card${frag ? ' frag' : ''}"${frag ? ` data-frag="${frag}"` : ''} style="position:absolute;${style};padding:9px 16px">
 <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:5px"><span style="font:700 18px Inter">${q} <span style="font-weight:500;color:var(--muted)">${axes}</span></span><span style="font:600 15px Inter;color:var(--muted)">${how}</span></div>
 ${body}</div>`;
const Q_H = 'height:198px', Q_W = 'width:574px';
const s24 = () => `
${quad('Q2', 'business-facing · supporting the team', 'automated', `
 ${qrow('Story acceptance', 'system', `${mo('StripeRolloutAcceptanceIT', 16)} · real HTTP, scheduler, signed webhooks`)}
 ${qrow('End-to-end', 'system', 'Playwright · 8 specs, on the preview build and on dev')}
 ${runs(`${mo('build-quality', 15)}, ${mo('e2e-preview', 15)}, ${mo('e2e-dev', 15)}`)}
 ${caught('a 502 on e2e-dev during a deploy (3–4 Oct): one host')}`, `left:0;top:0;${Q_W};${Q_H}`, 1)}
${quad('Q3', 'business-facing · critiquing the product', 'manual', `
 ${qrow('Exploratory, acceptance', 'system', 'by hand, logged')}
 <div style="font:400 18px/1.3 Inter;margin-bottom:6px">Stripe Sandbox START and JOIN in a browser (20 Sep, local) · sign-in cookie Secure and HttpOnly on dev (8 Oct)</div>
 ${caught('no defect logged from these runs')}
 <div style="font:400 17px/1.3 Inter;color:#B02E1B;margin-top:4px">Not done: acceptance testing with outside users</div>`, `left:590px;top:0;${Q_W};${Q_H}`, 2)}
${quad('Q1', 'technology-facing · supporting the team', 'automated', `
 ${qrow('Unit, component', 'unit', 'JUnit 185 classes · Vitest 77 files')}
 ${qrow('Integration', 'integration', 'Testcontainers: 45 of 49 classes')}
 ${qrow('Contract, ArchUnit', 'integration', 'openapi-diff · 18 rules')}
 ${runs(`${mo('build-quality', 15)}, ${mo('frontend-check', 15)} (required), ${mo('contract-check', 15)}`)}
 ${caught('two red tests reproduced the join-quote defect (#422)')}`, `left:0;top:208px;${Q_W};${Q_H}`)}
${quad('Q4', 'technology-facing · critiquing the product', 'tools', `
 ${qrow('Performance, load', 'system', 'k6 on SWE5001 ECS, by hand')}
 ${qrow('Security scanning', 'types', 'SAST · dependencies (SCA) · image and IaC configuration · secrets')}
 ${runs('by hand (load); security gates on slide 28')}
 ${caught('full-history gitleaks: 24 matches, 4 badge tokens still open (slide 30)')}`, `left:590px;top:208px;${Q_W};${Q_H}`, 3)}
<div style="position:absolute;left:0;right:0;top:416px;border-radius:8px;background:#E3E8FB;padding:8px 16px">
 <div style="display:flex;justify-content:space-between;margin-bottom:3px"><span style="font:700 17px Inter;color:#3E5CD9">Complex classes under test (proposal §6.3)</span><span style="font:400 15px Inter;color:var(--muted)">Quadrants: EPAT 03 TDD p7 · levels: p5</span></div>
 ${[['Team state machine', `${mo('DealStatusTest', 16)}, ${mo('PaidGroupDealTest', 16)}`],
    ['Last-seat capacity', `${mo('StripeLocalIntegrationTest.twentyBuyersRaceForLastSeat…', 16)} (integration)`],
    ['Refund idempotency', `${mo('RefundServiceTest.lostResponseRetriesSameDurableKey', 16)}`]]
   .map(r => `<div style="display:flex;gap:12px;font:400 18px/1.3 Inter"><b style="width:190px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('')}
</div>`;

// ---------- slide 25 (3.2) ----------
const kv = rows => rows.map(r => `<div style="display:flex;gap:14px;font:400 18px/1.35 Inter;margin-bottom:10px"><b style="width:118px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('');
const s25 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:620px;height:536px;padding:18px 22px">
 ${kicker('The scripted test')}
 ${kv([
   ['Tool', `k6 1.6.1, image pinned by digest · ${mo('perf/k6/browse.js', 16)}`],
   ['Target system', '<b>SWE5001 ECS environment, same code</b>: CloudFront → ALB → Fargate. Never the shared SWE5006 dev host, which also runs its database'],
   ['Load', 'open arrival rate 10 → 200 requests/s in ten 60 s steps; 100 VUs preallocated, 400 max'],
   ['Mix', '7 listing pages : 3 listing details, anonymous; no think time; 10 s timeout'],
   ['Pass, per step', 'p95 &lt; 500 ms · errors &lt; 1% · zero dropped requests'],
   ['Result', `run in the system demo (slide 34) · ${mk('FILL', '5.3 pre-run label and date')}`],
 ])}
</div>
<div style="position:absolute;left:640px;top:0;width:524px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">
  ${kicker('Proposal target (§6.3)', '#B02E1B')}
  <div style="font:600 19px/1.35 Inter">Interactive operations ≤ 2 s with up to 100 concurrent users</div>
  <div style="font:400 18px/1.4 Inter;margin-top:10px">Little’s law <span class="mono" style="font-size:17px">N = X · (R + Z)</span>: 100 users, 0.5 s think, ~30 ms response → <b>X ≈ 190 requests/s</b>, inside the 200 driven, with a p95 gate four times stricter than 2 s</div>
  <div style="font:400 16px/1.35 Inter;color:var(--muted);margin-top:6px">The 0.5 s think time is our assumption</div>
 </div>
 <div class="card" style="padding:16px 20px">
  ${kicker('Expiry ≤ 1 min after the window', '#B02E1B')}
  <div style="font:400 18px/1.4 Inter">Measured <b>locally</b> under 100-user load (30 Sep): 30 teams, worst lag 58.2 s; one cohort waited a full one-minute sweep</div>
  <div style="font:400 16px/1.35 Inter;color:var(--muted);margin-top:6px">On ECS: ${mk('FILL', 'V4 expiry burst, or "local only"')}</div>
 </div>
</div>`;

// ---------- diagram walk-through (rubric V1, V3, V5) ----------
// The full diagram, scaled to `view`, orients the audience; each crop is then shown 1:1 (labels ≥ 24 px) as an opaque
// fragment layer stacked over the body in order. Only the live deck shows the crops: screenshots and the PDF keep the
// orientation view, which stays vector and zoomable.
const nat = (name, W, H) => svg(name).replace('<svg ', `<svg style="display:block;width:${W}px;height:${H}px" `);
// One 1:1 crop layer. `head` repeats the diagram's top `head` px (a sequence diagram's lifeline headers) as a strip
// above the cropped area, so every crop still names its lifelines. z-index keeps the crop above any side text.
const cropLayer = (name, W, H, x, y, label, i, n, head = 0) => { const strip = head > 0 && y >= head; return `<div class="frag zcrop" data-frag="${i + 1}" style="position:absolute;left:0;top:0;width:1164px;height:548px;background:#fff;overflow:hidden;border-radius:6px;box-shadow:0 0 0 1.5px #DDD5C8;z-index:10">
 <div style="position:absolute;left:${-x}px;top:${strip ? head - y : -y}px">${nat(name, W, H)}</div>
 ${strip ? `<div style="position:absolute;left:0;top:0;width:1164px;height:${head}px;overflow:hidden;background:#fff;border-bottom:2px dashed #A69E92"><div style="position:absolute;left:${-x}px;top:0">${nat(name, W, H)}</div></div>` : ''}
 <div style="position:absolute;right:10px;bottom:10px;font:700 15px/1 Inter;letter-spacing:.04em;background:#1B1A18;color:#fff;border-radius:999px;padding:6px 11px">${i + 1}/${n} · ${label}</div></div>`; };
const zoom = (name, { W, H, view, at = 'left:0;top:0', crops }) => {
  const s = Math.min(view[0] / W, view[1] / H);
  return `<style>body:not(.present) .zcrop{display:none}</style>
<div style="position:absolute;${at};width:${Math.round(W * s)}px;height:${Math.round(H * s)}px">${nat(name, Math.round(W * s), Math.round(H * s))}</div>
${crops.map(([x, y, label, head], i) => cropLayer(name, W, H, x, y, label, i, crops.length, head)).join('')}`;
};

// Several diagrams on one slide: each view is a scaled orientation copy at its own place; the crops step through any
// diagram 1:1 in the order given, like zoom().
const fit = v => Math.min(v.view[0] / v.W, v.view[1] / v.H);
const walk = (views, crops) => `<style>body:not(.present) .zcrop{display:none}</style>
${views.map(v => { const s = fit(v), w = Math.round(v.W * s), h = Math.round(v.H * s); return `<div style="position:absolute;${v.at};width:${w}px;height:${h}px">${nat(v.name, w, h)}</div>`; }).join('')}
${crops.map(([name, x, y, label, head], i) => { const v = views.find(d => d.name === name); return cropLayer(name, v.W, v.H, x, y, label, i, crops.length, head); }).join('')}`;
const report = t => `<div style="font:600 15px/1.3 Inter;color:#3E5CD9;margin-top:8px">${icon('book')} ${t}</div>`;
const fact = (k, v, fs = 16.5) => `<div style="margin-bottom:7px"><div style="font:700 13.5px Inter;letter-spacing:.06em;text-transform:uppercase;color:#6B655C">${k}</div><div style="font:400 ${fs}px/1.33 Inter">${v}</div></div>`;

// ---------- slide 9 (2.1) ----------
const s9 = () => `
<div style="position:absolute;left:0;top:0;width:372px">
 ${kicker('4+1 logical view · SAD 04a p24–29')}
 <div style="font:400 16.5px/1.35 Inter;margin-bottom:10px">Java 21 · Spring Boot 4 · MyBatis · PostgreSQL 18 · Redis 8 · Sa-Token · Stripe Checkout | React 19 · TanStack Router and Query · TypeScript | AWS EC2, S3, CloudFront</div>
 ${kicker('Decisions that shape it')}
 ${[['Modular monolith, COLA layers, six contexts', 'pre-ADR D1, D4'], ['Domain framework-free; 18 ArchUnit rules', 'pre-ADR D1 · no ADR'], ['Consequences run before the commit', 'ADR 0002'], ['Cross-service events through an outbox', 'ADR 0007'], ['One codebase, two topologies', 'ADR 0008'], ['Router owns URL state; generated client', 'F-ADR 0001 · 0003']].map(r => `<div style="display:flex;justify-content:space-between;gap:8px;font:400 16px/1.3 Inter;border-bottom:1px solid #E6DFD3;padding:5px 0"><span>${r[0]}</span><b style="white-space:nowrap;color:#6B655C">${r[1]}</b></div>`).join('')}
 <div style="font:400 15px/1.3 Inter;color:var(--muted);margin-top:6px">pre-ADR = locked decision table D1–D20 (skeleton plan, 30 Aug), written before numbered ADRs</div>
 ${report('Report §3.1 · Liu Jin, presented by Eric')}
</div>
${walk([{ name: 'logical-layers', W: 2320, H: 1644, view: [774, 548], at: 'left:390px;top:0' }],
  [['logical-layers', 1160, 0, 'adapter · contract'], ['logical-layers', 1160, 548, 'application'], ['logical-layers', 690, 1096, 'infrastructure depends on domain ports']])}`;

// ---------- slide 10 (2.2) ----------
const strat = [
  ['Language, framework', 'maintainability', '«control», «entity» · all', 'Spring <span class="mono" style="font-size:14.5px">@Service</span> by constructor injection; domain stays plain Java', 'D1–D20, ArchUnit · <i>no ADR</i>'],
  ['Presentation', 'usability', 'GUI «boundary» · UC1–UC9', 'TanStack route + component + Query hook', 'F-ADR 0001, 0009'],
  ['Distribution', 'interoperability', '«control» behind a boundary · all', '<span class="mono" style="font-size:14.5px">@RestController</span> returning <span class="mono" style="font-size:14.5px">Result</span>; generated TS client is the stub', 'D11–D12, F-ADR 0003'],
  ['Persistence', 'integrity', '«entity» · all', 'aggregate + repository port ◁ RepositoryImpl, MyBatis mapper, DO', 'D1, D9 · <i>no ADR</i>'],
  ['External interface', 'provider isolation', 'system «boundary» · UC2–UC6', 'gateway port ◁ Stripe / Google adapter; signed webhook', 'ADR 0001, 0004, 0005'],
  ['Security', 'security', 'every «control» · all', 'login interceptor; <span class="mono" style="font-size:14.5px">checkRole(ADMIN)</span>; CSRF header; rate limits', 'D6–D7, F-ADR 0002'],
  ['Scheduling', 'expiry ≤ 1 min', 'Scheduler actor · UC5, UC6', '<span class="mono" style="font-size:14.5px">@Scheduled</span> job under a database lease, every minute', 'ADR 0002 · <i>job: no ADR</i>'],
  ['Concurrency', 'no oversell', '«entity» with capacity · UC4', 'row locks in a fixed order + guarded UPDATE + unique indexes', 'ADR 0001 · <i>no ADR</i>'],
  ['Domain events', 'decoupling', '«control» → other contexts · UC5–UC7', 'event record + transactional listener; outbox across services', 'ADR 0002, 0003, 0007'],
];
const s10 = () => `
${tbl(['Strategy', 'Concern', 'Analysis objects · use cases', 'Become in design', 'Record'], strat.map(r => [`<b>${r[0]}</b>`, r[1], r[2], r[3], `<span style="color:var(--muted)">${r[4]}</span>`]), { fs: 15.5, w: [150, 120, 230, null, 170], pad: '6px 9px' })}
<div style="position:absolute;left:0;right:0;top:500px;display:flex;justify-content:space-between;font:400 15.5px Inter;color:var(--muted)"><span>Template: SAD 04a p37 (object types, use cases, static and dynamic change). Typical class and sequence changes: slide 11.</span>${report('Report §3.2 · Yorik, Eric').replace('margin-top:8px', 'margin:0')}</div>`;

// ---------- slide 11 (2.2) ----------
const s11 = () => `
<div style="position:absolute;left:0;top:0;width:580px">
 ${fact('Typical class-diagram change', '«boundary» → route, hook and generated client · «control» → REST controller and application service · «entity» → aggregate behind a domain repository port, implemented by a MyBatis mapper · external «boundary» → gateway port with an adapter', 17)}
 ${fact('Typical sequence-diagram change', 'control → entity.update() becomes service → repository.lockDeal() <span class="mono" style="font-size:15px">SELECT … FOR UPDATE</span> → aggregate.joinPaid() → guarded <span class="mono" style="font-size:15px">UPDATE</span>, in one transaction', 17)}
 ${fact('Rationale', 'the domain names its needs as interfaces; infrastructure supplies them (dependency inversion), so persistence and providers can change without touching the rules', 17)}
 <div style="font:400 15px/1.3 Inter;color:var(--muted)">Tags on the design side: Pres, Dist, Pers, Ext, Sec, Conc, Sched, Event (slide 10)</div>
 ${report('Report §3.2 · transition strategies')}
</div>
${walk([{ name: 'transition-pair', W: 2320, H: 2270, view: [560, 548], at: 'left:604px;top:0' }],
  [['transition-pair', 0, 0, 'analysis: UC4'], ['transition-pair', 1160, 0, 'strategy key'], ['transition-pair', 580, 1700, 'persistence: port ◁ impl']])}`;

// ---------- slide 12 (2.3) ----------
const s12 = () => `<div style="position:absolute;left:0;top:520px">${report('Report §3.3 · use case diagram and specifications')}</div>` + walk([{ name: 'use-case', W: 2320, H: 1096, view: [1164, 514], at: 'left:0;top:0' }],
  [['use-case', 0, 312, 'Buyer and the money path'], ['use-case', 560, 40, 'resolution: Scheduler, Stripe'], ['use-case', 1160, 52, 'out of scope']]);

// ---------- slide 13 (2.4) ----------
const s13 = () => `
<div style="position:absolute;left:0;top:520px">${report('Report §3.4 · Eric (UC4 analysis model)')}</div>
${walk([{ name: 'uc4-analysis-class', W: 1160, H: 1070, view: [560, 512], at: 'left:0;top:0' }, { name: 'uc4-analysis-seq', W: 2320, H: 2186, view: [584, 512], at: 'left:580px;top:0' }],
  [['uc4-analysis-class', 0, 522, 'entities'], ['uc4-analysis-seq', 560, 1150, 'seat available: join', 160], ['uc4-analysis-seq', 1160, 1720, 'else: refund through the gateway', 160]])}`;

// ---------- slide 14 (2.4) ----------
const tagKey = [['Pres', 'presentation'], ['Dist', 'distribution'], ['Pers', 'persistence'], ['Ext', 'external interface'], ['Sec', 'security'], ['Conc', 'concurrency'], ['Sched', 'scheduling'], ['Event', 'domain events']];
const s14 = () => `
${walk([{ name: 'uc4-design-class', W: 2320, H: 2314, view: [900, 548], at: 'left:0;top:0' }],
  [['uc4-design-class', 1160, 0, 'payment meets groupbuy through a port'], ['uc4-design-class', 1160, 1096, 'aggregate behind its repository port'], ['uc4-design-class', 1160, 1652, 'MyBatis implementation of the port']])}
<div style="position:absolute;left:924px;top:0;width:240px">
 ${kicker('Strategy tags · slide 10')}
 ${tagKey.map(t => `<div style="font:400 18px/1.45 Inter"><b>${t[0]}</b> ${t[1]}</div>`).join('')}
 <div style="font:400 17px/1.35 Inter;margin-top:10px">Typed signatures, visibility and multiplicities: SAD 01b p14–16</div>
 ${report('Report §3.4 · Eric')}
</div>`;

// ---------- slide 15 (2.4) ----------
const s15 = () => `
${walk([{ name: 'uc4-design-seq', W: 2320, H: 3306, view: [380, 548], at: 'left:0;top:0' }],
  [['uc4-design-seq', 700, 760, 'team row locked first', 180], ['uc4-design-seq', 1100, 1840, 'guarded update · last seat', 180], ['uc4-design-seq', 1100, 2500, 'loser: compensation, refund owed', 180]])}
<div style="position:absolute;left:404px;top:0;width:760px">
 ${fact('Major flow', 'paid join confirmed by the Stripe webhook: signature checked outside the transaction, then one transaction (READ COMMITTED, 10 s), drawn as <span class="mono" style="font-size:17px">critical</span> because UML has no transaction fragment', 18)}
 ${fact('Concurrency strategy', 'lock order team row → intent → payment → receipt, each <span class="mono" style="font-size:17px">SELECT … FOR UPDATE</span>; a racing join waits on the team row', 18)}
 ${fact('Guarded update', '<span class="mono" style="font-size:17px">UPDATE … WHERE current_size = expected AND status = open</span>; the last seat publishes <span class="mono" style="font-size:17px">TeamSucceeded</span>', 18)}
 ${fact('Alternative', 'DEAL_FULL or DEAL_CLOSED: receipt <span class="mono" style="font-size:17px">compensation_required</span>, a refund obligation, never an extra member', 18)}
 ${fact('Evidence', '<span class="mono" style="font-size:17px">StripeLocalIntegrationTest</span> <span class="mono" style="font-size:17px">.twentyBuyersRaceForLastSeat…</span>: 20 paid joins, 1 seat, 19 refunds owed (local, PostgreSQL in Testcontainers)', 18)}
 ${report('Report §3.4 · Eric')}
</div>`;

// ---------- slide 16 (2.4) ----------
const s16 = () => `
${walk([{ name: 'team-statechart', W: 2320, H: 548, view: [1164, 280], at: 'left:0;top:0' }],
  [['team-statechart', 0, 0, 'OPEN and its joins'], ['team-statechart', 1160, 0, 'succeeded or expired']])}
<div style="position:absolute;left:0;top:296px;width:1164px;display:grid;grid-template-columns:1fr 1fr;gap:12px 24px">
 ${fact('Why a statechart (SAD 03c p4, p11)', 'team state gates UC4, UC5, UC6 and UC8', 17)}
 ${fact('Code it matches', '<span class="mono" style="font-size:15px">DealStatus.canTransitionTo</span>: only OPEN moves, never to itself', 17)}
 ${fact('Guards', '<span class="mono" style="font-size:15px">GroupDeal.joinPaid</span>: price → new member → window → seat', 17)}
 ${fact('Time event', '<span class="mono" style="font-size:15px">at(expireTime)</span>, detected by <span class="mono" style="font-size:15px">TeamExpirySweepJob</span> every minute', 17)}
 ${fact('Greyed', 'FAILED and CANCELED are declared but never written', 17)}
 <div>${report('Report §3.4 · Eric (team statechart)')}</div>
</div>`;

// ---------- slides 17–20 (2.5): one 20 s slot per member ----------
// Each slot: the SDP fields (SDP 06 design case study p10) beside the after-diagram; crop 1 = class structure,
// crop 2 = the collaboration (sequence), both 1:1.
const mono = t => `<span class="mono" style="font-size:15px">${t}</span>`;
const pslot = (rows, name, W, H, crops, rep) => `
<div style="position:absolute;left:0;top:0;width:560px">
 ${rows.map(r => fact(r[0], r[1], 16)).join('')}
 ${report(rep)}
</div>
${walk([{ name, W, H, view: [580, 548], at: 'left:584px;top:0' }], crops)}`;
const s17 = () => pslot([
  ['Where it shows (class diagram)', `the «create» of ${mono('GroupDeal')}: start-team rules must stay fixed while how a team is built may vary; until #424 the Client also named ${mono('new StandardPaidGroupDealCreator()')}`],
  ['Cause for redesign · what varies (SDP 05a p15)', `creating an object by specifying a class explicitly · the construction step varies while ${mono('initiate')} stays fixed (GoF p136: a template method calling a factory method)`],
  ['Candidates', 'Factory Method · static factory (static “forbids subclassing of the base factory class”, SDP 01a p34) · Builder · Abstract Factory'],
  ['Participants', `Creator ${mono('PaidGroupDealCreator')} (final ${mono('initiate')} validates, then abstract ${mono('createGroupDeal')}) · ConcreteCreator ${mono('StandardPaidGroupDealCreator')} · Product ${mono('GroupDeal')} · Client ${mono('PaidGroupDealService')}`],
  ['Needed because · liability (SDP 05a p35)', `every creator keeps the start rules (final initiate); a test or config swaps the creator without editing the service (#421) · one ConcreteCreator today, chosen in config; product class fixed (${mono('GroupDeal')} is final)`],
  ['Tests', `${mono('PaidGroupDealServiceTest.startUsesTheInjectedCreator')}`],
], 'fm-start', 1160, 1400, [['fm-start', 0, 0, 'Client · Creator'], ['fm-start', 0, 852, 'initiate → createGroupDeal']], 'Report §3.5 · Eric (UC3)');
const s18 = () => pslot([
  ['Where it shows (sequence)', 'an expiry that called order and refund code inline: groupbuy would change for every new consequence (ADR 0002)'],
  ['Cause for redesign · what varies', 'tight coupling · “number of objects that depend on another object”'],
  ['Candidates', 'Observer · Mediator · direct facade calls · a message broker (ADR 0002)'],
  ['Participants', `Subject (attach, notify): Spring’s ${mono('ApplicationEventPublisher')} and multicaster · Observer: the listener contract · ConcreteObservers ${mono('OrderLifecycleSubscriber')} @Order(10), ${mono('TeamExpiryRefundSubscriber')} @Order(20), before commit · event source ${mono('GroupDealExpiryService')}`],
  ['Needed because · liability', 'proposal §6.3: new team consequences without editing domain logic · implicit control flow; a wrong phase is a silent bug'],
  ['Tests', `${mono('TeamExpiryRefundSubscriberTest')} (rolls the expiry back on failure)`],
], 'observer-expiry', 1160, 1870, [['observer-expiry', 0, 600, 'Observer · ConcreteObservers'], ['observer-expiry', 0, 1316, 'publish → before commit, in order']], 'Report §3.5 · Yorik (UC6)');
const s19 = () => pslot([
  ['Where it shows (class diagram)', 'brute force: the SQS and SNS transports each copy the batch loop, lease checks and result mapping'],
  ['Cause for redesign · what varies', 'algorithmic dependencies · “steps of an algorithm”'],
  ['Candidates', 'Template Method · Strategy (the whole algorithm does not vary) · Adapter per SDK'],
  ['Participants', `AbstractClass ${mono('BatchingOutboxEventTransport&lt;E&gt;')}, final ${mono('publish')} · primitive operations ${mono('entry')}, ${mono('send')} · ConcreteClasses ${mono('Sqs…')}, ${mono('SnsOutboxEventTransport')}`],
  ['Needed because · liability', 'two providers today: ElasticMQ/SQS locally, SNS on ECS (ADR 0007) · a subclass cannot change the loop'],
  ['Tests', `${mono('OutboxBatchFailureTest')} (6, both providers)`],
], 'template-method-outbox', 1160, 1724, [['template-method-outbox', 0, 290, 'template method'], ['template-method-outbox', 0, 1176, 'publish calls entry, send']], 'Report §3.5 · Pan (notifications)');
const s20 = () => pslot([
  ['Where it shows (class diagram)', `brute force: browse and the team quote each switch on ${mono('pricingMode')} with the arithmetic inline`],
  ['Cause for redesign · what varies', 'algorithmic dependencies · “an algorithm”'],
  ['Candidates', 'Strategy · Template Method (no shared skeleton) · Decorator (no stacking rule)'],
  ['Participants', `Strategy ${mono('PricingStrategy')} · ConcreteStrategy ${mono('DualPricingStrategy')} · Contexts ${mono('ProductAppServiceImpl')}, ${mono('TeamPriceAppServiceImpl')} · registry ${mono('PricingStrategyResolver')}`],
  ['Needed because · liability (SDP 05a p35)', 'proposal §6.3: new pricing schemes are a design driver · one ConcreteStrategy today'],
  ['Tests', `${mono('PricingStrategyResolverTest')} (duplicate mode fails startup), ${mono('DualPricingStrategyTest')}`],
], 'strategy-pricing', 1160, 1770, [['strategy-pricing', 0, 300, 'resolver · Strategy'], ['strategy-pricing', 0, 1222, 'quote → resolve → strategy']], 'Report §3.5 · Lim (UC1)');

// ---------- slides 21–22 (2.5): the full-form problem ----------
const s21 = () => `
<div style="position:absolute;left:0;top:0;width:560px">
 ${fact('Problem (UC9)', 'publish, delist, revise and delete are legal in different lifecycle states', 17)}
 ${fact('Where it shows · brute-force design (RT 3.5)', 'the same <span class="mono" style="font-size:15px">switch (status)</span> in four operations of <span class="mono" style="font-size:15px">Product</span>; in the sequence, <span class="mono" style="font-size:15px">publish()</span> branches on status', 17)}
 ${fact('Why redesign (SDP 05a p4 wording)', 'hard to maintain: one rule in four places · hard to extend: a new state edits every switch · hard to reuse: the rule cannot be tested apart from Product', 17)}
 ${fact('What varies (SDP 05a p13)', '“states of an object”', 17)}
 ${fact('Candidates', '<b>State</b> · Strategy (nobody chooses; the state decides) · transition table, as on the team', 17)}
 ${report('Report §3.5 · Liu Jin (UC9)')}
</div>
${walk([{ name: 'state-before', W: 1160, H: 1120, view: [586, 548], at: 'left:578px;top:0' }],
  [['state-before', 0, 0, 'brute force: class'], ['state-before', 0, 570, 'brute force: sequence']])}`;
const s22 = () => `
<div style="position:absolute;left:0;top:0;width:690px">
 ${fact('Participants', 'Context <span class="mono" style="font-size:15px">Product</span> · State <span class="mono" style="font-size:15px">ProductStatus</span> · ConcreteStates <span class="mono" style="font-size:15px">DRAFT</span>, <span class="mono" style="font-size:15px">ON_SALE</span>, <span class="mono" style="font-size:15px">OFF_SALE</span>: enum constants with bodies, each compiled to its own subclass · client <span class="mono" style="font-size:15px">AdminListingAppServiceImpl</span>', 16.5)}
 ${fact('Implementation decisions', 'the ConcreteState returns its successor and the Context applies it · default bodies refuse with 409 <span class="mono" style="font-size:15px">PRODUCT_STATE_CONFLICT</span> · stateless, shared states · each transition returns a new revision, version + 1; a stale write gets 412', 16.5)}
 ${fact('Liability', 'a new state still needs a constant, its overrides and tests; the app service keeps one switch that picks the command, not its legality', 16.5)}
 ${fact('Why the team uses a table instead', 'only OPEN has behaviour; SUCCEEDED and EXPIRED are final, so <span class="mono" style="font-size:15px">DealStatus.canTransitionTo</span> suffices (SDP 05a p35). The proposal promised State there: slide 4', 16.5)}
 ${fact('Tests', '<span class="mono" style="font-size:15px">ProductStatusTest.onSalePublishThrowsStateConflict</span>, <span class="mono" style="font-size:15px">ProductLifecycleTest</span>', 16.5)}
 ${report('Report §3.5 · Liu Jin (UC9)')}
</div>
${walk([{ name: 'state-after', W: 1160, H: 1424, view: [450, 548], at: 'left:714px;top:0' }],
  [['state-after', 0, 330, 'State · ConcreteStates'], ['state-after', 0, 870, 'after: publish delegates']])}`;

// ---------- slide 23 (2.6) ----------
const s23 = () => `
${walk([{ name: 'core-erd', W: 2320, H: 1190, view: [720, 548], at: 'left:0;top:0' }],
  [['core-erd', 580, 54, 'deal · membership'], ['core-erd', 1160, 600, 'payment · refund']])}
<div style="position:absolute;left:744px;top:0;width:420px">
 ${kicker('Mapping decisions · SAD 04a p53–61')}
 ${fact('Keys', 'UUIDv7 surrogate primary keys from PostgreSQL 18')}
 ${fact('Associations', 'logical FK columns; integrity is checked by the application. No FK or CHECK constraints is our DDL convention, not a course rule')}
 ${fact('Aggregates', 'one table per aggregate root; no class hierarchy is persisted, so no inheritance mapping')}
 ${fact('Race backstops', 'unique (deal, user) membership · one refund per payment · one order per payment')}
 ${report('Report §3.6 · Liu Jin · all tables: B4')}
</div>`;

// ---------- slide 26 (4.2) ----------
const tree = [['backend/', 'Maven COLA modules; services/ for SWE5001'], ['frontend/', 'React SPA'], ['contracts/', 'openapi.json, the API contract'], ['deployment/', 'Compose, SSM bundle, deploy.sh'], ['infra/', 'Terraform: dev frontend, SWE5001'], ['perf/ · docs/', 'k6 · ADRs, sprint logs'], ['.github/', 'workflows, CODEOWNERS, Dependabot']];
const art = [
  ['Backend image', `${mo('git-&lt;sha&gt;-run-&lt;n&gt;', 15.5)}, deployed by ${mo('@sha256', 15.5)} digest`, 'ECR, immutable tags, newest 30 kept'],
  ['API contract', `${mo('openapi.json', 15.5)} committed; drift fails CI`, 'repository'],
  ['TS client', 'generated from the contract, gitignored', 'build only (F-ADR 0003)'],
  ['Frontend bundle', 'content-hashed files, immutable cache', 'S3 behind CloudFront'],
  ['DB schema', 'Flyway V1–V28', 'repository'],
  ['Infrastructure', 'Terraform plan saved, applied by an operator; CI never applies', 'S3 state, DynamoDB lock'],
  ['SBOM', '<span style="color:#B02E1B">not produced</span>', '—'],
];
const s26 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:520px;height:548px;padding:14px 20px">
 ${kicker('Source · one monorepo')}
 ${tree.map(t => `<div style="display:flex;gap:10px;font:400 17px/1.3 Inter;margin-bottom:3px">${mo(t[0], 16)}<span style="color:var(--muted)">${t[1]}</span></div>`).join('')}
 <div style="margin-top:12px">${kicker('Branching · EPAT 07 CI p41–44')}</div>
 <div style="font:400 17px/1.35 Inter"><b>Branch by Feature</b>: one short branch per story, a pull request, squash merge onto the trunk <b>dev</b>; linear history since 12 Sep. Instead of merging trunk daily, the branch must be up to date to merge.</div>
 <div style="margin-top:12px">${kicker('Who may change it')}</div>
 <div style="font:400 17px/1.35 Inter">GitHub: a <b>code owner</b> approves (backend Liu Jin, frontend Lim, pipelines Pan), six checks green. AWS: IAM Identity Center, one-hour sessions ${mk('VERIFY', 'MFA enforced')}. CI: one OIDC role per job, no AWS keys in GitHub.</div>
 <div style="font:400 16px/1.35 Inter;color:#B02E1B;margin-top:6px">Exception: two IAM users for SSM port forwarding, one with an access key</div>
</div>
<div style="position:absolute;left:540px;top:0;width:624px">
 ${kicker('Artefacts · each traceable to a commit')}
 ${tbl(['Artefact', 'Identity', 'Stored in'], art.map(a => [`<b>${a[0]}</b>`, a[1], `<span style="color:var(--muted)">${a[2]}</span>`]), { fs: 16.5, w: [150, 270], pad: '7px 10px' })}
</div>`;

// ---------- slide 27 (4.3) ----------
const s27 = () => zoom('cicd-pr-vs-merge', { W: 2320, H: 1096, view: [1164, 548], crops: [[0, 0, 'PR · backend'], [1160, 0, 'PR · frontend, security'], [0, 548, 'gate · delivery'], [1160, 548, 'deploy · verification']] });

// ---------- slide 28 (4.3, 3.3) ----------
// Stages as in DSOEA 07 slide 3 (pre-commit, commit, build, test, deploy).
const sec = [
  ['Pre-commit', 'Lefthook: format, lint, type-check (frontend)', 'style and type errors', ''],
  ['Commit · PR', `<b>gitleaks</b> ${req2()} on the PR’s commits, full history weekly; code-owner review; actionlint`, 'secrets, unsafe workflows', ''],
  ['Build', `SAST: <b>SonarCloud</b> ${req2()}, SpotBugs; SCA: Trivy deps, Dependabot daily`, 'code flaws, vulnerable libraries', 'CodeQL off: code scanning unavailable on this repository'],
  ['Test', `<b>Trivy image</b> ${req2()} (HIGH, CRITICAL); Trivy config, deployment-bundle rules`, 'image CVEs, IaC and Compose misconfiguration', 'no DAST against the running site'],
  ['Deploy', 'OIDC role per job; image by digest; read-only container, all capabilities dropped', 'stolen keys, swapped images', 'rebuilt after merge, not rescanned (ECR scans on push)'],
];
function req2() { return '<span style="font:700 13.5px/1 Inter;color:#3E5CD9;border:1.5px solid #3E5CD9;border-radius:999px;padding:2px 6px;vertical-align:2px">required</span>'; }
const s28 = () => `
${tbl(['Stage', 'Control', 'Catches', 'Gap'], sec.map(r => [`<b style="white-space:nowrap">${r[0]}</b>`, r[1], r[2], r[3] ? `<span style="color:#B02E1B">${r[3]}</span>` : '—']), { fs: 17, w: [130, 470, 240], pad: '9px 11px' })}
<div style="position:absolute;left:0;right:0;top:470px;border-radius:8px;background:#E3E8FB;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#3E5CD9">Types tested (slide 24, Q4):</b> static analysis, dependency (SCA), image and IaC configuration, secrets. Every third-party action is pinned to a commit SHA. Stages: DSOEA 07 slide 3.</div>`;

// ---------- slide 29 (4.4) ----------
const cd = [
  ['Environments', 'one: <b>dev</b>. The SWE5006 prod stack was planned, not applied (#226)'],
  ['Approver', 'the <b>code owner</b> on the pull request; the merge releases'],
  ['Practice', 'every merge deploys automatically to <b>dev</b>, not production; no production, so no promotion gate. SWE5001 prod waits for an approved promotion PR: <b>Continuous Delivery</b> (EPAT 07 CI p34–35)'],
  ['Verified by', 'on-host health check with <b>automatic rollback</b>; public health via CloudFront; Playwright smoke on dev'],
  ['Trade-off', 'PostgreSQL and Redis on the app host: cheap and always on, one point of failure'],
];
const s29 = () => `
<div style="position:absolute;left:0;top:0;width:392px">
 ${cd.map(r => `<div style="margin-bottom:12px">${kicker(r[0])}<div style="font:400 17px/1.35 Inter;margin-top:-4px">${r[1]}</div></div>`).join('')}
</div>
${zoom('dev-deployment', { W: 2320, H: 1096, view: [756, 548], at: 'left:408px;top:20px', crops: [[0, 20, 'request path'], [940, 300, 'one host'], [1160, 548, 'deploy path']] })}`;

// ---------- slide 30 (4.5) ----------
const finds = [
  ['First dev deploys failed (14–17 Sep)', 'Hand-built environment drifted from its design: OIDC trust used the old subject format; the deploy policy kept a placeholder instance ID; port 8589 was opened wide', 'Fixed on dev by hand; the prod stack renders each value in Terraform, so a plan shows it. No deploy failed after 17 Sep (to 19 Oct)'],
  ['build-quality red for two days (19–20 Oct)', 'Dependabot upgraded the SpotBugs engine without its Maven plugin; the plugin lacked the engine’s BCEL classes', 'Upgrade both together, one Dependabot group, a POM policy test (PR #580)'],
  ['Weekly full-history gitleaks failing (since 21 Sep)', '20 matches were SHA-256 file digests in a contract baseline, not secrets', 'Each digest re-hashed, then fingerprinted exactly (PR #580); 4 badge-token findings still open'],
];
const s30 = () => `
${tbl(['Finding', 'Root cause', 'Fix'], finds.map(f => [`<b>${f[0]}</b>`, f[1], f[2]]), { fs: 17, w: [250, 470], pad: '11px 12px' })}
<div style="position:absolute;left:0;right:0;top:440px;border-radius:8px;background:#FDEFEC;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#B02E1B">Still open:</b> the Stripe webhook never confirms a payment on dev (#517), so a green pipeline is not yet a working paid flow. Sources: backend-prod-terraform-deployment.md, ci-failure-audit-20261021.md.</div>`;

// ---------- slide 31 (4.1, 4.6) ----------
const live = [
  ['P1', 'The pull request: one frontend change, six required checks green', '60 s'],
  ['P2', 'Lim approves as the frontend code owner', '30 s'],
  ['P3', 'Squash merge: one commit on dev', '20 s'],
  ['P4', 'Delivery runs; the backend PR and deploy, triggered beforehand', '90 s'],
  ['P5', 'Verified: e2e-dev green, the change live on dev', '90 s'],
  ['P6', 'Merge to live in minutes; hand-over', '40 s'],
];
const s31 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 18, w: [70, null, 80], pad: '10px 12px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Why the backend is pre-run')}<div style="font:400 17px/1.4 Inter">Backend PR checks take about 16 min, longer than the segment, so that run was triggered before the talk and we walk through it finished.</div></div>
 <div class="card" style="padding:16px 20px">${kicker('Backup video', '#B02E1B')}<div style="font:400 17px/1.4 Inter">Recorded on dev by 24 Oct, one clip per step, in the cloud and on a USB drive. ${mk('FILL', 'link')}</div></div>
</div>`;

// ---------- slide 32 (5.1, 5.2) ----------
// Environment per step follows the 23 Oct go/no-go (open item 1); the NO-GO wording is in the run book.
const env5 = (t, tone = 'ok') => {
  const k = { ok: ['#E3E8FB', '#3E5CD9'], rec: ['#FFEDC7', '#7A5C20'], loc: ['#ECE6DC', '#1B1A18'], ecs: ['#FBDFD9', '#B02E1B'] }[tone];
  return `<span style="display:inline-block;font:700 15px/1 Inter;background:${k[0]};color:${k[1]};border-radius:999px;padding:5px 10px;white-space:nowrap">${t}</span>`;
};
const plan5 = [
  ['D1', 'Leader signs in, starts a bloc, pays in Stripe Sandbox, copies the invite', 'UC2 · UC1 · UC3 · UC7', 'security: only a signed webhook confirms a payment', env5('dev, if GO')],
  ['D2', 'Joiner opens the invite, joins and pays: 2 of 2, the team succeeds', 'UC4 · UC5 · UC7', 'correctness: guards re-checked after payment', env5('dev, if GO')],
  ['D3', 'My blocs and my orders, filtered by the URL', 'UC8', 'functionality', env5('dev')],
  ['D4', 'The bloc started at 16:05 expired and refunded itself', 'UC5 · UC6', 'unattended expiry and refund, recorded once; ≤ 1 min measured locally (slide 34)', env5('dev, if GO')],
  ['D5', 'No session, a buyer on admin, a write without the marker', '—', 'security: 401 · 403 · 400 · HttpOnly', env5('dev')],
  ['D6', 'Twenty paid buyers race for the last seat', 'UC4', 'correctness under concurrency', env5('local test', 'loc')],
  ['34', 'Scripted k6 load, pre-run', 'UC1', 'performance: ≤ 2 s at 100 users', env5('SWE5001 ECS', 'ecs')],
  ['36', 'Back to the §4 pipeline run', '—', 'maintainability', env5('dev')],
];
const s32 = () => `
${tbl(['Step', 'What you see', 'Use cases', 'Quality attribute (slide 3)', 'Runs on'], plan5.map(r => [`<b>${r[0]}</b>`, r[1], `<span style="white-space:nowrap">${r[2]}</span>`, r[3], r[4]]), { fs: 17.5, w: [60, 400, 150, 300], pad: '8px 10px' })}
<div style="position:absolute;left:0;right:0;top:486px;font:400 17px/1.35 Inter;color:var(--muted)">“dev, if GO”: live on deployed dev after the 23 Oct go/no-go (#517, #223); otherwise the 24 Oct recording of that step, said as recorded. A local result is never presented as a dev one.</div>`;

// ---------- slide 33 (5.1, 5.2, 5.5) ----------
const live5 = [
  ['D1', 'Start a bloc and share the invite', '120 s'],
  ['D2', 'A friend joins and pays: the team succeeds', '120 s'],
  ['D3', 'My blocs and my orders', '75 s'],
  ['D4', 'The 16:05 bloc: expired, order cancelled, refunded', '90 s'],
  ['D5', 'Security: 401, 403, 400, HttpOnly cookie', '60 s'],
  ['D6', 'Last-seat race: the test, in the IDE', '45 s'],
];
const s33 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live5.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 18, w: [70, null, 80], pad: '10px 12px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Two buyers, one screen')}<div style="font:400 17px/1.4 Inter">Two Chrome profiles, Leader and Joiner, both signed in with Google. Stripe Sandbox: a test card, no money moves.</div></div>
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Started before the talk')}<div style="font:400 17px/1.4 Inter">A team of two on <span class="mono" style="font-size:16px">US34-04</span> at 16:05: its 40-minute window, the shortest allowed, ends ≈ 16:45.</div></div>
 <div class="card" style="padding:16px 20px">${kicker('Backup video', '#B02E1B')}<div style="font:400 17px/1.4 Inter">Recorded on dev by 24 Oct, one clip per step, in the cloud and on a USB drive. ${mk('FILL', 'link')}</div></div>
</div>`;

// ---------- slide 34 (5.3) ----------
const steps33 = [10, 20, 40, 60, 80, 100, 125, 150, 175, 200];
const s34 = () => `
<div style="position:absolute;left:0;top:0;width:600px">
 ${kicker('k6 pre-run · SWE5001 ECS environment, same code · not the dev host', '#B02E1B')}
 ${tbl(['Requests/s', 'p95 (ms)', 'Errors', 'Dropped', 'Step'], steps33.map(r => [`<b>${r}</b>${r >= 175 ? ' <span style="color:#B02E1B">●</span>' : ''}`, '—', '—', '—', '—']), { fs: 17, w: [130, 110, 100, 110], pad: '5px 12px' })}
 <div style="font:400 16px/1.35 Inter;color:var(--muted);margin-top:6px"><span style="color:#B02E1B">●</span> at or above ≈ 190 requests/s, the proposal’s 100 users (slide 25) · results ${mk('FILL', 'from the 24 Oct pre-run')}</div>
</div>
<div style="position:absolute;left:624px;top:0;width:540px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">
  ${kicker('Target (proposal §6.3)', '#B02E1B')}
  <div style="font:600 19px/1.35 Inter">≤ 2 s with up to 100 concurrent users</div>
  <div style="font:400 18px/1.4 Inter;margin-top:8px">Our gate per step: p95 &lt; 500 ms, errors &lt; 1 %, no dropped requests</div>
  <div style="font:700 18px/1.4 Inter;margin-top:8px">Verdict: ${mk('FILL', 'pass or fail per gate')}</div>
 </div>
 <div class="card" style="padding:16px 20px;margin-bottom:12px">
  ${kicker('Expiry ≤ 1 min')}
  <div style="font:400 18px/1.4 Inter">Sweep every minute under a lease; worst lag 58.2 s for 30 teams under 100-user load (<b>local</b>, 30 Sep)</div>
 </div>
 <div class="card" style="padding:16px 20px">
  ${kicker('Not claimed')}
  <div style="font:400 18px/1.4 Inter">Authenticated paths under load on a deployed system; anything about the dev host; scaling (one task)</div>
 </div>
</div>`;

// ---------- slide 35 (5.2) ----------
const s35 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:560px;height:240px;padding:18px 22px">
 ${kicker('Dev, the system you saw')}
 ${kv([
   ['Compute', 'one EC2 t3.large, no Auto Scaling group'],
   ['Data', 'PostgreSQL 18 and Redis 8 on the same host'],
   ['Effect', 'one point of failure; a deploy restarts the API'],
   ['Read', 'AWS, read-only, 21 Oct'],
 ])}
</div>
<div class="card" style="position:absolute;left:584px;top:0;width:580px;height:240px;padding:18px 22px">
 ${kicker('Why, deliberately', '#B02E1B')}
 <ul class="b" style="font:400 18px/1.4 Inter;margin:0">
  <li style="margin-bottom:8px">one complete system, always on, at low cost</li>
  <li style="margin-bottom:8px">the proposal lists no scalability target (§6.3)</li>
  <li>one codebase, two topologies (backend ADR 0008)</li>
 </ul>
</div>
<div style="position:absolute;left:0;right:0;top:264px;border-radius:8px;background:#ECE6DC;padding:14px 18px;font:400 18px/1.4 Inter"><b>Scale path, not claimed here:</b> the same code on ECS in our SWE5001 topology, commerce 2–6 tasks on requests per target, notification 1–4 on queue backlog (backup B10).</div>`;

// ---------- slide 36 (5.4) ----------
const seams = [
  ['New pricing mode', 'one more <span class="mono" style="font-size:16px">PricingStrategy</span> and its <span class="mono" style="font-size:16px">PricingMode</span> constant', 'Strategy · slide 20'],
  ['New consequence of a team’s outcome', 'one more subscriber to <span class="mono" style="font-size:16px">TeamSucceeded</span> / <span class="mono" style="font-size:16px">TeamExpired</span>', 'Observer · slide 18'],
  ['New payment provider', 'one more adapter behind <span class="mono" style="font-size:16px">CheckoutGateway</span>, <span class="mono" style="font-size:16px">RefundGateway</span>', 'gateway port, external-interface strategy · slide 11'],
];
const s36 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:420px;padding:18px 22px">
 ${kicker('Shown in §4 (slide 31)')}
 <div style="font:400 18px/1.45 Inter">One change, live on dev: six required checks, a code-owner review, an automatic deploy and a smoke test. 18 ArchUnit rules hold the module boundaries on every build.</div>
</div>
<div style="position:absolute;left:444px;top:0;width:720px">
 ${kicker('Proposal §6.3: the primary design driver', '#B02E1B')}
 ${tbl(['Change', 'Added as', 'Pattern'], seams.map(r => [`<b>${r[0]}</b>`, r[1], r[2]]), { fs: 17.5, w: [230, null, 190], pad: '10px 12px' })}
 <div style="font:400 17px/1.35 Inter;color:var(--muted);margin-top:10px">Existing rules stay unedited: each change adds classes; a pricing mode also adds one enum constant.</div>
</div>`;

const out = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 13: s13, 14: s14, 15: s15, 16: s16, 17: s17, 18: s18, 19: s19, 20: s20, 21: s21, 22: s22, 23: s23, 24: s24, 25: s25, 26: s26, 27: s27, 28: s28, 29: s29, 30: s30, 31: s31, 32: s32, 33: s33, 34: s34, 35: s35, 36: s36 };
for (const s of C.SLIDES) if (!out[s.n]) out[s.n] = s.slot === null ? (() => '') : pending(s.n);
// Rubric G8: every example slide names the report section holding the full set. §2 slides carry it in their layout;
// the others get it in the gap under the title. Our section numbers follow #463 and stay FILL until it is final;
// RT names the report template section the content answers.
const repSec = {
  3: 'scope, quality attributes · RT 1.4.1–1.4.3', 4: 'status, promised vs delivered · RT 1.4, 2.2', 5: 'milestones, effort · RT 2.3',
  6: 'ownership · RT 2.1, 3.4, 3.5', 7: 'sprints, DoD, retrospectives', 8: 'appendix: agile artefacts',
  24: 'types of testing', 25: 'performance test', 26: 'source control strategy · RT 4.1', 27: 'continuous integration · RT 4.2',
  28: 'pipeline security · RT 4.2', 29: 'continuous delivery · RT 4.3', 30: 'technical findings',
};
for (const [n, t] of Object.entries(repSec)) {
  const body = out[n];
  out[n] = () => body() + `<div style="position:absolute;right:0;top:-27px;font:600 15px/1 Inter;color:#3E5CD9;display:flex;gap:6px;align-items:center">${icon('book')} Report ${mk('FILL', '§ (#463)')} · ${t}</div>`;
}
// Closing slide (dark, full bleed).
out[37] = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8 · d1b3np88z657cp.cloudfront.net</div>`;

module.exports = out;
