const { icon, md, svg, lockup } = require('../../../presentation-kit/lib');
// Accent fills never carry text (design-screens.md §2.1): accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
const F = bg => bg === 'var(--orange)' ? { bg: 'var(--orange-l)', fg: 'var(--orange-d)', sub: 'var(--orange-d)' } : bg === '#6B655C' ? { bg: 'var(--default)', fg: 'var(--foreground)', sub: 'var(--muted)' } : { bg, fg: '#fff', sub: 'rgba(255,255,255,.85)' };

// ---------- slide 1 ----------
const s1 = () => `
<div style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F">SWE5001 PRACTICE PROJECT · TEAM 8</div>
${lockup()}
<div style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff">A scalable community group-buying platform</div>
<div style="position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center">Three services <span style="color:#E8402A">•</span> event-driven <span style="color:#E8402A">•</span> rebuilt on every release</div>
<div style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1">Graduate Certificate in Architecting Scalable Systems · 21 October 2026</div>
<div style="position:absolute;left:850px;top:190px;width:400px;height:340px">
  ${[0, 1, 2, 3, 4].map(i => {
    const h = 260 - i * 42, x = i * 72, y = 340 - h, last = i === 4;
    return `<div style="position:absolute;left:${x + 36}px;top:${y}px;width:56px;height:${h}px;border-radius:6px 6px 2px 2px;background:${last ? 'var(--accent)' : 'var(--teal)'}"></div>
    <div style="position:absolute;left:${x + 40}px;top:${y - 62}px;width:48px;height:48px;border-radius:50%;background:#FFB01F;display:flex;align-items:center;justify-content:center;color:#1B1A18">${icon('user', 'ic')}</div>`;
  }).join('')}
</div>
<div style="position:absolute;left:880px;top:544px;font:italic 400 19px Inter;color:#E6DFD3">The price drops as each member joins</div>`;

// ---------- slide 2 ----------
const row = (ic, txt, bg, col = 'var(--ink)') => `<div style="display:flex;align-items:center;gap:28px;margin-bottom:30px">
  <div class="dot" style="width:80px;height:80px;background:${bg};font-size:40px">${icon(ic)}</div>
  <div style="font:400 25px/1.3 Inter;color:${col}">${txt}</div></div>`;
const s2 = () => `
<div class="card" style="position:absolute;left:0;top:50px;width:560px;height:476px;padding:32px 34px">
  <div class="serif" style="font-size:30px;line-height:1.2;color:var(--muted);margin-bottom:22px">Today: chat groups and spreadsheets</div>
  ${row('users', "Buyers can't see deadlines or progress", '#6B655C')}
  ${row('clipboard-list', 'Organisers collect money and refund by hand', '#6B655C')}
  ${row('store', 'Sellers have no place for team prices', '#6B655C')}
</div>
<div style="position:absolute;left:574px;top:288px;width:62px;height:3px;background:var(--orange)"></div>
<div style="position:absolute;left:628px;top:282px;border-left:12px solid var(--orange);border-top:7px solid transparent;border-bottom:7px solid transparent"></div>
<div class="card" style="position:absolute;left:654px;top:50px;width:510px;height:476px;padding:32px 34px;background:var(--inverse);border:none">
  <div class="serif" style="font-size:30px;color:#E8402A;margin-bottom:34px">With BlocBuy</div>
  ${row('credit-card', 'Pay to join a team', 'var(--orange)', '#fff')}
  ${row('tags', 'More members, lower price', 'var(--orange)', '#fff')}
  ${row('arrows-rotate', 'Team fills, or everyone is refunded automatically', 'var(--orange)', '#fff')}
</div>`;

// ---------- slide 3 ----------
const s3 = () => `
<div style="position:absolute;left:0;top:30px;width:360px;height:84px;border-radius:8px;background:#FFEDC7;padding:16px 22px;font:400 22px/1.3 Inter"><b>Seed</b>&nbsp; a few curated launch offers</div>
<div style="position:absolute;right:0;top:30px;width:360px;height:84px;border-radius:8px;background:#FBDFD9;padding:16px 22px;font:400 22px/1.3 Inter"><b>Network effect</b>&nbsp; each new member lowers the price</div>
<div class="card" style="position:absolute;left:0;top:130px;width:340px;height:230px;padding:18px 24px">
  <div class="serif" style="font-size:29px;color:var(--teal-d);margin-bottom:12px">Producers</div>
  <ul class="b" style="font:400 22px/1.32 Inter"><li>Sellers and admins publish offers</li><li>Buyers who start or share a team</li></ul></div>
<div class="card" style="position:absolute;right:0;top:130px;width:340px;height:230px;padding:18px 24px">
  <div class="serif" style="font-size:29px;color:var(--teal-d);margin-bottom:12px">Consumers</div>
  <ul class="b" style="font:400 22px/1.3 Inter;margin-bottom:0"><li style="margin-bottom:8px">Browse offers</li><li style="margin-bottom:8px">Join a team</li><li>Track orders</li></ul></div>
<div style="position:absolute;left:394px;top:64px;width:376px;height:312px;border-radius:50%;border:2px solid #DDD5C8;padding:8px">
  <div style="width:100%;height:100%;border-radius:50%;background:var(--inverse);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">
   <div style="font:700 18px Inter;letter-spacing:.12em;color:#FFB01F;margin-bottom:14px">CORE INTERACTION</div>
   <div class="serif" style="font-size:31px;line-height:1.2;color:#fff">Form a team at a<br>team price</div></div></div>
<div style="position:absolute;left:342px;top:244px;width:42px;height:3px;background:var(--teal-d)"></div><div style="position:absolute;left:380px;top:238px;border-left:11px solid var(--teal-d);border-top:7.5px solid transparent;border-bottom:7.5px solid transparent"></div>
<div style="position:absolute;left:774px;top:244px;width:36px;height:3px;background:var(--teal-d)"></div><div style="position:absolute;left:808px;top:238px;border-left:11px solid var(--teal-d);border-top:7.5px solid transparent;border-bottom:7.5px solid transparent"></div>
<div style="position:absolute;left:170px;top:370px;width:824px;height:26px;border:3px solid var(--orange);border-top:none"></div>
<div style="position:absolute;left:162px;top:362px;border-bottom:13px solid var(--orange);border-left:8.5px solid transparent;border-right:8.5px solid transparent"></div>
<div style="position:absolute;left:0;right:0;top:400px;text-align:center;font:italic 400 20px Inter;color:var(--orange-d)">Consumers become producers when they start or share a team</div>
<div style="position:absolute;left:0;right:0;top:438px;display:flex;align-items:center;gap:22px">
  <div style="font:700 22px Inter;color:var(--teal-d);width:296px">Shared, reusable services</div>
  ${['Identity', 'Notifications', 'Payments'].map(t => `<div class="pill" style="height:54px;flex:1;font-size:22px">${t}</div>`).join('')}
</div>
<div style="position:absolute;left:0;right:0;top:506px;font:400 19px Inter;color:var(--ink3)"><b style="color:var(--orange-d)">Future seeds:</b>&nbsp; event tickets · bulk subscriptions · neighbourhood produce boxes</div>`;

