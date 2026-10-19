const { icon, md, mk, svg, lockup } = require('../../../presentation-kit/lib');
// Accent fills never carry text (design-screens.md §2.1): accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.

const todo = n => () => `<div style="margin-top:40px">${mk('FILL', 'slide ' + n + ' layout')}</div>`;

// ---------- slide 1 ----------
const s1 = () => `
<div style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F">SWE5006 PRACTICE PROJECT · TEAM 8</div>
${lockup()}
<div style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff">A scalable community group-buying platform</div>
<div style="position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center">Two prices <span style="color:#E8402A;font-size:20px">●</span> pay first to team up <span style="color:#E8402A;font-size:20px">●</span> refunded if the team doesn’t fill</div>
<div style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1">Graduate Certificate in Designing Modern Software Systems · 26 October 2026</div>`;

// ---------- segment 1 helpers (patterns from docs/swe5001/presentation/src/slides-main.js) ----------
const row = (ic, txt, bg, col = 'var(--ink)', sz = 80, fs = 25, mb = 30) => `<div style="display:flex;align-items:center;gap:26px;margin-bottom:${mb}px">
  <div class="dot" style="width:${sz}px;height:${sz}px;background:${bg};font-size:${sz / 2}px">${icon(ic)}</div>
  <div style="font:400 ${fs}px/1.3 Inter;color:${col}">${txt}</div></div>`;
const tick = (t, sub, kind = 'ok') => {
  const c = { ok: ['circle-check', 'var(--green)'], open: ['circle-exclamation', 'var(--orange)'], tbd: ['hourglass-half', '#6B655C'] }[kind];
  return `<div style="display:flex;gap:12px;margin-bottom:14px"><div style="color:${c[1]};font-size:24px;flex:none;margin-top:-1px">${icon(c[0])}</div>
  <div style="font:400 18px/1.3 Inter">${t}${sub ? `<div style="font:italic 400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px">${sub}</div>` : ''}</div></div>`;
};

// ---------- slide 2 ----------
const s2 = () => `
<div style="position:absolute;left:0;top:4px;font:italic 400 18px Inter;color:var(--muted)">A market coordination failure, solved with a pay-first team purchase modelled on the early Pinduoduo playbook (proposal §4)</div>
<div class="card" style="position:absolute;left:0;top:50px;width:520px;height:476px;padding:32px 34px">
  <div class="serif" style="font-size:30px;line-height:1.2;color:var(--muted);margin-bottom:26px">Today</div>
  ${row('store', 'Merchants can discount only when volume is predictable', '#6B655C', 'var(--ink)', 76, 24)}
  ${row('user', 'One buyer can never reach a bulk price', '#6B655C', 'var(--ink)', 76, 24)}
  ${row('bullhorn', 'Advertising is costly and not tied to real purchases', '#6B655C', 'var(--ink)', 76, 24)}
</div>
<div style="position:absolute;left:534px;top:288px;width:62px;height:3px;background:var(--orange)"></div>
<div style="position:absolute;left:588px;top:282px;border-left:12px solid var(--orange);border-top:7px solid transparent;border-bottom:7px solid transparent"></div>
<div class="card" style="position:absolute;left:614px;top:50px;width:550px;height:476px;padding:30px 34px;background:var(--inverse);border:none">
  <div class="serif" style="font-size:30px;color:#E8402A;margin-bottom:24px">With BlocBuy: pay-first teams</div>
  ${row('tags', 'Two prices: individual and team', 'var(--orange)', '#fff', 62, 22, 22)}
  ${row('credit-card', 'Pay upfront to start a team, then share the invite link', 'var(--orange)', '#fff', 62, 22, 22)}
  ${row('users', 'Friends join by paying upfront too', 'var(--orange)', '#fff', 62, 22, 22)}
  ${row('arrows-rotate', 'Fills in time: team price.<br>Expires: everyone refunded automatically', 'var(--orange)', '#fff', 62, 22, 0)}
  <div style="margin-top:20px;font:italic 400 16px Inter;color:#B5ADA1">Stripe test mode only: no live money moves</div>
</div>`;

// ---------- slide 3 ----------
const s3 = () => `<div class="dia" style="margin-top:-8px">${svg('use-case')}</div>
<div style="position:absolute;left:0;right:0;top:500px;font:400 16.5px/1.3 Inter"><b>Out of scope:</b> merchant self-service GUI · analytics · recommendations · automatic team matching · email, SMS or push · native apps · live payments</div>`;

// ---------- slide 4 ----------
const sprint = (n, dates, goal, result, stat, tone) => `<div class="card" style="flex:1;height:196px;padding:14px 18px;border-top:5px solid ${tone}">
  <div style="display:flex;justify-content:space-between;align-items:baseline"><div style="font:700 21px Inter">Sprint ${n}</div><div style="font:600 16px Inter;color:var(--muted)">${dates}</div></div>
  <div style="font:400 17.5px/1.3 Inter;margin-top:8px;height:69px">${goal}</div>
  <div style="font:700 16px/1.3 Inter;margin-top:6px;color:var(--orange-d)">${result}</div>
  <div style="font:400 15.5px Inter;color:var(--muted);margin-top:4px">${stat}</div></div>`;
