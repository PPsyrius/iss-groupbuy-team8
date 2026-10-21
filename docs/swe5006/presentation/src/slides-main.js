const { icon, mk, lockup } = require('../../../presentation-kit/lib');
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
const mo = (t, sz = 16) => `<span class="mono" style="font-size:${sz}px">${t}</span>`;
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

const out = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8 };
for (const s of C.SLIDES) if (!out[s.n]) out[s.n] = s.slot === null ? (() => '') : pending(s.n);
// Closing slide (dark, full bleed).
out[36] = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8 · d1b3np88z657cp.cloudfront.net</div>`;

module.exports = out;