// ---------- slide 4 ----------
const col4 = (ic, name, bg, items, w) => `<div class="card" style="width:${w}px;height:420px;overflow:hidden">
  <div style="background:${F(bg).bg};height:96px;display:flex;align-items:center;gap:18px;padding:0 20px;color:${F(bg).fg}">
    <div class="dot" style="width:66px;height:66px;background:${bg === 'var(--orange)' ? 'var(--accent)' : 'rgba(255,255,255,.18)'};font-size:34px;color:#fff">${icon(ic)}</div>
    <div style="font:700 27px/1.15 Inter">${name}</div></div>
  <ul class="b" style="padding:24px 22px 0 26px;font:400 22px/1.32 Inter">${items.map(i => `<li style="margin-bottom:14px">${i}</li>`).join('')}</ul></div>`;
const s4 = () => `
<div style="display:flex;gap:26px;margin-top:20px">
${col4('user-check', 'Identity service', 'var(--teal)', ['Sign in with Google', 'Buyer and admin roles'], 350)}
${col4('store', 'Commerce service', 'var(--orange)', ['Browse team offers', 'Start or join a team, and pay', 'Team succeeds, or expires with refunds', 'Share invites, order history', 'Admin listings'], 460)}
${col4('bell', 'Notification service', 'var(--teal)', ['Team succeeded or expired', 'Payment received'], 350)}
</div>
<div style="position:absolute;left:6px;right:0;top:462px;font:400 20px/1.35 Inter;color:var(--muted)"><b style="color:var(--muted)">Not in scope:</b> multi-region · real payments (Stripe test mode) · email or push · splitting payments into its own service (our documented next step)</div>`;

// ---------- slide 5 ----------
const tick = (t, sub, kind = 'ok') => {
  const c = { ok: ['circle-check', 'var(--green)'], open: ['circle-exclamation', 'var(--orange)'], tbd: ['hourglass-half', '#6B655C'], not: ['circle-minus', '#6B655C'] }[kind];
  return `<div style="display:flex;gap:12px;margin-bottom:14px"><div style="color:${c[1]};font-size:24px;flex:none;margin-top:-1px">${icon(c[0])}</div>
  <div style="font:400 18px/1.3 Inter">${t}${sub ? `<div style="font:italic 400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px">${sub}</div>` : ''}</div></div>`;
};
const s5 = () => `
<div style="display:flex;gap:20px;height:540px;margin-top:-6px">
 <div style="flex:1.12;background:#E3E8FB;border-radius:8px;padding:22px 22px">
   <div class="serif" style="font-size:25px;color:var(--green);margin-bottom:16px">Verified on AWS · 17 Oct</div>
   ${tick('3 services built from nothing', 'tasks 2/2 · 1/1 · 1/1, all healthy')}
   ${tick('9/9 smoke checks through CloudFront')}
   ${tick('SNS → SQS routing confirmed', 'with test events')}
   ${tick('Teardown: 115 resources in 709 s', 'nothing left behind')}
   ${tick('One task: 200 req/s, 0 errors', 'p95 &lt; 60 ms (16 Oct)')}
 </div>
 <div style="flex:1;background:#EFE9DF;border:2px dashed #DDD5C8;border-radius:8px;padding:20px 22px">
   <div class="serif" style="font-size:25px;color:var(--muted);margin-bottom:16px">Tested on 19 Oct</div>
   ${tick('Auto-scaling under load', 'commerce 1 → 3 tasks on its own; ≈ 455 req/s, server p95 &lt; 50 ms')}
   ${tick('Queue scaling', 'not run on AWS · local test passed', 'not')}
   ${tick('Self-healing: stop a task', 'not run on AWS', 'not')}
   ${tick('Bad message to dead-letter queue', 'not run on AWS · local test passed', 'not')}
   ${tick('Identity outage drill', 'not run on AWS · local test passed', 'not')}
   ${tick('Buyer journey end to end', 'not run on AWS', 'not')}
 </div>
 <div style="flex:1.05;background:#FDEFEC;border-radius:8px;padding:22px 22px">
   <div class="serif" style="font-size:25px;color:var(--orange-d);margin-bottom:16px">Still open</div>
   ${tick('An interrupted start-up can leave auto-scaling paused', 'repaired by hand; no fix yet', 'open')}
   ${tick('Database and cache in one zone', 'a cost choice; multi-zone for production', 'open')}
   ${tick('Alarm e-mails to confirm before the final release', null, 'open')}
 </div>
</div>`;

// ---------- slide 6 ----------
const ms = [['4 Oct', 'Proposal submitted'], ['6 Oct', 'First promotion to AWS (one service)'], ['14 Oct', 'Split into three services; outbox, events'], ['16 Oct', 'Load baseline: 200 req/s per task'], ['17 Oct', 'First green three-service release'], ['19 Oct', 'Auto-scaling on AWS: 1 → 3 tasks']];
const eff = [['Liu Jin', 12.5, 'AWS stacks, pipeline, service split', true], ['Panpakorn (Pan)', 11.5, 'Messaging, scaling, login, load tests'], ['Yu Qianshuo (Yorik)', 9, 'Event outbox and relay, failure drills'], ['Lim Jiechao', 8.5, 'Lead; AWS accounts and guardrails; architecture study; presentation'], ['Wang Ziang (Eric)', 8.5, 'Notification consumer, dead-letter redrive, demo lead']];
const s6 = () => `
<div style="position:absolute;left:10px;right:10px;top:52px;height:3px;background:var(--teal)"></div>
${ms.map((m, i) => {
  const x = 52 + i * 202, last = i === 5;
  return `<div style="position:absolute;left:${x - 90}px;width:180px;top:0;text-align:center;font:700 21px Inter;color:var(--teal-d)">${m[0]}</div>
  <div style="position:absolute;left:${x - 14}px;top:40px;width:28px;height:28px;border-radius:50%;border:2px solid var(--teal);background:#fff;padding:4px"><div style="width:100%;height:100%;border-radius:50%;background:${last ? 'var(--orange)' : 'var(--teal-d)'}"></div></div>
  <div style="position:absolute;left:${x - 96}px;width:192px;top:80px;text-align:center;font:400 17px/1.25 Inter">${m[1]}</div>`;
}).join('')}
<div style="position:absolute;left:0;top:178px;display:flex;align-items:center;gap:14px"><div class="serif" style="font-size:23px;color:var(--teal-d)">Effort by member (person-days)</div></div>
${eff.map((e, i) => `<div style="position:absolute;left:0;top:${222 + i * 54}px;width:1164px;height:44px;display:flex;align-items:center">
  <div style="width:236px;font:700 19px Inter">${e[0]}</div>
  <div style="width:404px;display:flex;align-items:center;gap:10px"><div style="height:30px;border-radius:5px;width:${e[1] * 26}px;background:${e[3] ? 'var(--orange)' : 'var(--teal)'}"></div><div style="font:700 19px Inter;color:var(--teal-d)">${e[1]}</div></div>
  <div style="flex:1;font:400 16.5px/1.22 Inter;color:var(--ink3)">${e[2]}</div></div>`).join('')}
<div style="position:absolute;left:0;top:500px;font:italic 400 16.5px Inter;color:var(--muted)">Total ≈ 50 person-days, estimated from the issues each person delivered</div>`;