const cer = (ic, t, a) => `<div style="flex:1;display:flex;flex-direction:column;align-items:center;text-align:center"><div class="dot" style="width:46px;height:46px;background:var(--teal);font-size:22px">${icon(ic)}</div>
  <div style="font:700 17px Inter;margin-top:6px">${t}</div><div style="font:400 15px/1.25 Inter;margin-top:2px">${a}</div></div>`;
const stat = (big, unit, sub) => `<div class="card" style="flex:1;padding:12px 16px"><div><span class="serif" style="font-size:34px">${big}</span> <span style="font:700 17px Inter">${unit}</span></div><div style="font:400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px">${sub}</div></div>`;
const s4 = () => `
<div style="display:flex;gap:18px">
 ${sprint(1, '2–12 Sep', 'Walking skeleton: CI/CD and dev live on 5 Sep; sign-in, browse, admin', 'Pipeline live; features late', '35 team PRs · 3 issues closed', '#6B655C')}
 ${sprint(2, '14–25 Sep', 'One idempotent chain: payment → team → order', '9 of 11 stories · 134 of 150 h', '64 team PRs · 47 issues closed', 'var(--teal)')}
 ${sprint(3, '28 Sep–9 Oct', 'Invite → pay → refund on the deployed dev site', 'Not met on dev · 117 of 150 h', '126 team PRs · 67 issues closed', 'var(--orange)')}
 ${sprint(4, '12–23 Oct', 'Complete system on dev; three services on ECS for SWE5001', mk('FILL', 'result, 23 Oct'), '38 team PRs · 17 closed so far', '#FFB01F')}
</div>
<div style="position:absolute;left:0;top:214px;width:640px;height:124px;border-radius:8px;background:#E3E8FB;padding:12px 10px;display:flex">
 ${cer('calendar-check', 'Planning', 'plan.md, hours')}${cer('clock', 'Day log', '35 dated logs')}${cer('scale-balanced', 'Mid-sprint', 'check, S3–S4')}${cer('magnifying-glass', 'Review', 'review, report')}${cer('comments', 'Retro', 'in the report')}
</div>
<div style="position:absolute;left:660px;top:214px;width:504px;display:flex;gap:12px">
 ${stat('159', 'cards', 'on the board: 132 Done, 7 in progress, 20 to do')}
 ${stat('356', 'PRs', 'merged: 263 team, 70 Dependabot, 23 snapshot')}
</div>
<div style="position:absolute;left:0;top:356px;width:640px;height:192px;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:14px 20px" class="on-surface">
 <div class="serif" style="font-size:21px;margin-bottom:8px">Definition of Done, Sprints 3–4 (highlights)</div>
 <ul class="b" style="font:400 17px/1.3 Inter"><li style="margin-bottom:5px">Acceptance criteria met; contract before controller</li><li style="margin-bottom:5px">Every gate green: Maven verify, ArchUnit, Gitleaks, Trivy, SonarCloud, <span class="mono" style="font-size:15.5px">pnpm check</span></li><li style="margin-bottom:5px"><b>“On dev” means deployed</b>: a local run does not count</li><li style="margin-bottom:0">PR under ~1,500 lines; remaining hours on the board daily</li></ul>
</div>
<div style="position:absolute;left:660px;top:356px;width:504px;height:192px;border-radius:8px;background:#FDEFEC;padding:14px 20px">
 <div class="serif" style="font-size:21px;margin-bottom:10px;color:var(--orange-d)">Was it met?</div>
 ${tick('Required gates block every merge', null)}
 ${tick('Sprint 3 not counted: Checkout off on dev', null, 'open')}
 ${tick('Daily remaining hours not kept', null, 'open')}
</div>`;

