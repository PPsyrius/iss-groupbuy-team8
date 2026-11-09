const { icon, md, lockup, svg } = require('../../../presentation-kit/lib');
const { site, fill } = require('./site-geometry');
// Accent fills never carry text (design-screens.md §2.1): accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
const F = bg => bg === 'var(--orange)' ? { bg: 'var(--orange-l)', fg: 'var(--orange-d)', sub: 'var(--orange-d)' } : bg === '#6B655C' ? { bg: 'var(--default)', fg: 'var(--foreground)', sub: 'var(--muted)' } : { bg, fg: '#fff', sub: 'rgba(255,255,255,.85)' };

// ---------- slide 1 ----------
// The kit lockup with the v3 entrances on its parts: bag pop 200, wordmark up 200, tagline up 320.
const sub1 = (s, from, to) => { if (!s.includes(from)) throw new Error(`lockup markup changed: ${from}`); return s.replace(from, to); };
const lockupA = () => [
  ['<svg width="102" height="128" viewBox="34 20 132 164" style="display:block">', '<svg class="a" data-a="pop" width="102" height="128" viewBox="34 20 132 164" style="display:block;--d:200ms">'],
  ['<div style="font:900 104px', '<div class="a" data-a="up" style="--d:200ms;font:900 104px'],
  ['<div style="font:600 17px', '<div class="a" data-a="up" style="--d:320ms;font:600 17px'],
].reduce((s, [f, t]) => sub1(s, f, t), lockup());
const s1 = () => `
<div class="a" data-a="up" style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F;--d:213ms">SWE5001 PRACTICE PROJECT · TEAM 8</div>
${lockupA()}
<div class="a" data-a="up" style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff;--d:420ms">A scalable community group-buying platform</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:368px;font:italic 400 26px Inter;color:#E6DFD3;display:flex;gap:16px;align-items:center;--d:520ms">Three services <span style="color:#E8402A">•</span> event-driven <span style="color:#E8402A">•</span> rebuilt on every release</div>
<div class="a" data-a="fade" style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff;--d:650ms">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div class="a" data-a="fade" style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1;--d:700ms">Graduate Certificate in Architecting Scalable Systems · 21 October 2026</div>
<div style="position:absolute;left:850px;top:190px;width:400px;height:340px">
  ${[0, 1, 2, 3, 4].map(i => {
    const h = 260 - i * 42, x = i * 72, y = 340 - h, last = i === 4;
    return `<div class="a" data-a="rise" style="position:absolute;left:${x + 36}px;top:${y}px;width:56px;height:${h}px;border-radius:6px 6px 2px 2px;background:${last ? 'var(--accent)' : 'var(--teal)'};--d:${500 + i * 160}ms"></div>
    <div class="a" data-a="drop" style="position:absolute;left:${x + 40}px;top:${y - 62}px;width:48px;height:48px;border-radius:50%;background:#FFB01F;display:flex;align-items:center;justify-content:center;color:#1B1A18;--d:${800 + i * 160}ms">${icon('user', 'ic')}</div>`;
  }).join('')}
</div>
<div class="a" data-a="up" style="position:absolute;left:880px;top:544px;font:italic 400 19px Inter;color:#E6DFD3;--d:1500ms">The price drops as each member joins</div>`;

// ---------- slide 2 ----------
// m = [pop, up] delays (optional): the v3 entrances on the icon and the text.
const row = (ic, txt, bg, col = 'var(--ink)', m) => `<div style="display:flex;align-items:center;gap:28px;margin-bottom:30px">
  <div class="dot${m ? ' a' : ''}"${m ? ' data-a="pop"' : ''} style="width:80px;height:80px;background:${bg};font-size:40px${m ? `;--d:${m[0]}ms` : ''}">${icon(ic)}</div>
  <div${m ? ' class="a" data-a="up"' : ''} style="font:400 25px/1.3 Inter;color:${col}${m ? `;--d:${m[1]}ms` : ''}">${txt}</div></div>`;
// Slides 2, 3, 6 and 7: the kit's CSS arrows redrawn as v3 connectors (class cn, pathLength 1) at the same pixels.
// The overlay SVG uses .body pixels; ahM is a CSS-border arrowhead (len along the path, h across) as a marker
// whose base sits on the path end; caps and joins stay square like the divs they replace. Head sizes are the
// rendered ones: Chrome draws a 7.5 or 8.5 px border triangle side as 7 or 8 px.
const ahM = (id, c, len, h, refY = h / 2) => `<marker id="${id}" markerUnits="userSpaceOnUse" viewBox="0 0 ${len} ${h}" markerWidth="${len}" markerHeight="${h}" refX="0" refY="${refY}" orient="auto"><path d="M0,0 L${len},${h / 2} L0,${h} z" style="fill:${c}"/></marker>`;
const cnP = (d, c, w, ms, head) => `<path class="cn" pathLength="1" d="${d}" style="fill:none;stroke:${c};stroke-width:${w}px;stroke-linecap:butt;stroke-linejoin:miter;--d:${ms}ms"${head ? ` marker-end="url(#${head})"` : ''}/>`;
const cnSvg = (inner, w = 1164, h = 560) => `<svg width="${w}" height="${h}" style="position:absolute;left:0;top:0;overflow:visible;pointer-events:none">${inner}</svg>`;
const s2 = () => `
<div class="card a" data-a="left" style="position:absolute;left:0;top:50px;width:560px;height:476px;padding:32px 34px;--d:289ms">
  <div class="serif a" data-a="up" style="font-size:30px;line-height:1.2;color:var(--muted);margin-bottom:22px;--d:318ms">Today: chat groups and spreadsheets</div>
  ${row('users', "Buyers can't see deadlines or progress", '#6B655C', undefined, [408, 424])}
  ${row('clipboard-list', 'Organisers collect money and refund by hand', '#6B655C', undefined, [505, 521])}
  ${row('store', 'Sellers have no place for team prices', '#6B655C', undefined, [602, 618])}
</div>
${cnSvg(`<defs>${ahM('s2ah', 'var(--orange)', 12, 14, 7.5)}</defs>${cnP('M574,289.5 L628,289.5', 'var(--orange)', 3, 700, 's2ah')}`)}
<div class="card a" data-a="right" style="position:absolute;left:654px;top:50px;width:510px;height:476px;padding:32px 34px;background:var(--inverse);border:none;--d:600ms">
  <div class="serif a" data-a="up" style="font-size:30px;color:#E8402A;margin-bottom:34px;--d:650ms">With BlocBuy</div>
  ${row('credit-card', 'Pay to join a team', 'var(--orange)', '#fff', [760, 800])}
  ${row('tags', 'More members, lower price', 'var(--orange)', '#fff', [880, 920])}
  ${row('arrows-rotate', 'Team fills, or everyone is refunded automatically', 'var(--orange)', '#fff', [1000, 1040])}
</div>`;

// ---------- slide 3 ----------
const s3 = () => `
<div class="a" data-a="fade" style="position:absolute;left:0;top:30px;width:360px;height:84px;border-radius:8px;background:#FFEDC7;padding:16px 22px;font:400 22px/1.3 Inter;--d:269ms"><div class="a" data-a="up" style="--d:272ms"><b>Seed</b>&nbsp; a few curated launch offers</div></div>
<div class="a" data-a="fade" style="position:absolute;right:0;top:30px;width:360px;height:84px;border-radius:8px;background:#FBDFD9;padding:16px 22px;font:400 22px/1.3 Inter;--d:419ms"><div class="a" data-a="up" style="--d:423ms"><b>Network effect</b>&nbsp; each new member lowers the price</div></div>
<div class="card a" data-a="left" style="position:absolute;left:0;top:130px;width:340px;height:230px;padding:18px 24px;--d:359ms">
  <div class="serif a" data-a="up" style="font-size:29px;color:var(--teal-d);margin-bottom:12px;--d:373ms">Producers</div>
  <ul class="b a" data-a="up" style="font:400 22px/1.32 Inter;--d:424ms"><li>Sellers and admins publish offers</li><li>Buyers who start or share a team</li></ul></div>
<div class="card a" data-a="right" style="position:absolute;right:0;top:130px;width:340px;height:230px;padding:18px 24px;--d:513ms">
  <div class="serif a" data-a="up" style="font-size:29px;color:var(--teal-d);margin-bottom:12px;--d:528ms">Consumers</div>
  <ul class="b a" data-a="up" style="font:400 22px/1.3 Inter;margin-bottom:0;--d:579ms"><li style="margin-bottom:8px">Browse offers</li><li style="margin-bottom:8px">Join a team</li><li>Track orders</li></ul></div>
<div class="a" data-a="pop" style="position:absolute;left:394px;top:64px;width:376px;height:312px;border-radius:50%;border:2px solid #DDD5C8;padding:8px;--d:380ms">
  <div class="a pulse" data-a="pop" style="width:100%;height:100%;border-radius:50%;background:var(--inverse);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;--d:420ms">
   <div class="a" data-a="up" style="font:700 18px Inter;letter-spacing:.12em;color:#FFB01F;margin-bottom:14px;--d:500ms">CORE INTERACTION</div>
   <div class="serif a" data-a="up" style="font-size:31px;line-height:1.2;color:#fff;--d:560ms">Form a team at a<br>team price</div></div></div>
${cnSvg(`<defs>${ahM('s3ah', 'var(--teal-d)', 11, 14, 7.5)}${ahM('s3up', 'var(--orange)', 13, 16, 9.5)}</defs>${cnP('M342,245.5 L380,245.5', 'var(--teal-d)', 3, 745, 's3ah')}${cnP('M774,245.5 L808,245.5', 'var(--teal-d)', 3, 826, 's3ah')}${cnP('M992.5,370 L992.5,394.5 L171.5,394.5 L171.5,375', 'var(--orange)', 3, 900, 's3up')}`)}
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:400px;text-align:center;font:italic 400 20px Inter;color:var(--orange-d);--d:1000ms">Consumers become producers when they start or share a team</div>
<div style="position:absolute;left:0;right:0;top:438px;display:flex;align-items:center;gap:22px">
  <div class="a" data-a="up" style="font:700 22px Inter;color:var(--teal-d);width:296px;--d:632ms">Shared, reusable services</div>
  ${['Identity', 'Notifications', 'Payments'].map((t, i) => `<div class="pill a" data-a="pop" style="height:54px;flex:1;font-size:22px;--d:${1000 + i * 90}ms">${t}</div>`).join('')}
</div>
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:506px;font:400 19px Inter;color:var(--ink3);--d:1300ms"><b style="color:var(--orange-d)">Future seeds:</b>&nbsp; event tickets · bulk subscriptions · neighbourhood produce boxes</div>`;

