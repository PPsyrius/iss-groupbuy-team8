const fs = require('fs');
const path = require('path');
const { icon } = require('../../../presentation-kit/lib');
const { site, dg } = require('./site-geometry');
const C = require('./content');

// B11: crop of the CloudWatch dashboard, inlined so the HTML stays self-contained.
const DASHBOARD = 'data:image/jpeg;base64,' + fs.readFileSync(path.join(__dirname, 'assets', 'b11-dashboard.jpg')).toString('base64');

const tbl = (head, rows, opts = {}) => `<table class="t" style="font-size:${opts.fs || 16}px">
<tr>${head.map((h, i) => `<th style="${opts.w && opts.w[i] ? 'width:' + opts.w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map(r => `<tr>${r.map((c, i) => `<td class="${i === 0 && opts.id ? 'id' : ''}">${c}</td>`).join('')}</tr>`).join('')}</table>`;

// ---- simplified diagrams (B13–B15) ----
const box = (x, y, w, h, html, st = '') => `<div style="position:absolute;left:${x}px;top:${y}px;width:${w}px;height:${h}px;${st}">${html}</div>`;
const wb = (x, y, w, h, t, extra = '') => box(x, y, w, h, `<div style="height:100%;display:flex;align-items:center;justify-content:center;text-align:center;font:400 21px/1.2 Inter">${t}</div>`, `background:#fff;border:1px solid #DDD5C8;border-radius:8px;box-shadow:var(--shadow-surface);${extra}`);
const db = (x, y, w, t) => `<svg style="position:absolute;left:${x}px;top:${y}px" width="${w}" height="86"><path d="M2,14 A${w / 2 - 2},12 0 0 1 ${w - 2},14 V70 A${w / 2 - 2},12 0 0 1 2,70 Z" fill="#EFE9DF" stroke="#6B655C" stroke-width="1.5"/><path d="M2,14 A${w / 2 - 2},12 0 0 0 ${w - 2},14" fill="none" stroke="#6B655C" stroke-width="1.5"/></svg><div style="position:absolute;left:${x}px;top:${y + 34}px;width:${w}px;text-align:center;font:400 20px Inter">${t}</div>`;
const ln = (pts, c = '#6B655C', w = 2, head = true) => `<polyline points="${pts}" fill="none" stroke="${c}" stroke-width="${w}" ${head ? 'marker-end="url(#ah' + c.replace('#', '') + ')"' : ''}/>`;
const mkr = c => `<marker id="ah${c.replace('#', '')}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${c}"/></marker>`;
const lines = (inner, cols = ['#6B655C', '#3E5CD9', '#8C2414', '#E8402A']) => `<svg style="position:absolute;left:0;top:0" width="1164" height="548"><defs>${cols.map(mkr).join('')}</defs>${inner}</svg>`;