// ---------- slide 5 ----------
const ms = [['27 Aug', 'Proposal'], ['5 Sep', 'Backend live on dev'], ['14 Sep', 'Sprint 1 tag'], ['22 Sep', 'Web app live on dev'], ['27 Sep', 'Sprint 2 tag'], ['8 Oct', 'Startable listings on dev'], ['11 Oct', 'Sprint 3 tag'], ['15 Oct', 'Three services merged'], ['23 Oct', 'Sprint 4 tag']];
const bdIdeal = [224.5, 202.1, 179.6, 157.2, 134.7, 112.3, 89.8, 67.4, 44.9, 22.5, 0], bdAct = [224.5, 224.5, 218.5, 184.5, 123.5, 123.5, 106.5];
const bdX = ['start', '12', '13', '14', '15', '16', '19', '20', '21', '22', '23'];
function burndown() {
  const W = 560, H = 196, L = 44, R = 548, T = 10, B = 168;
  const X = i => L + 14 + i * ((R - L - 24) / 10), Y = v => B - (v / 240) * (B - T);
  const line = (d, c, dash) => `<polyline points="${d.map((v, i) => `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => `<circle cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 60, 120, 180, 240].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14.5" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${bdX.map((x, i) => `<text x="${X(i)}" y="${B + 22}" font-size="14.5" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${line(bdIdeal, '#A69E92', true)}${line(bdAct, '#3E5CD9')}
   <text x="${X(6) + 10}" y="${Y(106.5) - 10}" font-size="15" font-weight="700" fill="#3E5CD9">106.5 h left</text></svg>`;
}
const mem = [['Liu Jin', 51, 39], ['Yu Qianshuo', 48, 8], ['Wang Ziang', 46, 17], ['Panpakorn', 41.5, 29], ['Lim Jiechao', 38, 29]];
const hrs = [['S1', null, null], ['S2', 150, 134], ['S3', 150, 117], ['S4', 224.5, 118]];
const s5 = () => `
<div style="position:absolute;left:10px;right:10px;top:48px;height:3px;background:var(--teal)"></div>
${ms.map((m, i) => {
  const x = 52 + i * 132.5, last = i === ms.length - 1;
  return `<div style="position:absolute;left:${x - 66}px;width:132px;top:0;text-align:center;font:700 18px Inter">${m[0]}</div>
  <div style="position:absolute;left:${x - 12}px;top:38px;width:24px;height:24px;border-radius:50%;border:2px solid var(--teal);background:#fff;padding:4px"><div style="width:100%;height:100%;border-radius:50%;background:${last ? '#ECE6DC' : 'var(--teal)'}"></div></div>
  <div style="position:absolute;left:${x - 66}px;width:132px;top:70px;text-align:center;font:400 15.5px/1.2 Inter">${m[1]}${last ? `<div style="margin-top:2px">${mk('FILL', 'date')}</div>` : ''}</div>`;
}).join('')}
<div style="position:absolute;left:0;top:150px;width:560px">
 <div style="display:flex;align-items:center;gap:10px;margin-bottom:8px"><div class="serif" style="font-size:21px">Planned hours per member</div>${mk('VERIFY', 'S4 split, PR #538')}</div>
 <table class="t" style="font-size:17px"><tr><th>Member</th><th style="text-align:right">S2</th><th style="text-align:right">S3</th><th style="text-align:right">S4</th><th style="text-align:right">Board items Done</th></tr>
 ${mem.map(m => `<tr><td style="font-weight:600">${m[0]}</td><td style="text-align:right">30</td><td style="text-align:right">30</td><td style="text-align:right">${m[1]}</td><td style="text-align:right">${m[2]}</td></tr>`).join('')}</table>
 <div style="font:italic 400 15.5px/1.35 Inter;margin-top:8px">S1 hours were not recorded. Board items are delivery evidence, not effort. Planned S2–S4: 98–111 h each; guideline ≈ 10 man-days each (proposal §7)</div>
 <div style="margin-top:8px">${mk('CONFIRM', 'actual effort per member, agreed by all five')}</div>
</div>
<div style="position:absolute;left:600px;top:150px;width:564px">
 <div class="serif" style="font-size:21px;margin-bottom:2px">Sprint 4 burndown to 19 Oct: plan hours not Done</div>
 ${burndown()}
 <div style="display:flex;gap:22px;font:400 15px Inter;margin:-2px 0 8px 44px"><span><i style="display:inline-block;width:22px;height:0;border-top:3px dashed #A69E92;vertical-align:4px"></i> Ideal (plan §7)</span><span><i style="display:inline-block;width:22px;height:3px;background:var(--teal);vertical-align:4px"></i> Daily board snapshot</span></div>
 ${hrs.map(h => `<div style="display:flex;align-items:center;height:30px;gap:10px"><div style="width:30px;font:700 16px Inter">${h[0]}</div>
   <div style="position:relative;width:220px;height:18px">${h[1] ? `<div style="position:absolute;left:0;top:0;height:18px;width:${h[1] / 224.5 * 220}px;background:#ECE6DC;border-radius:4px"></div>` : ''}${h[2] ? `<div style="position:absolute;left:0;top:0;height:18px;width:${h[2] / 224.5 * 220}px;background:var(--teal);border-radius:4px"></div>` : ''}</div>
   <div style="font:400 16px Inter">${!h[1] ? mk('FILL', 'S1 hours') : h[0] === 'S4' ? `<b>${h[2]}</b> of ${h[1]} h ${mk('FILL', 'final')}` : `<b>${h[2]}</b> of ${h[1]} h`}</div></div>`).join('')}
</div>`;

// ---------- slide 6 ----------
const rc = (s, was, now) => `<div style="margin-bottom:12px"><div style="font:700 16px Inter;color:var(--orange-d)">${s}</div><div style="font:400 16.5px/1.3 Inter">${was}${now ? ` <span style="color:var(--orange)">→</span> ${now}` : ''}</div></div>`;
const s6 = () => `
<div style="display:flex;gap:18px;height:540px;margin-top:-6px">
 <div style="flex:1;background:#E3E8FB;border-radius:8px;padding:20px 20px">
   <div class="serif" style="font-size:24px;color:var(--green);margin-bottom:6px">Live on dev · 19 Oct</div>
   <div style="margin-bottom:12px">${mk('VERIFY', 're-check on 25 Oct')}</div>
   ${tick('API health UP; web app loads', 'checked 19 Oct, 14:14 SGT')}
   ${tick('14 listings in the catalog', 'startable listings set through the admin API (8 Oct)')}
   ${tick('Google sign-in, Secure session cookie', 'NFR-01 verified on dev, 8 Oct')}
   ${tick('Stripe Checkout opens and returns', 'Sandbox, 16 Oct (#517)')}
 </div>
 <div style="flex:1;background:#FDEFEC;border-radius:8px;padding:20px 20px">
   <div class="serif" style="font-size:24px;color:var(--orange-d);margin-bottom:16px">Still open</div>
   ${tick('Webhook never confirms: payments stay “waiting”', '#517 · Yorik', 'open')}
   ${tick('Scheduling and expiry refunds off on dev', '#223 · Yorik · go/no-go for the live payment demo', 'open')}
   ${tick('Payment hardening in progress', '#539, #548, #550 · Eric', 'open')}
   ${tick('Sprint 3 review and retro not yet written', '#451 · Lim', 'open')}
 </div>
 <div class="card" style="flex:1.12;padding:20px 20px">
   <div class="serif" style="font-size:24px;margin-bottom:14px">Retro → next sprint</div>
   ${rc('Sprint 1 → 2', 'No burndown kept', 'daily board snapshots, hours per story')}
   ${rc('Sprint 2 → 3', 'A 10,000-line PR, cliff burndown, owners drifted', 'PRs under 1,500 lines, mid-sprint check, hours daily')}
   ${rc('Sprint 3 → 4', mk('FILL', 'retro, #451'), 'review: goal not met on dev, so #223 became the critical path')}
   ${rc('Sprint 4', mk('FILL', 'retro on 23 Oct'), '')}
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
const dMod = (m, t) => `<div style="display:flex;align-items:center;height:37px;border-top:1px solid #EFE9DF"><div style="width:230px;flex:none">${cn(m, 15.5)}</div><div style="font:400 16.5px Inter">${t}</div></div>`;
const dCtx = (t, k) => { const [b, bg, fg] = dTone[k]; return `<span style="display:inline-block;font:600 16px/1 Inter;color:${fg};background:${bg};border:1.4px solid ${b};border-radius:999px;padding:6px 12px">${t}</span>`; };
const dRule = (a, d) => `<div style="flex:1;border-radius:8px;background:#fff;border:1px solid #DDD5C8;padding:8px 12px"><div style="font:600 16.5px/1.25 Inter">${a}</div><div style="font:400 16px/1.3 Inter;color:#B02E1B;margin-top:3px">→ ${d}</div></div>`;
const s7 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:566px;height:398px;padding:14px 20px">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif" style="font-size:21px">One codebase: five COLA modules</div>${dTag('modular monolith')}</div>
 <div style="margin-top:8px">
  ${dMod('blocbuy-adapter', 'REST controllers, Result envelope')}
  ${dMod('blocbuy-application', 'use-case services, subscribers')}
  ${dMod('blocbuy-domain', 'aggregates, events, ports; no Spring')}
  ${dMod('blocbuy-infrastructure', 'MyBatis, Stripe, Redis, SQS, SNS')}
  ${dMod('blocbuy-client', 'DTOs, integration event contracts')}
 </div>
 <div style="font:600 16.5px Inter;margin:12px 0 8px">Each bounded context is a package in every module</div>
 <div style="display:flex;flex-wrap:wrap;gap:8px">${dCtx('identity', 'plat')}${dCtx('catalog', 'biz')}${dCtx('groupbuy', 'biz')}${dCtx('order', 'biz')}${dCtx('payment', 'biz')}${dCtx('notification', 'plat')}</div>
 <div style="margin-top:14px">${dNote('shield-halved', '<b>18 ArchUnit rules</b> fail the build on a wrong dependency', '#3E5CD9')}</div>
</div>
<div class="card" style="position:absolute;left:584px;top:0;width:580px;height:398px;padding:14px 20px">
 <div style="display:flex;align-items:baseline;justify-content:space-between"><div class="serif" style="font-size:21px">Two topologies from one build</div>${dTag('ADR 0008')}</div>
 <div style="margin-top:12px">${dK('dev · SWE5006 · one EC2 host, Docker Compose')}
  ${dBox('biz', 'Monolith: all six contexts in one process', null, 'events in-process, no broker')}</div>
 <div style="margin-top:14px">${dK('prod · SWE5001 · Amazon ECS')}
  <div style="display:flex;gap:8px">${dBox('plat', 'identity-svc', null, null, 'flex:1')}${dBox('biz', 'commerce-svc', null, 'catalog · groupbuy · order · payment', 'flex:1.9')}${dBox('plat', 'notification-svc', null, null, 'flex:1.15')}</div>
  <div style="margin-top:8px">${dBox('ev', 'Events: outbox → SNS → SQS (ADR 0007)', null, null)}</div></div>
 <div style="margin-top:14px;border-radius:8px;background:#FDEFEC;padding:9px 14px;font:400 16.5px/1.3 Inter"><b style="color:#B02E1B">Split only where no transaction crosses:</b> pay, join, order and stock commit together, so they stay in commerce</div>
</div>
<div style="position:absolute;left:0;top:414px;width:1164px">
 <div style="display:flex;align-items:baseline;justify-content:space-between;margin-bottom:8px"><div class="serif" style="font-size:20px">Analysis → design: our transition rule</div><div style="font:400 15.5px Inter;color:#6B655C">use cases: slide 3 · worked examples: B7</div></div>
 <div style="display:flex;gap:10px">${dRule('Bounded context', 'package in each module')}${dRule('Analysis entity', 'aggregate + repository port')}${dRule('External actor (Google, Stripe)', 'gateway port + adapter (ACL)')}${dRule('Use case', 'application service + endpoint')}</div>
</div>`;

// ---------- slide 8 ----------
const dState = () => {
  const node = (x, t) => `<rect x="${x}" y="22" width="150" height="46" rx="23" fill="url(#s8g)" stroke="#E8402A" stroke-width="1.4"/><text x="${x + 75}" y="51" text-anchor="middle" font-family="JetBrains Mono, monospace" font-size="15.5" font-weight="700" fill="#B02E1B">${t}</text>`;
  const lab = (x, y, t) => `<text x="${x}" y="${y}" text-anchor="middle" font-family="JetBrains Mono, monospace" font-size="14.5" fill="#1B1A18">${t}</text>`;
  return `<svg width="700" height="96" viewBox="0 0 700 96" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><linearGradient id="s8g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FBDFD9"/></linearGradient>
  <marker id="s8ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker></defs>
  <circle cx="8" cy="45" r="7" fill="#1B1A18"/><line x1="15" y1="45" x2="38" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>
  ${node(40, 'DRAFT')}${node(300, 'ON_SALE')}${node(548, 'OFF_SALE')}
  <line x1="190" y1="45" x2="298" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(245, 37, 'publish()')}
  <line x1="450" y1="36" x2="546" y2="36" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(499, 28, 'delist()')}
  <line x1="548" y1="56" x2="452" y2="56" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s8ah)"/>${lab(499, 76, 'publish()')}
  <text x="115" y="90" text-anchor="middle" font-size="14.5" fill="#6B655C">only state that may be deleted</text></svg>`;
};
const dSchema = [
  ['identity', 'plat', 'sys_user, sys_role +4'], ['catalog', 'biz', 'biz_product +3'], ['groupbuy', 'biz', 'biz_group_deal +4'],
  ['order', 'biz', 'biz_order, biz_purchase_intent'], ['payment', 'biz', 'biz_payment, biz_refund +4'],
  ['notification', 'plat', 'biz_notification, sys_consumed_message'], ['jobs', 'biz', 'sys_outbox +3'],
];
const s8 = () => `
${dBand('UC9 · An admin drafts, publishes and delists listings and sets team terms',
  `listing${dTo}${cn('Product')} «aggregate»<br>listing status${dTo}${cn('ProductStatus')} (State)<br>listing store${dTo}${cn('ProductRepository')} port`,
  'Legal moves depend on the status, and two admins can edit the same listing')}
