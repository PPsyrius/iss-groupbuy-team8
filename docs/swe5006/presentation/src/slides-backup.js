const { icon, md, mk, svg } = require('../../../presentation-kit/lib');
const C = require('./content');

const tbl = (head, rows, opts = {}) => `<table class="t" style="font-size:${opts.fs || 16}px">
<tr>${head.map((h, i) => `<th style="${opts.w && opts.w[i] ? 'width:' + opts.w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map(r => `<tr>${r.map((c, i) => `<td class="${i === 0 && opts.id ? 'id' : ''}">${c}</td>`).join('')}</tr>`).join('')}</table>`;

// ---- B11: Sprint 2 and 3 burndowns (segment 1) ----
function lineChart({ xs, series, max, step, W = 560, H = 250 }) {
  const L = 44, R = W - 12, T = 12, B = H - 30;
  const X = i => L + 14 + i * ((R - L - 24) / (xs.length - 1)), Y = v => B - (v / max) * (B - T);
  const ticks = []; for (let v = 0; v <= max; v += step) ticks.push(v);
  const pl = (d, c, dash) => `<polyline points="${d.map((v, i) => v == null ? '' : `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => v == null ? '' : `<circle cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${ticks.map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14.5" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 22}" font-size="14.5" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${series.map(s => pl(s.d, s.c, s.dash)).join('')}</svg>`;
}
const key = (c, t, dash) => `<span><i style="display:inline-block;width:22px;${dash ? `height:0;border-top:3px dashed ${c}` : `height:3px;background:${c}`};vertical-align:4px"></i> ${t}</span>`;
const b11 = () => `
<div style="position:absolute;left:0;top:0;width:560px">
 <div class="serif" style="font-size:21px">Sprint 2 · remaining hours, 15–26 Sep</div>
 <div style="font:400 15.5px Inter;margin:2px 0 6px">150 h planned · 16 h left · reconstructed from merge dates</div>
 ${lineChart({ xs: ['start', '15', '16', '17', '18', '19', '22', '23', '24', '25', '26'], max: 150, step: 50, series: [
   { d: [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0], c: '#A69E92', dash: true },
   { d: [150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16], c: '#3E5CD9' }] })}
 <div style="display:flex;gap:22px;font:400 15px Inter;margin-left:44px">${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Actual remaining')}</div>
 <div class="card" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter">Flat for a week, then a cliff: work merged late in very large pull requests. Retro answer: PRs under 1,500 lines and hours on the board daily.</div>
 <div style="font:italic 400 15px Inter;margin-top:8px">Source: docs/sprint2-progress-report.typ, “Daily burndown data”</div>
</div>
<div style="position:absolute;left:604px;top:0;width:560px">
 <div class="serif" style="font-size:21px">Sprint 3 · open sprint cards, 29 Sep–9 Oct</div>
 <div style="font:400 15.5px Inter;margin:2px 0 6px">21 cards · 150 h planned · 33 h left at freeze (review)</div>
 ${lineChart({ xs: ['29', '30', '1', '2', '5', '6', '7', '8', '9'], max: 21, step: 7, series: [
   { d: [21, 18.4, 15.8, 13.1, 10.5, 7.9, 5.3, 2.6, 0], c: '#A69E92', dash: true },
   { d: [19, 16, 13, 12, 7, 7, 7, 7, 8], c: '#3E5CD9' }] })}
 <div style="display:flex;gap:22px;font:400 15px Inter;margin-left:44px">${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Cards not Done (daily snapshot)')}</div>
 <div class="card" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter">Steadier burn, then stuck at 7 from 5 Oct: #223, #224 and #229 waited for Checkout on dev, #226 for the NFR-37 decision. Hours come from the review.</div>
 <div style="font:italic 400 15px Inter;margin-top:8px">Source: docs/agile/metrics/*.csv, sprint-03/review.md</div>
</div>`;