// ---------- slide 7 ----------
const op = (t) => `<div style="height:60px;border-radius:30px;background:var(--surface);border:2px solid var(--accent);color:var(--orange-d);font:700 21px/60px Inter;text-align:center;padding:0 20px">${t}</div>`;
const arr = (c = 'var(--orange)') => `<div style="display:flex;align-items:center"><div style="width:22px;height:3px;background:${c}"></div><div style="border-left:9px solid ${c};border-top:6px solid transparent;border-bottom:6px solid transparent"></div></div>`;
const s7 = () => `
<div style="position:absolute;left:0;top:56px;width:726px;height:304px;border-radius:8px;background:#FBDFD9;padding:24px 34px">
  <div class="serif" style="font-size:29px;color:var(--orange-d);margin-bottom:22px">One payment = one database transaction</div>
  <div style="display:flex;align-items:center;gap:2px">${op('Payment')}${arr()}${op('Order')}${arr()}${op('Stock')}${arr()}${op('Team')}</div>
  <div style="display:flex;align-items:center;justify-content:space-between;margin-top:30px">
   <div style="font:400 22px/1.32 Inter;width:420px">Splitting this needs sagas, so it stays together in</div>
   <div class="pill" style="height:64px;background:var(--accent);font-size:22px;white-space:nowrap;color:var(--foreground);background:var(--surface);border:2px solid var(--accent)">Commerce service</div></div>
</div>
<div class="card" style="position:absolute;left:0;top:380px;width:350px;height:170px;padding:22px 24px"><div style="font:400 21px/1.3 Inter;margin-bottom:16px">Identity: used through one small interface</div><div class="pill" style="height:54px;width:100%;font-size:20px">Identity service</div></div>
<div class="card" style="position:absolute;left:376px;top:380px;width:350px;height:170px;padding:22px 24px"><div style="font:400 21px/1.3 Inter;margin-bottom:16px">Notifications: nothing calls them</div><div class="pill" style="height:54px;width:100%;font-size:20px">Notification service</div></div>
<div class="card" style="position:absolute;left:756px;top:56px;width:408px;height:254px;padding:30px 34px;background:var(--inverse);border:none;display:flex;flex-direction:column;justify-content:center">
  <div class="serif" style="font-size:23px;color:#E8402A;margin-bottom:6px">Our rule</div>
  <div class="serif" style="font-size:38px;line-height:1.18;color:#fff">Split only where no transaction crosses</div></div>
<div style="position:absolute;left:756px;top:330px;width:408px;height:220px;border-radius:8px;background:#EFE9DF;border-left:6px solid #8C2414;padding:26px 26px;display:flex;gap:18px">
  <div class="dot" style="width:42px;height:42px;background:var(--red);font-size:36px">${icon('xmark')}</div>
  <div style="font:400 22px/1.32 Inter"><b>Rejected: five services</b><br>Sagas everywhere, about 50–55 person-days: our whole budget</div></div>
<div class="ref" style="right:0;bottom:auto;top:16px">AD-02</div>`;

// ---------- slide 8 ----------
const dc = (ic, bg, t, s, ad) => `<div class="card" style="height:198px;padding:30px 28px;display:flex;gap:28px">
  <div class="dot" style="width:88px;height:88px;background:${bg};font-size:42px">${icon(ic)}</div>
  <div style="flex:1"><div class="serif" style="font-size:27px;line-height:1.15;margin:4px 0 14px;padding-right:56px">${t}</div><div style="font:400 21px/1.32 Inter;color:var(--ink3)">${s}</div></div>
  <span class="tag" style="position:absolute;right:18px;top:18px">${ad}</span></div>`;
const s8 = () => `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:6px">
 ${dc('code-branch', 'var(--orange)', 'Split by transaction boundary', 'No distributed transactions to build or debug', 'AD-02')}
 ${dc('paper-plane', 'var(--teal)', 'Events through an outbox and queues', 'An event is sent only if the change really committed', 'AD-04')}
 ${dc('id-badge', 'var(--teal)', 'One shared login session', 'Any service checks roles without calling identity', 'AD-05')}
 ${dc('shield-halved', 'var(--teal)', 'Fail safe when identity is down', 'Purchases are refused, never half-done', 'AD-06')}
</div>
<div style="margin-top:22px;border-radius:8px;background:#FDEFEC;padding:16px 24px;font:400 20px/1.3 Inter;display:flex;gap:14px;align-items:center">
 <span style="color:var(--orange-d);font-size:24px">${icon('scale-balanced')}</span><span><b style="color:var(--orange-d)">Cost of these choices:</b> notifications lag about 2 s; purchases pause while identity is down</span></div>`;

// ---------- slide 9 ----------
const s9 = () => `<div class="dia" style="margin-top:-8px">${svg('logical-view')}</div>
<div class="cap" style="margin-top:8px"><b style="color:var(--red)">One direct call</b> (commerce → identity); everything else by events.</div>`;

// ---------- slide 10 ----------
const ctx = (n, s, c) => `<div style="background:#fff;border:2px solid ${c};border-radius:8px;padding:12px 18px;height:100%"><div style="font:700 25px Inter;color:${c === 'var(--orange)' ? 'var(--orange-d)' : c}">${n}</div><div style="font:400 20px Inter;margin-top:2px">${s}</div></div>`;
const ev = (a, b) => `<div style="display:flex;align-items:center;gap:6px;margin-bottom:18px"><div style="width:196px;height:54px;border-radius:27px;background:var(--default);color:var(--foreground);font:600 15.5px/54px Inter;text-align:center">${a}</div>${arr()}<div style="width:196px;height:54px;border-radius:27px;background:var(--orange-l);color:var(--orange-d);font:600 15.5px/54px Inter;text-align:center">${b}</div></div>`;
const s10 = () => `
<div style="position:absolute;left:0;top:20px;width:730px;height:296px;background:#FBDFD9;border-radius:8px;padding:14px 22px">
  <div style="font:700 25px Inter;color:var(--orange-d);margin-bottom:10px">Commerce service</div>
  <div style="display:grid;grid-template-columns:1fr 1fr;grid-template-rows:106px 106px;gap:14px">
   ${ctx('Catalog', 'products, team prices', 'var(--orange)')}${ctx('Group buying', 'teams, members', 'var(--orange)')}${ctx('Orders', 'orders', 'var(--orange)')}${ctx('Payments', 'payments, refunds', 'var(--orange)')}</div></div>
<div style="position:absolute;left:0;top:336px;width:356px;height:190px;background:#E3E8FB;border-radius:8px;padding:14px 22px">
  <div style="font:700 23px Inter;color:var(--teal-d);margin-bottom:10px">Identity service</div><div style="height:110px">${ctx('Identity', 'users, roles', 'var(--teal-d)')}</div></div>
<div style="position:absolute;left:374px;top:336px;width:356px;height:190px;background:#E3E8FB;border-radius:8px;padding:14px 22px">
  <div style="font:700 23px Inter;color:var(--teal-d);margin-bottom:10px">Notification service</div><div style="height:110px">${ctx('Notification', 'notifications', 'var(--teal-d)')}</div></div>
<div style="position:absolute;left:750px;top:22px;width:414px">
  <div class="serif" style="font-size:22.5px;color:var(--teal-d);margin-bottom:22px">Domain event → integration event</div>
  ${ev('TeamSucceeded', 'TeamSucceededV1')}${ev('TeamExpired', 'TeamExpiredV1')}${ev('PaymentSucceeded', 'PaymentSucceededV1')}
  <div class="card" style="padding:20px 22px;margin-top:4px"><ul class="b" style="font:400 21px/1.3 Inter"><li style="margin-bottom:12px">Inside a service: free to change</li><li style="margin-bottom:0">Between services: versioned, IDs only</li></ul></div>
</div>
`;

