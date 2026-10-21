const { icon, mk, lockup, svg } = require('../../../presentation-kit/lib');
const C = require('./content');
// Accent fills never carry text: accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.
// Legibility (rubric V3): body and table text 17–18 px or larger on the 1280 × 720 canvas.

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
const ucs = [['UC1', 'Browse listings, two prices'], ['UC2', 'Sign in with Google'], ['UC3', 'Start a team, pay first'], ['UC4', 'Join a team, pay first'], ['UC5', 'Team succeeds or expires'], ['UC6', 'Refund on expiry'], ['UC7', 'Share an invite, credit the sharer'], ['UC8', 'My orders and my teams'], ['UC9', 'Admin: listings and team terms']];
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
  ['UC1–UC9', chip('Delivered', 'ok') + 'admin console beyond scope', '#24–#32 · F-ADR 0015'],
  ['Pay-first START, JOIN, refund on dev', chip('Open', 'open') + 'Checkout returns; webhook never confirms', '#517 · #223'],
  ['State for the team lifecycle', chip('Changed', 'chg') + 'transition table; State on listings', 'design doc · slide 21'],
  ['Google sign-in with JWT', chip('Changed', 'chg') + 'Sa-Token session, HttpOnly cookie', 'D6/D7 · F-ADR 0002'],
  ['Spring Data JPA', chip('Changed', 'chg') + 'MyBatis behind repository ports', 'skeleton plan §3'],
  ['Dev on ECS Fargate and RDS', chip('Changed', 'chg') + 'one EC2 host, Compose, DB on host', 'ADR 0008'],
  ['Secrets in Secrets Manager', chip('Changed', 'chg') + 'SSM SecureString, root-only env file', 'operator guide §7'],
  ['Human-gated prod, ECS circuit breaker', chip('Dropped', 'drop') + 'for SWE5006; built for SWE5001', '#226 · ADR 0008'],
  ['E2E suite, auto burndown, saved-plan Terraform', chip('Partial', 'part') + 'stubs; daily data, no chart; manual', 'F-ADR 0011 · #229'],
];
const s4 = () => `
${tbl(['Promised (proposal)', 'Status on 21 Oct', 'Record'], pvd.map(r => [`<b>${r[0]}</b>`, r[1], `<span style="color:var(--muted)">${r[2]}</span>`]), { fs: 17, w: [360, null, 210], pad: '6px 11px' })}
<div style="position:absolute;left:0;right:0;top:478px;border-radius:8px;background:#FDEFEC;padding:10px 16px;font:400 17px/1.35 Inter;display:flex;gap:10px;align-items:baseline">
 <b style="color:#B02E1B;white-space:nowrap">Outstanding</b><span>#517 and #223 (go/no-go 23 Oct) · SSH 22 open on the dev host · CodeQL off · CloudFront → ALB over HTTP until 16 Nov (#227) · dev volume unencrypted</span>
</div>`;

// ---------- slide 5 (1.4) ----------
const ms = [['27 Aug', 'Proposal submitted'], ['5 Sep', 'Backend pipeline live on dev'], ['14 Sep', 'Sprint 1 tag'], ['20 Sep', 'Stripe Sandbox acceptance (local)'], ['22 Sep', 'Web app live on dev'], ['27 Sep', 'Sprint 2 tag'], ['30 Sep', 'Performance baseline (local)'], ['11 Oct', 'Sprint 3 tag'], ['13 Oct', 'Admission refactor (PR #424)']];
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
  ['Liu Jin', 'UC9 admin listings and team terms', 'Listing lifecycle → <b>State</b> (full form)', '20–21', 'was UC5 State, UC9'],
  ['Wang Ziang (Eric)', 'UC3 start, UC4 join', 'Team creation → <b>Factory Method</b>', '16', 'unchanged; UC4 race is a transition strategy'],
  ['Yu Qianshuo (Yorik)', 'UC6 refund on expiry', 'Team outcome consequences → <b>Observer</b>', '17', 'was Adapter'],
  ['Panpakorn (Pan)', 'Receive notifications', 'Outbox transports → <b>Template Method</b>', '18', 'was UC1 Strategy, UC2'],
  ['Lim Jiechao', 'UC8 my orders and teams', 'Pricing → <b>Strategy</b>', '19', 'was UC7 Observer, UC8'],
];
const s6 = () => `
${tbl(['Member', 'Use case', 'Design problem → GoF pattern', 'Slide', 'Change from proposal §6.1'], own.map(o => [`<b>${o[0]}</b>`, o[1], o[2], o[3], `<span style="color:var(--muted)">${o[4]}</span>`]), { fs: 18, w: [196, 270, 330, 70], pad: '12px 12px' })}
<div style="margin-top:18px;font:400 18px/1.4 Inter">Each member presents their own design problem in section 2. The team-formation statechart (UC4–UC6) is Eric’s, with his UC3/UC4 design.</div>
<div style="margin-top:12px;display:flex;gap:16px;align-items:center;font:400 17px Inter;color:var(--muted)">Report: use case in §3.4, design problem in §3.5 ${mk('FILL', 'section numbers (#463)')} ${mk('VERIFY', 'UC9 owner')}</div>`;