<div class="card" style="position:absolute;left:0;top:138px;width:744px;height:200px;padding:12px 20px">
 ${dPat('State', 'GoF · behavioural', 'ProductStatus.java')}
 ${dState()}
 <div style="font:400 16px/1.3 Inter;margin-top:2px">Each constant overrides only its legal moves; every other move is refused with <b>409</b></div>
</div>
<div class="card" style="position:absolute;left:0;top:350px;width:744px;height:198px;padding:12px 20px">
 ${dPat('Optimistic Offline Lock', 'Fowler, PoEAA', 'Product.requireVersion')}
 <div style="display:flex;align-items:center;gap:6px">
  ${dBox('n', 'Admin reads', 'version 4', null, 'width:128px', 14.5)}${dArr()}
  ${dBox('n', 'Write sends', 'expectedVersion=4', null, 'width:180px', 14.5)}${dArr()}
  ${dBox('biz', 'Guarded UPDATE', 'WHERE version=4', null, 'width:162px', 14.5)}${dArr()}
  <div style="display:flex;flex-direction:column;gap:6px;flex:1">${dBox('plat', null, '1 row: v5', null, '', 14.5)}${dBox('bad', null, '0 rows: 412', null, '', 14.5)}</div>
 </div>
 <div style="font:400 16px/1.3 Inter;margin-top:10px">A stale write changes nothing: <b>412</b>, and the console offers Reload (frontend ADR 0015)</div>