// ---------- slide 11 ----------
const plat = (t, s, bg, col = '#fff', sc = 'rgba(255,255,255,.85)') => `<div style="height:50px;border-radius:8px;background:${bg};padding:4px 18px;margin-bottom:6px"><div style="font:700 19px/1.15 Inter;color:${col}">${t}</div><div style="font:400 15.5px/1.2 Inter;color:${sc}">${s}</div></div>`;
const s11 = () => `
<div class="card" style="position:absolute;left:0;top:0;width:300px;height:218px;padding:14px 22px">
  <div class="serif" style="font-size:25px;color:var(--teal-d);margin-bottom:10px">Producers</div>
  <ul class="b" style="font:400 18.5px/1.3 Inter"><li>Sellers and admins publish listings</li><li>Buyers start and share teams</li></ul></div>
<div style="position:absolute;left:328px;top:0;width:508px">
  ${plat('Identity', 'one account for both sides', 'var(--teal)')}
  ${plat('Commerce', 'offers, teams, payments', 'var(--orange-l)', 'var(--orange-d)', 'var(--orange-d)')}
  ${plat('Events', 'any new consumer just subscribes', 'var(--yellow)', 'var(--warning-soft-foreground)', 'var(--warning-soft-foreground)')}
  ${plat('Notification', 'tells people what happened', 'var(--teal)')}
</div>
<div class="card" style="position:absolute;right:0;top:0;width:300px;height:218px;padding:14px 22px">
  <div class="serif" style="font-size:25px;color:var(--teal-d);margin-bottom:10px">Consumers</div>
  <ul class="b" style="font:400 18.5px/1.3 Inter"><li>Browse and join</li><li>Track orders</li><li>Get notified</li></ul></div>
<div style="position:absolute;left:300px;top:76px">${arr('var(--teal-d)')}</div>
<div style="position:absolute;left:836px;top:76px;transform:scaleX(-1)">${arr('var(--teal-d)')}</div>
<div style="position:absolute;left:836px;top:188px">${arr('var(--teal-d)')}</div>
<div style="position:absolute;left:0;top:232px;width:724px">
  <div style="display:flex;align-items:center;gap:12px;margin-bottom:8px"><div class="serif" style="font-size:21px;color:var(--teal-d)">Measured reuse</div></div>
  <table class="t" style="font-size:16.5px"><tr><th style="width:190px">Added to the platform</th><th>What it took</th><th style="width:150px">Commerce edits</th></tr>
   <tr><td><b>Audit queue</b></td><td>2 lines of Terraform (a name and a filter)</td><td style="font-weight:700;color:var(--green)">0</td></tr>
   <tr><td><b>A new service</b></td><td>1 entry in the services map (golden-path module)</td><td style="font-weight:700;color:var(--green)">0</td></tr></table>
</div>
<div class="card" style="position:absolute;left:744px;top:270px;width:420px;height:164px;padding:16px 20px">
  <div style="display:flex;gap:10px;align-items:center;margin-bottom:8px"><span style="color:var(--teal);font-size:22px">${icon('file-code')}</span><div class="serif" style="font-size:21px;color:var(--teal-d)">One public contract</div></div>
  <div style="font:400 17px/1.4 Inter"><span class="mono" style="font-size:15px;background:var(--teal-ll);padding:1px 5px;border-radius:4px">contracts/openapi.json</span> → generated TypeScript client<br>Events versioned (V1), IDs only</div></div>
<div style="position:absolute;left:0;right:0;top:456px;height:86px;border-radius:8px;background:var(--inverse);display:flex;align-items:center;padding:0 22px;gap:14px">
  <div style="font:700 19px/1.15 Inter;color:#E8402A;width:168px">Every new service starts with</div>
  ${['Login &amp; roles', 'Error format', 'Tracing', 'Outbox', 'Duplicate protection'].map(t => `<div style="background:#fff;border-radius:999px;height:44px;padding:0 18px;font:700 17px/44px Inter;color:var(--teal-d);white-space:nowrap">${t}</div>`).join('')}
</div>`;

// ---------- slide 12 ----------
const pc = (ic, bg, t, s, ad, w, h = 172) => `<div class="card" style="width:${w}px;height:${h}px;padding:20px 22px">
  <div style="display:flex;align-items:center;gap:16px;margin-bottom:12px"><div class="dot" style="width:64px;height:64px;background:${bg};font-size:30px">${icon(ic)}</div><div style="font:700 22px/1.18 Inter;flex:1">${t}</div></div>
  <div style="font:400 18px/1.32 Inter;color:var(--ink3)">${s}</div><span class="tag" style="position:absolute;right:14px;bottom:12px">${ad}</span></div>`;
const s12 = () => `
<div style="display:flex;gap:18px">
 ${pc('cubes', 'var(--teal)', 'Fargate containers on ARM', 'No servers to manage · ~20% cheaper · a Kubernetes control plane alone ≈ US$73 a month', 'AD-07', 376, 196)}
 ${pc('recycle', 'var(--orange)', 'Disposable environment', 'Built per session or release, then destroyed', 'AD-12', 376, 196)}
 ${pc('database', 'var(--teal)', 'One database server', 'A database per service: isolation at low cost; its own server is the scale-out path', 'AD-11', 376, 196)}
</div>
<div style="display:flex;gap:18px;margin-top:18px">
 ${pc('sitemap', 'var(--teal)', 'Small NAT instance', '≈ US$4 instead of US$43 a month', 'AD-10', 573, 150)}
 ${pc('key', 'var(--teal)', 'No stored cloud keys', 'Short-lived tokens · secrets in Parameter Store', 'AD-14', 573, 150)}
</div>
<div style="margin-top:18px;border-radius:8px;background:var(--inverse);padding:16px 26px;display:flex;align-items:center;gap:22px;color:#fff">
 <div style="font:700 15px Inter;letter-spacing:.1em;color:#FFB01F;width:120px;line-height:1.25;text-transform:uppercase">Measured,<br>not estimated</div>
 <div style="font:700 50px/1 Inter">US$0.49</div>
 <div style="font:400 19px/1.35 Inter;flex:1">in total for the runtime's database, cache and containers across <b>7 up/down sessions</b>, 15–17 Oct<div style="font:400 14.5px Inter;color:#B5ADA1">AWS Cost Explorer, filtered to the runtime services</div></div></div>
<div style="margin-top:12px;font:400 18px Inter;color:var(--muted)"><b style="color:var(--muted)">Trade-off:</b> less redundancy (one NAT, single-zone database), acceptable for this project</div>`;