// ---------- slide 4 ----------
// v3 column entrances from base d: card up d, header band and its square bottom patch fade d, icon pop d+60,
// name up d+80, list up d+160. The patch repaints the band's own colour under the band's content (z-index -1).
const col4 = (ic, name, bg, items, w, d) => `<div class="card a" data-a="up" style="width:${w}px;height:420px;overflow:hidden;--d:${d}ms">
  <div class="a" data-a="fade" style="background:${F(bg).bg};height:96px;display:flex;align-items:center;gap:18px;padding:0 20px;color:${F(bg).fg};position:relative;z-index:0;--d:${d}ms"><div class="a" data-a="fade" style="position:absolute;left:0;right:0;bottom:0;height:19.33px;background:${F(bg).bg};z-index:-1;--d:${d}ms"></div>
    <div class="dot a" data-a="pop" style="width:66px;height:66px;background:${bg === 'var(--orange)' ? 'var(--accent)' : 'rgba(255,255,255,.18)'};font-size:34px;color:#fff;--d:${d + 60}ms">${icon(ic)}</div>
    <div class="a" data-a="up" style="font:700 27px/1.15 Inter;--d:${d + 80}ms">${name}</div></div>
  <ul class="b a" data-a="up" style="padding:24px 22px 0 26px;font:400 22px/1.32 Inter;--d:${d + 160}ms">${items.map(i => `<li style="margin-bottom:14px">${i}</li>`).join('')}</ul></div>`;
const s4 = () => `
<div style="display:flex;gap:26px;margin-top:20px">
${col4('user-check', 'Identity service', 'var(--teal)', ['Sign in with Google', 'Buyer and admin roles'], 350, 200)}
${col4('store', 'Commerce service', 'var(--orange)', ['Browse team offers', 'Start or join a team, and pay', 'Team succeeds, or expires with refunds', 'Share invites, order history', 'Admin listings'], 460, 320)}
${col4('bell', 'Notification service', 'var(--teal)', ['Team succeeded or expired', 'Payment received'], 350, 440)}
</div>
<div class="a" data-a="up" style="position:absolute;left:6px;right:0;top:462px;font:400 20px/1.35 Inter;color:var(--muted);--d:649ms"><b style="color:var(--muted)">Not in scope:</b> multi-region · real payments (Stripe test mode) · email or push · splitting payments into its own service (our documented next step)</div>`;

// ---------- slide 5 ----------
// d (optional): the v3 icon pop delay; the text rises 30 ms later.
const tick = (t, sub, kind = 'ok', d) => {
  const c = { ok: ['circle-check', 'var(--green)'], open: ['circle-exclamation', 'var(--orange)'], tbd: ['hourglass-half', '#6B655C'], not: ['circle-minus', '#6B655C'] }[kind];
  const a = (type, ms) => d === undefined ? ['', ''] : [` class="a" data-a="${type}"`, `;--d:${ms}ms`];
  const [pc, ps] = a('pop', d), [uc, us] = a('up', d + 30);
  return `<div style="display:flex;gap:12px;margin-bottom:14px"><div${pc} style="color:${c[1]};font-size:24px;flex:none;margin-top:-1px${ps}">${icon(c[0])}</div>
  <div${uc} style="font:400 18px/1.3 Inter${us}">${t}${sub ? `<div style="font:italic 400 15.5px/1.3 Inter;color:var(--muted);margin-top:2px">${sub}</div>` : ''}</div></div>`;
};
const s5 = () => `
<div style="display:flex;gap:20px;height:540px;margin-top:-6px">
 <div class="a" data-a="left" style="flex:1.12;background:#E3E8FB;border-radius:8px;padding:22px 22px;--d:241ms">
   <div class="serif a" data-a="up" style="font-size:25px;color:var(--green);margin-bottom:16px;--d:259ms">Verified on AWS · 17 Oct</div>
   ${tick('3 services built from nothing', 'tasks 2/2 · 1/1 · 1/1, all healthy', 'ok', 300)}
   ${tick('9/9 smoke checks through CloudFront', undefined, 'ok', 380)}
   ${tick('SNS → SQS routing confirmed', 'with test events', 'ok', 460)}
   ${tick('Teardown: 115 resources in 709 s', 'nothing left behind', 'ok', 540)}
   ${tick('One task: 200 req/s, 0 errors', 'p95 &lt; 60 ms (16 Oct)', 'ok', 620)}
 </div>
 <div class="a" data-a="up" style="flex:1;background:#EFE9DF;border:2px dashed #DDD5C8;border-radius:8px;padding:20px 22px;--d:200ms">
   <div class="serif a" data-a="up" style="font-size:25px;color:var(--muted);margin-bottom:16px;--d:250ms">Tested on 19 Oct</div>
   ${tick('Auto-scaling under load', 'commerce 1 → 3 tasks on its own; ≈ 455 req/s, server p95 &lt; 50 ms', 'ok', 400)}
   ${tick('Queue scaling', 'not run on AWS · local test passed', 'not', 480)}
   ${tick('Self-healing: stop a task', 'not run on AWS', 'not', 560)}
   ${tick('Bad message to dead-letter queue', 'not run on AWS · local test passed', 'not', 640)}
   ${tick('Identity outage drill', 'not run on AWS · local test passed', 'not', 720)}
   ${tick('Buyer journey end to end', 'not run on AWS', 'not', 800)}
 </div>
 <div class="a" data-a="right" style="flex:1.05;background:#FDEFEC;border-radius:8px;padding:22px 22px;--d:300ms">
   <div class="serif a" data-a="up" style="font-size:25px;color:var(--orange-d);margin-bottom:16px;--d:350ms">Still open</div>
   ${tick('An interrupted start-up can leave auto-scaling paused', 'repaired by hand; no fix yet', 'open', 500)}
   ${tick('Database and cache in one zone', 'a cost choice; multi-zone for production', 'open', 590)}
   ${tick('Alarm e-mails to confirm before the final release', null, 'open', 680)}
 </div>
</div>`;

// ---------- slide 6 ----------
const ms = [['4 Oct', 'Proposal submitted'], ['6 Oct', 'First promotion to AWS (one service)'], ['14 Oct', 'Split into three services; outbox, events'], ['16 Oct', 'Load baseline: 200 req/s per task'], ['17 Oct', 'First green three-service release'], ['19 Oct', 'Auto-scaling on AWS: 1 → 3 tasks']];
const eff = [['Liu Jin', 12.5, 'AWS stacks, pipeline, service split', true], ['Panpakorn (Pan)', 11.5, 'Messaging, scaling, login, load tests'], ['Yu Qianshuo (Yorik)', 9, 'Event outbox and relay, failure drills'], ['Lim Jiechao', 8.5, 'Lead; AWS accounts and guardrails; architecture study and proof of concept; presentation'], ['Wang Ziang (Eric)', 8.5, 'Notification consumer, dead-letter redrive, demo lead']];
const s6 = () => `
${cnSvg(cnP('M10,53.5 L1154,53.5', 'var(--teal)', 3, 150))}
${ms.map((m, i) => {
  const x = 52 + i * 202, last = i === 5, d = 300 + i * 110;
  return `<div class="a" data-a="up" style="position:absolute;left:${x - 90}px;width:180px;top:0;text-align:center;font:700 21px Inter;color:var(--teal-d);--d:${d}ms">${m[0]}</div>
  <div class="a" data-a="pop" style="position:absolute;left:${x - 14}px;top:40px;width:28px;height:28px;border-radius:50%;border:2px solid var(--teal);background:#fff;padding:4px;--d:${d}ms"><div style="width:100%;height:100%;border-radius:50%;background:${last ? 'var(--orange)' : 'var(--teal-d)'}"></div></div>
  <div class="a" data-a="up" style="position:absolute;left:${x - 96}px;width:192px;top:80px;text-align:center;font:400 17px/1.25 Inter;--d:${d + 40}ms">${m[1]}</div>`;
}).join('')}
<div style="position:absolute;left:0;top:178px;display:flex;align-items:center;gap:14px"><div class="serif a" data-a="up" style="font-size:23px;color:var(--teal-d);--d:393ms">Effort by member (person-days)</div></div>
${eff.map((e, i) => { const d = 700 + i * 110; return `<div style="position:absolute;left:0;top:${222 + i * 54}px;width:1164px;height:44px;display:flex;align-items:center">
  <div class="a" data-a="up" style="width:236px;font:700 19px Inter;--d:${d}ms">${e[0]}</div>
  <div style="width:404px;display:flex;align-items:center;gap:10px"><div class="a" data-a="grow" style="height:30px;border-radius:5px;width:${e[1] * 26}px;background:${e[3] ? 'var(--orange)' : 'var(--teal)'};--d:${d + 60}ms"></div><div class="a" data-a="up" style="font:700 19px Inter;color:var(--teal-d);--d:${d + 200}ms">${e[1]}</div></div>
  <div class="a" data-a="up" style="flex:1;font:400 16.5px/1.22 Inter;color:var(--ink3);--d:${d + 100}ms">${e[2]}</div></div>`; }).join('')}
<div class="a" data-a="up" style="position:absolute;left:0;top:500px;font:italic 400 16.5px Inter;color:var(--muted);--d:688ms">Total ≈ 50 person-days, estimated from the issues each person delivered</div>`;