// ---- B1–B9: segment 2, analysis and design ----
const dt = (head, rows, { fs = 15.5, w = [], pad = '8px 12px' } = {}) => `<table class="t" style="font-size:${fs}px"><tr>${head.map((h, i) => `<th style="font-size:15px;padding:${pad}${w[i] ? ';width:' + w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map(r => `<tr>${r.map(c => `<td style="padding:${pad}">${c}</td>`).join('')}</tr>`).join('')}</table>`;
const mo = (t, sz = 14.5) => `<span class="mono" style="font-size:${sz}px">${t}</span>`;
const topo = t => `<div class="cap" style="margin-top:6px;font-size:16px">${t}</div>`;
const fit = (name, w) => svg(name).replace('<svg ', `<svg style="width:${w}px;height:auto" `);
const b1 = () => `<div style="display:flex;gap:30px;align-items:flex-start">
 <div style="width:436px;flex:none">${fit('context-map', 436)}</div>
 <div style="flex:1;padding-top:4px"><ul class="b" style="font:400 18px/1.4 Inter">
  <li style="margin-bottom:10px"><b>identity → Google:</b> anti-corruption layer, ${mo('OAuthIdentityGateway')} port, ${mo('GoogleOAuthGateway')} adapter</li>
  <li style="margin-bottom:10px"><b>identity → commerce:</b> Customer/Supplier through the ${mo('UserAppService')} port; on ECS the one synchronous HTTP call, on dev an in-process call</li>
  <li style="margin-bottom:10px"><b>commerce → Stripe:</b> anti-corruption layer (Checkout and Refund gateways, signed webhook)</li>
  <li><b>commerce → notification:</b> Published Language, ${mo('TeamSucceededV1')}, ${mo('TeamExpiredV1')}, ${mo('PaymentSucceededV1')} over SNS → SQS</li></ul>
  <div style="margin-top:14px;border-radius:8px;background:#E3E8FB;padding:12px 16px;font:400 16.5px/1.4 Inter">Drawn for the <b>ECS topology</b>. On dev the same contexts run in one monolith: the identity call is in-process, and no events leave commerce (no broker).</div></div></div>`;
const b2 = () => `<div style="display:flex;gap:30px">
 <div style="flex:1"><ul class="b" style="font:400 18px/1.4 Inter">
  <li style="margin-bottom:8px"><b>GroupDeal</b> (team): only OPEN may move, once; ${mo('DealStatus.canTransitionTo')} is the one transition matrix (ADR 0002)</li>
  <li style="margin-bottom:8px"><b>Order:</b> PENDING → CONFIRMED or CANCELLED</li>
  <li style="margin-bottom:8px"><b>PurchaseIntent</b> is the order-side aggregate (ADR 0001)</li>
  <li style="margin-bottom:8px"><b>Product</b> + ${mo('ProductStatus')} (State, slide 8); also CheckoutPayment, RefundWork, Notification</li>
  <li><b>Domain events:</b> MemberJoined, TeamSucceeded, TeamExpired, PaymentSucceeded; subscribers run before commit (ADR 0002), the share credit after commit (ADR 0003)</li></ul>
  <div style="margin-top:12px;border-radius:8px;background:#FDEFEC;padding:12px 16px;font:400 16.5px/1.4 Inter"><b style="color:var(--orange-d)">Be ready to explain:</b> FAILED and CANCELED are allowed by the matrix but have no trigger in code. GroupDeal uses a matrix, not the State pattern.</div></div>
 <div style="width:420px;flex:none">${fit('aggregates', 420)}</div></div>`;