// ---------- slide 7 (1.6, 1.7) ----------
const steps = ['coded', 'tested', 'integrated', 'releasable', 'released'];
const ladder = () => `<div style="display:flex;align-items:center;gap:8px;margin-bottom:12px"><span style="font:600 16px Inter;color:var(--muted);margin-right:6px">DoD ladder (EPAT 01 Agile p26)</span>${steps.map((s, i) => `<span style="font:600 17px/1 Inter;padding:7px 12px;border-radius:999px;background:${i === 2 ? '#E3E8FB' : '#ECE6DC'};color:${i === 2 ? '#3E5CD9' : '#1B1A18'}">${s}</span>${i < 4 ? '<span style="color:#A69E92">→</span>' : ''}`).join('')}</div>`;
const spr = [
  ['1 · 2–12 Sep', 'Walking skeleton: sign-in, browse, admin on dev', '<b>integrated</b>: backend on dev 5 Sep; no frontend yet'],
  ['2 · 14–25 Sep', 'Pay, join a team, see the order, replay-safe', '<b>tested</b> for the money path (Checkout off on dev); 134 of 150 h'],
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
${tbl(['Sprint', 'Goal', 'Definition of Done reached'], spr.map(s => [`<b style="white-space:nowrap">${s[0]}</b>`, s[1], s[2]]), { fs: 17, w: [160, 450], pad: '6px 11px' })}
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
 ${kicker('Sprint 2 burndown · remaining hours, 15–26 Sep')}
 ${burndown()}
 <div style="font:400 16.5px/1.35 Inter;margin-top:2px">Flat for a week, then a cliff: work merged late in large PRs. Reconstructed from merge dates.</div>
</div>
<div style="position:absolute;left:0;right:0;top:450px;border-radius:8px;background:#E3E8FB;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#3E5CD9">Practice (EPAT 03, 05):</b> two red tests reproduced a quote defect (7 Oct log), then PR #424 refactored admission into shared guards and a Factory Method. Full set of artefacts: backup B1.</div>`;

// ---------- slide 23 (3.1, 3.3) ----------
// Agile Testing Quadrants (EPAT 03 TDD p7), laid out as in the deck: business-facing on top, supporting the team on the left.
const lvl = t => `<span style="display:inline-block;font:600 14.5px/1 Inter;border:1.5px solid #6B655C;color:#1B1A18;border-radius:999px;padding:3px 8px;margin-left:6px;vertical-align:2px">${t}</span>`;
const qrow = (name, level, rest) => `<div style="font:400 18px/1.3 Inter;margin-bottom:6px"><b>${name}</b>${lvl(level)} <span style="color:var(--muted)">·</span> ${rest}</div>`;
const caught = t => `<div style="font:400 17px/1.3 Inter;margin-top:2px"><b style="color:#B02E1B">Caught:</b> ${t}</div>`;
const runs = t => `<div style="font:400 17px/1.3 Inter;color:var(--muted);margin-bottom:2px">Runs in ${t}</div>`;
const quad = (q, axes, how, body, style, frag) => `<div class="card${frag ? ' frag' : ''}"${frag ? ` data-frag="${frag}"` : ''} style="position:absolute;${style};padding:9px 16px">
 <div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:5px"><span style="font:700 18px Inter">${q} <span style="font-weight:500;color:var(--muted)">${axes}</span></span><span style="font:600 15px Inter;color:var(--muted)">${how}</span></div>
 ${body}</div>`;
const Q_H = 'height:190px', Q_W = 'width:574px';
const s23 = () => `
${quad('Q2', 'business-facing · supporting the team', 'automated', `
 ${qrow('Integration', 'integration', 'Testcontainers · 49 classes')}
 ${qrow('Contract', 'integration', 'OpenAPI export + openapi-diff')}
 ${qrow('End-to-end', 'system', 'Playwright · 8 specs')}
 ${runs(`${mo('build-quality', 15)}, ${mo('contract-check', 15)}, ${mo('e2e-preview', 15)}, ${mo('e2e-dev', 15)}`)}
 ${caught('a 502 on e2e-dev during a deploy (3–4 Oct): one host')}`, `left:0;top:0;${Q_W};${Q_H}`, 1)}
${quad('Q3', 'business-facing · critiquing the product', 'manual', `
 ${qrow('Exploratory, acceptance', 'system', 'by hand, logged')}
 <div style="font:400 18px/1.3 Inter;margin-bottom:6px">Stripe Sandbox START and JOIN in a browser (20 Sep, local)</div>
 <div style="font:400 18px/1.3 Inter;margin-bottom:6px">Sign-in cookie Secure and HttpOnly on dev (8 Oct)</div>
 <div style="font:400 17px/1.3 Inter;color:#B02E1B">Not done: acceptance testing with outside users</div>`, `left:590px;top:0;${Q_W};${Q_H}`, 2)}
${quad('Q1', 'technology-facing · supporting the team', 'automated', `
 ${qrow('Unit', 'unit', 'JUnit 5, Mockito · 185 classes')}
 ${qrow('Component', 'unit', 'Vitest, MSW · 77 files')}
 ${qrow('Architecture', 'unit', 'ArchUnit · 18 rules')}
 ${runs(`${mo('build-quality', 15)}, ${mo('frontend-check', 15)} (both required)`)}
 ${caught('two red tests reproduced the join-quote defect (#422)')}`, `left:0;top:200px;${Q_W};${Q_H}`)}
${quad('Q4', 'technology-facing · critiquing the product', 'tools', `
 ${qrow('Performance, load', 'system', 'k6 on SWE5001 ECS, by hand')}
 ${qrow('Security scanning', 'types', 'SAST · dependencies (SCA) · image and IaC configuration · secrets')}
 ${runs('by hand (load); security gates on slide 27')}
 ${caught('an expiry cohort used 58 s of its 60 s (local)')}`, `left:590px;top:200px;${Q_W};${Q_H}`, 3)}
<div style="position:absolute;left:0;right:0;top:400px;border-radius:8px;background:#E3E8FB;padding:9px 16px">
 <div style="display:flex;justify-content:space-between;margin-bottom:4px"><span style="font:700 17px Inter;color:#3E5CD9">Complex classes under test (proposal §6.3)</span><span style="font:400 15px Inter;color:var(--muted)">Quadrants: EPAT 03 TDD p7 · levels: p5</span></div>
 ${[['Team state machine', `${mo('DealStatusTest', 16)}, ${mo('PaidGroupDealTest', 16)}`],
    ['Last-seat capacity', `${mo('StripeCheckoutFlowTest.concurrentPaidBuyersCannotBothTakeLastSeat', 16)}`],
    ['Refund idempotency', `${mo('RefundServiceTest.lostResponseRetriesSameDurableKey', 16)}`]]
   .map(r => `<div style="display:flex;gap:12px;font:400 18px/1.35 Inter"><b style="width:190px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('')}
</div>`;

// ---------- slide 24 (3.2) ----------
const kv = rows => rows.map(r => `<div style="display:flex;gap:14px;font:400 18px/1.35 Inter;margin-bottom:10px"><b style="width:118px;flex:none">${r[0]}</b><span>${r[1]}</span></div>`).join('');
const s24 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:620px;height:536px;padding:18px 22px">
 ${kicker('The scripted test')}
 ${kv([
   ['Tool', `k6 1.6.1, image pinned by digest · ${mo('perf/k6/browse.js', 16)}`],
   ['Target system', '<b>SWE5001 ECS environment, same code</b>: CloudFront → ALB → Fargate. Never the shared SWE5006 dev host, which also runs its database'],
   ['Load', 'open arrival rate 10 → 200 requests/s in ten 60 s steps; 100 VUs preallocated, 400 max'],
   ['Mix', '7 listing pages : 3 listing details, anonymous; no think time; 10 s timeout'],
   ['Pass, per step', 'p95 &lt; 500 ms · errors &lt; 1% · zero dropped requests'],
   ['Result', `run in the system demo (slide 33) · ${mk('FILL', '5.3 pre-run label and date')}`],
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
const zoom = (name, { W, H, view, at = 'left:0;top:0', crops }) => {
  const s = Math.min(view[0] / W, view[1] / H);
  return `<style>body:not(.present) .zcrop{display:none}</style>
<div style="position:absolute;${at};width:${Math.round(W * s)}px;height:${Math.round(H * s)}px">${nat(name, Math.round(W * s), Math.round(H * s))}</div>
${crops.map(([x, y, label], i) => `<div class="frag zcrop" data-frag="${i + 1}" style="position:absolute;left:0;top:0;width:1164px;height:548px;background:#fff;overflow:hidden;border-radius:6px;box-shadow:0 0 0 1.5px #DDD5C8">
 <div style="position:absolute;left:${-x}px;top:${-y}px">${nat(name, W, H)}</div>
 <div style="position:absolute;right:10px;top:10px;font:700 15px/1 Inter;letter-spacing:.04em;background:#1B1A18;color:#fff;border-radius:999px;padding:6px 11px">${i + 1}/${crops.length} · ${label}</div></div>`).join('')}`;
};

// ---------- slide 25 (4.2) ----------
const tree = [['backend/', 'Maven COLA modules; services/ for SWE5001'], ['frontend/', 'React SPA'], ['contracts/', 'openapi.json, the API contract'], ['deployment/', 'Compose, SSM bundle, deploy.sh'], ['infra/', 'Terraform: dev frontend, SWE5001'], ['perf/ · docs/', 'k6 · ADRs, sprint logs'], ['.github/', 'workflows, CODEOWNERS, Dependabot']];
const art = [
  ['Backend image', `${mo('git-&lt;sha&gt;-run-&lt;n&gt;', 15.5)}, deployed by ${mo('@sha256', 15.5)} digest`, 'ECR, immutable tags, newest 30 kept'],
  ['API contract', `${mo('openapi.json', 15.5)} committed; drift fails CI`, 'repository'],
  ['TS client', 'generated from the contract, gitignored', 'build only (F-ADR 0003)'],
  ['Frontend bundle', 'content-hashed files, immutable cache', 'S3 behind CloudFront'],
  ['DB schema', 'Flyway V1–V28', 'repository'],
  ['Infrastructure', 'Terraform; CI never applies it', 'S3 state, DynamoDB lock'],
  ['SBOM', '<span style="color:#B02E1B">not produced</span>', '—'],
];
const s25 = () => `
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

// ---------- slide 26 (4.3) ----------
const s26 = () => zoom('cicd-pr-vs-merge', { W: 2320, H: 1096, view: [1164, 548], crops: [[0, 0, 'PR · backend'], [1160, 0, 'PR · frontend, security'], [0, 548, 'gate · delivery'], [1160, 548, 'deploy · verification']] });

// ---------- slide 27 (4.3, 3.3) ----------
// Stages as in DSOEA 07 slide 3 (pre-commit, commit, build, test, deploy).
const sec = [
  ['Pre-commit', 'Lefthook: format, lint, type-check (frontend)', 'style and type errors', ''],
  ['Commit · PR', `<b>gitleaks</b> ${req2()} over full history; code-owner review; actionlint`, 'secrets, unsafe workflows', ''],
  ['Build', `SAST: <b>SonarCloud</b> ${req2()}, SpotBugs; SCA: Trivy deps, Dependabot daily`, 'code flaws, vulnerable libraries', 'CodeQL off: code scanning unavailable on this repository'],
  ['Test', `<b>Trivy image</b> ${req2()} (HIGH, CRITICAL); Trivy config, deployment-bundle rules`, 'image CVEs, IaC and Compose misconfiguration', 'no DAST against the running site'],
  ['Deploy', 'OIDC role per job; image by digest; read-only container, all capabilities dropped', 'stolen keys, swapped images', 'rebuilt after merge, not rescanned (ECR scans on push)'],
];
function req2() { return '<span style="font:700 13.5px/1 Inter;color:#3E5CD9;border:1.5px solid #3E5CD9;border-radius:999px;padding:2px 6px;vertical-align:2px">required</span>'; }
const s27 = () => `
${tbl(['Stage', 'Control', 'Catches', 'Gap'], sec.map(r => [`<b style="white-space:nowrap">${r[0]}</b>`, r[1], r[2], r[3] ? `<span style="color:#B02E1B">${r[3]}</span>` : '—']), { fs: 17, w: [130, 470, 240], pad: '9px 11px' })}
<div style="position:absolute;left:0;right:0;top:470px;border-radius:8px;background:#E3E8FB;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#3E5CD9">Types tested (slide 23, Q4):</b> static analysis, dependency (SCA), image and IaC configuration, secrets. Every third-party action is pinned to a commit SHA. Stages: DSOEA 07 slide 3.</div>`;

// ---------- slide 28 (4.4) ----------
const cd = [
  ['Environments', 'one: <b>dev</b>. The SWE5006 prod stack was planned, not applied (#226)'],
  ['Approver', 'the <b>code owner</b> on the pull request; the merge releases'],
  ['Practice', '<b>Continuous Deployment</b> to dev: no manual step after merge. SWE5001 prod waits for an approved promotion PR: Continuous Delivery (EPAT 07 CI p34–35)'],
  ['Verified by', 'on-host health check with <b>automatic rollback</b>; public health via CloudFront; Playwright smoke on dev'],
  ['Trade-off', 'PostgreSQL and Redis on the app host: cheap and always on, one point of failure'],
];
const s28 = () => `
<div style="position:absolute;left:0;top:0;width:392px">
 ${cd.map(r => `<div style="margin-bottom:12px">${kicker(r[0])}<div style="font:400 17px/1.35 Inter;margin-top:-4px">${r[1]}</div></div>`).join('')}
</div>
${zoom('dev-deployment', { W: 2320, H: 1096, view: [756, 548], at: 'left:408px;top:20px', crops: [[0, 20, 'request path'], [940, 300, 'one host'], [1160, 548, 'deploy path']] })}`;

// ---------- slide 29 (4.5) ----------
const finds = [
  ['First dev deploys failed (5–17 Sep)', 'Hand-built environment drifted from its design: OIDC trust used the old subject format; the deploy policy kept a placeholder instance ID; port 8589 was opened wide', 'Fixed on dev by hand; the prod stack renders each value in Terraform, so a plan shows it. No deploy failed after 17 Sep (to 19 Oct)'],
  ['build-quality red for two days (19–20 Oct)', 'Dependabot upgraded the SpotBugs engine without its Maven plugin; the plugin lacked the engine’s BCEL classes', 'Upgrade both together, one Dependabot group, a POM policy test (PR #580)'],
  ['Weekly full-history gitleaks failing (since 21 Sep)', '20 matches were SHA-256 file digests in a contract baseline, not secrets', 'Each digest re-hashed, then fingerprinted exactly (PR #580); 4 badge-token findings still open'],
];
const s29 = () => `
${tbl(['Finding', 'Root cause', 'Fix'], finds.map(f => [`<b>${f[0]}</b>`, f[1], f[2]]), { fs: 17, w: [250, 470], pad: '11px 12px' })}
<div style="position:absolute;left:0;right:0;top:440px;border-radius:8px;background:#FDEFEC;padding:10px 16px;font:400 17px/1.35 Inter"><b style="color:#B02E1B">Still open:</b> the Stripe webhook never confirms a payment on dev (#517), so a green pipeline is not yet a working paid flow. Sources: backend-prod-terraform-deployment.md, ci-failure-audit-20261021.md.</div>`;

// ---------- slide 30 (4.1, 4.6) ----------
const live = [
  ['P1', 'The pull request: one frontend change, six required checks green', '60 s'],
  ['P2', 'Lim approves as the frontend code owner', '30 s'],
  ['P3', 'Squash merge: one commit on dev', '20 s'],
  ['P4', 'Delivery runs; the backend PR and deploy, triggered beforehand', '90 s'],
  ['P5', 'Verified: e2e-dev green, the change live on dev', '90 s'],
  ['P6', 'Merge to live in minutes; hand-over', '40 s'],
];
const s30 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 18, w: [70, null, 80], pad: '10px 12px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Why the backend is pre-run')}<div style="font:400 17px/1.4 Inter">Backend PR checks take about 16 min, longer than the segment, so that run was triggered before the talk and we walk through it finished.</div></div>
 <div class="card" style="padding:16px 20px">${kicker('Backup video', '#B02E1B')}<div style="font:400 17px/1.4 Inter">Recorded on dev by 24 Oct, one clip per step, in the cloud and on a USB drive. ${mk('FILL', 'link')}</div></div>