// ---------- slide 7 ----------
const op = (t) => `<div style="height:60px;border-radius:30px;background:var(--surface);border:2px solid var(--accent);color:var(--orange-d);font:700 21px/60px Inter;text-align:center;padding:0 20px">${t}</div>`;
const arr = (c = 'var(--orange)') => `<div style="display:flex;align-items:center"><div style="width:22px;height:3px;background:${c}"></div><div style="border-left:9px solid ${c};border-top:6px solid transparent;border-bottom:6px solid transparent"></div></div>`;
// opA: op() with the v3 pop; arrA: arr() as a v3 connector (same 31 x 12 box: 22 px line, 9 x 12 head).
// The line sits at y 5-8 because Chrome snaps arr()'s centred 3 px div (top 4.5) to whole pixels.
const opA = (t, d) => op(t).replace('<div style="', `<div class="a" data-a="pop" style="--d:${d}ms;`);
const arrA = (d, c = 'var(--orange)') => `<svg width="31" height="12" style="display:block;overflow:visible"><defs>${ahM(`s7ah${d}`, c, 9, 12)}</defs>${cnP('M0,6.5 L22,6.5', c, 3, d, `s7ah${d}`)}</svg>`;
const s7 = () => `
<div class="a" data-a="fade" style="position:absolute;left:0;top:56px;width:726px;height:304px;border-radius:8px;background:#FBDFD9;padding:24px 34px;--d:282ms">
  <div class="serif a" data-a="up" style="font-size:29px;color:var(--orange-d);margin-bottom:22px;--d:305ms">One payment = one database transaction</div>
  <div style="display:flex;align-items:center;gap:2px">${opA('Payment', 471)}${arrA(500)}${opA('Order', 592)}${arrA(590)}${opA('Stock', 708)}${arrA(680)}${opA('Team', 824)}</div>
  <div style="display:flex;align-items:center;justify-content:space-between;margin-top:30px">
   <div class="a" data-a="up" style="font:400 22px/1.32 Inter;width:420px;--d:445ms">Splitting this needs sagas, so it stays together in</div>
   <div class="pill a" data-a="pop" style="height:64px;background:var(--accent);font-size:22px;white-space:nowrap;color:var(--foreground);background:var(--surface);border:2px solid var(--accent);--d:529ms">Commerce service</div></div>
</div>
<div class="card a" data-a="up" style="position:absolute;left:0;top:380px;width:350px;height:170px;padding:22px 24px;--d:569ms"><div class="a" data-a="up" style="font:400 21px/1.3 Inter;margin-bottom:16px;--d:577ms">Identity: used through one small interface</div><div class="pill a" data-a="pop" style="height:54px;width:100%;font-size:20px;--d:658ms">Identity service</div></div>
<div class="card a" data-a="up" style="position:absolute;left:376px;top:380px;width:350px;height:170px;padding:22px 24px;--d:639ms"><div class="a" data-a="up" style="font:400 21px/1.3 Inter;margin-bottom:16px;--d:648ms">Notifications: nothing calls them</div><div class="pill a" data-a="pop" style="height:54px;width:100%;font-size:20px;--d:729ms">Notification service</div></div>
<div class="card a" data-a="right" style="position:absolute;left:756px;top:56px;width:408px;height:254px;padding:30px 34px;background:var(--inverse);border:none;display:flex;flex-direction:column;justify-content:center;--d:424ms">
  <div class="a" data-a="up" style="--d:438ms"><div class="serif" style="font-size:23px;color:#E8402A;margin-bottom:6px">Our rule</div>
  <div class="serif" style="font-size:38px;line-height:1.18;color:#fff">Split only where no transaction crosses</div></div></div>
<div class="a" data-a="right" style="position:absolute;left:756px;top:330px;width:408px;height:220px;border-radius:8px;background:#EFE9DF;padding:26px 26px 26px 32px;display:flex;gap:18px;--d:679ms">
  <div class="a" data-a="fade" style="position:absolute;left:0;top:0;width:100%;height:100%;border-radius:8px;border-left:6px solid #8C2414;--d:679ms"></div>
  <div class="dot a" data-a="pop" style="width:42px;height:42px;background:var(--red);font-size:36px;--d:706ms">${icon('xmark')}</div>
  <div class="a" data-a="up" style="font:400 22px/1.32 Inter;--d:710ms"><b>Rejected: five services</b><br>Sagas everywhere, about 50–55 person-days: our whole budget</div></div>
<div class="ref a" data-a="pop" style="right:0;bottom:auto;top:16px;--d:455ms">AD-02</div>`;

// ---------- slide 8 ----------
// v3 card entrances from base d: card up d, icon pop d+33, title up d+48, text up d+125, tag pop d+110.
const dc = (ic, bg, t, s, ad, d) => `<div class="card a" data-a="up" style="height:198px;padding:30px 28px;display:flex;gap:28px;--d:${d}ms">
  <div class="dot a" data-a="pop" style="width:88px;height:88px;background:${bg};font-size:42px;--d:${d + 33}ms">${icon(ic)}</div>
  <div style="flex:1"><div class="serif a" data-a="up" style="font-size:27px;line-height:1.15;margin:4px 0 14px;padding-right:56px;--d:${d + 48}ms">${t}</div><div class="a" data-a="up" style="font:400 21px/1.32 Inter;color:var(--ink3);--d:${d + 125}ms">${s}</div></div>
  <span class="tag a" data-a="pop" style="position:absolute;right:18px;top:18px;--d:${d + 110}ms">${ad}</span></div>`;
const s8 = () => `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:6px">
 ${dc('code-branch', 'var(--orange)', 'Split by transaction boundary', 'No distributed transactions to build or debug', 'AD-02', 250)}
 ${dc('paper-plane', 'var(--teal)', 'Events through an outbox and queues', 'An event is sent only if the change really committed', 'AD-04', 361)}
 ${dc('id-badge', 'var(--teal)', 'One shared login session', 'Any service checks roles without calling identity', 'AD-05', 443)}
 ${dc('shield-halved', 'var(--teal)', 'Fail safe when identity is down', 'Purchases are refused, never half-done', 'AD-06', 554)}
</div>
<div class="a" data-a="up" style="margin-top:22px;border-radius:8px;background:#FDEFEC;padding:16px 24px;font:400 20px/1.3 Inter;display:flex;gap:14px;align-items:center;--d:900ms">
 <span class="a" data-a="pop" style="color:var(--orange-d);font-size:24px;--d:950ms">${icon('scale-balanced')}</span><span class="a" data-a="up" style="--d:980ms"><b style="color:var(--orange-d)">Cost of these choices:</b> notifications lag about 2 s; purchases pause while identity is down</span></div>`;

// ---------- slide 9 ----------
const s9 = () => `<div class="a" data-a="fade" style="${site(90, 184, 1745, 750)};--d:200ms">${fill(svg('logical-view'), '-0.17 2.67 1163.51 500')}</div>
<div class="cap a" data-a="up" style="${site(88, 953, 1745, 55)};font:400 22px/1.2 Inter;--d:900ms"><b style="color:var(--red)">One direct call</b> (commerce → identity); everything else by events.</div>`;