</div>
<div class="card" style="position:absolute;left:760px;top:138px;width:404px;height:410px;padding:12px 18px">
 <div class="serif" style="font-size:20px">Database schema</div>
 <div style="font:400 15px Inter;color:#6B655C;margin:2px 0 8px">29 tables · 28 Flyway migrations</div>
 ${dSchema.map(([g, k, t]) => `<div style="display:flex;gap:10px;align-items:baseline;padding:4px 0 4px 10px;border-left:4px solid ${dTone[k][0]};margin-bottom:5px"><div style="width:90px;flex:none;font:700 15px Inter;color:${dTone[k][2]}">${g}</div><div class="mono" style="font-size:14.5px;line-height:1.3">${t}</div></div>`).join('')}
 <div style="font:400 14.5px/1.35 Inter;color:#6B655C;margin-top:8px">UUID keys, timestamptz, numeric money; UNIQUE keys make retries safe · full list B5</div>
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
  return `<svg width="556" height="224" viewBox="0 0 556 224" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><linearGradient id="s9g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#FBDFD9"/></linearGradient>
  <marker id="s9ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker>
  <marker id="s9op" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10" fill="none" stroke="#1B1A18" stroke-width="1.4"/></marker>
  <marker id="s9tri" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="14" markerHeight="14" orient="auto-start-reverse"><path d="M0,0 L12,6 L0,12 z" fill="#fff" stroke="#1B1A18" stroke-width="1.2"/></marker></defs>
  ${cls(0, 22, 206, 'PaidGroupDealService', null, null)}
  ${cls(282, 0, 274, 'PaidGroupDealCreator', '«abstract»', ['+ initiate()  {final}', '# createGroupDeal()'])}
  ${cls(270, 170, 286, 'StandardPaidGroupDealCreator', null, null)}
  ${cls(0, 170, 150, 'GroupDeal', null, null)}
  <line x1="206" y1="45" x2="280" y2="45" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s9ah)"/><text x="243" y="70" text-anchor="middle" font-size="14.5" fill="#6B655C">injected</text>
  <line x1="419" y1="170" x2="419" y2="124" stroke="#1B1A18" stroke-width="1.4" marker-end="url(#s9tri)"/>
  <line x1="270" y1="193" x2="152" y2="193" stroke="#1B1A18" stroke-width="1.4" stroke-dasharray="6 4" marker-end="url(#s9op)"/><text x="211" y="185" text-anchor="middle" font-size="14.5" fill="#6B655C">creates</text></svg>`;
};
const s9 = () => `
${dBand('UC3/UC4 · A buyer pays first to start a team, or pays to join an open one',
  `team${dTo}${cn('GroupDeal')} «aggregate»<br>accepted join${dTo}${cn('PaidAdmission')}<br>team store${dTo}${cn('PaidGroupDealRepository')} port`,
  'Join rules were written twice, for the quote and after payment, and drifted: a buyer could pay, then be refunded')}
<div class="card" style="position:absolute;left:0;top:138px;width:596px;height:410px;padding:12px 20px">
 ${dPat('Factory Method', 'GoF · creational')}
 ${dFactory()}
 <div style="font:400 16px/1.35 Inter;margin-top:10px">${cn('initiate()', 15)} is final: it checks terms and price, then calls ${cn('createGroupDeal()', 15)}, so it is also a Template Method</div>
 <div style="margin-top:8px">${dNote('circle-info', 'One creator, wired at build time by <span class="mono" style="font-size:14.5px">GroupDealCreationConfig</span> (#421): a seam, not runtime-pluggable')}</div>
</div>
<div class="card" style="position:absolute;left:612px;top:138px;width:552px;height:410px;padding:12px 20px">
 ${dPat('Shared guards on the aggregate', 'domain methods, not GoF')}
 ${dK('Before PR #424')}
 <div style="display:flex;gap:8px">${dBox('n', 'Quote', 'lockJoinTerms', 'own inline checks', 'flex:1')}${dBox('n', 'After payment', 'joinPaid', 'own inline checks', 'flex:1')}</div>
 <div style="margin-top:6px">${dBox('bad', 'Team with 0 members: quote OK, DEAL_FULL after payment, refund', null, null)}</div>
 <div style="margin-top:12px">${dK('After (#422, merged 13 Oct)')}</div>
 <div style="display:flex;align-items:center;gap:6px">
  <div style="display:flex;flex-direction:column;gap:6px;width:150px">${dBox('n', null, 'lockJoinTerms', null, '', 14.5)}${dBox('n', null, 'joinPaid', null, '', 14.5)}</div>${dArr()}
  ${dBox('biz', 'GroupDeal', 'requireJoinWindow()<br>requireAvailableSeat()<br>requireNewMember()', null, 'flex:1', 14.5)}
 </div>
 <div style="font:400 15px/1.3 Inter;color:#6B655C;margin-top:6px">Each path keeps its own lock and check order</div>
</div>`;