const b3 = () => `<div class="dia" style="margin-top:-8px">${fit('uc4-join-sequence', 1164)}</div>${topo('As built for ECS. On dev the active-user check is in-process and there is no broker, so no SNS → SQS or notification steps.')}`;
const b4 = () => `<div class="dia" style="margin-top:-6px">${fit('expiry-refund-sequence', 1140)}</div>${topo('As built for ECS. The SNS → SQS hop and notification-svc (steps 2–8) are the ECS topology; on dev the monolith runs no broker.')}`;
const b9 = () => `<div class="dia" style="margin-top:-8px">${fit('logical-view', 1120)}</div>${topo('SWE5001 prod on ECS: three services assembled from the same modules (backend ADR 0008). SWE5006 dev runs all of it as one monolith.')}`;
const b5 = () => dt(['Context', 'Tables', 'Migrations', 'Keys that make “once” hold'], [
  ['<b>identity</b>', mo('sys_user, sys_user_oauth, sys_role, sys_permission, rel_user_role, rel_role_permission'), 'V3, V24', 'one binding per Google subject'],
  ['<b>catalog</b>', mo('biz_product, biz_team_price_policy, biz_team_price_quote, log_product_team_terms'), 'V4, V6, V14, V20, V21', 'biz_product.version (Optimistic Offline Lock)'],
  ['<b>groupbuy</b>', mo('biz_group_deal, biz_group_member, biz_group_participation_receipt, biz_share_invite, biz_share_credit'), 'V5, V7, V18', 'one member per user and team; one receipt per payment'],
  ['<b>order</b>', mo('biz_order, biz_purchase_intent'), 'V12, V15, V19, V22', 'intent fulfilled by one payment'],
  ['<b>payment</b>', mo('biz_payment, log_payment_webhook_event, biz_refund, log_payment_review, biz_payment_review_case, log_refund_attempt'), 'V2, V9–V11, V16', 'webhook event_id; one refund per payment'],
  ['<b>notification</b>', mo('biz_notification, sys_consumed_message'), 'V27, V28', 'PK (event_id, consumer); (event_id, user_id)'],
  ['<b>jobs, outbox</b>', mo('sys_outbox, sys_scheduler_lock, sys_scheduled_item_retry, sys_checkout_expiry_cursor'), 'V13, V17, V25, V26', 'one lease per job; outbox id = eventId'],
], { fs: 15, w: [130, 520, 120], pad: '6px 10px' }) + `<div style="margin-top:8px;font:400 15.5px/1.4 Inter">28 Flyway files, 29 tables in schema <span class="mono" style="font-size:14.5px">domain</span>; V1 sets the rules: snake_case, timestamptz, numeric money, invariants in the database. On ECS (ADR 0008): one database per service on one RDS instance; identity-svc and notification-svc own their rows above, commerce-svc the rest.</div>`;
const b6 = () => `<div style="display:flex;gap:22px">
 <div style="width:640px;flex:none">${dt(['Candidate', 'Decision', 'Why'], [
  ['Mark refunded, then call Stripe', '<b>Rejected</b>', 'A crash would report money returned that never left'],
  ['Hold a DB transaction across the call', '<b>Rejected</b>', 'A rollback cannot undo a refund; locks held over network I/O'],
  ['Idempotency Key per obligation', '<b style="color:var(--orange-d)">Selected</b>', 'Every retry reuses one request identity'],
  ['Retry after a delay', '<b style="color:var(--orange-d)">Selected</b>', 'Fixed 30 s in code, not exponential backoff'],
  ['Human-in-the-loop review', '<b style="color:var(--orange-d)">Selected</b>', 'Mismatched or ambiguous work leaves automatic claims'],
  ['Distributed transaction with Stripe', '<b>Rejected</b>', 'No shared protocol across PostgreSQL and the provider'],
 ], { fs: 15.5, w: [230, 92] })}</div>
 <div style="flex:1"><div class="serif" style="font-size:20px;margin-bottom:8px">Implementation decisions</div><ol style="font:400 16px/1.35 Inter;padding-left:22px;margin:0">
  <li style="margin-bottom:6px">Obligation commits with the expiry, before any I/O</li>
  <li style="margin-bottom:6px">Key from the obligation ID: ${mo('"blocbuy-refund-" + id')}</li>
  <li style="margin-bottom:6px">Short claim and finish transactions; lease and version fence workers</li>
  <li style="margin-bottom:6px">Verify payment facts before, provider snapshot after</li>
  <li style="margin-bottom:6px">After 23 h: search Stripe by metadata, never create</li>
  <li>Reconcile only retrieves an existing refund; no admin endpoint exists</li></ol>
  <div style="margin-top:10px;font:italic 400 15px/1.35 Inter">Source: docs/swe5006/report-drafts/yorick-yu-contribution.md. Evidence is local with a provider substitute.</div></div></div>`;