</div>`;

// ---------- slide 31 (5.1, 5.2) ----------
// Environment per step follows the 23 Oct go/no-go (open item 1); the NO-GO wording is in the run book.
const env5 = (t, tone = 'ok') => {
  const k = { ok: ['#E3E8FB', '#3E5CD9'], rec: ['#FFEDC7', '#7A5C20'], loc: ['#ECE6DC', '#1B1A18'], ecs: ['#FBDFD9', '#B02E1B'] }[tone];
  return `<span style="display:inline-block;font:700 15px/1 Inter;background:${k[0]};color:${k[1]};border-radius:999px;padding:5px 10px;white-space:nowrap">${t}</span>`;
};
const plan5 = [
  ['D1', 'Leader signs in, starts a bloc, pays in Stripe Sandbox, copies the invite', 'UC2 · UC1 · UC3 · UC7', 'security: only a signed webhook confirms a payment', env5('dev, if GO')],
  ['D2', 'Joiner opens the invite, joins and pays: 2 of 2, the team succeeds', 'UC4 · UC5 · UC7', 'correctness: guards re-checked after payment', env5('dev, if GO')],
  ['D3', 'My blocs and my orders, filtered by the URL', 'UC8', 'functionality', env5('dev')],
  ['D4', 'The bloc started at 16:05 expired and refunded itself', 'UC5 · UC6', 'expiry ≤ 1 min · one refund per payment', env5('dev, if GO')],
  ['D5', 'No session, a buyer on admin, a write without the marker', '—', 'security: 401 · 403 · 400 · HttpOnly', env5('dev')],
  ['D6', 'Twenty paid buyers race for the last seat', 'UC4', 'correctness under concurrency', env5('local test', 'loc')],
  ['33', 'Scripted k6 load, pre-run', 'UC1', 'performance: ≤ 2 s at 100 users', env5('SWE5001 ECS', 'ecs')],
  ['35', 'Back to the §4 pipeline run', '—', 'maintainability', env5('dev')],
];
const s31 = () => `
${tbl(['Step', 'What you see', 'Use cases', 'Quality attribute (slide 3)', 'Runs on'], plan5.map(r => [`<b>${r[0]}</b>`, r[1], `<span style="white-space:nowrap">${r[2]}</span>`, r[3], r[4]]), { fs: 17.5, w: [60, 400, 150, 300], pad: '8px 10px' })}
<div style="position:absolute;left:0;right:0;top:486px;font:400 17px/1.35 Inter;color:var(--muted)">“dev, if GO”: live on deployed dev after the 23 Oct go/no-go (#517, #223); otherwise the 24 Oct recording of that step, said as recorded. A local result is never presented as a dev one.</div>`;

// ---------- slide 32 (5.1, 5.2, 5.5) ----------
const live5 = [
  ['D1', 'Start a bloc and share the invite', '120 s'],
  ['D2', 'A friend joins and pays: the team succeeds', '120 s'],
  ['D3', 'My blocs and my orders', '75 s'],
  ['D4', 'The 16:05 bloc: expired, order cancelled, refunded', '90 s'],
  ['D5', 'Security: 401, 403, 400, HttpOnly cookie', '60 s'],
  ['D6', 'Last-seat race: the test, in the IDE', '45 s'],
];
const s32 = () => `
<div style="position:absolute;left:0;top:0;width:700px">
 ${tbl(['Step', 'What you see', 'Time'], live5.map(l => [`<b>${l[0]}</b>`, l[1], l[2]]), { fs: 18, w: [70, null, 80], pad: '10px 12px' })}
</div>
<div style="position:absolute;left:724px;top:0;width:440px">
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Two buyers, one screen')}<div style="font:400 17px/1.4 Inter">Two Chrome profiles, Leader and Joiner, both signed in with Google. Stripe Sandbox: a test card, no money moves.</div></div>
 <div class="card" style="padding:16px 20px;margin-bottom:12px">${kicker('Started before the talk')}<div style="font:400 17px/1.4 Inter">A team of two on <span class="mono" style="font-size:16px">US34-04</span> at 16:05: its 40-minute window, the shortest allowed, ends ≈ 16:45.</div></div>
 <div class="card" style="padding:16px 20px">${kicker('Backup video', '#B02E1B')}<div style="font:400 17px/1.4 Inter">Recorded on dev by 24 Oct, one clip per step, in the cloud and on a USB drive. ${mk('FILL', 'link')}</div></div>
</div>`;

// ---------- slide 33 (5.3) ----------
const steps33 = [10, 20, 40, 60, 80, 100, 125, 150, 175, 200];
const s33 = () => `
<div style="position:absolute;left:0;top:0;width:600px">
 ${kicker('k6 pre-run · SWE5001 ECS environment, same code · not the dev host', '#B02E1B')}
 ${tbl(['Requests/s', 'p95 (ms)', 'Errors', 'Dropped', 'Step'], steps33.map(r => [`<b>${r}</b>${r >= 175 ? ' <span style="color:#B02E1B">●</span>' : ''}`, '—', '—', '—', '—']), { fs: 17, w: [130, 110, 100, 110], pad: '5px 12px' })}
 <div style="font:400 16px/1.35 Inter;color:var(--muted);margin-top:6px"><span style="color:#B02E1B">●</span> at or above ≈ 190 requests/s, the proposal’s 100 users (slide 24) · results ${mk('FILL', 'from the 24 Oct pre-run')}</div>
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

// ---------- slide 34 (5.2) ----------
const s34 = () => `
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

// ---------- slide 35 (5.4) ----------
const seams = [
  ['New pricing mode', 'one more <span class="mono" style="font-size:16px">PricingStrategy</span>', 'Strategy · slide 19'],
  ['New consequence of a team’s outcome', 'one more subscriber to <span class="mono" style="font-size:16px">TeamSucceeded</span> / <span class="mono" style="font-size:16px">TeamExpired</span>', 'Observer · slide 17'],
  ['New payment provider', 'one more adapter behind <span class="mono" style="font-size:16px">CheckoutGateway</span>, <span class="mono" style="font-size:16px">RefundGateway</span>', 'Adapter · slide 11'],
];
const s35 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:420px;padding:18px 22px">
 ${kicker('Shown in §4 (slide 30)')}
 <div style="font:400 18px/1.45 Inter">One change, live on dev: six required checks, a code-owner review, an automatic deploy and a smoke test. 18 ArchUnit rules hold the module boundaries on every build.</div>
</div>
<div style="position:absolute;left:444px;top:0;width:720px">
 ${kicker('Proposal §6.3: the primary design driver', '#B02E1B')}
 ${tbl(['Change', 'Added as', 'Pattern'], seams.map(r => [`<b>${r[0]}</b>`, r[1], r[2]]), { fs: 17.5, w: [230, null, 190], pad: '10px 12px' })}
 <div style="font:400 17px/1.35 Inter;color:var(--muted);margin-top:10px">Existing domain logic stays unedited in each case.</div>
</div>`;

const out = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 23: s23, 24: s24, 25: s25, 26: s26, 27: s27, 28: s28, 29: s29, 30: s30, 31: s31, 32: s32, 33: s33, 34: s34, 35: s35 };
for (const s of C.SLIDES) if (!out[s.n]) out[s.n] = s.slot === null ? (() => '') : pending(s.n);
// Closing slide (dark, full bleed).
out[36] = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8 · d1b3np88z657cp.cloudfront.net</div>`;

module.exports = out;