// ---------- slide 10 ----------
// Card box fades in (d[0]), its text rises a beat later (d[1]), as in v3.
const ctx = (n, s, c, d) => `<div class="a" data-a="fade" style="background:#fff;border:2px solid ${c};border-radius:8px;padding:12px 18px;height:100%;--d:${d[0]}ms"><div class="a" data-a="up" style="--d:${d[1]}ms"><div style="font:700 25px Inter;color:${c === 'var(--orange)' ? 'var(--orange-d)' : c}">${n}</div><div style="font:400 20px Inter;margin-top:2px">${s}</div></div></div>`;
// arr() drawn as an SVG connector (same 22 px shaft and 9 x 12 head) so it can draw on like a v3 .cn path;
// the shaft sits on y 5..8 because the flex layout of arr() rounds its 4.5 px offset down to whole pixels.
const arrCn = (c, d, id) => `<svg width="31" height="12" style="display:block;overflow:visible"><defs><marker id="${id}" markerUnits="userSpaceOnUse" markerWidth="9" markerHeight="12" refX="0" refY="6.5" orient="auto" viewBox="0 0 9 12"><path d="M0,0 L9,6 L0,12 Z" style="fill:${c}"/></marker></defs><path class="cn" pathLength="1" d="M0,6.5 L22,6.5" marker-end="url(#${id})" style="stroke:${c};stroke-width:3px;stroke-linecap:butt;--d:${d}ms"/></svg>`;
const ev = (a, b, d, id) => `<div style="display:flex;align-items:center;gap:6px;margin-bottom:18px"><div class="a" data-a="pop" style="width:196px;height:54px;border-radius:27px;background:var(--default);color:var(--foreground);font:600 15.5px/54px Inter;text-align:center;--d:${d[0]}ms">${a}</div>${arrCn('var(--orange)', d[1], id)}<div class="a" data-a="pop" style="width:196px;height:54px;border-radius:27px;background:var(--orange-l);color:var(--orange-d);font:600 15.5px/54px Inter;text-align:center;--d:${d[2]}ms">${b}</div></div>`;
const s10 = () => `
<div class="a" data-a="fade" style="position:absolute;left:0;top:20px;width:730px;height:296px;background:#FBDFD9;border-radius:8px;padding:14px 22px;--d:254ms">
  <div class="a" data-a="up" style="font:700 25px Inter;color:var(--orange-d);margin-bottom:10px;--d:263ms">Commerce service</div>
  <div style="display:grid;grid-template-columns:1fr 1fr;grid-template-rows:106px 106px;gap:14px">
   ${ctx('Catalog', 'products, team prices', 'var(--orange)', [307, 310])}${ctx('Group buying', 'teams, members', 'var(--orange)', [373, 375])}${ctx('Orders', 'orders', 'var(--orange)', [413, 416])}${ctx('Payments', 'payments, refunds', 'var(--orange)', [479, 481])}</div></div>
<div class="a" data-a="fade" style="position:absolute;left:0;top:336px;width:356px;height:190px;background:#E3E8FB;border-radius:8px;padding:14px 22px;--d:535ms">
  <div class="a" data-a="up" style="font:700 23px Inter;color:var(--teal-d);margin-bottom:10px;--d:544ms">Identity service</div><div style="height:110px">${ctx('Identity', 'users, roles', 'var(--teal-d)', [586, 588])}</div></div>
<div class="a" data-a="fade" style="position:absolute;left:374px;top:336px;width:356px;height:190px;background:#E3E8FB;border-radius:8px;padding:14px 22px;--d:605ms">
  <div class="a" data-a="up" style="font:700 23px Inter;color:var(--teal-d);margin-bottom:10px;--d:614ms">Notification service</div><div style="height:110px">${ctx('Notification', 'notifications', 'var(--teal-d)', [656, 659])}</div></div>
<div style="position:absolute;left:750px;top:22px;width:414px">
  <div class="serif a" data-a="up" style="font-size:22.5px;color:var(--teal-d);margin-bottom:22px;--d:399ms">Domain event → integration event</div>
  ${ev('TeamSucceeded', 'TeamSucceededV1', [450, 728, 690], 's10-ah1')}${ev('TeamExpired', 'TeamExpiredV1', [514, 791, 754], 's10-ah2')}${ev('PaymentSucceeded', 'PaymentSucceededV1', [578, 855, 817], 's10-ah3')}
  <div class="card a" data-a="up" style="padding:20px 22px;margin-top:4px;--d:646ms"><ul class="b a" data-a="up" style="font:400 21px/1.3 Inter;--d:662ms"><li style="margin-bottom:12px">Inside a service: free to change</li><li style="margin-bottom:0">Between services: versioned, IDs only</li></ul></div>
</div>
`;

// ---------- slide 11 ----------
// Bar pops in (d[0]), its two text lines rise just after (d[1]), as in v3.
const plat = (t, s, bg, col, sc, d) => `<div class="a" data-a="pop" style="height:50px;border-radius:8px;background:${bg};padding:4px 18px;margin-bottom:6px;--d:${d[0]}ms"><div class="a" data-a="up" style="--d:${d[1]}ms"><div style="font:700 19px/1.15 Inter;color:${col}">${t}</div><div style="font:400 15.5px/1.2 Inter;color:${sc}">${s}</div></div></div>`;
const W85 = ['#fff', 'rgba(255,255,255,.85)'];
const s11 = () => `
<div class="card a" data-a="left" style="position:absolute;left:0;top:0;width:300px;height:218px;padding:14px 22px;--d:246ms">
  <div class="serif a" data-a="up" style="font-size:25px;color:var(--teal-d);margin-bottom:10px;--d:258ms">Producers</div>
  <ul class="b a" data-a="up" style="font:400 18.5px/1.3 Inter;--d:305ms"><li>Sellers and admins publish listings</li><li>Buyers start and share teams</li></ul></div>
<div style="position:absolute;left:328px;top:0;width:508px">
  ${plat('Identity', 'one account for both sides', 'var(--teal)', ...W85, [305, 309])}
  ${plat('Commerce', 'offers, teams, payments', 'var(--orange-l)', 'var(--orange-d)', 'var(--orange-d)', [356, 359])}
  ${plat('Events', 'any new consumer just subscribes', 'var(--yellow)', 'var(--warning-soft-foreground)', 'var(--warning-soft-foreground)', [405, 408])}
  ${plat('Notification', 'tells people what happened', 'var(--teal)', ...W85, [454, 457])}
</div>
<div class="card a" data-a="right" style="position:absolute;right:0;top:0;width:300px;height:218px;padding:14px 22px;--d:408ms">
  <div class="serif a" data-a="up" style="font-size:25px;color:var(--teal-d);margin-bottom:10px;--d:420ms">Consumers</div>
  <ul class="b a" data-a="up" style="font:400 18.5px/1.3 Inter;--d:467ms"><li>Browse and join</li><li>Track orders</li><li>Get notified</li></ul></div>
<div style="position:absolute;left:300px;top:76px">${arrCn('var(--teal-d)', 593, 's11-ah1')}</div>
<div style="position:absolute;left:836px;top:76px;transform:scaleX(-1)">${arrCn('var(--teal-d)', 698, 's11-ah2')}</div>
<div style="position:absolute;left:836px;top:188px">${arrCn('var(--teal-d)', 793, 's11-ah3')}</div>
<div style="position:absolute;left:0;top:232px;width:724px">
  <div class="a" data-a="up" style="display:flex;align-items:center;gap:12px;margin-bottom:8px;--d:449ms"><div class="serif" style="font-size:21px;color:var(--teal-d)">Measured reuse</div></div>
  <table class="t a" data-a="fade" style="font-size:16.5px;--d:485ms"><tr><th style="width:190px">Added to the platform</th><th>What it took</th><th style="width:150px">Commerce edits</th></tr>
   <tr class="trow" style="--d:608ms"><td><b>Audit queue</b></td><td>2 lines of Terraform (a name and a filter)</td><td style="font-weight:700;color:var(--green)">0</td></tr>
   <tr class="trow" style="--d:646ms"><td><b>A new service</b></td><td>1 entry in the services map (golden-path module)</td><td style="font-weight:700;color:var(--green)">0</td></tr></table>
</div>
<div class="card a" data-a="up" style="position:absolute;left:744px;top:270px;width:420px;height:164px;padding:16px 20px;--d:627ms">
  <div style="display:flex;gap:10px;align-items:center;margin-bottom:8px"><span class="a" data-a="pop" style="color:var(--teal);font-size:22px;--d:647ms">${icon('file-code')}</span><div class="serif a" data-a="up" style="font-size:21px;color:var(--teal-d);--d:648ms">One public contract</div></div>
  <div class="a" data-a="up" style="font:400 17px/1.4 Inter;--d:685ms"><span class="mono" style="font-size:15px;background:var(--teal-ll);padding:1px 5px;border-radius:4px">contracts/openapi.json</span> → generated TypeScript client<br>Events versioned (V1), IDs only</div></div>
<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:456px;height:86px;border-radius:8px;background:var(--inverse);display:flex;align-items:center;padding:0 22px;gap:14px;--d:645ms">
  <div class="a" data-a="up" style="font:700 19px/1.15 Inter;color:#E8402A;width:168px;--d:649ms">Every new service starts with</div>
  ${[['Login &amp; roles', 1009], ['Error format', 1101], ['Tracing', 1193], ['Outbox', 1277], ['Duplicate protection', 1361]].map(([t, d]) => `<div class="a" data-a="pop" style="background:#fff;border-radius:999px;height:44px;padding:0 18px;font:700 17px/44px Inter;color:var(--teal-d);white-space:nowrap;--d:${d}ms">${t}</div>`).join('')}
</div>`;

// ---------- slide 12 ----------
// d = v3 delays [card up, icon pop, title up, text up, AD tag pop].
const pc = (ic, bg, t, s, ad, w, h, d) => `<div class="card a" data-a="up" style="width:${w}px;height:${h}px;padding:20px 22px;--d:${d[0]}ms">
  <div style="display:flex;align-items:center;gap:16px;margin-bottom:12px"><div class="dot a" data-a="pop" style="width:64px;height:64px;background:${bg};font-size:30px;--d:${d[1]}ms">${icon(ic)}</div><div class="a" data-a="up" style="font:700 22px/1.18 Inter;flex:1;--d:${d[2]}ms">${t}</div></div>
  <div class="a" data-a="up" style="font:400 18px/1.32 Inter;color:var(--ink3);--d:${d[3]}ms">${s}</div><span class="tag a" data-a="pop" style="position:absolute;right:14px;bottom:12px;--d:${d[4]}ms">${ad}</span></div>`;