// ---------- slide 13 ----------
const s13 = () => `<div class="dia" style="margin-top:-12px">${svg('physical-deployment')}</div>`;

// ---------- slide 14 ----------
const p95a = [57, 34, 26, 43, 28, 26, 23, 26, 35, 49], p95b = [53, 39, 27, 28, 23, 23, 42, 23, 23, 23], xs = [10, 20, 40, 60, 80, 100, 125, 150, 175, 200];
function chart() {
  const W = 560, H = 186, L = 40, B = 160, T = 10, R = 548;
  const X = i => L + 18 + i * ((R - L - 30) / 9), Y = v => B - (v / 80) * (B - T);
  const line = (d, c) => `<polyline points="${d.map((v, i) => `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"/>` + d.map((v, i) => `<circle cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join('');
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 20, 40, 60, 80].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 20}" font-size="14" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${line(p95a, '#A69E92')}${line(p95b, '#3E5CD9')}</svg>`;
}
const sig = (t, s, bg, c) => `<div style="border-radius:8px;background:${bg};padding:12px 18px;margin-bottom:10px"><div class="serif" style="font-size:22px;color:${c}">${t}</div><div style="font:400 17.5px/1.3 Inter;margin-top:2px">${s}</div></div>`;
const s14 = () => `
<div style="position:absolute;left:0;top:0;width:470px">
 ${sig('Commerce', 'Requests per task (target 12,000/min) → <b>2 to 6 tasks</b>', '#FBDFD9', 'var(--orange-d)')}
 ${sig('Notification', 'Queue backlog per task (target 50) → <b>1 to 4 tasks</b>', '#E3E8FB', 'var(--teal-d)')}
 ${sig('Identity', 'Fixed at <b>1 task</b>', '#EFE9DF', 'var(--ink2)')}
 <div class="card" style="padding:14px 18px;margin-top:14px;border:2px solid var(--teal-d)">
  <div class="serif" style="font-size:21px;color:var(--teal-d);margin-bottom:8px">Capacity statement</div>
  <div style="font:400 16.5px/1.38 Inter">
   <div><b>Measured:</b> 200 req/s on one task (16 Oct); ≈ 455 req/s on 3 tasks (19 Oct, three services)</div>
   <div><b>Projected:</b> 6 tasks × 200 ≈ <b>1,200 req/s</b> for browsing <span style="color:var(--orange-d);font-weight:700">(projection, not measured)</span></div>
   <div><b>Next limit:</b> the single database, then the single outbox relay (≈ 50 events/s, calculated)</div></div></div>
</div>
<div style="position:absolute;left:500px;top:0;width:664px">
 <div style="font:700 17px Inter;margin-bottom:2px">Load test, one task, 16 Oct: response time (p95, ms)</div>
 ${chart()}
 <div style="display:flex;gap:22px;font:400 14.5px Inter;color:var(--ink3);margin:-2px 0 10px 40px"><span><i style="display:inline-block;width:20px;height:3px;background:#A69E92;vertical-align:4px"></i> First run</span><span><i style="display:inline-block;width:20px;height:3px;background:var(--teal);vertical-align:4px"></i> Repeat run</span><span style="font-style:italic;color:var(--muted);margin-left:auto">requests per second →</span></div>
 <div style="display:flex;gap:12px">
  ${[['200', 'req/s', 'highest tested, not saturated'], ['&lt; 60', 'ms', '95% of responses'], ['0', 'errors', '57,600 requests']].map(s => `<div class="card" style="flex:1;padding:8px 10px;text-align:center"><div><span class="serif" style="font-size:34px;color:var(--teal-d)">${s[0]}</span> <span style="font:700 16px Inter;color:var(--teal-d)">${s[1]}</span></div><div style="font:400 14.5px Inter;color:var(--muted)">${s[2]}</div></div>`).join('')}
 </div>
 <div style="margin-top:12px;border-radius:8px;background:#E3E8FB;padding:10px 16px;display:flex;align-items:center;gap:14px">
  <span style="color:var(--teal);font-size:26px;flex:none">${icon('chart-line')}</span>
  <div style="font:400 16.5px/1.35 Inter"><b>Three services, 19 Oct</b><br>commerce 1 → 3 tasks on its own; ≈ 455 req/s, server p95 &lt; 50 ms</div></div>
</div>`;

// ---------- slide 15 ----------
const cyl = (t, s, c, bg) => `<div style="position:relative;width:370px;height:190px">
 <svg width="370" height="190" style="position:absolute;left:0;top:0"><path d="M2,30 A183,28 0 0 1 368,30 V160 A183,28 0 0 1 2,160 Z" fill="${bg}" stroke="${c}" stroke-width="2"/><path d="M2,30 A183,28 0 0 0 368,30" fill="none" stroke="${c}" stroke-width="2"/><ellipse cx="185" cy="30" rx="183" ry="28" fill="${bg}" stroke="${c}" stroke-width="2" fill-opacity=".6"/></svg>
 <div style="position:absolute;left:0;right:0;top:70px;text-align:center"><div class="serif" style="font-size:28px;color:${c}">${t}</div><div style="font:400 19px/1.3 Inter;margin-top:4px">${s}</div></div></div>`;
const cal = (ic, t, w, bg = 'var(--teal)') => `<div class="card" style="width:${w}px;height:226px;padding:18px 20px"><div class="dot" style="width:60px;height:60px;background:${bg};font-size:28px;margin-bottom:14px">${icon(ic)}</div><div style="font:700 18.5px/1.3 Inter">${t}</div></div>`;
const s15 = () => `
<div style="display:flex;justify-content:space-between">${cyl('Identity DB', 'users, roles', 'var(--teal-d)', '#E3E8FB')}${cyl('Commerce DB', 'products, teams, orders,<br>payments, outbox', 'var(--orange)', '#FBDFD9')}${cyl('Notification DB', 'notifications,<br>processed events', 'var(--teal-d)', '#E3E8FB')}</div>
<div style="display:flex;gap:16px;margin-top:20px">
 ${cal('lock', 'Each service reads only its own tables<div style="font:400 15.5px/1.3 Inter;color:var(--ink3);margin-top:6px">per-service roles, enforced by grants and tested</div>', 238)}
 ${cal('bolt', 'The cache holds sessions only', 200)}
 ${cal('credit-card', 'No card data stored; events carry IDs only', 236)}
 ${cal('arrow-down-short-wide', 'Concurrency<div style="font:400 16px/1.35 Inter;color:var(--ink2);margin-top:6px">Row locks in a fixed order (<b>team → intent → payment</b>) settle the last-seat race. Scheduled jobs hold a database lease, so one task runs them however many are up.</div>', 442, 'var(--orange)')}
</div>`;