const b7 = () => `<div style="font:400 17px/1.4 Inter;margin-bottom:10px"><b>Rule:</b> bounded context → package in each module · analysis entity → aggregate + repository port · external actor → gateway port + adapter (anti-corruption layer) · use case → application service</div>
${dt(['Member · use case', 'Analysis class', 'Design class or port', 'Pattern'], [
  ['Liu Jin · UC9', 'Listing, its status', `${mo('Product')}, ${mo('ProductStatus')}, ${mo('ProductRepository')} → ${mo('ProductRepositoryImpl')}`, 'State, Optimistic Offline Lock'],
  ['Eric · UC3/UC4', 'Team, Membership, Purchase intent', `${mo('GroupDeal')}, ${mo('PaidAdmission')}, ${mo('PurchaseIntent')}, ${mo('PaidGroupDealRepository')}; payment settles through ${mo('PaymentBusinessPort')} ← ${mo('OrderFulfilmentAdapter')}`, 'Factory Method'],
  ['Yorik · UC6', 'Refund obligation, Payment provider', `${mo('biz_refund')} + ${mo('RefundWork')}, ${mo('RefundRepository')}; ${mo('RefundGateway')} ← ${mo('StripeRefundGateway')}`, 'Idempotency Key, Observer'],
  ['Pan · notifications', 'Notice, commerce event', `${mo('Notification')}, ${mo('NotificationRepository')}; ${mo('TeamExpiredV1')}; ${mo('NotificationEventHandler')} ← ${mo('SqsNotificationEventConsumer')}`, 'Observer, Outbox, Idempotent Receiver'],
  ['Lim · UC8', 'My blocs, My orders', `${mo('GET /group-deals/me')}, ${mo('GET /orders/me')} → route ${mo('/account')}, ${mo('myTeamsQuery')}, ${mo('accountSearchSchema')}`, 'URL as view state, Adapter'],
  ['(identity)', 'Google account', `${mo('OAuthIdentityGateway')} ← ${mo('GoogleOAuthGateway')}`, 'Adapter (ACL)'],
], { fs: 15.5, w: [150, 190, 560] })}
<div style="margin-top:8px;font:italic 400 15px Inter">Sources: report drafts (Yorik, Eric's unmerged PR #542), backend design docs, code checked 19 Oct. ← means “implemented by”.</div>`;
const b8 = () => `<div style="display:flex;gap:18px">
 <div style="width:520px;flex:none"><div class="serif" style="font-size:19px;margin-bottom:6px">Backend · backend/docs/adr</div>${dt(['#', 'Decision'], [
  ['0001', 'Purchase intent lives in order; group terms on the product'],
  ['0002', 'Team resolution is published; consequences run before commit'],
  ['0003', 'Referral credit is a post-commit subscriber'],
  ['0004', 'Retire the unused legacy PaymentIntent path'],
  ['0005', 'Gate Stripe Checkout with a deployment property'],
  ['0006', 'Payment owns the admin attention read model'],
  ['0007', 'Outbox, one SNS topic, one SQS queue per consumer (Proposed)'],
  ['0008', 'One codebase, two topologies: dev monolith, three services on ECS'],
 ], { fs: 15, w: [56], pad: '6px 10px' })}</div>
 <div style="flex:1"><div class="serif" style="font-size:19px;margin-bottom:6px">Frontend · frontend/docs/adr</div>${dt(['#', 'Decision'], [
  ['0001', 'React SPA, pure CSR; Router owns URL state, Query server state'],
  ['0002', 'Same-origin /api with an HttpOnly cookie session'],
  ['0003', 'Generated API client, not committed'],
  ['0004', 'One build artifact promoted dev to prod'],
  ['0005', 'SPA fallback by a CloudFront Function'],
  ['0006', 'oxlint and oxfmt toolchain'],
  ['0007', 'Hand-managed dev CloudFront, then Terraform import'],
  ['0008', 'Documentation and evidence conventions'],
  ['0009', 'Result envelope: one unwrapResult as select'],
  ['0010', 'React Compiler via the Babel preset'],
  ['0011', 'Testing strategy: Vitest, MSW, Playwright smoke'],
  ['0012', 'Agent configuration in AGENTS.md'],
  ['0013', 'pnpm and Node pinned, exact versions'],
  ['0014', 'Stripe Hosted Checkout with a server-owned intent'],
  ['0015', 'Admin console shell, roles guard, versioned writes'],
 ], { fs: 14.5, w: [56], pad: '4px 10px' })}</div></div>`;

// Backup slides follow the last main slide; press B in the deck for the index (BX). Rows link with data-go.
// Each segment adds its backups to BACKUPS (content.js) and a matching entry below, in index order.
function list() {
  const B = [];
  B.push({ id: 'BX', title: 'Backup slides: index', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px 22px;margin-top:4px">
 ${C.BACKUPS.map(b => `<div data-go="${b[0]}" style="cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:8px 14px">
   <span class="tag" style="min-width:44px;text-align:center">${b[0]}</span><div><div style="font:600 17px Inter">${b[1]}</div><div style="font:400 14.5px Inter;color:var(--muted)">${b[2]}</div></div>
 </div>`).join('')}
</div><div style="margin-top:12px;font:italic 400 15px Inter;color:var(--muted)">Click a row (or type its number in the URL as #B6). Slide 22 stays up during Q&amp;A; open a backup only when a question needs it.</div>` });
  B.push({ id: 'B1', title: 'Context map', speaker: 'Eric', body: b1() });
  B.push({ id: 'B2', title: 'Aggregates and state machines', speaker: 'Eric', body: b2() });
  B.push({ id: 'B3', title: 'UC4 join sequence', speaker: 'Eric', body: b3() });
  B.push({ id: 'B4', title: 'Expiry and refund sequence', speaker: 'Yorik', body: b4() });
  B.push({ id: 'B5', title: 'Database schema', speaker: 'Liu Jin', body: b5() });
  B.push({ id: 'B6', title: 'UC6 candidates and implementation decisions', speaker: 'Yorik', body: b6() });
  B.push({ id: 'B7', title: 'Transition from analysis to design', speaker: 'Eric', body: b7() });
  B.push({ id: 'B8', title: 'ADR index', speaker: 'Eric', body: b8() });
  B.push({ id: 'B9', title: 'Logical view (ECS topology)', speaker: 'Eric', body: b9() });
  B.push({ id: 'B11', title: 'Sprint 2 and 3 burndowns', speaker: 'Lim', body: b11() });
  return B;
}

module.exports = { list, tbl };