// ---------- slide 10 ----------
const dCrash = () => {
  const xs = { w: 64, s: 240, d: 380 };
  const msg = (x1, x2, y, t, c = '#1B1A18', dash = false) => `<line x1="${x1}" y1="${y}" x2="${x2 + (x2 > x1 ? -2 : 2)}" y2="${y}" stroke="${c}" stroke-width="${c === '#1B1A18' ? 1.4 : 2}"${dash ? ' stroke-dasharray="6 4"' : ''} marker-end="url(#s10${c === '#1B1A18' ? 'a' : 'r'})"/><text x="${(x1 + x2) / 2}" y="${y - 8}" text-anchor="middle" font-size="15" fill="${c}">${t}</text>`;
  const head = (x, t) => `<rect x="${x - 58}" y="0" width="116" height="36" fill="#fff" stroke="#6B655C" stroke-width="1.4"/><text x="${x}" y="24" text-anchor="middle" font-size="15.5" font-weight="700" fill="#1B1A18">${t}</text><line x1="${x}" y1="36" x2="${x}" y2="300" stroke="#6B655C" stroke-width="1.2" stroke-dasharray="5 4"/>`;
  return `<svg width="440" height="300" viewBox="0 0 440 300" font-family="Inter, Helvetica, Arial, sans-serif">
  <defs><marker id="s10a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#1B1A18"/></marker>
  <marker id="s10r" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#E8402A"/></marker></defs>
  ${head(xs.w, 'Worker')}${head(xs.s, 'Stripe')}${head(xs.d, 'Database')}
  ${msg(xs.w, xs.s, 76, '1 create refund')}${msg(xs.s, xs.w, 116, '2 accepted', '#1B1A18', true)}
  <path d="M${xs.w - 9},${139} l18,18 M${xs.w + 9},${139} l-18,18" stroke="#8C2414" stroke-width="3"/><text x="${xs.w + 18}" y="${153}" font-size="15" font-weight="700" fill="#8C2414">3 crash, result not saved</text>
  ${msg(xs.d, xs.w, 200, '4 restart: still pending', '#1B1A18', true)}
  ${msg(xs.w, xs.s, 246, '5 create refund again', '#E8402A')}
  <text x="${xs.s + 10}" y="${280}" font-size="15" font-weight="700" fill="#B02E1B">6 a second refund</text></svg>`;
};
const dSol = (name, body) => `<div style="display:flex;gap:14px;padding:8px 0;border-top:1px solid #EFE9DF"><div style="width:150px;flex:none;font:700 16.5px/1.3 Inter">${name}</div><div style="font:400 16px/1.4 Inter">${body}</div></div>`;
const s10 = () => `
${dBand('UC6 · When a team expires, every paid member is refunded automatically',
  `refund owed${dTo}${cn('biz_refund')} + ${cn('RefundWork')}<br>payment provider${dTo}${cn('RefundGateway')} port<br>Stripe${dTo}${cn('StripeRefundGateway')} adapter`,
  'Stripe accepts a refund and the process dies before saving it: a naive retry refunds twice')}
<div class="card" style="position:absolute;left:0;top:138px;width:476px;height:410px;padding:12px 18px">
 ${dK('The crash window', '#B02E1B')}
 ${dCrash()}
 <div style="font:400 15.5px/1.3 Inter;margin-top:6px">Without a stable request identity, the retry is a new refund</div>
</div>
<div class="card" style="position:absolute;left:492px;top:138px;width:672px;height:410px;padding:12px 20px">
 ${dK('Solution')}
 ${dSol('Idempotency Key', `${cn('RefundWork.providerKey()', 15)} = "blocbuy-refund-" + id, sent by ${cn('StripeRefundGateway', 15)}: a retry gets the same refund back`)}
 ${dSol('Lease + version', 'A claim leases the row for 2 min and bumps its version; the result is saved only if the version still matches')}
 ${dSol('Fixed retry', 'A failed call is due again after a fixed 30 s, not exponential backoff')}
 ${dSol('Human review', 'Payment mismatch, permanent provider error, or no result after 23 h: parked for review, never a new refund')}
 ${dSol('Observer', `${cn('TeamExpired', 15)} → order and refund subscribers, before the same commit (ADR 0002)`)}
 <div style="margin-top:6px;border-radius:8px;background:#FFEDC7;padding:8px 14px">${dNote('flask', 'Proven locally with a Stripe substitute (3 Oct); no refund on dev yet (#223)', '#7A5C20')}</div>
</div>`;