const b13 = () => `<div style="position:relative;width:1164px;height:548px">
${lines([
  ln('200,52 236,52'), ln('446,52 482,52'), ln('560,82 160,154'), ln('630,82 600,154'),
  ln('780,52 830,52 830,456 866,456'), ln('412,196 274,196', '#E8402A', 3), ln('760,196 866,196', '#E8402A', 3),
  ln('150,236 150,260'), ln('590,236 590,260'), ln('945,232 945,260'), ln('1098,232 1098,260'), ln('945,334 945,406'),
].join(''))}
${wb(0, 22, 200, 60, 'Browser')}${wb(244, 22, 200, 60, 'CloudFront')}${wb(490, 22, 290, 60, 'Load balancer')}
${box(870, 22, 294, 66, '<div style="font:400 18px/1.4 Inter;color:var(--muted);text-align:center;padding-top:8px">Google sign-in → identity<br>Stripe payments → commerce</div>', 'background:#EFE9DF;border-radius:8px')}
${box(0, 156, 270, 80, '<div style="text-align:center;color:#fff;padding-top:6px"><div style="font:700 25px Inter">Identity</div><div style="font:italic 400 18px Inter">platform service</div><div style="font:400 17px Inter">sign-in · roles</div></div>', 'background:var(--teal);border-radius:8px')}
${box(412, 156, 348, 80, '<div style="text-align:center;color:var(--orange-d);padding-top:6px"><div style="font:700 25px Inter">Commerce</div><div style="font:italic 400 18px Inter">business application</div><div style="font:400 17px Inter">offers · teams · orders · payments</div></div>', 'background:var(--orange-l);border:2px solid var(--accent);border-radius:8px')}
${box(282, 158, 128, 80, '<div style="text-align:center;font:700 17px/1.2 Inter;color:var(--orange-d)">is this user active?<div style="font:400 15px Inter">the only direct call</div></div>')}
<div style="position:absolute;left:770px;top:166px;font:700 16px Inter;color:var(--orange-d)">outbox</div>
${box(870, 166, 294, 66, '<div style="font:400 23px/66px Inter;text-align:center">Event topic (SNS)</div>', 'background:var(--yellow);border:2px solid var(--warning-soft-foreground);color:var(--warning-soft-foreground);border-radius:8px')}
${db(40, 262, 200, 'identity DB')}${db(440, 262, 300, 'commerce DB + outbox')}
${wb(870, 262, 150, 72, '<span style="font-size:18px">Notification<br>queue + DLQ</span>')}${wb(1032, 262, 132, 72, '<span style="font-size:18px">Audit<br>queue + DLQ</span>')}
${box(870, 410, 294, 92, '<div style="text-align:center;color:#fff;padding-top:8px"><div style="font:700 25px Inter">Notification</div><div style="font:italic 400 18px Inter">platform service</div><div style="font:400 17px Inter">own database</div></div>', 'background:var(--teal);border-radius:8px')}
${box(0, 400, 760, 92, '<div style="padding:16px 22px;font:400 20px/1.35 Inter"><b>Shared session store (Valkey)</b><br>sign in once; every service reads your role</div>', 'background:#E3E8FB;border:1.5px dashed var(--teal);border-radius:8px')}
</div>`;

const b14 = () => `<div style="position:relative;width:1164px;height:548px">
${lines([ln('115,82 115,118'), ln('230,150 296,150', '#3E5CD9', 3), ln('115,200 115,236'), ln('470,180 470,226', '#3E5CD9', 3), ln('900,312 980,312', '#E8402A', 3)].join(''))}
${wb(0, 22, 230, 58, 'Users')}
${box(0, 118, 230, 82, '<div style="font:700 21px/1.25 Inter;color:#fff;text-align:center;padding-top:14px">CloudFront<br>the only way in</div>', 'background:var(--teal-d);border-radius:8px')}
${box(0, 236, 230, 60, '<div style="font:700 21px/60px Inter;color:#fff;text-align:center">S3 · web app</div>', 'background:var(--teal-d);border-radius:8px')}
${box(0, 360, 36, 36, '', 'background:var(--teal-d);border-radius:6px')}<div style="position:absolute;left:46px;top:364px;font:400 19px Inter">permanent</div>
${box(0, 410, 36, 36, '', 'background:#fff;border:1.5px solid #9AB;border-radius:6px')}<div style="position:absolute;left:46px;top:414px;font:400 19px Inter">rebuilt per release</div>
${box(266, 0, 680, 548, '<div style="font:700 21px Inter;color:var(--teal-d);padding:8px 14px">Network across two zones · rebuilt every release</div>', 'border:2px dashed var(--teal);border-radius:8px')}
<div style="position:absolute;left:286px;top:40px;width:640px;height:110px;background:#EFE9DF;border-radius:8px"></div><div style="position:absolute;left:292px;top:42px;font:400 16px Inter;color:var(--muted)">public</div>
${wb(296, 64, 330, 70, 'Load balancer<br>checks CloudFront\'s secret')}${wb(646, 64, 266, 70, 'NAT · outbound only')}
<div style="position:absolute;left:286px;top:160px;width:640px;height:370px;background:#EFE9DF;border-radius:8px"></div><div style="position:absolute;left:292px;top:162px;font:400 16px Inter;color:var(--muted)">private</div>
${box(300, 188, 610, 124, '<div style="font:700 20px Inter;color:var(--orange-d);padding:6px 10px">Fargate containers (ARM)</div><div style="display:flex;gap:10px;padding:6px 10px">' + [['Identity ×1', 'var(--teal)'], ['Commerce ×2–6', 'var(--orange)'], ['Notification ×1–4', 'var(--teal)']].map(x => `<div style="flex:1;height:62px;border-radius:31px;background:${x[1] === 'var(--orange)' ? 'var(--orange-l)' : x[1]};color:${x[1] === 'var(--orange)' ? 'var(--orange-d)' : '#fff'};font:700 19px/62px Inter;text-align:center;white-space:nowrap">${x[0]}</div>`).join('') + '</div>', 'background:#fff;border:1.5px solid var(--accent);border-radius:8px')}
${wb(300, 340, 290, 96, 'PostgreSQL (RDS)<br>3 databases')}${wb(620, 340, 290, 96, 'Valkey cache<br>sessions')}
${box(980, 22, 184, 64, '<div style="font:700 21px/64px Inter;color:#fff;text-align:center">ECR · images</div>', 'background:var(--teal-d);border-radius:8px')}
${box(980, 104, 184, 82, '<div style="font:700 20px/1.25 Inter;color:#fff;text-align:center;padding-top:14px">Parameter Store<br>secrets</div>', 'background:var(--teal-d);border-radius:8px')}
${wb(980, 270, 184, 82, 'SNS → SQS<br>events')}${wb(980, 370, 184, 82, 'CloudWatch<br>dashboard, alarms')}
</div>`;

