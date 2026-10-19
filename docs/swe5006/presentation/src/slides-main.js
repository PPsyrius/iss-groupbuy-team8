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

// ---------- slide 22 ----------
const s22 = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8</div>`;

const S = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 22: s22 };
for (let n = 2; n <= 21; n++) if (!S[n]) S[n] = todo(n);
module.exports = S;