const s12 = () => `
<div style="display:flex;gap:18px">
 ${pc('cubes', 'var(--teal)', 'Fargate containers on ARM', 'No servers to manage · ~20% cheaper · a Kubernetes control plane alone ≈ US$73 a month', 'AD-07', 376, 196, [244, 267, 278, 335, 447])}
 ${pc('recycle', 'var(--orange)', 'Disposable environment', 'Built per session or release, then destroyed', 'AD-12', 376, 196, [318, 341, 352, 409, 521])}
 ${pc('database', 'var(--teal)', 'One database server', 'A database per service: isolation at low cost; its own server is the scale-out path', 'AD-11', 376, 196, [392, 415, 426, 483, 595])}
</div>
<div style="display:flex;gap:18px;margin-top:18px">
 ${pc('sitemap', 'var(--teal)', 'Small NAT instance', '≈ US$4 instead of US$43 a month', 'AD-10', 573, 150, [437, 460, 471, 525, 630])}
 ${pc('key', 'var(--teal)', 'No stored cloud keys', 'Short-lived tokens · secrets in Parameter Store', 'AD-14', 573, 150, [548, 571, 582, 636, 741])}
</div>
<div class="a" data-a="up" style="margin-top:18px;border-radius:8px;background:var(--inverse);padding:16px 26px;display:flex;align-items:center;gap:22px;color:#fff;--d:800ms">
 <div class="a" data-a="up" style="font:700 15px Inter;letter-spacing:.1em;color:#FFB01F;width:120px;line-height:1.25;text-transform:uppercase;--d:850ms">Measured,<br>not estimated</div>
 <div class="a" data-a="up" style="font:700 50px/1 Inter;--d:900ms">US$0.49</div>
 <div class="a" data-a="up" style="font:400 19px/1.35 Inter;flex:1;--d:950ms">in total for the runtime's database, cache and containers across <b>7 up/down sessions</b>, 15–17 Oct<div style="font:400 14.5px Inter;color:#B5ADA1">AWS Cost Explorer, filtered to the runtime services</div></div></div>
<div class="a" data-a="up" style="margin-top:12px;font:400 18px Inter;color:var(--muted);--d:679ms"><b style="color:var(--muted)">Trade-off:</b> less redundancy (one NAT, single-zone database), acceptable for this project</div>`;

// ---------- slide 13 ----------
const s13 = () => `<div class="a" data-a="fade" style="${site(95, 172, 1740, 799)};--d:200ms">${fill(svg('physical-deployment'), '3.33 -1.33 1160 532.67')}</div>`;