const b15 = () => {
  const L = ['Expiry job', 'Commerce DB', 'Relay', 'SNS topic', 'Notification queue', 'Notification service', 'Notification DB'];
  const col = ['var(--orange-l)', 'var(--orange-l)', 'var(--orange-l)', 'var(--yellow)', 'var(--yellow)', 'var(--teal)', 'var(--teal)'];
  const fg = ['var(--orange-d)', 'var(--orange-d)', 'var(--orange-d)', 'var(--warning-soft-foreground)', 'var(--warning-soft-foreground)', '#fff', '#fff'];
  const X = i => 78 + i * 168;
  const steps = [[0, 1, 1, '<b style="color:var(--orange-d)">1 One transaction:</b> expire team · cancel orders · start refunds · save event', true], [2, 1, 2, '<b>2</b> pick up new events'], [2, 3, 3, '<b>3</b> publish'], [3, 4, 4, '<b>4</b> route to the notification queue'], [4, 5, 5, '<b>5</b> receive'], [5, 6, 6, '<b style="color:var(--orange-d)">6 One transaction:</b> record event ID · create notifications', true], [5, 4, 7, '<b>7</b> delete message']];
  return `<div style="position:relative;width:1164px;height:548px">
  ${L.map((t, i) => `<div style="position:absolute;left:${X(i) - 78}px;top:0;width:156px;height:66px;border-radius:8px;background:${col[i]};color:${fg[i]};font:700 19px/1.15 Inter;display:flex;align-items:center;justify-content:center;text-align:center">${t}</div><div style="position:absolute;left:${X(i)}px;top:66px;width:0;height:420px;border-left:1.5px dashed #A69E92"></div>`).join('')}
  ${steps.map(([a, b, n, t, o]) => { const y = 72 + n * 52, x1 = X(a), x2 = X(b), l = Math.min(x1, x2), w = Math.abs(x2 - x1), c = o ? 'var(--orange)' : 'var(--teal-d)';
    return `<div style="position:absolute;left:${l}px;top:${y + 26}px;width:${w}px;height:3px;background:${c}"></div><div style="position:absolute;left:${x2 > x1 ? x2 - 10 : x2}px;top:${y + 21}px;border-${x2 > x1 ? 'left' : 'right'}:11px solid ${c};border-top:6.5px solid transparent;border-bottom:6.5px solid transparent"></div><div style="position:absolute;left:${n === 6 ? 560 : Math.min(x1, x2) + 4}px;top:${y - 4}px;font:400 21px Inter;white-space:nowrap;background:rgba(246,242,236,.92);padding:0 4px">${t}</div>`; }).join('')}
  <div style="position:absolute;left:4px;top:${72 + 8 * 52}px;font:700 21px Inter;color:var(--teal-d)">8 &nbsp;Each member sees it in the app</div></div>`;
};