// ---------- slide 11 ----------
const s11 = () => `
${dBand('Receive notifications · A buyer is told when their team succeeds or expires, or a payment succeeds',
  `notice${dTo}${cn('Notification')} «aggregate»<br>commerce fact${dTo}${cn('TeamExpiredV1')} contract<br>queue message${dTo}${cn('NotificationEventHandler')} port`,
  'Tell buyers without commerce calling notification, and never lose or repeat a notice')}
<div class="card" style="position:absolute;left:0;top:138px;width:1164px;height:196px;padding:12px 18px">
 <div style="display:flex;align-items:stretch;gap:6px">
  <div style="width:160px">${dK('Observer', '#B02E1B')}${dBox('ev', 'domain event', 'TeamExpired', 'published in-process', 'height:112px', 14.5)}</div>${dArr()}
  <div style="width:240px">${dK('Transactional Outbox', '#B02E1B')}${dBox('biz', 'BEFORE_COMMIT', 'IntegrationOutboxWriter', 'same transaction → sys_outbox', 'height:112px', 14.5)}</div>${dArr()}
  <div style="width:184px">${dK('Relay')}${dBox('biz', 'every 2 s, leased', 'OutboxRelayJob', 'batches of 100, 10 tries', 'height:112px', 14.5)}</div>${dArr()}
  <div style="width:170px">${dK('Transport')}${dBox('ev', 'at least once', 'SNS → SQS', 'DLQ after 5 receives', 'height:112px', 14.5)}</div>${dArr()}
  <div style="flex:1">${dK('Idempotent Receiver', '#B02E1B')}${dBox('plat', 'one transaction', 'markConsumed', 'then biz_notification rows', 'height:112px', 14.5)}</div>
 </div>
 <div style="margin-top:12px">${dNote('shield-halved', 'Commerce never calls notification: the ArchUnit rule <span class="mono" style="font-size:14.5px">nothing_depends_on_notification</span> fails the build', '#3E5CD9')}</div>
</div>
<div class="card" style="position:absolute;left:0;top:350px;width:574px;height:198px;padding:12px 20px">
 ${dK('Idempotent Receiver in SQL')}
 <div style="background:#1B1A18;color:#F6F2EC;border-radius:8px;padding:10px 14px;font:500 15px/1.5 'JetBrains Mono',monospace">INSERT INTO sys_consumed_message<br>&nbsp;&nbsp;(event_id, consumer, …)<br>ON CONFLICT (event_id, consumer) DO NOTHING</div>
 <div style="font:400 16px/1.3 Inter;margin-top:8px">0 rows inserted = a duplicate: nothing written, message deleted</div>
</div>
<div class="card" style="position:absolute;left:590px;top:350px;width:574px;height:198px;padding:12px 20px">
 ${dK('Where it runs')}
 ${[['dev · EC2', 'monolith: writer, relay and consumer off; no broker (ADR 0007)'], ['prod · ECS', 'commerce-svc → SNS → SQS → notification-svc'], ['local, CI', 'SQS on ElasticMQ: OutboxRelayElasticMqIT, NotificationConsumerElasticMqIT']].map(([a, b]) => `<div style="display:flex;gap:12px;padding:6px 0;border-top:1px solid #EFE9DF"><div style="width:96px;flex:none;font:700 16px Inter">${a}</div><div style="font:400 15.5px/1.35 Inter">${b}</div></div>`).join('')}
</div>`;