// ---------- slide 16 ----------
const s16 = () => `<div class="dia" style="margin-top:-10px">${svg('expiry-refund-sequence')}</div>`;

// ---------- slide 17 ----------
const fc = (ic, bg, t, s) => `<div class="card" style="flex:1;height:340px;padding:30px 30px">
 <div class="dot" style="width:88px;height:88px;background:${bg};font-size:40px;margin-bottom:22px">${icon(ic)}</div>
 <div class="serif" style="font-size:20px;color:var(--muted)">If…</div><div class="serif" style="font-size:26px;line-height:1.2;margin-bottom:18px">${t}</div>
 <div style="font:400 22px/1.32 Inter;color:var(--ink3)">${s}</div></div>`;
const ok = t => `<div style="flex:1;height:92px;border-radius:8px;background:var(--inverse);display:flex;align-items:center;gap:22px;padding:0 30px"><div class="dot" style="width:44px;height:44px;background:var(--accent);color:#fff;font-size:40px">${icon('check')}</div><div style="font:700 27px/1.15 Inter;color:#fff">${t}</div></div>`;
const s17 = () => `
<div style="display:flex;gap:18px;margin-top:6px">
 ${fc('clone', 'var(--teal)', 'A message arrives twice', 'Ignored: its (event ID, consumer) is already recorded')}
 ${fc('biohazard', 'var(--orange)', 'A message keeps failing', 'Moved to a dead-letter queue after 5 tries; an alarm fires')}
 ${fc('rotate-right', 'var(--teal)', 'Publishing fails', 'The event stays in the outbox and is retried')}
</div>
<div style="display:flex;gap:18px;margin-top:22px">${ok('No lost events')}${ok('No double notifications')}${ok('Bad messages isolated')}</div>`;

// ---------- slide 18 ----------
const s18 = () => `
<div style="position:absolute;left:0;top:0;width:760px;height:540px">
 <div style="position:absolute;left:0;top:6px;font:italic 400 20px Inter;color:var(--muted)">feature branches</div>
 ${[0, 1, 2, 3, 4, 5].map(i => `<svg style="position:absolute;left:${92 + i * 100}px;top:36px" width="44" height="58"><line x1="4" y1="2" x2="36" y2="50" stroke="#6B655C" stroke-width="2"/><path d="M28,48 L37,52 L37,42" fill="none" stroke="#6B655C" stroke-width="2"/></svg>`).join('')}
 <div style="position:absolute;left:0;top:76px;font:700 28px Inter;color:var(--teal-d)">dev</div>
 <div style="position:absolute;left:0;top:118px;font:400 18px Inter;color:var(--teal-d)">main branch</div>
 <div style="position:absolute;left:88px;top:93px;width:640px;height:4px;background:var(--teal-d)"></div><div style="position:absolute;left:724px;top:87px;border-left:15px solid var(--teal-d);border-top:8px solid transparent;border-bottom:8px solid transparent"></div>
 <div style="position:absolute;left:244px;top:110px;font:400 15px Inter;color:var(--ink3);background:#F6F2EC;padding:0 4px;z-index:2">squash · 1 review + CODEOWNERS · required checks</div>
 ${[200, 520].map(x => `<svg style="position:absolute;left:${x}px;top:96px" width="40" height="176"><line x1="2" y1="0" x2="30" y2="168" stroke="#E8402A" stroke-width="3"/><path d="M22,158 L31,172 L36,156" fill="none" stroke="#E8402A" stroke-width="3"/></svg><div style="position:absolute;left:${x + 40}px;top:150px;font:700 19px/1.2 Inter;color:var(--orange-d)">promotion<br>pull request</div>`).join('')}
 <div style="position:absolute;left:0;top:248px;font:700 28px Inter;color:var(--orange-d)">prod</div>
 <div style="position:absolute;left:0;top:290px;font:400 18px Inter;color:var(--orange-d)">release branch</div>
 <div style="position:absolute;left:88px;top:265px;width:640px;height:4px;background:var(--orange)"></div><div style="position:absolute;left:724px;top:259px;border-left:15px solid var(--orange);border-top:8px solid transparent;border-bottom:8px solid transparent"></div>
 <div style="position:absolute;left:150px;top:284px;font:400 16px Inter;color:var(--ink3)">promotion PR only · no direct pushes · no bypass actors</div>
 <div class="pill neutral" style="position:absolute;left:0;top:362px;height:62px;width:350px;font-size:21px">Review + automated checks</div>
 <div class="pill biz" style="position:absolute;left:372px;top:362px;height:62px;width:350px;font-size:21px">No direct pushes to prod</div>
</div>
<div class="card" style="position:absolute;right:0;top:0;width:410px;height:500px;padding:26px 28px">
 <div class="serif" style="font-size:29px;color:var(--teal-d);margin-bottom:26px">Artefacts</div>
 ${[['cubes', 'Container images', 'one ARM64 image per service, stored by digest, not tag'], ['key', 'Release record', 'which digests are live'], ['file-code', 'API contract', 'one file in the repository']].map(a => `<div style="display:flex;gap:20px;align-items:center;margin-bottom:34px"><div class="dot" style="width:72px;height:72px;background:var(--teal);font-size:32px">${icon(a[0])}</div><div><div style="font:700 23px Inter">${a[1]}</div><div style="font:400 19px/1.3 Inter;color:var(--ink3)">${a[2]}</div></div></div>`).join('')}
</div>`;