// ---------- slide 14 ----------
const p95a = [57, 34, 26, 43, 28, 26, 23, 26, 35, 49], p95b = [53, 39, 27, 28, 23, 23, 42, 23, 23, 23], xs = [10, 20, 40, 60, 80, 100, 125, 150, 175, 200];
function chart() {
  const W = 560, H = 186, L = 40, B = 160, T = 10, R = 548;
  const X = i => L + 18 + i * ((R - L - 30) / 9), Y = v => B - (v / 80) * (B - T);
  // v3: the line draws on from d0, then its dots pop 60 ms apart from d0 + 100 ms (the path traces the old polyline).
  const line = (d, c, d0) => `<path class="cline" pathLength="1" style="--d:${d0}ms" d="${d.map((v, i) => `${i ? 'L' : 'M'}${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"/>` + d.map((v, i) => `<circle class="cdot" style="--d:${d0 + 100 + i * 60}ms" cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join('');
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${[0, 20, 40, 60, 80].map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 20}" font-size="14" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${line(p95a, '#A69E92', 500)}${line(p95b, '#3E5CD9', 800)}</svg>`;
}
// Panel slides in from the left (d[0]), its text rises a beat later (d[1]), as in v3.
const sig = (t, s, bg, c, d) => `<div class="a" data-a="left" style="border-radius:8px;background:${bg};padding:12px 18px;margin-bottom:10px;--d:${d[0]}ms"><div class="a" data-a="up" style="--d:${d[1]}ms"><div class="serif" style="font-size:22px;color:${c}">${t}</div><div style="font:400 17.5px/1.3 Inter;margin-top:2px">${s}</div></div></div>`;
const s14 = () => `
<div style="position:absolute;left:0;top:0;width:470px">
 ${sig('Commerce', 'Requests per task (target 12,000/min) → <b>2 to 6 tasks</b>', '#FBDFD9', 'var(--orange-d)', [244, 251])}
 ${sig('Notification', 'Queue backlog per task (target 50) → <b>1 to 4 tasks</b>', '#E3E8FB', 'var(--teal-d)', [335, 342])}
 ${sig('Identity', 'Fixed at <b>1 task</b>', '#EFE9DF', 'var(--ink2)', [426, 433])}
 <div class="card a" data-a="up" style="padding:14px 18px;margin-top:14px;border:2px solid var(--teal-d);--d:502ms">
  <div class="serif a" data-a="up" style="font-size:21px;color:var(--teal-d);margin-bottom:8px;--d:514ms">Capacity statement</div>
  <div class="a" data-a="up" style="font:400 16.5px/1.38 Inter;--d:553ms">
   <div><b>Measured:</b> 200 req/s on one task (16 Oct); ≈ 455 req/s on 3 tasks (19 Oct, three services)</div>
   <div><b>Projected:</b> 6 tasks × 200 ≈ <b>1,200 req/s</b> for browsing <span style="color:var(--orange-d);font-weight:700">(projection, not measured)</span></div>
   <div><b>Next limit:</b> the single database, then the single outbox relay (≈ 50 events/s, calculated)</div></div></div>
</div>
<div style="position:absolute;left:500px;top:0;width:664px">
 <div class="a" data-a="up" style="font:700 17px Inter;margin-bottom:2px;--d:335ms">Load test, one task, 16 Oct: response time (p95, ms)</div>
 <div class="a" data-a="fade" style="--d:300ms">${chart()}
 <div style="display:flex;gap:22px;font:400 14.5px Inter;color:var(--ink3);margin:-2px 0 10px 40px"><span><i style="display:inline-block;width:20px;height:3px;background:#A69E92;vertical-align:4px"></i> First run</span><span><i style="display:inline-block;width:20px;height:3px;background:var(--teal);vertical-align:4px"></i> Repeat run</span><span style="font-style:italic;color:var(--muted);margin-left:auto">requests per second →</span></div></div>
 <div style="display:flex;gap:12px">
  ${[['200', 'req/s', 'highest tested, not saturated', [609, 607, 655]], ['&lt; 60', 'ms', '95% of responses', [651, 649, 698]], ['0', 'errors', '57,600 requests', [693, 692, 740]]].map(s => `<div class="card a" data-a="up" style="flex:1;padding:8px 10px;text-align:center;--d:${s[3][0]}ms"><div class="a" data-a="up" style="--d:${s[3][1]}ms"><span class="serif" style="font-size:34px;color:var(--teal-d)">${s[0]}</span> <span style="font:700 16px Inter;color:var(--teal-d)">${s[1]}</span></div><div class="a" data-a="up" style="font:400 14.5px Inter;color:var(--muted);--d:${s[3][2]}ms">${s[2]}</div></div>`).join('')}
 </div>
 <div class="a" data-a="fade" style="margin-top:12px;border-radius:8px;background:#E3E8FB;padding:10px 16px;display:flex;align-items:center;gap:14px;--d:709ms">
  <span class="a" data-a="pop" style="color:var(--teal);font-size:26px;flex:none;--d:745ms">${icon('chart-line')}</span>
  <div class="a" data-a="up" style="font:400 16.5px/1.35 Inter;--d:722ms"><b>Three services, 19 Oct</b><br>commerce 1 → 3 tasks on its own; ≈ 455 req/s, server p95 &lt; 50 ms</div></div>
</div>`;

// ---------- slide 15 ----------
// d = v3 delays [cylinder up, name up, contents up].
const cyl = (t, s, c, bg, d) => `<div style="position:relative;width:370px;height:190px">
 <svg class="a" data-a="up" width="370" height="190" style="position:absolute;left:0;top:0;--d:${d[0]}ms"><path d="M2,30 A183,28 0 0 1 368,30 V160 A183,28 0 0 1 2,160 Z" fill="${bg}" stroke="${c}" stroke-width="2"/><path d="M2,30 A183,28 0 0 0 368,30" fill="none" stroke="${c}" stroke-width="2"/><ellipse cx="185" cy="30" rx="183" ry="28" fill="${bg}" stroke="${c}" stroke-width="2" fill-opacity=".6"/></svg>
 <div style="position:absolute;left:0;right:0;top:70px;text-align:center"><div class="serif a" data-a="up" style="font-size:28px;color:${c};--d:${d[1]}ms">${t}</div><div class="a" data-a="up" style="font:400 19px/1.3 Inter;margin-top:4px;--d:${d[2]}ms">${s}</div></div></div>`;
// d = v3 delays [card up, icon pop, text up]; a nested sub-line carries its own class="a".
const cal = (ic, t, w, bg, d) => `<div class="card a" data-a="up" style="width:${w}px;height:226px;padding:18px 20px;--d:${d[0]}ms"><div class="dot a" data-a="pop" style="width:60px;height:60px;background:${bg};font-size:28px;margin-bottom:14px;--d:${d[1]}ms">${icon(ic)}</div><div class="a" data-a="up" style="font:700 18.5px/1.3 Inter;--d:${d[2]}ms">${t}</div></div>`;
const s15 = () => `
<div style="display:flex;justify-content:space-between">${cyl('Identity DB', 'users, roles', 'var(--teal-d)', '#E3E8FB', [244, 297, 342])}${cyl('Commerce DB', 'products, teams, orders,<br>payments, outbox', 'var(--orange)', '#FBDFD9', [318, 371, 416])}${cyl('Notification DB', 'notifications,<br>processed events', 'var(--teal-d)', '#E3E8FB', [393, 446, 491])}</div>
<div style="display:flex;gap:16px;margin-top:20px">
 ${cal('lock', 'Each service reads only its own tables<div class="a" data-a="up" style="font:400 15.5px/1.3 Inter;color:var(--ink3);margin-top:6px;--d:400ms">per-service roles, enforced by grants and tested</div>', 238, 'var(--teal)', [300, 340, 380])}
 ${cal('bolt', 'The cache holds sessions only', 200, 'var(--teal)', [400, 440, 480])}
 ${cal('credit-card', 'No card data stored; events carry IDs only', 236, 'var(--teal)', [500, 540, 580])}
 ${cal('arrow-down-short-wide', 'Concurrency<div class="a" data-a="up" style="font:400 16px/1.35 Inter;color:var(--ink2);margin-top:6px;--d:700ms">Row locks in a fixed order (<b>team → intent → payment</b>) settle the last-seat race. Scheduled jobs hold a database lease, so one task runs them however many are up.</div>', 442, 'var(--orange)', [600, 640, 680])}
</div>`;

// ---------- slide 16 ----------
// The kit drawing has three fragments; v3 shows the expiry transaction from the start and reveals the rest in two clicks.
// So kit fragment 1 (expiry transaction) is always visible, fragment 2 (relay, SNS -> SQS, notification) comes at click 1
// and fragment 3 (refund worker) at click 2. The kit file stays unchanged; SWE5006 uses all three steps.
const s16 = () => `<div class="a" data-a="fade" style="${site(84, 170, 1753, 761)};--d:200ms">${fill(svg('expiry-refund-sequence'), '-4.33 -4.33 1168.67 507.67')
  .replace('<g class="frag" data-frag="1">', '<g>')
  .replace('<g class="frag" data-frag="2">', '<g class="frag" data-frag="1">')
  .replace('<g class="frag" data-frag="3">', '<g class="frag" data-frag="2">')}</div>`;

// ---------- slide 17 ----------
// d = v3 delays [card up, icon pop, "If…" + title up, outcome up] and [band up, check pop, text up].
const fc = (ic, bg, t, s, d) => `<div class="card a" data-a="up" style="flex:1;height:340px;padding:30px 30px;--d:${d[0]}ms">
 <div class="dot a" data-a="pop" style="width:88px;height:88px;background:${bg};font-size:40px;margin-bottom:22px;--d:${d[1]}ms">${icon(ic)}</div>
 <div class="a" data-a="up" style="--d:${d[2]}ms"><div class="serif" style="font-size:20px;color:var(--muted)">If…</div><div class="serif" style="font-size:26px;line-height:1.2;margin-bottom:18px">${t}</div></div>
 <div class="a" data-a="up" style="font:400 22px/1.32 Inter;color:var(--ink3);--d:${d[3]}ms">${s}</div></div>`;
const ok = (t, d) => `<div class="a" data-a="up" style="flex:1;height:92px;border-radius:8px;background:var(--inverse);display:flex;align-items:center;gap:22px;padding:0 30px;--d:${d[0]}ms"><div class="dot a" data-a="pop" style="width:44px;height:44px;background:var(--accent);color:#fff;font-size:40px;--d:${d[1]}ms">${icon('check')}</div><div class="a" data-a="up" style="font:700 27px/1.15 Inter;color:#fff;--d:${d[2]}ms">${t}</div></div>`;
const s17 = () => `
<div style="display:flex;gap:18px;margin-top:6px">
 ${fc('clone', 'var(--teal)', 'A message arrives twice', 'Ignored: its (event ID, consumer) is already recorded', [200, 260, 280, 320])}
 ${fc('biohazard', 'var(--orange)', 'A message keeps failing', 'Moved to a dead-letter queue after 5 tries; an alarm fires', [350, 410, 430, 470])}
 ${fc('rotate-right', 'var(--teal)', 'Publishing fails', 'The event stays in the outbox and is retried', [500, 560, 580, 620])}
</div>
<div style="display:flex;gap:18px;margin-top:22px">${ok('No lost events', [900, 960, 980])}${ok('No double notifications', [1050, 1110, 1130])}${ok('Bad messages isolated', [1200, 1260, 1280])}</div>`;

// ---------- slide 18 ----------
// Branch line (4px bar, x 88..728, centred on y) with a 15 x 16 arrowhead from x 724, drawn as one v3 connector.
const s18Line = (y, c, id, d) => `<svg style="position:absolute;left:0;top:0;overflow:visible" width="760" height="300"><defs><marker id="${id}" markerUnits="userSpaceOnUse" viewBox="0 0 15 16" markerWidth="15" markerHeight="16" refX="4" refY="8" orient="auto"><path d="M0,0 L15,8 L0,16 Z" style="fill:${c}"/></marker></defs><path class="cn" pathLength="1" d="M88,${y} H728" style="stroke:${c};stroke-width:4px;stroke-linecap:butt;--d:${d}ms" marker-end="url(#${id})"/></svg>`;
const s18 = () => `
<div style="position:absolute;left:0;top:0;width:760px;height:540px">
 <div class="a" data-a="up" style="position:absolute;left:0;top:6px;font:italic 400 20px Inter;color:var(--muted);--d:248ms">feature branches</div>
 ${[0, 1, 2, 3, 4, 5].map(i => `<svg style="position:absolute;left:${92 + i * 100}px;top:36px" width="44" height="58"><path class="cn" pathLength="1" d="M4,2 L36,50 M28,48 L37,52 L37,42" fill="none" stroke="#6B655C" stroke-width="2" style="stroke-linecap:butt;stroke-linejoin:miter;--d:${200 + i * 80}ms"/></svg>`).join('')}
 <div class="a" data-a="up" style="position:absolute;left:0;top:76px;font:700 28px Inter;color:var(--teal-d);--d:307ms">dev</div>
 <div class="a" data-a="up" style="position:absolute;left:0;top:118px;font:400 18px Inter;color:var(--teal-d);--d:348ms">main branch</div>
 ${s18Line(95, 'var(--teal-d)', 'ah18d', 120)}
 <div class="a" data-a="up" style="position:absolute;left:244px;top:110px;font:400 15px Inter;color:var(--ink3);background:#F6F2EC;padding:0 4px;z-index:2;--d:386ms">squash · 1 review + CODEOWNERS · required checks</div>
 ${[[200, 700, 800], [520, 950, 1050]].map(([x, dl, dt]) => `<svg style="position:absolute;left:${x}px;top:96px" width="40" height="176"><path class="cn" pathLength="1" d="M2,0 L30,168 M22,158 L31,172 L36,156" fill="none" stroke="#E8402A" stroke-width="3" style="stroke-linecap:butt;stroke-linejoin:miter;--d:${dl}ms"/></svg><div class="a" data-a="up" style="position:absolute;left:${x + 40}px;top:150px;font:700 19px/1.2 Inter;color:var(--orange-d);--d:${dt}ms">promotion<br>pull request</div>`).join('')}
 <div class="a" data-a="up" style="position:absolute;left:0;top:248px;font:700 28px Inter;color:var(--orange-d);--d:461ms">prod</div>
 <div class="a" data-a="up" style="position:absolute;left:0;top:290px;font:400 18px Inter;color:var(--orange-d);--d:501ms">release branch</div>
 ${s18Line(267, 'var(--orange)', 'ah18p', 300)}
 <div class="a" data-a="up" style="position:absolute;left:150px;top:284px;font:400 16px Inter;color:var(--ink3);--d:519ms">promotion PR only · no direct pushes · no bypass actors</div>
 <div class="pill neutral a" data-a="pop" style="position:absolute;left:0;top:362px;height:62px;width:350px;font-size:21px;--d:564ms">Review + automated checks</div>
 <div class="pill biz a" data-a="pop" style="position:absolute;left:372px;top:362px;height:62px;width:350px;font-size:21px;--d:634ms">No direct pushes to prod</div>
</div>
<div class="card a" data-a="right" style="position:absolute;right:0;top:0;width:410px;height:500px;padding:26px 28px;--d:387ms">
 <div class="serif a" data-a="up" style="font-size:29px;color:var(--teal-d);margin-bottom:26px;--d:410ms">Artefacts</div>
 ${[['cubes', 'Container images', 'one ARM64 image per service, stored by digest, not tag', 484, 490], ['key', 'Release record', 'which digests are live', 579, 585], ['file-code', 'API contract', 'one file in the repository', 674, 681]].map(a => `<div style="display:flex;gap:20px;align-items:center;margin-bottom:34px"><div class="dot a" data-a="pop" style="width:72px;height:72px;background:var(--teal);font-size:32px;--d:${a[3]}ms">${icon(a[0])}</div><div class="a" data-a="up" style="--d:${a[4]}ms"><div style="font:700 23px Inter">${a[1]}</div><div style="font:400 19px/1.3 Inter;color:var(--ink3)">${a[2]}</div></div></div>`).join('')}
</div>`;

// ---------- slide 19 ----------
const ck = (t, d) => `<div style="display:flex;gap:12px;margin-bottom:12px;font:400 19px/1.25 Inter"><span class="a" data-a="pop" style="color:var(--green);font-size:22px;flex:none;--d:${d}ms">${icon('check')}</span><span class="a" data-a="up" style="--d:${d + 10}ms">${t}</span></div>`;
// d: card entrance; dot +60, title +90, check rows from +160, 70 ms apart (v3 slide 19).
const gc = (ic, bg, t, items, d) => `<div class="card a" data-a="up" style="flex:1;height:350px;padding:22px 22px;--d:${d}ms"><div class="dot a" data-a="pop" style="width:76px;height:76px;background:${bg};font-size:34px;margin-bottom:16px;--d:${d + 60}ms">${icon(ic)}</div><div class="serif a" data-a="up" style="font-size:25px;margin-bottom:18px;--d:${d + 90}ms">${t}</div>${items.map((x, i) => ck(x, d + 160 + i * 70)).join('')}</div>`;
const s19 = () => `
<div style="display:flex;gap:16px">
 ${gc('gears', 'var(--teal)', 'Build &amp; test', ['Unit tests and API contract check', '≥ 80% coverage on new code (SonarCloud)'], 200)}
 ${gc('cubes', 'var(--teal)', 'Containers', ['Build and scan every service image', 'Boot the app and check its health'], 310)}
 ${gc('user-shield', 'var(--orange)', 'Security', ['Secret scanning (gitleaks)', 'Dependency and image scans (Trivy)', 'Static analysis (SonarCloud)'], 420)}
 ${gc('server', 'var(--teal)', 'Infrastructure', ['Terraform format, validate, test', 'Policy checks (checkov)', 'Plan posted to the PR'], 530)}
</div>
<div style="margin-top:16px;display:flex;gap:14px">
 ${[['5,252', 'workflow runs, 16 Sep – 16 Oct'], ['94% / 95%', 'runs green, backend / frontend'], ['8.6 / 3.1 min', 'median run, backend / frontend']].map((m, i) => `<div class="a" data-a="up" style="flex:1;border-radius:8px;background:var(--teal-ll);border:1px solid #DDD5C8;padding:10px 18px;--d:${800 + i * 100}ms"><div class="a" data-a="up" style="display:flex;align-items:baseline;gap:12px;--d:${850 + i * 100}ms"><span class="serif" style="font-size:30px;color:var(--teal-d);white-space:nowrap">${m[0]}</span><span style="font:400 16px/1.25 Inter;color:var(--ink3)">${m[1]}</span></div></div>`).join('')}
</div>
<div class="a" data-a="up" style="position:relative;margin-top:14px;border-radius:8px;background:#EFE9DF;padding:14px 26px 14px 32px;font:700 23px Inter;color:var(--red);--d:1100ms"><div class="a" data-a="fade" style="position:absolute;left:0;top:0;bottom:0;width:8px;box-sizing:border-box;border-left:6px solid #8C2414;border-radius:8px 0 0 8px;--d:1100ms"></div><div class="a" data-a="up" style="--d:1150ms">If any check fails, the pull request cannot merge</div></div>`;

// ---------- slide 20 ----------
// a: stage entrance (pop); title + subtitle +40, duration +80, first run +90 (v3 slide 20).
const st = (t, s, bg, d, d0, a) => `<div class="a" data-a="pop" style="flex:1;height:226px;border-radius:8px;background:${F(bg).bg};color:${F(bg).fg};padding:22px 14px 0;text-align:center;position:relative;--d:${a}ms">
 <div class="a" data-a="up" style="--d:${a + 40}ms"><div style="font:700 26px/1.12 Inter">${t}</div><div style="font:400 17px/1.25 Inter;color:${F(bg).sub};margin-top:6px">${s}</div></div>
 <div${d ? ` class="a" data-a="up"` : ''} style="position:absolute;left:0;right:0;top:136px;font:700 28px 'JetBrains Mono',monospace${d ? `;--d:${a + 80}ms` : ''}">${d}</div>
 ${d0 ? `<div class="a" data-a="up" style="position:absolute;left:0;right:0;top:182px;font:400 14.5px Inter;color:${F(bg).sub};--d:${a + 90}ms">first run 6 Oct: ${d0}</div>` : ''}</div>`;
// The caret is the v3 connector: its filled path carries class cn so it fades in at the connector's delay.
const chev = d => `<div style="width:20px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:16px">${icon('caret-right').replace('<path ', `<path class="cn" pathLength="1" style="fill:currentColor;--d:${d}ms" `)}</div>`;
const s20 = () => `
<div style="display:flex;align-items:stretch">
 ${st('Approve', 'promotion pull request', '#6B655C', '', '', 200)}${chev(320)}
 ${st('Build', 'container images', 'var(--teal)', '≈ 5 min', '2.5 min', 370)}${chev(490)}
 ${st('Create + deploy', 'environment and smoke tests', 'var(--teal)', '≈ 32 min', '13 min', 540)}${chev(660)}
 ${st('Release', 'mark images as live', 'var(--teal)', '≈ 1 min', '1 min', 710)}${chev(830)}
 ${st('Destroy', 'and check nothing is left', 'var(--orange)', '≈ 13 min', '10 min', 880)}
</div>
<div style="display:flex;gap:16px;margin-top:18px">
 <div class="a" data-a="up" style="flex:1.25;border-radius:8px;background:#E3E8FB;padding:18px 22px;--d:1100ms">
  <div class="a" data-a="up" style="display:flex;align-items:baseline;gap:12px;--d:1150ms"><span class="serif" style="font-size:44px;color:var(--teal-d);white-space:nowrap">51 min</span><span style="font:400 18px/1.25 Inter">end to end, three services, 17 Oct</span></div>
  <div class="a" data-a="up" style="font:400 16.5px/1.35 Inter;color:var(--ink3);margin-top:4px;--d:1200ms">28 min on 6 Oct with one service · 115 resources destroyed in 709 s</div></div>
 <div class="a" data-a="up" style="flex:1;border-radius:8px;background:#FBDFD9;padding:18px 22px;--d:1250ms"><div class="a" data-a="up" style="--d:1280ms"><div style="font:700 22px Inter">Destroy always runs</div><div style="font:400 17px/1.35 Inter;color:var(--ink3);margin-top:4px">even if an earlier step failed</div></div></div>
 <div class="a" data-a="up" style="flex:1;border-radius:8px;background:#E3E8FB;padding:18px 22px;--d:1350ms"><div class="a" data-a="up" style="--d:1380ms"><div style="font:700 22px Inter">Failed deploy rolls back</div><div style="font:400 17px/1.35 Inter;color:var(--ink3);margin-top:4px">the ECS circuit breaker restores the last good version</div></div></div>
</div>
<div style="margin-top:14px;display:flex;gap:16px;font:400 17.5px/1.3 Inter">
 <div style="flex:1;display:flex;gap:10px"><span class="a" data-a="pop" style="color:var(--teal);font-size:20px;--d:1500ms">${icon('key')}</span><span class="a" data-a="up" style="--d:1520ms">Release digests in Parameter Store are the last-known-good set</span></div>
 <div style="flex:1;display:flex;gap:10px"><span class="a" data-a="pop" style="color:var(--teal);font-size:20px;--d:1560ms">${icon('arrows-rotate')}</span><span class="a" data-a="up" style="--d:1580ms">Every release is a rebuild from zero, so every release also tests disaster recovery</span></div>
</div>`;

// ---------- slide 21 ----------
// d: card entrance; number +60, title +80, text +110, fix panel +150, its text +170 (v3 slide 21).
const lc = (n, bg, t, s, fix, d) => `<div class="card a" data-a="up" style="flex:1;height:500px;padding:28px 28px;--d:${d}ms">
 <div class="dot a" data-a="pop" style="width:70px;height:70px;background:${F(bg).bg};color:${F(bg).fg};font:700 30px Inter;margin-bottom:24px;--d:${d + 60}ms">${n}</div>
 <div class="serif a" data-a="up" style="font-size:28px;line-height:1.18;margin-bottom:18px;height:70px;--d:${d + 80}ms">${t}</div>
 <div class="a" data-a="up" style="font:400 21px/1.35 Inter;color:var(--ink3);height:150px;--d:${d + 110}ms">${s}</div>
 <div class="a" data-a="fade" style="border-radius:8px;background:#E3E8FB;padding:20px 20px;font:700 22px/1.25 Inter;color:var(--green);height:98px;display:flex;align-items:center;--d:${d + 150}ms"><div class="a" data-a="up" style="--d:${d + 170}ms">${fix}</div></div></div>`;
const s21 = () => `<div style="display:flex;gap:18px">
 ${lc(1, 'var(--teal-d)', 'Mocks passed, the real deploy failed', 'Missing cloud permissions only showed up in a real run', 'Test permissions for real', 200)}
 ${lc(2, 'var(--teal-d)', 'Events lost right after deploy', 'New SNS subscriptions take up to 15 minutes to route; nothing errors, messages just go nowhere', 'Deploy now waits for routing', 360)}
 ${lc(3, 'var(--orange)', 'Auto-scaling silently paused', 'A retried start-up reported success but left scaling suspended', "Check scaling, don't assume it", 520)}
</div>`;

// ---------- slide 22 (new) ----------
const s22 = () => `
<div style="position:absolute;left:0;top:0;width:330px">
 <div class="card a" data-a="left" style="padding:16px 18px;margin-bottom:14px;--d:246ms">
  <div style="display:flex;gap:12px;align-items:center;margin-bottom:8px"><div class="dot a" data-a="pop" style="width:46px;height:46px;background:var(--teal);font-size:22px;--d:264ms">${icon('users')}</div><div class="a" data-a="up" style="--d:268ms"><div style="font:700 19px Inter">People</div><div style="font:400 15.5px Inter;color:var(--ink3)">IAM Identity Center</div></div></div>
  <div class="a" data-a="up" style="font:400 16.5px/1.38 Inter;--d:313ms">admins (3) · developers (2)<br>1-hour sessions</div></div>
 <div class="card a" data-a="left" style="padding:16px 18px;margin-bottom:14px;--d:200ms">
  <div style="display:flex;gap:12px;align-items:center;margin-bottom:8px"><div class="dot a" data-a="pop" style="width:46px;height:46px;background:var(--teal);font-size:22px;--d:220ms">${icon('robot')}</div><div class="a" data-a="up" style="--d:240ms"><div style="font:700 19px Inter">Machines</div><div style="font:400 15.5px Inter;color:var(--ink3)">GitHub OIDC</div></div></div>
  <div class="a" data-a="up" style="font:400 16.5px/1.38 Inter;--d:260ms">plan / deploy / apply roles<br><b>no AWS keys in GitHub</b></div></div>
 <div class="a" data-a="left" style="border-radius:8px;background:#FDEFEC;border:1.5px solid #F6BFB5;padding:14px 18px;--d:400ms"><div class="a" data-a="up" style="--d:440ms">
  <div style="display:flex;gap:10px;align-items:center;font:700 17px Inter;color:var(--orange-d);margin-bottom:6px">${icon('triangle-exclamation').replace('<svg class="ic"', '<svg class="ic a" data-a="pop" style="--d:420ms"')} Disclosed exception</div>
  <div style="font:400 16px/1.35 Inter">2 IAM users for port forwarding (1 active key), inside the same guardrails</div></div></div>
</div>
<svg style="position:absolute;left:334px;top:60px" width="70" height="300"><defs><marker id="ah22" markerUnits="userSpaceOnUse" viewBox="0 0 12 16" markerWidth="12" markerHeight="16" refX="2" refY="8" orient="auto"><path d="M0,0 L12,8 L0,16 Z" fill="#3E5CD9"/></marker></defs><g stroke="#3E5CD9" stroke-width="3" fill="none"><path class="cn" pathLength="1" d="M4,40 H60" style="stroke-linecap:butt;--d:587ms" marker-end="url(#ah22)"/><path class="cn" pathLength="1" d="M4,186 H60" style="stroke-linecap:butt;--d:718ms" marker-end="url(#ah22)"/></g></svg>
<div class="a" data-a="fade" style="position:absolute;left:408px;top:0;width:756px;height:540px;border-radius:8px;border:2.5px solid var(--teal-d);background:#fff;padding:14px 18px;--d:150ms">
 <div style="display:flex;align-items:center;gap:12px"><span class="a" data-a="pop" style="font-size:24px;color:var(--teal-d);--d:200ms">${icon('building')}</span><div class="a" data-a="up" style="display:flex;align-items:center;gap:12px;--d:220ms"><div style="font:700 21px Inter;color:var(--teal-d)">AWS Organization</div><div style="font:400 16px Inter;color:var(--ink3)">management account runs no workloads</div></div></div>
 <div class="a" data-a="fade" style="position:absolute;left:18px;right:18px;top:58px;bottom:16px;border-radius:8px;border:2px solid var(--teal);background:var(--teal-ll);padding:12px 16px;--d:300ms">
  <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><div class="a" data-a="up" style="font:700 19px Inter;--d:320ms">Root</div><span class="tag a" data-a="pop" style="--d:412ms">SCP · Org-Baseline</span><span class="tag a" data-a="pop" style="--d:445ms">SCP · Region-Pin: Singapore + global</span></div>
  <div class="a" data-a="fade" style="position:absolute;left:16px;right:16px;top:54px;bottom:16px;border-radius:8px;border:2px solid var(--orange);background:var(--orange-ll);padding:12px 16px;--d:450ms">
   <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap"><div class="a" data-a="up" style="font:700 19px Inter;--d:470ms">Sandbox OU</div><span class="tag o a" data-a="pop" style="--d:479ms">SCP · Sandbox-Guardrails</span></div>
   <div class="a" data-a="up" style="font:400 15.5px Inter;color:var(--ink3);margin-top:4px;--d:490ms">no expensive services · small instance sizes only</div>
   <div class="a" data-a="fade" style="position:absolute;left:16px;right:16px;top:84px;bottom:16px;border-radius:8px;border:2px solid var(--teal-d);background:#fff;padding:14px 18px;--d:600ms">
    <div style="display:flex;align-items:center;gap:10px"><span class="a" data-a="pop" style="font-size:22px;color:var(--teal-d);--d:620ms">${icon('server')}</span><div class="a" data-a="up" style="font:700 20px Inter;--d:640ms">Team 8 project account</div></div>
    <div style="display:flex;gap:14px;margin-top:14px">
     <div class="a" data-a="fade" style="flex:1;border-radius:8px;background:#EFE9DF;padding:12px 14px;--d:700ms"><div class="a" data-a="up" style="--d:720ms"><div style="font:700 17px Inter">dev environment</div><div style="font:400 15.5px Inter;color:var(--ink3)">always-on EC2 host</div></div></div>
     <div class="a" data-a="fade" style="flex:1;border-radius:8px;background:#E3E8FB;padding:12px 14px;--d:760ms"><div class="a" data-a="up" style="--d:780ms"><div style="font:700 17px Inter;color:var(--teal-d)">prod environment</div><div style="font:400 15.5px Inter;color:var(--ink3)">ECS, rebuilt per release</div></div></div>
    </div>
    <div class="a" data-a="fade" style="margin-top:16px;border-radius:8px;background:var(--inverse);color:#fff;padding:12px 16px;font:400 16.5px/1.35 Inter;--d:850ms"><div class="a" data-a="up" style="--d:880ms"><b style="color:#FFB01F">Guardrails above the account:</b> they bind even its admins and root user</div></div>
   </div></div></div>
</div>`;

// ---------- slide 23 ----------
// d: card entrance; number +60, title +90, pill +140 (v3 slide 23).
const dt = (n, bg, t, q, d) => `<div class="card a" data-a="up" style="height:206px;padding:24px 24px;display:flex;flex-direction:column;justify-content:space-between;--d:${d}ms">
 <div style="display:flex;gap:20px;align-items:center"><div class="dot a" data-a="pop" style="width:64px;height:64px;background:${F(bg).bg};color:${F(bg).fg};font:700 28px Inter;--d:${d + 60}ms">${n}</div><div class="a" data-a="up" style="font:700 23px/1.25 Inter;flex:1;--d:${d + 90}ms">${t}</div></div>
 <div class="pill neutral a" data-a="pop" style="height:46px;font-size:19px;--d:${d + 140}ms">${q}</div></div>`;
const s23 = () => `
<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:6px">
 ${dt(1, 'var(--teal-d)', 'Buy as a customer, across three services', 'Functionality', 200)}
 ${dt(2, 'var(--orange)', 'Auto-scaling, on the dashboard', 'Scalability, performance', 320)}
 ${dt(3, 'var(--orange)', 'The pipeline that deployed it', 'Maintainability', 440)}
</div>
<div class="card a" data-a="up" style="margin-top:20px;padding:20px 24px;display:flex;align-items:center;gap:20px;--d:600ms">
 <div class="dot a" data-a="pop" style="width:64px;height:64px;background:var(--default);color:var(--foreground);font-size:28px;flex:none;--d:640ms">${icon('flask')}</div>
 <div class="a" data-a="up" style="--d:680ms"><div style="font:700 23px/1.25 Inter">Failure handling</div><div style="font:400 19px/1.35 Inter;color:var(--ink3);margin-top:4px">Identity outage · dead-letter queue · stop a server: not run · results in B12</div></div></div>
<div class="a" data-a="up" style="margin-top:20px;border-radius:8px;background:#FBDFD9;padding:12px 22px;font:700 19px Inter;display:flex;align-items:center;gap:12px;flex-wrap:wrap;--d:1100ms"><span style="color:transparent;font-size:14px;position:relative">●<span class="a recdot" data-a="fade" style="position:absolute;left:1px;top:3.6px;width:10.7px;height:10.7px;border-radius:50%;background:var(--red);--d:1100ms"></span></span> <span class="a" data-a="up" style="--d:1120ms">Live on AWS · the scaling graphs are from our 19 October load test</span></div>`;

// ---------- slide 24 ----------
const s24 = () => `
<div class="a" data-a="up" style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff;--d:150ms">Thank you</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff;--d:300ms">Questions?</div>
<div class="a" data-a="up" style="position:absolute;left:80px;top:304px;font:700 16px Inter;letter-spacing:.14em;color:#FFB01F;text-transform:uppercase;--d:420ms">What we hope you remember</div>
<div style="position:absolute;left:80px;top:338px;width:980px;display:grid;grid-template-columns:1fr 1fr;gap:14px 20px">
 ${['Split where no transaction crosses', 'Events never lost or doubled', 'Fails safe when identity is down', 'Rebuilt from scratch every release'].map((t, i) => `<div class="a" data-a="pop" style="height:52px;border-radius:26px;background:#fff;color:var(--teal-d);font:700 19px/52px Inter;text-align:center;--d:${600 + i * 110}ms">${t}</div>`).join('')}
</div>
<div style="position:absolute;left:80px;top:486px;width:980px;display:flex;gap:20px">
 ${[['1 → 3 tasks', 'auto-scaled at ≈ 455 req/s, 19 Oct'], ['US$0.49', 'runtime database, cache and containers, 7 sessions'], ['51 min', 'approved PR → released, tested, destroyed']].map((n, i) => `<div class="a" data-a="up" style="flex:1;border-radius:8px;border:1.5px solid rgba(255,255,255,.35);background:rgba(255,255,255,.08);padding:10px 18px;--d:${1000 + i * 120}ms"><div class="a" data-a="up" style="--d:${1040 + i * 120}ms"><div style="font:700 30px/1.2 Inter;color:#fff">${n[0]}</div><div style="font:400 15.5px Inter;color:#E6DFD3">${n[1]}</div></div></div>`).join('')}
</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span class="a" data-a="pop" style="font-size:20px;--d:1400ms">${icon('github')}</span> <span class="a" data-a="up" style="--d:1400ms">github.com/PPsyrius/iss-groupbuy-team8 · d3lvq02o2e7tcn.cloudfront.net</span></div>`;

module.exports = { 1: s1, 2: s2, 3: s3, 4: s4, 5: s5, 6: s6, 7: s7, 8: s8, 9: s9, 10: s10, 11: s11, 12: s12, 13: s13, 14: s14, 15: s15, 16: s16, 17: s17, 18: s18, 19: s19, 20: s20, 21: s21, 22: s22, 23: s23, 24: s24 };