function list() {
  const B = [];
  B.push({ id: 'BX', speaker: 'Lim', title: 'Backup slides', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 22px;margin-top:4px">
 ${C.BACKUPS.map(b => `<div data-go="${b[0]}" style="cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:8px 14px">
   <span class="tag" style="min-width:44px;text-align:center">${b[0]}</span><div><div style="font:600 17px Inter">${b[1]}</div><div style="font:400 14.5px Inter;color:var(--muted)">${b[2]}</div></div></div>`).join('')}
</div><div style="margin-top:12px;font:italic 400 15px Inter;color:var(--muted)">Click a row, or press X to come back here. Slide 24 stays up during questions; open a backup only when a question needs it.</div>` });

  B.push({ id: 'B1a', speaker: 'Eric', title: 'Logical decisions (AD-02 to AD-06)', body: tbl(['ID', 'Decision', 'Why'], [
    ['AD-02', 'Split where no transaction crosses, not by nouns', 'Payment and expiry stay one transaction, so no sagas. Five services rejected (50–55 person-days)'],
    ['AD-03', 'One database and one database role per service', 'No joins across services; enforced by grants and architecture tests'],
    ['AD-04', 'Outbox → SNS → SQS with dead-letter queues; consumers skip duplicates', 'Events are never lost or applied twice, and there is no broker to run'],
    ['AD-05', 'One shared login session in Valkey, roles inside it', 'Services check roles without calling identity on every request'],
    ['AD-06', 'One direct call, commerce → identity, failing closed', 'An outage returns an error and Stripe retries; a buyer is never wrongly rejected'],
  ], { id: true, fs: 20, w: [90, 500] }) });
  B.push({ id: 'B1b', speaker: 'Liu Jin', title: 'Physical decisions (AD-07 to AD-14)', body: tbl(['ID', 'Decision', 'Why'], [
    ['AD-07', 'Fargate on ARM, not Kubernetes (EKS)', 'The EKS control plane (≈ US$73/month) costs more than all our compute; ARM is ~20% cheaper'],
    ['AD-08', 'Fargate Spot above the minimum (1 : 3)', 'Cheap extra capacity; the minimum stays on regular Fargate'],
    ['AD-09', 'CloudFront → load balancer with a secret header', 'HTTPS at the edge; the load balancer refuses everything else (403)'],
    ['AD-10', 'Small NAT instance, not a NAT Gateway', '≈ US$4 instead of ≈ US$43 a month'],
    ['AD-11', 'One database server, three databases', 'Low cost; moving a database to its own server needs no code change'],
    ['AD-12', 'Permanent edge, disposable runtime', '≈ US$0.12–0.20 an hour while up, instead of ≈ US$125 a month always on'],
    ['AD-13', 'No Kubernetes or Argo CD', 'They would cost 8–12 days of set-up before any feature work'],
    ['AD-14', 'Own account in an AWS Organization', 'Separate blast radius and bill; short-lived logins; guardrails the account admin cannot lift'],
  ], { id: true, fs: 17, w: [90, 440] }) });
  B.push({ id: 'B2', speaker: 'Liu Jin', title: 'Known limitations and the way out', body: tbl(['ID', 'Limitation', 'Way out'], [
    ['AISS-01', 'Database, cache and NAT each run in one zone', 'A cost choice; production adds multi-zone and replicas'],
    ['AISS-02', 'The commerce service is large', 'Move payments out next, with a saga for refunds'],
    ['AISS-03', 'Checkout stops while identity is down', 'By design (no wrong refunds); later a cache or circuit breaker'],
    ['AISS-04', 'Internal endpoints rely on network rules only', 'Add a service token or mutual TLS'],
    ['AISS-05', 'One shared database server', 'Move a database to its own server; no code change'],
    ['AISS-06', 'Building the environment takes ≈ 32 min, 15 of them waiting for SNS', 'Start it early on the day; the edge stays permanent'],
  ], { id: true, fs: 19, w: [110, 520] }) });
  B.push({ id: 'B3', speaker: 'Eric', title: 'Context map: where service boundaries may go', body: `<div class="a" data-a="fade" style="${site(79, 184, 721, 815)};--d:200ms">${dg('context-map')}</div>
   <ul class="b a" data-a="up" style="${site(871, 209, 958, 518)};margin:0;font:400 22.67px/1.3 Inter;--d:352ms">
    <li style="margin-bottom:16px"><b>Identity → Google:</b> anti-corruption layer (the OAuth gateway port)</li>
    <li style="margin-bottom:16px"><b>Identity → commerce:</b> customer and supplier, one synchronous call (the user port)</li>
    <li style="margin-bottom:16px"><b>Commerce → Stripe:</b> anti-corruption layer (checkout and refund gateways, webhook)</li>
    <li style="margin-bottom:16px"><b>Commerce → notification:</b> published language: version 1 events over SNS → SQS</li></ul>
   <div class="a" data-a="fade" style="${site(871, 742, 962, 144)};border-radius:8px;background:#E3E8FB;--d:667ms"></div>
   <div class="a" data-a="up" style="${site(900, 742, 907, 144)};display:flex;align-items:center;padding:4.67px;font:400 20px/1.2 Inter;--d:670ms">Four contexts share one service because they share transactions; the map shows where network boundaries are allowed to be.</div>` });
  B.push({ id: 'B4', speaker: 'Eric', title: 'Aggregates and their life cycles', body: `<ul class="b a" data-a="up" style="${site(88, 209, 1037, 562)};margin:0;font:400 22px/1.3 Inter;--d:254ms">
    <li style="margin-bottom:13.33px"><b>Team (GroupDeal):</b> only OPEN may change state, and only once</li><li style="margin-bottom:13.33px"><b>Order:</b> PENDING → CONFIRMED or CANCELLED</li>
    <li style="margin-bottom:13.33px"><b>Purchase intent</b> is the order-side aggregate</li><li style="margin-bottom:13.33px">Also: checkout payment, refund work, product with team terms, notification</li>
    <li style="margin-bottom:13.33px">Domain events: MemberJoined, TeamSucceeded, TeamExpired, PaymentSucceeded</li>
    <li style="margin-bottom:13.33px">In the same transaction, the outbox writer turns them into versioned integration events</li></ul>
   <div class="a" data-a="fade" style="${site(88, 785, 1037, 130)};border-radius:8px;background:#FDEFEC;--d:594ms"></div>
   <div class="a" data-a="up" style="${site(115, 785, 994, 130)};display:flex;align-items:center;padding:4.67px;font:400 19.33px/1.2 Inter;--d:598ms"><div><b style="color:var(--orange-d)">Be ready to explain:</b> FAILED and CANCELED are allowed by the status table, but nothing in the code triggers them yet.</div></div>
   <div class="a" data-a="fade" style="${site(1180, 184, 661, 828)};--d:200ms">${dg('aggregates')}</div>` });
  B.push({ id: 'B5', speaker: 'Eric', title: 'Layers, and tests that keep boundaries in place', body: tbl(['Layer', 'Module', 'Contains'], [
    ['Client', '<span class="mono">blocbuy-client</span>', 'DTOs and the integration event contracts (TeamSucceededV1, TeamExpiredV1, PaymentSucceededV1)'],
    ['Adapter', '<span class="mono">blocbuy-adapter</span>', 'REST controllers, the result envelope, the global error handler'],
    ['Application', '<span class="mono">blocbuy-application</span>', 'Commands, queries, application services, outbox relay'],
    ['Domain', '<span class="mono">blocbuy-domain</span>', 'Aggregates, domain events, ports (no framework code)'],
    ['Infrastructure', '<span class="mono">blocbuy-infrastructure</span>', 'MyBatis, Stripe, Redis, SNS/SQS transports'],
    ['Assemblies', '<span class="mono">services/*-svc</span>', 'Thin Spring Boot entry points that pick which contexts to run'],
  ], { fs: 18, w: [150, 270] }) + `<div style="margin-top:18px;border-radius:8px;background:var(--inverse);color:#fff;padding:16px 22px;font:400 19px/1.4 Inter"><b style="color:#FFB01F">Boundaries are tested:</b> 18 architecture rules run with the unit tests, e.g. <span class="mono" style="font-size:16px">commerce_reaches_identity_only_through_user_port</span></div>` });
  B.push({ id: 'B6', speaker: 'Yorik', title: 'Joining a team by paying, step by step', body: `<div class="a" data-a="fade" style="${site(79, 184, 1762, 711)};--d:200ms">${dg('uc4-join-sequence')}</div>
   <div class="cap a" data-a="up" style="${site(130, 907, 1656, 89)};font:400 20px/1.2 Inter;--d:672ms">The signature is checked outside the transaction; one transaction then deduplicates, locks, admits the member, saves the order, takes stock and writes the outbox, re-checking that the user is active.</div>` });
  B.push({ id: 'B7', speaker: 'Liu Jin', title: 'Technology and services', body: tbl(['Concern', 'Choice'], [
    ['Edge', 'CloudFront (permanent: Google sign-in and the Stripe webhook depend on its address), S3 for the web app'],
    ['Compute', 'ECS Fargate on ARM64, 0.5 vCPU / 1 GB per task, circuit breaker with rollback, Service Connect'],
    ['Scaling', 'commerce: requests per target, 2–6, out after 60 s / in after 180 s · notification: backlog per task (50), 1–4 · identity: 1'],
    ['Data', 'PostgreSQL 18 on RDS db.t4g.micro (TLS) · Valkey 8 on ElastiCache cache.t4g.micro (TLS and password)'],
    ['Messaging', 'SNS (encrypted) → SQS (60 s visibility) with dead-letter queues after 5 receives, filter policies'],
    ['Secrets', 'Parameter Store SecureString; database passwords never stored in Terraform state'],
    ['Observability', 'CloudWatch dashboard (edge, services, messaging), dead-letter and queue-age alarms, trace ID on every request'],
    ['App stack', 'Java 21, Spring Boot 4, MyBatis, Sa-Token, Stripe · React 19, TanStack, HeroUI'],
  ], { fs: 17, w: [150] }) });
  B.push({ id: 'B8', speaker: 'Pan', title: 'Everything the real cloud taught us', body: tbl(['What happened', 'What we did'], [
    ['The first promotion failed five times (image digests, variables, a tag policy, an early 503)', 'Fixed one at a time; first green run on 6 Oct'],
    ['A read-only container disk crashed the web server\'s temp folder', 'Mounted a writable /tmp'],
    ['The AWS tagging API still lists resources that are already deleted', 'The teardown check asks each service directly'],
    ['Default database privileges exposed tables to every role', 'Grants set after each migration, and tested'],
    ['The outbox relay marked unsent rows as failed after a mid-batch error', 'Caught in code review and fixed'],
    ['The notification consumer was never switched on in AWS', 'Now enabled together with messaging'],
    ['Setting up scaling was denied: tags need an extra permission', 'Permission added, limited to this project (17 Oct)'],
    ['The deploy stopped twice: alarm e-mails were not yet confirmed', 'Testing opt-out for now; confirm the e-mails before the final release'],
    ['An interrupted start-up leaves auto-scaling paused', 'Repaired by hand; no fix yet'],
    ['An organisation policy blocked CloudFront metrics (us-east-1)', 'Policies consolidated with a narrow exception'],
  ], { fs: 15.5, w: [640] }) });
  B.push({ id: 'B9', speaker: 'Pan', title: 'Infrastructure as code, and the actual bill', body: tbl(['Stack', 'Lifetime', 'Contents'], [
    ['foundation', 'permanent', 'state bucket, GitHub OIDC roles with a permissions boundary, ECR, Parameter Store'],
    ['edge', 'permanent', 'CloudFront and the S3 web app (pointed at the new load balancer on every build)'],
    ['runtime', 'disposable', 'network, ECS, load balancer, database, cache, SNS/SQS, dashboard, alarms: 113–115 resources'],
  ], { fs: 17, w: [140, 140] }) + `<ul class="b" style="font:400 17.5px/1.38 Inter;margin-top:16px">
    <li><span class="mono" style="font-size:15px">env-up.sh / env-down.sh / env-status.sh</span> (status checks that nothing is left)</li>
    <li>Up ≈ 32 min (17 Oct, twice); down ≈ 13 min (115 resources in 709 s)</li>
    <li>Golden-path <span class="mono" style="font-size:15px">ecs-service</span> module: a new service is one map entry</li>
    <li>The apply role can only touch IAM under <span class="mono" style="font-size:15px">/swe5001/</span>, limited by tags and names</li></ul>
   <div style="margin-top:6px;border-radius:8px;background:var(--inverse);color:#fff;padding:14px 20px;font:400 17.5px/1.4 Inter"><b style="color:#FFB01F">Actual AWS bill</b> (whole account, Cost Explorer): Sep US$78.51; 1–17 Oct ≈ US$65 (≈ US$4 a day), mostly the always-on development server. This project's runtime (database, cache, containers): <b>US$0.49</b> across 7 sessions.</div>` });
  B.push({ id: 'B10', speaker: 'Eric', title: 'API access for producers and consumers', body: `
   <div style="display:flex;align-items:stretch;gap:10px;height:150px">
    ${[['users', 'Clients', 'the web app; future partner apps', '#EFE9DF', 'var(--ink)'], ['shield-halved', 'CloudFront', 'the only way in; adds the secret header', 'var(--teal-l)', 'var(--teal-d)'], ['sitemap', 'Load-balancer paths', '/auth, /users, /notifications, /api/*; anything else 403', 'var(--teal-l)', 'var(--teal-d)'], ['cubes', 'Services', 'identity · commerce · notification', 'var(--orange-l)', 'var(--orange-d)']].map((c, i, a) => `<div style="flex:1;border-radius:8px;background:${c[3]};padding:14px 16px"><div style="font-size:24px;color:${c[4]}">${icon(c[0])}</div><div style="font:700 18px Inter;margin-top:6px">${c[1]}</div><div style="font:400 15.5px/1.3 Inter;color:var(--ink3)">${c[2]}</div></div>${i < a.length - 1 ? `<div style="display:flex;align-items:center;color:var(--muted)">${icon('arrow-right-long')}</div>` : ''}`).join('')}
   </div>
   <div style="display:flex;gap:18px;margin-top:16px">
    <div class="card" style="flex:1;padding:16px 20px"><div class="serif" style="font-size:22px;color:var(--teal-d);margin-bottom:8px">Built</div><ul class="b" style="font:400 16.5px/1.38 Inter">
     <li>Public contract <span class="mono" style="font-size:14px">contracts/openapi.json</span>, committed; TypeScript client generated; contract check on every pull request</li>
     <li>Events versioned (V1): producers can add V2 without a joint release</li>
     <li>Internal endpoints: not routed, peer services only, not in the public contract (tested)</li></ul></div>
    <div style="flex:1;border-radius:8px;background:#FDEFEC;padding:16px 20px"><div class="serif" style="font-size:22px;color:var(--orange-d);margin-bottom:8px">Not built (be upfront)</div><ul class="b" style="font:400 16.5px/1.38 Inter">
     <li>API gateway, per-client keys, rate limiting</li><li>Service-to-service token or mutual TLS (AISS-04)</li>
     <li><b>Way forward:</b> API Gateway, or load-balancer and WAF rate rules behind CloudFront; a service token on internal endpoints</li></ul></div>
   </div>` });
  B.push({ id: 'B11', speaker: 'Lim', title: 'Analytics: what the platform measures', body: `
   <div style="display:flex;gap:18px">
    <div class="card" style="flex:1;padding:16px 20px"><div style="display:flex;gap:10px;align-items:center"><span style="color:var(--green);font-size:22px">${icon('chart-line')}</span><div class="serif" style="font-size:22px;color:var(--green)">Operational, in use</div></div>
     <ul class="b" style="font:400 16.5px/1.38 Inter;margin-top:10px"><li>CloudWatch dashboard: edge, services, messaging</li><li>The 16 Oct load results set the scaling target (12,000 requests per task per minute)</li><li>Dead-letter and queue-age alarms</li></ul></div>
    <div class="card" style="flex:1;padding:16px 20px"><div style="display:flex;gap:10px;align-items:center"><span style="color:var(--orange-d);font-size:22px">${icon('money-bill-trend-up')}</span><div class="serif" style="font-size:22px;color:var(--orange-d)">Business, in the data, not built</div></div>
     <ul class="b" style="font:400 16.5px/1.38 Inter;margin-top:10px"><li>Team fill rate and time to fill, per product</li><li>Referrals from share invites (invites and credits are already stored)</li><li>Start-to-success conversion; refund rate per product</li></ul></div>
   </div>
   <div style="display:flex;gap:18px;margin-top:16px;align-items:flex-start">
    <div style="flex:1">
     <div style="display:flex;align-items:center;gap:10px;border-radius:8px;background:#E3E8FB;padding:16px 18px">
      <div style="font:700 15px Inter;letter-spacing:.08em;color:var(--teal-d);width:84px">Next step</div>
      ${['Event bus', 'Analytics consumer', 'Reporting store'].map((t, i) => `<div style="flex:1;height:64px;border-radius:8px;background:${i === 0 ? 'var(--yellow)' : '#fff'};border:1.5px solid ${i === 0 ? 'var(--yellow)' : 'var(--teal)'};display:flex;align-items:center;justify-content:center;text-align:center;font:700 16px/1.2 Inter">${t}</div>${i < 2 ? `<span style="color:var(--teal-d)">${icon('arrow-right-long')}</span>` : ''}`).join('')}
     </div>
     <div style="margin-top:12px;font:400 15.5px/1.4 Inter;color:var(--muted)">A read-only consumer on the same event bus, the same pattern as the audit queue: no change to commerce.</div>
    </div>
    <div style="width:470px;flex:none">
     <img alt="" src="${DASHBOARD}" style="display:block;width:470px;height:224px;object-fit:cover;border-radius:8px;outline:1.5px solid #E7E0D4">
     <div style="margin-top:6px;text-align:center;font:italic 400 14px Inter;color:var(--muted)">CloudWatch dashboard blocbuy-swe5001, October test session</div>
    </div>
   </div>` });
  const R = (n, t, r) => [n, t, r];
  B.push({ id: 'B12', speaker: 'Lim', title: 'Scale-out and resilience results', body: tbl(['Check', 'Target', 'Result on AWS'], [
    R('HTTP auto-scaling', '≥ 80% of linear from 1 → 4 tasks; p95 &lt; 800 ms except 3 min after a scale-out', 'commerce 1 → 3 tasks on its own; ≈ 455 req/s, server p95 &lt; 50 ms. Partial: the load laptop hit its limit first, so 4 tasks and linearity are not shown yet'),
    R('Queue scaling', '2,000 expiries → up to 4 tasks, drained, dead-letter queue empty', 'not run on AWS · local test: 141 s end to end'),
    R('Self-healing', 'Stop 1 of 2 tasks: no failed requests, replacement within 2 min', 'not run on AWS'),
    R('Bad message', '5 receives → dead-letter queue → alarm', 'not run on AWS · local test: passed 15 Oct'),
    R('Identity outage', 'Browsing works; checkout returns 500 and is redelivered once', 'not run on AWS · local test: passed'),
    R('Build from nothing', '3 services stable, 9 smoke checks', '17 Oct, twice: tasks 2/2 · 1/1 · 1/1; 9/9 checks; SNS → SQS verified; buyer journey end to end: not run on AWS'),
    R('Teardown', 'Everything destroyed, absence check clean', '115 resources in 709 s (17 Oct); 113 in 784 s (16 Oct)'),
  ], { fs: 16, w: [190, 360] }) + `<div style="margin-top:10px;font:italic 400 15px Inter;color:var(--muted)">A local result is never presented as an AWS result.</div>` });
  B.push({ id: 'B13', speaker: 'Eric', title: 'Logical view, simplified', body: b13() });
  B.push({ id: 'B14', speaker: 'Liu Jin', title: 'Physical view, simplified', body: b14() });
  B.push({ id: 'B15', speaker: 'Yorik', title: 'Team expiry, numbered steps', body: b15() });
  return B;
}
module.exports = { list };