// ---------- slide 19 ----------
const ck = t => `<div style="display:flex;gap:12px;margin-bottom:12px;font:400 19px/1.25 Inter"><span style="color:var(--green);font-size:22px;flex:none">${icon('check')}</span><span>${t}</span></div>`;
const gc = (ic, bg, t, items) => `<div class="card" style="flex:1;height:350px;padding:22px 22px"><div class="dot" style="width:76px;height:76px;background:${bg};font-size:34px;margin-bottom:16px">${icon(ic)}</div><div class="serif" style="font-size:25px;margin-bottom:18px">${t}</div>${items.map(ck).join('')}</div>`;
const s19 = () => `
<div style="display:flex;gap:16px">
 ${gc('gears', 'var(--teal)', 'Build &amp; test', ['Unit tests and API contract check', '≥ 80% coverage on new code (SonarCloud)'])}
 ${gc('cubes', 'var(--teal)', 'Containers', ['Build and scan every service image', 'Boot the app and check its health'])}
 ${gc('user-shield', 'var(--orange)', 'Security', ['Secret scanning (gitleaks)', 'Dependency and image scans (Trivy)', 'Static analysis (SonarCloud)'])}
 ${gc('server', 'var(--teal)', 'Infrastructure', ['Terraform format, validate, test', 'Policy checks (checkov)', 'Plan posted to the PR'])}
</div>
<div style="margin-top:16px;display:flex;gap:14px">
 ${[['5,252', 'workflow runs, 16 Sep – 16 Oct'], ['94% / 95%', 'runs green, backend / frontend'], ['8.6 / 3.1 min', 'median run, backend / frontend']].map(m => `<div style="flex:1;border-radius:8px;background:var(--teal-ll);border:1px solid #DDD5C8;padding:10px 18px;display:flex;align-items:baseline;gap:12px"><span class="serif" style="font-size:30px;color:var(--teal-d);white-space:nowrap">${m[0]}</span><span style="font:400 16px/1.25 Inter;color:var(--ink3)">${m[1]}</span></div>`).join('')}
</div>
<div style="margin-top:14px;border-radius:8px;background:#EFE9DF;border-left:6px solid #8C2414;padding:14px 26px;font:700 23px Inter;color:var(--red)">If any check fails, the pull request cannot merge</div>`;

// ---------- slide 20 ----------
const st = (t, s, bg, d, d0) => `<div style="flex:1;height:226px;border-radius:8px;background:${F(bg).bg};color:${F(bg).fg};padding:22px 14px 0;text-align:center;position:relative">
 <div style="font:700 26px/1.12 Inter">${t}</div><div style="font:400 17px/1.25 Inter;color:${F(bg).sub};margin-top:6px">${s}</div>
 <div style="position:absolute;left:0;right:0;top:136px;font:700 28px 'JetBrains Mono',monospace">${d}</div>
 ${d0 ? `<div style="position:absolute;left:0;right:0;top:182px;font:400 14.5px Inter;color:${F(bg).sub}">first run 6 Oct: ${d0}</div>` : ''}</div>`;
const chev = `<div style="width:20px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:16px">${icon('caret-right')}</div>`;
const s20 = () => `
<div style="display:flex;align-items:stretch">
 ${st('Approve', 'promotion pull request', '#6B655C', '', '')}${chev}
 ${st('Build', 'container images', 'var(--teal)', '≈ 5 min', '2.5 min')}${chev}
 ${st('Create + deploy', 'environment and smoke tests', 'var(--teal)', '≈ 32 min', '13 min')}${chev}
 ${st('Release', 'mark images as live', 'var(--teal)', '≈ 1 min', '1 min')}${chev}
 ${st('Destroy', 'and check nothing is left', 'var(--orange)', '≈ 13 min', '10 min')}
</div>
<div style="display:flex;gap:16px;margin-top:18px">
 <div style="flex:1.25;border-radius:8px;background:#E3E8FB;padding:18px 22px">
  <div style="display:flex;align-items:baseline;gap:12px"><span class="serif" style="font-size:44px;color:var(--teal-d);white-space:nowrap">51 min</span><span style="font:400 18px/1.25 Inter">end to end, three services, 17 Oct</span></div>
  <div style="font:400 16.5px/1.35 Inter;color:var(--ink3);margin-top:4px">28 min on 6 Oct with one service · 115 resources destroyed in 709 s</div></div>
 <div style="flex:1;border-radius:8px;background:#FBDFD9;padding:18px 22px"><div style="font:700 22px Inter">Destroy always runs</div><div style="font:400 17px/1.35 Inter;color:var(--ink3);margin-top:4px">even if an earlier step failed</div></div>
 <div style="flex:1;border-radius:8px;background:#E3E8FB;padding:18px 22px"><div style="font:700 22px Inter">Failed deploy rolls back</div><div style="font:400 17px/1.35 Inter;color:var(--ink3);margin-top:4px">the ECS circuit breaker restores the last good version</div></div>
</div>
<div style="margin-top:14px;display:flex;gap:16px;font:400 17.5px/1.3 Inter">
 <div style="flex:1;display:flex;gap:10px"><span style="color:var(--teal);font-size:20px">${icon('key')}</span>Release digests in Parameter Store are the last-known-good set</div>
 <div style="flex:1;display:flex;gap:10px"><span style="color:var(--teal);font-size:20px">${icon('arrows-rotate')}</span>Every release is a rebuild from zero, so every release also tests disaster recovery</div>
</div>`;

// ---------- slide 21 ----------
const lc = (n, bg, t, s, fix) => `<div class="card" style="flex:1;height:500px;padding:28px 28px">
 <div class="dot" style="width:70px;height:70px;background:${F(bg).bg};color:${F(bg).fg};font:700 30px Inter;margin-bottom:24px">${n}</div>
 <div class="serif" style="font-size:28px;line-height:1.18;margin-bottom:18px;height:70px">${t}</div>
 <div style="font:400 21px/1.35 Inter;color:var(--ink3);height:150px">${s}</div>
 <div style="border-radius:8px;background:#E3E8FB;padding:20px 20px;font:700 22px/1.25 Inter;color:var(--green);height:98px;display:flex;align-items:center">${fix}</div></div>`;
const s21 = () => `<div style="display:flex;gap:18px">
 ${lc(1, 'var(--teal-d)', 'Mocks passed, the real deploy failed', 'Missing cloud permissions only showed up in a real run', 'Test permissions for real')}
 ${lc(2, 'var(--teal-d)', 'Events lost right after deploy', 'New SNS subscriptions take up to 15 minutes to route; nothing errors, messages just go nowhere', 'Deploy now waits for routing')}
 ${lc(3, 'var(--orange)', 'Auto-scaling silently paused', 'A retried start-up reported success but left scaling suspended', "Check scaling, don't assume it")}
</div>`;