// ---------- slide 12 ----------
const s12 = () => `
${dBand('UC8 · A buyer sees their blocs (Active, Completed, Refunded) and orders on /account',
  `My blocs${dTo}${cn('GET /group-deals/me')}, latest 100<br>My orders${dTo}${cn('GET /orders/me')}, paged<br>view state${dTo}URL ${cn('?blocs=&amp;page=&amp;size=')}`,
  `A filter and a pager must survive reload, Back and a shared link; the API has no status filter <span style="white-space:nowrap">${mk('CONFIRM', 'Lim picks the problem')}</span>`)}
<div class="card" style="position:absolute;left:0;top:138px;width:1164px;height:180px;padding:12px 18px">
 ${dPat('URL as the single source of view state', 'router owns URL state, Query owns server state (frontend ADR 0001)')}
 <div style="display:flex;align-items:stretch;gap:6px">
  ${dBox('n', 'URL', '/account?<br>blocs=completed&amp;page=2', null, 'width:226px', 14.5)}${dArr()}
  ${dBox('plat', 'validateSearch', 'accountSearchSchema', 'bad value → fallback; defaults stripped', 'flex:1', 14.5)}${dArr()}
  ${dBox('plat', 'loader', 'orders + blocs', 'prefetched in parallel; past the last page → redirect', 'flex:1', 14.5)}${dArr()}
  ${dBox('plat', 'chip click', '?blocs=', 'not a loader dependency: filters the cache, no refetch', 'flex:1', 14.5)}
 </div>
</div>
<div class="card" style="position:absolute;left:0;top:332px;width:574px;height:216px;padding:12px 20px">
 ${dPat('Adapter at one boundary', 'frontend ADR 0009')}
 <div style="display:flex;align-items:center;gap:6px">${dBox('n', 'generated client', 'Result&lt;T&gt;', null, 'width:150px', 14.5)}${dArr()}${dBox('biz', 'select:', 'unwrapResult', null, 'width:160px', 14.5)}${dArr()}${dBox('plat', 'components', 'T or ApiError', null, 'flex:1', 14.5)}</div>
 <div style="font:400 15.5px/1.35 Inter;margin-top:10px">One function knows the envelope; the client is generated from the contract (ADR 0003)</div>
</div>
<div class="card" style="position:absolute;left:590px;top:332px;width:574px;height:216px;padding:12px 20px">
 ${dK('Evidence · account.test.tsx')}
 <ul class="b" style="font:400 15.5px/1.35 Inter"><li>switches chips through ?blocs= without refetching</li><li>falls back to Active for an unknown ?blocs= value</li><li>sends a page past the last one to the last page</li></ul>
 <div style="font:400 14.5px/1.35 Inter;color:#6B655C;margin-top:6px">Limit: only the latest 100 blocs; a closed bloc reads “Closed”, as this endpoint carries no refund data</div>
</div>`;

// ---------- slide 22 ----------
const s22 = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8</div>`;

const S = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 22: s22 };
for (let n = 2; n <= 21; n++) if (!S[n]) S[n] = todo(n);
module.exports = S;
