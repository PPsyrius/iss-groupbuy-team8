const { icon, md, mk, svg } = require('../../../presentation-kit/lib');
const C = require('./content');

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
  <div style="position:absolute;left:4px;top:${72 + 8 * 52}px;font:700 21px Inter;color:var(--teal-d)">8 &nbsp;The team owner sees it in the app ${mk('VERIFY', 'owner or each member')}</div></div>`;
};

function list() {
  const B = [];
  B.push({ id: 'BX', title: 'Backup slides: index', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 22px;margin-top:4px">
 ${C.BACKUPS.map(b => `<div data-go="${b[0]}" style="cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:8px 14px">
   <span class="tag" style="min-width:44px;text-align:center">${b[0]}</span><div><div style="font:600 17px Inter">${b[1]}</div><div style="font:400 14.5px Inter;color:var(--muted)">${b[2]}</div></div></div>`).join('')}
</div><div style="margin-top:12px;font:italic 400 15px Inter;color:var(--muted)">Click a row (or type its number in the URL as #B6). Slide 24 stays up during Q&amp;A; open a backup only when a question needs it.</div>` });

  B.push({ id: 'B1a', speaker: 'Eric', title: 'Architecture decisions AD-01 to AD-06 (logical)', body: tbl(['ID', 'Decision', 'Trade-off / rationale'], [
    ['AD-01', 'Strangler, not big bang: one codebase, two topologies (monolith on dev, distributed on ECS)', 'Commerce code churned in 46 commits in Sep → split by assembly, not relocation (ADR 0008)'],
    ['AD-02', 'Split along transactional consistency boundaries, not nouns', 'Settlement and expiry stay single-transaction → no sagas. 5-service split rejected (50–55 person-days)'],
    ['AD-03', 'Database per service, role per service', 'No cross-context joins; enforced by grants + ArchUnit'],
    ['AD-04', 'Transactional outbox → SNS → SQS + DLQ, idempotent consumers (ADR 0007)', 'At-least-once + dedupe = effectively-once; no broker to operate'],
    ['AD-05', 'Shared Sa-Token session in Valkey, roles in session', 'No per-request call to identity for authorisation'],
    ['AD-06', 'One sync call commerce → identity, fail closed', 'Outage → 500 → Stripe redelivers; never wrongly rejects a buyer'],
  ], { id: true, fs: 18, w: [90, 520] }) });
  B.push({ id: 'B1b', speaker: 'Liu Jin', title: 'Architecture decisions AD-07 to AD-14 (physical)', body: tbl(['ID', 'Decision', 'Rationale'], [
    ['AD-07', 'ECS Fargate ARM64, not EKS', 'EKS control plane ≈ US$73/mo, more than all Fargate compute; ARM ~20% cheaper than x86'],
    ['AD-08', 'Fargate base + Spot (1 : 3) above the minimum', 'Cheap burst capacity, baseline protected'],
    ['AD-09', 'CloudFront → ALB with origin-secret header; ALB default 403', 'TLS at the edge; ALB unusable except through CloudFront'],
    ['AD-10', 'fck-nat t4g.nano instead of NAT Gateway', '≈ US$4 vs ≈ US$43 per month'],
    ['AD-11', 'One RDS instance, three databases', 'Logical isolation at demo cost; own instance is the scale-out path, no code change'],
    ['AD-12', 'Persistent foundation/edge, disposable runtime', '≈ US$0.12–0.20/h while up vs ≈ US$125/mo 24×7 (proposal estimate)'],
    ['AD-13', 'No K8s / Argo CD', '8–12 days of setup before any feature work (study 03)'],
    ['AD-14', 'Dedicated member account in an AWS Organization; Identity Center for people, OIDC for CI, SCP guardrails', 'Isolated blast radius and billing; short-lived credentials; limits the account admin can\'t lift'],
  ], { id: true, fs: 16, w: [90, 480] }) });
  B.push({ id: 'B2', speaker: 'Liu Jin', title: 'Known limitations AISS-01 to AISS-06', body: tbl(['ID', 'Limitation', 'Resolution path'], [
    ['AISS-01', 'RDS single-AZ, single Valkey node, one fck-nat', 'Cost-driven; Multi-AZ, replicas, NAT per AZ'],
    ['AISS-02', 'commerce-svc is coarse', 'Extract payment next, with a saga for fulfilment/refund'],
    ['AISS-03', 'Identity outage fails checkout closed', 'By design; Stripe redelivery; cache or circuit breaker later'],
    ['AISS-04', '/internal/** relies on network isolation only', 'Add a service token or mTLS'],
    ['AISS-05', 'One shared RDS instance', 'Move a DB to its own instance, no code change'],
    ['AISS-06', 'Distributed cold start ≈ 32 min (17 Oct, ×2), 15 min of it the SNS filter wait', 'Pre-warm on the day; edge stays persistent'],
  ], { id: true, fs: 19, w: [110, 520] }) });
  B.push({ id: 'B3', speaker: 'Eric', title: 'Context map', body: `<div style="display:flex;gap:30px;align-items:flex-start">
   <div style="width:470px;flex:none">${svg('context-map').replace('<svg ', '<svg style="width:470px;height:auto" ')}</div>
   <div style="flex:1;padding-top:6px"><ul class="b" style="font:400 19px/1.4 Inter">
    <li><b>identity → Google:</b> anti-corruption layer via the OAuthIdentityGateway port</li>
    <li><b>identity → commerce:</b> Customer/Supplier, sync HTTP through the UserAppService port (the one direct call)</li>
    <li><b>commerce → Stripe:</b> anti-corruption layer (Checkout and Refund gateways, webhook)</li>
    <li><b>commerce → notification:</b> Published Language, integration events V1 over SNS → SQS</li></ul>
    <div style="margin-top:14px;border-radius:8px;background:#E3E8FB;padding:14px 18px;font:400 17px/1.4 Inter">Four contexts share one service because they share transactions; the map shows where the network boundaries are allowed to be.</div></div></div>` });
  B.push({ id: 'B4', speaker: 'Eric', title: 'Aggregates and state machines', body: `<div style="display:flex;gap:30px">
   <div style="flex:1"><ul class="b" style="font:400 19px/1.4 Inter">
    <li><b>GroupDeal (team):</b> only OPEN may move, once</li><li><b>Order:</b> PENDING → CONFIRMED | CANCELLED</li>
    <li><b>PurchaseIntent</b> is the order-side aggregate (ADR 0001)</li><li>Also: CheckoutPayment, RefundWork, Product + TeamTerms, Notification</li>
    <li>Domain events: MemberJoined, TeamSucceeded, TeamExpired, PaymentSucceeded</li>
    <li>In-process subscribers run before commit (ADR 0002), so in the same transaction the outbox writer maps them to versioned integration events</li></ul>
    <div style="margin-top:8px;border-radius:8px;background:#FDEFEC;padding:12px 16px;font:400 16.5px/1.4 Inter"><b style="color:var(--orange-d)">Be ready to explain:</b> FAILED and CANCELED are allowed by the status matrix but have no trigger in code.</div></div>
   <div style="width:430px;flex:none">${svg('aggregates').replace('<svg ', '<svg style="width:430px;height:auto" ')}</div></div>` });
  B.push({ id: 'B5', speaker: 'Eric', title: 'Layers and enforced boundaries', body: tbl(['Layer', 'Module', 'Contains'], [
    ['Client', '<span class="mono">blocbuy-client</span>', 'DTOs, integration event contracts (TeamSucceededV1, TeamExpiredV1, PaymentSucceededV1)'],
    ['Adapter', '<span class="mono">blocbuy-adapter</span>', 'REST controllers, Result envelope, GlobalExceptionHandler'],
    ['Application', '<span class="mono">blocbuy-application</span>', 'Commands, queries, app services, outbox relay'],
    ['Domain', '<span class="mono">blocbuy-domain</span>', 'Aggregates, domain events, ports (framework-free)'],
    ['Infrastructure', '<span class="mono">blocbuy-infrastructure</span>', 'MyBatis, Stripe, Redis, SNS/SQS transports'],
    ['Assemblies', '<span class="mono">services/*-svc</span>', 'Thin Spring Boot entry points that select contexts'],
  ], { fs: 18, w: [150, 270] }) + `<div style="margin-top:18px;border-radius:8px;background:var(--inverse);color:#fff;padding:16px 22px;font:400 19px/1.4 Inter"><b style="color:#FFB01F">Boundaries are tested:</b> 18 ArchUnit rules, e.g. <span class="mono" style="font-size:16px">commerce_reaches_identity_only_through_user_port</span>, <span class="mono" style="font-size:16px">nothing_depends_on_notification</span></div>` });
  B.push({ id: 'B6', speaker: 'Yorik', title: 'UC4: join a team by paying', body: `<div class="dia">${svg('uc4-join-sequence').replace('<svg ', '<svg style="width:1164px;height:auto" ')}</div>
   <div class="cap" style="margin-top:8px">Signature verified outside the transaction; one transaction for dedupe, lock order, admit, order, stock and outbox; the active user is re-checked inside it.</div>` });
  B.push({ id: 'B7', speaker: 'Liu Jin', title: 'Technology and services', body: tbl(['Concern', 'Choice'], [
    ['Edge', 'CloudFront (persistent: OAuth redirect and Stripe webhook depend on its domain), S3 SPA bucket'],
    ['Compute', 'ECS Fargate ARM64, 0.5 vCPU / 1 GB, circuit breaker + rollback, Service Connect'],
    ['Scaling', 'commerce: ALB requests/target, min 2 / max 6, 60 s out / 180 s in · notification: SQS backlog per task (target 50), 1–4 · identity: fixed 1'],
    ['Data', 'RDS PostgreSQL 18 db.t4g.micro (TLS forced) · ElastiCache Valkey 8 cache.t4g.micro (TLS + AUTH)'],
    ['Messaging', 'SNS (KMS) → SQS (visibility 60 s) + DLQ (maxReceive 5), filter policy'],
    ['Secrets', 'SSM Parameter Store SecureString; DB passwords via write-only arguments (never in state)'],
    ['Observability', 'CloudWatch dashboard (Edge · Services · Messaging), DLQ > 0 and queue-age alarms, X-Trace-Id'],
    ['App stack', 'Java 21, Spring Boot 4, MyBatis, Sa-Token, Stripe · React 19, TanStack, HeroUI v3'],
  ], { fs: 17, w: [150] }) });
  B.push({ id: 'B8', speaker: 'Pan', title: 'Full findings list', body: tbl(['Finding', 'Fix / status'], [
    ['First promotion: 5 failed runs (digests, tfvars, SG tag deny, early 503)', '#405–#417; green 6 Oct'],
    ['Fargate read-only root FS crashed Tomcat on /tmp', 'VOLUME ["/tmp"]'],
    ['Tagging API returns already-deleted ARNs', 'Per-type describe checks in env-status'],
    ['Flyway default privileges exposed tables to all roles', 'afterMigrate grants + ServiceGrantsIT'],
    ['Outbox relay marked unsent rows failed on a mid-batch error', 'Caught in review (#485)'],
    ['Consumer flag never set on ECS → events unconsumed', '#512'],
    ['RegisterScalableTarget AccessDenied: default tags also need TagResource', '#527, scoped to Course=swe5001; in effect 17 Oct'],
    ['Fail-closed messaging gate stopped up twice: alarm email unconfirmed (#520, #528)', '#529 testing opt-out; green 17 Oct. Confirm emails, opt-out off (#515) ' + mk('FILL')],
    ['Interrupted bootstrap restores suspended scaling', 'Manual repair only ' + mk('FILL', 'fix status')],
    ['SCP denied CloudFront metrics (us-east-1)', '#530: SCPs consolidated, scoped exception'],
  ], { fs: 15.5, w: [640] }) });
  B.push({ id: 'B9', speaker: 'Pan', title: 'Infrastructure as code and actual bill', body: tbl(['Stack', 'Lifetime', 'Contents'], [
    ['foundation', 'persistent', 'state bucket, GitHub OIDC roles + permissions boundary, ECR, SSM'],
    ['edge', 'persistent', 'CloudFront + S3 SPA (origin switched to the new ALB on each up)'],
    ['runtime', 'disposable', 'VPC, ECS, ALB, RDS, Valkey, SNS/SQS, dashboard, alarms: 113–115 resources'],
  ], { fs: 17, w: [140, 140] }) + `<ul class="b" style="font:400 17.5px/1.38 Inter;margin-top:16px">
    <li><span class="mono" style="font-size:15px">env-up.sh / env-down.sh / env-status.sh</span> (absence check with per-type describe calls)</li>
    <li>Up ≈ 13–15 min (monolith), ≈ 32 min (distributed, 17 Oct ×2); down ≈ 9–13 min (115 resources in 709 s on 17 Oct)</li>
    <li>Golden-path <span class="mono" style="font-size:15px">ecs-service</span> module: a new service is one map entry</li>
    <li>Apply-role guardrails: IAM only under <span class="mono" style="font-size:15px">/swe5001/</span>, tag- and name-conditioned denies</li></ul>
   <div style="margin-top:6px;border-radius:8px;background:var(--inverse);color:#fff;padding:14px 20px;font:400 17.5px/1.4 Inter"><b style="color:#FFB01F">Actual AWS bill</b> (whole account, Cost Explorer): Sep US$78.51; 1–17 Oct ≈ US$65 (≈ US$4/day), mostly the always-on SWE5006 EC2 host + ALB. The SWE5001 runtime’s RDS + Valkey + ECS: <b>US$0.49</b> across 7 up/down sessions (15–17 Oct).</div>` });
  B.push({ id: 'B10', speaker: 'Eric', title: 'API access for producers and consumers', body: `
   <div style="display:flex;align-items:stretch;gap:10px;height:150px">
    ${[['users', 'Producers & consumers', 'web SPA, future partner clients', '#EFE9DF', 'var(--ink)'], ['shield-halved', 'CloudFront', 'the only way in; adds origin secret', 'var(--teal-l)', 'var(--teal-d)'], ['sitemap', 'ALB path rules', '/auth, /users, /notifications, /api/*; default 403', 'var(--teal-l)', 'var(--teal-d)'], ['cubes', 'Services', 'identity · commerce · notification', 'var(--orange-l)', 'var(--orange-d)']].map((c, i, a) => `<div style="flex:1;border-radius:8px;background:${c[3]};padding:14px 16px"><div style="font-size:24px;color:${c[4]}">${icon(c[0])}</div><div style="font:700 18px Inter;margin-top:6px">${c[1]}</div><div style="font:400 15.5px/1.3 Inter;color:var(--ink3)">${c[2]}</div></div>${i < a.length - 1 ? `<div style="display:flex;align-items:center;color:var(--muted)">${icon('arrow-right-long')}</div>` : ''}`).join('')}
   </div>
   <div style="display:flex;gap:18px;margin-top:16px">
    <div class="card" style="flex:1;padding:16px 20px"><div class="serif" style="font-size:22px;color:var(--teal-d);margin-bottom:8px">Built</div><ul class="b" style="font:400 16.5px/1.38 Inter">
     <li>Public contract <span class="mono" style="font-size:14px">contracts/openapi.json</span>, committed; TypeScript client generated, not committed; contract check on every PR</li>
     <li>Integration events versioned (…V1); consumers ignore unknown versions; producers can add V2 without a lock-step release</li>
     <li><span class="mono" style="font-size:14px">/internal/**</span> not routed by the ALB, peer security groups only, absent from the public contract (InternalPathsContractTest)</li></ul></div>
    <div style="flex:1;border-radius:8px;background:#FDEFEC;padding:16px 20px"><div class="serif" style="font-size:22px;color:var(--orange-d);margin-bottom:8px">Not built (be upfront)</div><ul class="b" style="font:400 16.5px/1.38 Inter">
     <li>API gateway, per-client keys, rate limiting</li><li>Service-to-service token or mTLS (AISS-04)</li>
     <li><b>Path:</b> API Gateway, or ALB + WAF rate rules behind the CloudFront origin; a service token on <span class="mono" style="font-size:14px">/internal/**</span></li></ul></div>
   </div>` });
  B.push({ id: 'B11', speaker: 'Lim', title: 'Analytics: what the platform measures', body: `
   <div style="display:flex;gap:18px">
    <div class="card" style="flex:1;padding:16px 20px"><div style="display:flex;gap:10px;align-items:center"><span style="color:var(--green);font-size:22px">${icon('chart-line')}</span><div class="serif" style="font-size:22px;color:var(--green)">Operational, in use</div></div>
     <ul class="b" style="font:400 16.5px/1.38 Inter;margin-top:10px"><li>CloudWatch dashboard <span class="mono" style="font-size:14px">blocbuy-swe5001</span>: edge, services, messaging</li><li>V2 load results set the 12,000 req/target/min scaling target</li><li>DLQ and queue-age alarms</li></ul></div>
    <div class="card" style="flex:1;padding:16px 20px"><div style="display:flex;gap:10px;align-items:center"><span style="color:var(--orange-d);font-size:22px">${icon('money-bill-trend-up')}</span><div class="serif" style="font-size:22px;color:var(--orange-d)">Business, in the data, not built</div></div>
     <ul class="b" style="font:400 16.5px/1.38 Inter;margin-top:10px"><li>Team fill rate and time-to-fill per product</li><li>Referral attribution from share invites (UC7) ${mk('VERIFY', 'data in biz_* tables?')}</li><li>Conversion from team start to success; refund rate by product</li></ul></div>
   </div>
   <div style="display:flex;gap:18px;margin-top:16px;align-items:stretch">
    <div style="flex:1.2;display:flex;align-items:center;gap:10px;border-radius:8px;background:#E3E8FB;padding:16px 18px">
     <div style="font:700 15px Inter;letter-spacing:.08em;color:var(--teal-d);width:84px">NEXT STEP</div>
     ${['Event bus', 'Analytics consumer', 'Reporting store'].map((t, i) => `<div style="flex:1;height:64px;border-radius:8px;background:${i === 0 ? 'var(--yellow)' : '#fff'};border:1.5px solid ${i === 0 ? 'var(--yellow)' : 'var(--teal)'};display:flex;align-items:center;justify-content:center;text-align:center;font:700 16px/1.2 Inter">${t}</div>${i < 2 ? `<span style="color:var(--teal-d)">${icon('arrow-right-long')}</span>` : ''}`).join('')}
    </div>
    <div style="flex:1;border:2px dashed #8C2414;border-radius:8px;background:#EFE9DF;padding:12px 16px;font:400 15px/1.35 Inter;color:#8C2414">${mk('FILL', 'dashboard crop')}<br>Crop of dashboard <b>blocbuy-swe5001</b>, all three rows, from the 19 Oct session.</div>
   </div>
   <div style="margin-top:12px;font:400 15.5px Inter;color:var(--muted)">A read-only consumer on the same bus, the same pattern as audit: no change to commerce.</div>` });
  const R = (n, t, r) => [n, t, r];
  B.push({ id: 'B12', speaker: 'Lim', title: 'Scale-out and resilience results', body: tbl(['Check', 'Target', 'Result'], [
    R('V3 HTTP auto-scale', '≥ 80% of linear from 1 → 4 tasks; p95 &lt; 800 ms outside 3 min after scale-out', mk('FILL')),
    R('V4 queue scaling', '2,000 TeamExpired → 4 tasks, drain, DLQ 0', mk('FILL') + ' local IT: 141 s end to end'),
    R('V5 self-heal', 'stop 1 of 2 tasks, 0 non-2xx, replacement &lt; 2 min', mk('FILL')),
    R('V7 poison message', '5 receives → DLQ → alarm', mk('FILL') + ' local: verified 15 Oct'),
    R('Identity outage', 'browse 200; checkout 500 → redelivered once', mk('FILL') + ' local: verified'),
    R('Distributed up + smoke', '3 services stable, 9 smoke checks', '17 Oct ×2: tasks 2/2 · 1/1 · 1/1; 9/9 checks; SNS→SQS verified with synthetic events. Buyer E2E (#460): ' + mk('FILL')),
    R('Teardown', 'all resources destroyed, absence check clean', '115 resources, 709 s (17 Oct); 113, 784 s (16 Oct)'),
  ], { fs: 16, w: [200, 380] }) + `<div style="margin-top:10px;font:italic 400 15px Inter;color:var(--muted)">Anything not run on AWS stays “not run on AWS; local result: …”. Never upgrade a local result to an AWS one.</div>` });
  B.push({ id: 'B13', speaker: 'Eric', title: 'Logical view, simplified', body: b13() });
  B.push({ id: 'B14', speaker: 'Liu Jin', title: 'Physical view, simplified', body: b14() });
  B.push({ id: 'B15', speaker: 'Yorik', title: 'Team expiry, numbered steps (fallback for slide 16)', body: b15() });
  return B;
}
module.exports = { list };