// ---------- slide 22 (new) ----------
const s22 = () => `
<div style="position:absolute;left:0;top:0;width:330px">
 <div class="card" style="padding:16px 18px;margin-bottom:14px">
  <div style="display:flex;gap:12px;align-items:center;margin-bottom:8px"><div class="dot" style="width:46px;height:46px;background:var(--teal);font-size:22px">${icon('users')}</div><div><div style="font:700 19px Inter">People</div><div style="font:400 15.5px Inter;color:var(--ink3)">IAM Identity Center</div></div></div>
  <div style="font:400 16.5px/1.38 Inter">admins (3) · developers (2)<br>1-hour sessions</div></div>
 <div class="card" style="padding:16px 18px;margin-bottom:14px">
  <div style="display:flex;gap:12px;align-items:center;margin-bottom:8px"><div class="dot" style="width:46px;height:46px;background:var(--teal);font-size:22px">${icon('robot')}</div><div><div style="font:700 19px Inter">Machines</div><div style="font:400 15.5px Inter;color:var(--ink3)">GitHub OIDC</div></div></div>
  <div style="font:400 16.5px/1.38 Inter">plan / deploy / apply roles<br><b>no AWS keys in GitHub</b></div></div>
 <div style="border-radius:8px;background:#FDEFEC;border:1.5px solid #F6BFB5;padding:14px 18px">
  <div style="display:flex;gap:10px;align-items:center;font:700 17px Inter;color:var(--orange-d);margin-bottom:6px">${icon('triangle-exclamation')} Disclosed exception</div>
  <div style="font:400 16px/1.35 Inter">2 IAM users for port forwarding (1 active key), inside the same guardrails</div></div>
</div>
<svg style="position:absolute;left:334px;top:60px" width="70" height="300"><g stroke="#3E5CD9" stroke-width="3" fill="none"><path d="M4,40 H60"/><path d="M4,186 H60"/></g><path d="M58,32 L70,40 L58,48 Z" fill="#3E5CD9"/><path d="M58,178 L70,186 L58,194 Z" fill="#3E5CD9"/></svg>
<div style="position:absolute;left:408px;top:0;width:756px;height:540px;border-radius:8px;border:2.5px solid var(--teal-d);background:#fff;padding:14px 18px">
 <div style="display:flex;align-items:center;gap:12px"><span style="font-size:24px;color:var(--teal-d)">${icon('building')}</span><div style="font:700 21px Inter;color:var(--teal-d)">AWS Organization</div><div style="font:400 16px Inter;color:var(--ink3)">management account runs no workloads</div></div>
 <div style="position:absolute;left:18px;right:18px;top:58px;bottom:16px;border-radius:8px;border:2px solid var(--teal);background:var(--teal-ll);padding:12px 16px">
  <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><div style="font:700 19px Inter">Root</div><span class="tag">SCP · Org-Baseline</span><span class="tag">SCP · Region-Pin: Singapore + global</span></div>
  <div style="position:absolute;left:16px;right:16px;top:54px;bottom:16px;border-radius:8px;border:2px solid var(--orange);background:var(--orange-ll);padding:12px 16px">
   <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><div style="font:700 19px Inter">Sandbox OU</div><span class="tag o">SCP · Sandbox-Guardrails</span></div>
   <div style="font:400 15.5px Inter;color:var(--ink3);margin-top:4px">no expensive services · small instance sizes only</div>
   <div style="position:absolute;left:16px;right:16px;top:84px;bottom:16px;border-radius:8px;border:2px solid var(--teal-d);background:#fff;padding:14px 18px">
    <div style="display:flex;align-items:center;gap:10px"><span style="font-size:22px;color:var(--teal-d)">${icon('server')}</span><div style="font:700 20px Inter">Team 8 project account</div></div>
    <div style="display:flex;gap:14px;margin-top:14px">
     <div style="flex:1;border-radius:8px;background:#EFE9DF;padding:12px 14px"><div style="font:700 17px Inter">dev environment</div><div style="font:400 15.5px Inter;color:var(--ink3)">always-on EC2 host</div></div>
     <div style="flex:1;border-radius:8px;background:#E3E8FB;padding:12px 14px"><div style="font:700 17px Inter;color:var(--teal-d)">prod environment</div><div style="font:400 15.5px Inter;color:var(--ink3)">ECS, rebuilt per release</div></div>
    </div>
    <div style="margin-top:16px;border-radius:8px;background:var(--inverse);color:#fff;padding:12px 16px;font:400 16.5px/1.35 Inter"><b style="color:#FFB01F">Guardrails above the account:</b> they bind even its admins and root user</div>
   </div></div></div>
</div>`;

// ---------- slide 23 ----------
const dt = (n, bg, t, q) => `<div class="card" style="height:206px;padding:24px 24px;display:flex;flex-direction:column;justify-content:space-between">
 <div style="display:flex;gap:20px;align-items:center"><div class="dot" style="width:64px;height:64px;background:${F(bg).bg};color:${F(bg).fg};font:700 28px Inter">${n}</div><div style="font:700 23px/1.25 Inter;flex:1">${t}</div></div>
 <div class="pill neutral" style="height:46px;font-size:19px">${q}</div></div>`;
const s23 = () => `
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:6px">
 ${dt(1, 'var(--teal-d)', 'Buy as a customer, across three services', 'Functionality')}
 ${dt(2, 'var(--orange)', 'Auto-scaling, on the dashboard', 'Scalability, performance')}
 ${dt(3, 'var(--orange)', 'The pipeline that deployed it', 'Maintainability')}
</div>
<div class="card" style="margin-top:20px;padding:20px 24px;display:flex;align-items:center;gap:20px">
 <div class="dot" style="width:64px;height:64px;background:var(--default);color:var(--foreground);font-size:28px;flex:none">${icon('flask')}</div>
 <div><div style="font:700 23px/1.25 Inter">Failure handling: not shown live</div><div style="font:400 19px/1.35 Inter;color:var(--ink3);margin-top:4px">Identity outage · dead-letter queue: tested locally · stop a server: not run · results in B12</div></div></div>
<div style="margin-top:20px;border-radius:8px;background:#FBDFD9;padding:12px 22px;font:700 19px Inter;display:flex;align-items:center;gap:12px;flex-wrap:wrap"><span style="color:var(--red);font-size:14px">●</span> Live on AWS · the scaling graphs are from our 19 October load test</div>`;

// ---------- slide 24 ----------
const s24 = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:304px;font:700 16px Inter;letter-spacing:.14em;color:#FFB01F;text-transform:uppercase">What we hope you remember</div>
<div style="position:absolute;left:80px;top:338px;width:980px;display:grid;grid-template-columns:1fr 1fr;gap:14px 20px">
 ${['Split where no transaction crosses', 'Events never lost or doubled', 'Fails safe when identity is down', 'Rebuilt from scratch every release'].map(t => `<div style="height:52px;border-radius:26px;background:#fff;color:var(--teal-d);font:700 19px/52px Inter;text-align:center">${t}</div>`).join('')}
</div>
<div style="position:absolute;left:80px;top:486px;width:980px;display:flex;gap:20px">
 ${[['1 → 3 tasks', 'auto-scaled at ≈ 455 req/s, 19 Oct'], ['US$0.49', 'runtime database, cache and containers, 7 sessions'], ['51 min', 'approved PR → released, tested, destroyed']].map(n => `<div style="flex:1;border-radius:8px;border:1.5px solid rgba(255,255,255,.35);background:rgba(255,255,255,.08);padding:10px 18px"><div style="font:700 30px/1.2 Inter;color:#fff">${n[0]}</div><div style="font:400 15.5px Inter;color:#E6DFD3">${n[1]}</div></div>`).join('')}
</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8 · d3lvq02o2e7tcn.cloudfront.net</div>`;

module.exports = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 13: s13, 14: s14, 15: s15, 16: s16, 17: s17, 18: s18, 19: s19, 20: s20, 21: s21, 22: s22, 23: s23, 24: s24 };
