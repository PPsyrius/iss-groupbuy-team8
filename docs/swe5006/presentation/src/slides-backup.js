const { icon, md, mk, svg } = require('../../../presentation-kit/lib');
const C = require('./content');

// opts.m (optional): [table, first row, step] in ms, as dt() below.
const tbl = (head, rows, opts = {}) => `<table class="t${opts.m ? ' a" data-a="fade' : ''}" style="font-size:${opts.fs || 16}px${opts.m ? `;--d:${opts.m[0]}ms` : ''}">
<tr>${head.map((h, i) => `<th style="${opts.w && opts.w[i] ? 'width:' + opts.w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map((r, j) => `<tr${opts.m ? ` class="trow" style="--d:${Math.round(opts.m[1] + opts.m[2] * j)}ms"` : ''}>${r.map((c, i) => `<td class="${i === 0 && opts.id ? 'id' : ''}">${c}</td>`).join('')}</tr>`).join('')}</table>`;

// ---- B11: Sprint 2 and 3 burndowns (segment 1) ----
// m (ms, optional): Line chart motion. The chart fades in at m, a dashed series fades at m + 100, a solid series draws on
// (cline) at m + 200 and its dots pop (cdot) from m + 300, 50 ms apart.
function lineChart({ xs, series, max, step, W = 560, H = 250, m }) {
  const L = 44, R = W - 12, T = 12, B = H - 30;
  const X = i => L + 14 + i * ((R - L - 24) / (xs.length - 1)), Y = v => B - (v / max) * (B - T);
  const ticks = []; for (let v = 0; v <= max; v += step) ticks.push(v);
  const mv = (cls, d) => m === undefined ? '' : cls === 'a' ? ` class="a" data-a="fade" style="--d:${d}ms"` : ` class="${cls}" style="--d:${d}ms"${cls === 'cline' ? ' pathLength="1"' : ''}`;
  const pl = (d, c, dash) => `<polyline${mv(dash ? 'a' : 'cline', m + (dash ? 100 : 200))} points="${d.map((v, i) => v == null ? '' : `${X(i)},${Y(v)}`).join(' ')}" fill="none" stroke="${c}" stroke-width="3"${dash ? ' stroke-dasharray="7 5"' : ''}/>` + (dash ? '' : d.map((v, i) => v == null ? '' : `<circle${mv('cdot', m + 300 + 50 * i)} cx="${X(i)}" cy="${Y(v)}" r="4.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`).join(''));
  return `<svg${mv('a', m)} width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="Inter">
   ${ticks.map(v => `<line x1="${L}" x2="${R}" y1="${Y(v)}" y2="${Y(v)}" stroke="${v ? '#EFE9DF' : '#A69E92'}" stroke-width="1"/><text x="${L - 8}" y="${Y(v) + 5}" font-size="14.5" fill="#6B655C" text-anchor="end">${v}</text>`).join('')}
   ${xs.map((x, i) => `<text x="${X(i)}" y="${B + 22}" font-size="14.5" fill="#6B655C" text-anchor="middle">${x}</text>`).join('')}
   ${series.map(s => pl(s.d, s.c, s.dash)).join('')}</svg>`;
}
const key = (c, t, dash) => `<span><i style="display:inline-block;width:22px;${dash ? `height:0;border-top:3px dashed ${c}` : `height:3px;background:${c}`};vertical-align:4px"></i> ${t}</span>`;
// Twin burndown charts (MOTION.md), columns at 150 and 250: heading, subtitle +50, chart +150 (ideal +250, actual
// +350, dots from +450), legend +550, comment card +1000, source +1150.
const b11 = () => `
<div style="position:absolute;left:0;top:0;width:560px">
 <div class="serif a" data-a="up" style="font-size:21px;--d:150ms">Sprint 2 · remaining hours, 15–26 Sep</div>
 <div ${A('up', 200, 'font:400 15.5px Inter;margin:2px 0 6px;')}>150 h planned · 16 h left · reconstructed from merge dates</div>
 ${lineChart({ xs: ['start', '15', '16', '17', '18', '19', '22', '23', '24', '25', '26'], m: 300, max: 150, step: 50, series: [
   { d: [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0], c: '#A69E92', dash: true },
   { d: [150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16], c: '#3E5CD9' }] })}
 <div ${A('up', 700, 'display:flex;gap:22px;font:400 15px Inter;margin-left:44px;')}>${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Actual remaining')}</div>
 <div class="card a" data-a="up" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter;--d:1150ms">Flat for a week, then a cliff: no committed story merged before 22 Sep; week-1 merges were carry-over and enabling work. Retro answer: PRs under 1,500 lines.</div>
 <div ${A('up', 1300, 'font:italic 400 15px Inter;margin-top:8px;')}>Source: docs/sprint2-progress-report.typ, “Daily burndown data”</div>
</div>
<div style="position:absolute;left:604px;top:0;width:560px">
 <div class="serif a" data-a="up" style="font-size:21px;--d:250ms">Sprint 3 · remaining hours, 28 Sep–9 Oct</div>
 <div ${A('up', 300, 'font:400 15.5px Inter;margin:2px 0 6px;')}>150 h planned · 66 h left, whole stories · 33 h with partial credit (review)</div>
 ${lineChart({ xs: ['start', '28', '29', '30', '1', '2', '5', '6', '7', '8', '9'], m: 400, max: 150, step: 50, series: [
   { d: [150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0], c: '#A69E92', dash: true },
   { d: [150, 132, 114, 90, 82, 76, 68, 68, 68, 66, 66], c: '#3E5CD9' }] })}
 <div ${A('up', 800, 'display:flex;gap:22px;font:400 15px Inter;margin-left:44px;')}>${key('#A69E92', 'Ideal', true)}${key('#3E5CD9', 'Actual remaining')}</div>
 <div class="card a" data-a="up" style="margin-top:14px;padding:12px 16px;font:400 16px/1.35 Inter;--d:1250ms">On the ideal line for the first week, then stuck from 5 Oct: #223 (Checkout on dev) stayed open, #224 had local evidence only, #229 waited on #223 and #222, #226 on the NFR-37 decision. Daily card counts agree: 19, 16, 13, 12, then 7.</div>
 <div ${A('up', 1400, 'font:italic 400 15px Inter;margin-top:8px;')}>Source: sprint-03/review.md hours, burned on issue close dates (SGT); docs/agile/metrics/*.csv</div>
</div>`;

// ---- B1: agile artefacts appendix (rubric 1.8) ----
const b1 = () => dt(['Artefact', 'Where it lives', 'State on 21 Oct'], [
  ['<b>Product backlog</b>', 'GitHub Issues and Project board 2 (GroupBuy Kanban Board); issue templates in .github/ISSUE_TEMPLATE', '170 items: 137 Done, 15 Ready, 15 Backlog, 3 In progress'],
  ['<b>User stories with acceptance criteria</b>', 'UC issues #24–#32; story issues (e.g. #221, #223, #180); AC per story in docs/agile/sprint-03/plan.md §5', 'template: complexity, accountable members, design problem, AC'],
  ['<b>Sprint plans</b>', 'S1 #49; S2 Sprint 2 plan (docx) and #93; S3 docs/agile/sprint-03/plan.md and #232; S4 docs/agile/sprint-04/plan.md and #465', 'all four sprints'],
  ['<b>Burndown charts</b>', 'S2 chart in docs/sprint2-progress-report.typ; daily data docs/agile/metrics/*.csv (board-snapshot.yml, since 17 Sep)', 'S2, S3 reconstructed (B2); S4 from snapshots'],
  ['<b>Sprint reviews</b>', 'S1, S2 in the progress reports; S3 docs/agile/sprint-03/review.md', 'S4 after the talk, with its remarks (#619)'],
  ['<b>Retrospectives</b>', 'S1 report; S2 report §Sprint Retrospective; S3 docs/agile/sprint-03/retro.md', 'S3 reconstructed 24 Oct · S4 after the talk (#619)'],
  ['<b>Day logs, DoD, practices</b>', 'docs/agile/sprint-0N/log/ (S2–S4); DoD per sprint: docs/agile/definition-of-done.md; EPAT practices: docs/agile/practices.md', 'dated logs per working day'],
], { fs: 15.5, w: [230, 560], pad: '7px 10px', m: quick(7) }) + `<div class="a" data-a="up" style="--d:${after(7)}ms;margin-top:10px;font:italic 400 15.5px Inter;color:var(--muted)">Source: board and issues read with gh on 21 Oct; docs/agile/README.md. Report Appendix A holds the full set (#463 plan, #681).</div>`;

// ---- B3–B6: segment 2, analysis and design ----
// Motion (MOTION.md): `m` turns on the table choreography, true = the v3 rhythm (table fade 246 ms, body rows
// trow from 368 ms, +38.4 each), or [table, first row, step] for a table that enters later.
const dt = (head, rows, { fs = 15.5, w = [], pad = '8px 12px', m } = {}) => {
  const [td, rd, rs] = m === true ? [246, 368, 38.4] : m || [];
  return `<table class="t${m ? ' a" data-a="fade' : ''}" style="font-size:${fs}px${m ? `;--d:${td}ms` : ''}"><tr>${head.map((h, i) => `<th style="font-size:15px;padding:${pad}${w[i] ? ';width:' + w[i] + 'px' : ''}">${h}</th>`).join('')}</tr>
${rows.map((r, j) => `<tr${m ? ` class="trow" style="--d:${Math.round(rd + rs * j)}ms"` : ''}>${r.map(c => `<td style="padding:${pad}">${c}</td>`).join('')}</tr>`).join('')}</table>`;
};
// entrance attributes for an element without its own class or style: A('up', 300, 'margin-top:8px;')
const A = (a, d, st = '') => `class="a" data-a="${a}" style="${st}--d:${d}ms"`;
// Table with rows, quick (MOTION.md): a backup settles by about 700 ms, so the table fades in at t, its n rows enter
// from r, at most 300 ms apart in all (38 ms each at most); after(n) is the delay of a closing line 80 ms after them.
const quick = (n, t = 150, r = 200) => [t, r, Math.min(38, 300 / (n - 1))];
const after = (n, r = 200) => Math.round(r + quick(n)[2] * (n - 1) + 80);
const mo = (t, sz = 14.5) => `<span class="mono" style="font-size:${sz}px">${t}</span>`;
const topo = (t, d) => `<div class="cap${d ? ' a" data-a="up' : ''}" style="margin-top:6px;font-size:16px${d ? `;--d:${d}ms` : ''}">${t}</div>`;
const fit = (name, w) => svg(name).replace('<svg ', `<svg style="width:${w}px;height:auto" `);
// Diagram slide (MOTION.md): the drawing fades in at 150, its caption rises at 450.
const b4 = () => `<div class="dia a" data-a="fade" style="margin-top:-6px;--d:150ms">${fit('expiry-refund-sequence', 1140)}</div>${topo('As built for ECS. The SNS → SQS hop and notification-svc (steps 2–8) are the ECS topology; on dev the monolith runs no broker.', 450)}`;
const b5 = () => dt(['Context', 'Tables', 'Migrations', 'Keys that make “once” hold'], [
  ['<b>identity</b>', mo('sys_user, sys_user_oauth, sys_role, sys_permission, rel_user_role, rel_role_permission'), 'V3, V24', 'one binding per Google subject'],
  ['<b>catalog</b>', mo('biz_product, biz_team_price_policy, biz_team_price_quote, log_product_team_terms'), 'V4, V6, V14, V20, V21', 'biz_product.version (Optimistic Offline Lock)'],
  ['<b>groupbuy</b>', mo('biz_group_deal, biz_group_member, biz_group_participation_receipt, biz_share_invite, biz_share_credit'), 'V5, V7, V18', 'one member per user and team; one receipt per payment'],
  ['<b>order</b>', mo('biz_order, biz_purchase_intent'), 'V12, V15, V19, V22', 'intent fulfilled by one payment'],
  ['<b>payment</b>', mo('biz_payment, log_payment_webhook_event, biz_refund, log_payment_review, biz_payment_review_case, log_refund_attempt'), 'V2, V9–V11, V16', 'webhook event_id; one refund per payment'],
  ['<b>notification</b>', mo('biz_notification, sys_consumed_message'), 'V27, V28', 'PK (event_id, consumer); (event_id, user_id)'],
  ['<b>jobs, outbox</b>', mo('sys_outbox, sys_scheduler_lock, sys_scheduled_item_retry, sys_checkout_expiry_cursor'), 'V13, V17, V25, V26', 'one lease per job; outbox id = eventId'],
], { fs: 15, w: [130, 520, 120], pad: '6px 10px', m: quick(7) }) + `<div class="a" data-a="up" style="--d:${after(7)}ms;margin-top:8px;font:400 15.5px/1.4 Inter">28 Flyway files, 29 tables in schema <span class="mono" style="font-size:14.5px">domain</span>; V1 sets the rules: snake_case, timestamptz, numeric money, invariants in the database. On ECS (ADR 0008): one database per service on one RDS instance; identity-svc and notification-svc own their rows above, commerce-svc the rest.</div>`;
const b6 = () => `<div style="display:flex;gap:22px">
 <div style="width:640px;flex:none">${dt(['Candidate', 'Decision', 'Why'], [
  ['Mark refunded, then call Stripe', '<b>Rejected</b>', 'A crash would report money returned that never left'],
  ['Hold a DB transaction across the call', '<b>Rejected</b>', 'A rollback cannot undo a refund; locks held over network I/O'],
  ['Idempotency Key per obligation', '<b style="color:var(--orange-d)">Selected</b>', 'Every retry reuses one request identity'],
  ['Retry after a delay', '<b style="color:var(--orange-d)">Selected</b>', 'Fixed 30 s in code, not exponential backoff'],
  ['Human-in-the-loop review', '<b style="color:var(--orange-d)">Selected</b>', 'Mismatched or ambiguous work leaves automatic claims'],
  ['Distributed transaction with Stripe', '<b>Rejected</b>', 'No shared protocol across PostgreSQL and the provider'],
 ], { fs: 15.5, w: [230, 92], m: quick(6) })}</div>
 <div style="flex:1"><div class="serif a" data-a="up" style="font-size:20px;margin-bottom:8px;--d:300ms">Implementation decisions</div><ol style="font:400 16px/1.35 Inter;padding-left:22px;margin:0">
  <li ${A('up', 330, 'margin-bottom:6px;')}>Obligation commits with the expiry, before any I/O</li>
  <li ${A('up', 370, 'margin-bottom:6px;')}>Key from the obligation ID: ${mo('"blocbuy-refund-" + id')}</li>
  <li ${A('up', 410, 'margin-bottom:6px;')}>Short claim and finish transactions; lease and version fence workers</li>
  <li ${A('up', 450, 'margin-bottom:6px;')}>Verify payment facts before, provider snapshot after</li>
  <li ${A('up', 490, 'margin-bottom:6px;')}>After 23 h: search Stripe by metadata, never create</li>
  <li ${A('up', 530)}>Reconcile only retrieves an existing refund; no admin endpoint exists</li></ol>
  <div ${A('up', 610, 'margin-top:10px;font:italic 400 15px/1.35 Inter;')}>Source: docs/swe5006/report-drafts/yorick-yu-contribution.md. Evidence is local with a provider substitute.</div></div></div>`;
const b8 = () => `<div style="display:flex;gap:18px">
 <div style="width:520px;flex:none"><div class="serif a" data-a="up" style="font-size:19px;margin-bottom:6px;--d:120ms">Backend · backend/docs/adr</div>${dt(['#', 'Decision'], [
  ['0001', 'Purchase intent lives in order; group terms on the product'],
  ['0002', 'Team resolution is published; consequences run before commit'],
  ['0003', 'Referral credit is a post-commit subscriber'],
  ['0004', 'Retire the unused legacy PaymentIntent path'],
  ['0005', 'Gate Stripe Checkout with a deployment property'],
  ['0006', 'Payment owns the admin attention read model'],
  ['0007', 'Outbox, one SNS topic, one SQS queue per consumer (Proposed)'],
  ['0008', 'One codebase, two topologies: dev monolith, three services on ECS'],
 ], { fs: 15, w: [56], pad: '6px 10px', m: quick(8) })}</div>
 <div style="flex:1"><div class="serif a" data-a="up" style="font-size:19px;margin-bottom:6px;--d:230ms">Frontend · frontend/docs/adr</div>${dt(['#', 'Decision'], [
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
 ], { fs: 14.5, w: [56], pad: '4px 10px', m: quick(15, 260, 300) })}</div></div>`;

// ---- B10: test inventory (segment 3), counted in the repository on 22 Oct at a1c6ac6e ----
const b10 = () => dt(['Kind · level', 'Tools', 'Count by module', 'Example classes', 'Runs'], [
  ['<b>Unit</b> · unit', 'JUnit Jupiter, Mockito', '185 *Test classes (162 unit; 23 Spring/Testcontainers), 1,329 annotations: domain 42, application 41, infrastructure 47, bootstrap 46, adapter 4, platform 2, commerce-svc 2, client 1 (some, like StripeCheckoutFlowTest, use real PostgreSQL)', `${mo('RefundServiceTest')}, ${mo('PaidGroupDealTest')}, ${mo('DealStatusTest')}, ${mo('ProductStatusTest')}`, 'every PR (build-quality, required)'],
  ['<b>Integration</b> · integration', 'Testcontainers: PostgreSQL 18, Redis 8.10, ElasticMQ', '49 *IT classes (45 start containers), 242 annotations: bootstrap 24, infrastructure 15, services 9, platform 1', `${mo('OrderRepositoryIT')}, ${mo('TeamExpirySchedulingIT')}, ${mo('StripeRolloutAcceptanceIT')} (acceptance)`, 'every PR (build-quality, required)'],
  ['<b>Architecture</b> · unit', 'ArchUnit', '17 rules in 1 class', `${mo('ArchitectureTest')}`, 'every PR (build-quality, required)'],
  ['<b>API contract</b> · integration', 'springdoc export, diff of contracts/, openapi-diff', '1 contract file', `${mo('contracts/openapi.json')}`, 'backend PRs (contract-check, not required)'],
  ['<b>Component</b> · unit', 'Vitest, Testing Library, MSW', '77 files: features 58, routes 10, components 4, test 3, api 2', `${mo('account.test.tsx')}, ${mo('admin-listing-edit.test.tsx')}`, 'every PR (frontend-check, required)'],
  ['<b>End-to-end</b> · system', 'Playwright, Chromium', '8 specs, 24 test declarations', `${mo('buyer-journey.spec.ts')}, ${mo('join-refusal.spec.ts')}, ${mo('smoke.spec.ts')}`, 'PR (e2e-preview); after deploy (e2e-dev)'],
  ['<b>Performance</b> · system', 'k6 1.6.1 (SWE5001 ECS); Node workload (local)', 'k6 browse V2/V3; 3 local scenarios', `${mo('perf/k6/browse.js')}, ${mo('backend/perf/workload.mjs')}`, 'by hand (k6 tooling tested in CI)'],
], { fs: 14.5, w: [112, 190, 300, 330], pad: '5px 10px', m: quick(7) }) + `<div class="a" data-a="up" style="--d:${after(7)}ms;margin-top:8px;font:400 15px/1.35 Inter">Counted 22 Oct at a1c6ac6e: 186 <span class="mono" style="font-size:14.5px">*Test.java</span> (185 + ArchitectureTest), 49 <span class="mono" style="font-size:14.5px">*IT.java</span> (45 start containers). Latest full runs: backend 2,048 passed, 1 opt-in skip (21 Oct log, local); frontend 799 (19 Oct log).</div>`;

// ---- B12–B14: segment 4, CI/CD. gh read-only queries of 19 Oct; medians of successful runs ----
const req = '<b style="color:#3E5CD9">required</b>';
const b12 = () => dt(['Workflow · trigger', 'Jobs (required in blue)', 'Median', 'Green'], [
  ['<b>ci-backend</b> · PR', `${req}: build-quality (13.1 min), docker (2.8 min), SonarCloud Backend; contract-check (12 s)`, '16.1 min to docker end (18 runs, 16–19 Oct)', '18 of 20 finished runs; 16 cancelled by a newer push'],
  ['<b>ci-backend</b> · push to dev', 'package-dev 46 s → publish-ecr-dev 73 s → deploy-existing-ec2 40 s; SonarCloud Backend refreshes the dashboard', '3.1 min to deployed (88 runs)', 'deploy 88 of 97 in this sample; its 9 failures 14–17 Sep (SSM: instance not in a valid state)'],
  ['<b>ci-frontend</b> · PR', `${req}: frontend-check (3.4 min), SonarCloud Frontend; e2e-preview (69 s), intent-stale`, '4.8 min to e2e-preview end (19 runs)', '19 of 20 finished runs'],
  ['<b>ci-frontend</b> · push to dev', 'package-dev 35 s → deploy-s3-dev 21 s → e2e-dev 82 s; SonarCloud Frontend', '2.7 min to e2e-dev green (94 runs)', 'deploy 97 of 97; e2e-dev 94 of 97 (3–4 Oct)'],
  ['<b>security</b> · PR, dev, weekly', `${req}: gitleaks (10 s); trivy-deps (72 s), trivy-config, workflow-lint, deployment-bundle when their paths change`, '12 s on most PRs', 'PR 97 of 98 finished; dev push 100 of 100'],
  ['<b>dast-baseline</b> · weekly, manual, scan PRs', 'zap-baseline: passive OWASP ZAP of anonymous dev pages; findings advisory, coverage or scanner errors fail it', '—', 'PR runs 22 Oct green; dev dispatch 24 Oct green (added after this read)'],
  ['<b>sbom</b> · tag, weekly, manual, run-sbom label', 'sbom: CycloneDX source SBOMs and licence inventories; licences advisory, a missing inventory fails it', '—', 'PR runs 22 Oct green on earlier PR heads (added after this read)'],
  ['<b>rollback-backend</b> · manual', 'validate, then SSM rollback to the recorded previous digest or a given one', '—', 'never run'],
  ['<b>SWE5001</b> · prod branch', 'swe5001-release-gate, swe5001-deploy, infra-swe5001 (promotion PR; see the SWE5001 deck)', '—', '—'],
], { fs: 15, w: [190, 470, 220], pad: '6px 10px', m: quick(7) }) + `<div class="a" data-a="up" style="--d:${after(7)}ms;margin-top:8px;font:400 15px/1.4 Inter">Last 100 runs per workflow and event, read with <span class="mono" style="font-size:14.5px">gh run list</span> and the jobs API on 19 Oct. PR durations count only runs where the gated jobs ran (docs-only PRs skip them in about 10 s). Push medians run from the push run start, not the merge click.</div>`;
const b13 = () => `<div class="dia a" data-a="fade" style="margin-top:-8px;--d:150ms">${fit('physical-deployment', 1130)}</div>${topo('SWE5001 prod: the same code on ECS, rebuilt from scratch on every release and destroyed after it. SWE5006 dev is slide 26.', 450)}`;
const b14 = () => dt(['Gap', 'Effect', 'Status'], [
  ['CloudFront → ALB over HTTP', '/api payloads and the origin header cross the AWS network unencrypted', 'NFR-37 TLS still unmet; accepted risk on dev until 16 Nov (#227, NFR register); fix needs an origin hostname and ACM'],
  ['SSH open to the internet', 'port 22 from 0.0.0.0/0 on the dev host, which has a public IP; SSM sessions make it unnecessary', 'AWS read-only check, 21 Oct; open item 9'],
  ['Dev EBS volume not encrypted', 'database and host data at rest unencrypted', 'AWS read-only check, 19 Oct; not raised as an issue yet'],
  ['Rollback never exercised', 'recovery path untested on the real host', 'rollback-backend.yml: 0 runs; no automatic rollback has fired'],
  ['No dev alarms', 'failures are noticed by e2e-dev or by people', 'all six CloudWatch alarms belong to SWE5001'],
  ['Dev not zero-downtime', 'a deploy restarts the only API container (502 on 3–4 Oct)', 'accepted for dev'],
  ['Secrets in a host file', 'manual creation and rotation, root-only 0600', 'accepted for dev; prod uses Parameter Store'],
  ['Delivery image rebuilt', 'dev delivery rebuilds the backend JAR and image without repeating the Trivy image gate, so the deployed image is not the PR-scanned one; SonarCloud, deploy health checks and the frontend smoke still run', 'artifact-assurance limit; ECR scans on push (not a gate); ADR 0004 text to reconcile (open item 18)'],
  ['Weekly full-history gitleaks red', '20 digest false positives fingerprinted; 4 badge-token findings still fail the scan', 'PR #580, ci-failure-audit-20261021.md'],
  ['Passive DAST; SBOM not per image', 'OWASP ZAP of dev is anonymous and passive: no logins, payments or active attacks; advisory, 2 Medium, 4 Low CSP and header findings (22 Oct), fixes in #610; the CycloneDX SBOMs list the backend Maven and frontend pnpm source dependencies, not the exact deployed JAR, image or bundle; licence findings advisory', '#607, #610, #609'],
], { fs: 15, w: [250, 450], pad: '5px 10px', m: quick(11) });

// ---- B15–B16: segment 5, system demo ----
// B15 embeds two screenshots from the 20 Sep local Sandbox acceptance (JPEG data despite the .png names).
const fs = require('fs');
const path = require('path');
const EVID = path.join(__dirname, '../../../../backend/docs/payment/evidence/20260920');
// d (optional): the screenshot and its caption rise at d ms.
const shot = (file, cap, d) => `<div ${d === undefined ? '' : `class="a" data-a="up" `}style="flex:1${d === undefined ? '' : `;--d:${d}ms`}"><div style="height:226px;overflow:hidden;border:1px solid #DDD5C8;border-radius:8px;background:#F2F2F2"><img src="data:image/jpeg;base64,${fs.readFileSync(path.join(EVID, file)).toString('base64')}" style="width:100%;display:block"></div>
 <div style="font:400 15px/1.3 Inter;color:var(--muted);margin-top:5px">${cap}</div></div>`;
const b15 = () => `<div style="display:flex;gap:20px">
 ${shot('frontend-refunded-order.png', 'My orders: the expired bloc’s order is refunded (UI of 20 Sep, since redesigned)', 150)}
 ${shot('frontend-refund-receipt.png', 'Receipt: “Refund sent”, payment 01a0bce8…, SGD 10.00', 230)}
</div>
<div style="margin-top:12px">${dt(['Scenario', 'Result on 20 Sep (Team8 Stripe Sandbox, livemode=false)'], [
  ['START and JOIN', 'two SGD 10 payments → one member and one order each; the bloc reached succeeded, 2 of 2'],
  ['Webhook replay', 'replayed valid events changed nothing; a modified payload with the old signature got 400'],
  ['Expiry', 'an under-filled bloc expired → one SGD 10 refund obligation; its unpaid Checkout session expired'],
  ['Refund', `one full Sandbox refund ${mo('re_3UHbeV5iEVLN2uQJ1V71krwQ')}, succeeded; a recheck created no duplicate`],
], { fs: 15, w: [170], pad: '5px 10px', m: quick(4, 300, 350) })}</div>
<div class="a" data-a="up" style="--d:${after(4, 350)}ms;margin-top:10px;border-radius:8px;background:#FDEFEC;padding:9px 16px;font:400 15.5px/1.35 Inter"><b style="color:var(--orange-d)">Local, not dev:</b> real Stripe Sandbox calls from a local backend and PostgreSQL; no Google sign-in, no deployed environment. On dev the refund waits for #223 and #517. Source: ${mo('backend/docs/payment/stripe-sandbox-acceptance-20260920.md')}</div>`;

// d (optional): the card rises at d ms (Icon card).
const rCard = (head, env, inner, tone = '#3E5CD9', d) => `<div class="card${d === undefined ? '' : ' a" data-a="up'}" style="flex:1;padding:14px 16px;display:flex;flex-direction:column${d === undefined ? '' : `;--d:${d}ms`}">
 <div style="font:600 13px Inter;letter-spacing:.04em;color:${tone}">${env}</div>
 <div class="serif" style="font-size:21px;margin:4px 0 8px">${head}</div>${inner}</div>`;
const li = t => `<li style="margin-bottom:6px">${t}</li>`;
const b16 = () => `<div style="display:flex;gap:14px;align-items:stretch">
 ${rCard('Scale-out', 'SWE5001 PROD · ECS FARGATE', `<ul class="b" style="font:400 15.5px/1.35 Inter;margin:0">
  ${li('Target tracking on ALB requests per target: 12,000 a minute, scale-out cooldown 60 s')}
  ${li('commerce 2–6 tasks in two AZs, notification 1–4 (read back 16 Oct)')}
  ${li('V3, 19 Oct: commerce <b>1 → 3 tasks</b> in about four minutes, no manual scaling; at 3 tasks ≈ 455 req/s, server p95 13–50 ms, 2 errors in 570,445')}
  ${li('Not V3 acceptance: the workstation generator dropped iterations, so the analyzer rejects the run')}
  ${li('V4 queue drain: not run on AWS; still open in #458')}</ul>`, undefined, 150)}
 ${rCard('Load, one task', 'SWE5001 V2 · ECS, 16 OCT', `<ul class="b" style="font:400 15.5px/1.35 Inter;margin:0">
  ${li('k6, ten one-minute plateaus from 10 to 200 requests a second, through CloudFront')}
  ${li('57,600 requests, <b>0 errors</b>, run twice')}
  ${li('p95 between 23 and 57 ms on every plateau (target under 500 ms)')}
  ${li('No saturation reached: 200 a second is a tested lower bound, not a limit')}</ul>`, undefined, 230)}
 ${rCard('Workload baseline', 'DEV CODE · LOCAL RUN, 30 SEP', `${dt(['100 users each', 'p95', 'Errors'], [
  ['Browse (list + detail)', '24 ms', '0'], ['JOIN intent, unpaid', '57 ms', '0'], ['My Orders', '18 ms', '0'],
 ], { fs: 15, pad: '4px 8px' })}<div style="font:400 15px/1.35 Inter;margin-top:8px">30 paid teams expired within 58 s of their deadlines under that load. One laptop with real HTTP, PostgreSQL and Redis; <b>not a deployed result</b>.</div>`, 'var(--orange-d)', 310)}
</div>
<div ${A('up', 560, 'margin-top:14px;border-radius:8px;background:var(--inverse);color:#fff;padding:11px 18px;font:400 16px/1.35 Inter;')}><b style="color:#FFB01F">Dev, where the demo runs:</b> one EC2 host by design (backend ADR 0008), so no auto-scaling there. Same code, two topologies: scale-out exists only in the SWE5001 topology and is not claimed for SWE5006 (slide 32).</div>
<div ${A('up', 680, 'margin-top:8px;font:italic 400 14.5px/1.35 Inter;color:var(--muted);')}>Sources: docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md (V2, scaling policy); docs/swe5001/evidence/20261019-v3-autoscaling/README.md (V3); docs/perf/sprint3-baseline.md (local workload).</div>`;

// ---- §2 full diagrams (rubric G8, V1, V6): vector, so zoom in the browser or the PDF ----
const sized = (name, w, h) => svg(name).replace('<svg ', `<svg style="display:block;width:${w}px;height:${h}px" `);
const scroll = (name, W, H, w) => `<div data-scroll class="a" data-a="fade" style="--d:150ms;position:absolute;left:0;top:0;width:1164px;height:568px;overflow:auto;background:#fff;border-radius:6px;box-shadow:0 0 0 1.5px #DDD5C8">${sized(name, w, Math.round(H * w / W))}</div>`;
const bLs = () => `
<div ${A('fade', 150, 'position:absolute;left:0;top:0;')}>${sized('listing-statechart', 1160, 548)}</div>`;
const bUc4 = () => `
<div style="position:absolute;left:0;top:0;width:1164px;height:568px;display:flex;gap:16px;align-items:flex-start">
 <div ${A('fade', 150)}>${sized('uc4-design-class', 500, 499)}<div style="font:400 14.5px Inter;color:var(--muted)">Design class diagram (2320 × 2314)</div></div>
 <div ${A('fade', 260)}>${sized('uc4-design-seq', 348, 496)}<div style="font:400 14.5px Inter;color:var(--muted)">Design sequence (2320 × 3306)</div></div>
 <div ${A('up', 450, 'width:220px;font:400 16px/1.4 Inter;')}>Both are vector: open <span class="mono" style="font-size:14px">src/diagrams/uc4-design-*.svg</span> in a browser tab and zoom. Every message carries its source line in the diagram spec. Report §3.4.1 (Eric).</div>
</div>`;
const bMembers = () => tbl(['Member', 'Use case (report section)', 'Design problem (report section)', 'On a slide'], [
  ['Eric', '§3.4.1 UC3, UC4 start and join a team, pay upfront', '§3.5.1 Factory Method: team creation', 'UC4 models 13–15, statechart B18; pattern B21'],
  ['Yorik', '§3.4.2 UC6 refund on team expiry', '§3.5.2 Observer: team outcome; refund idempotency as a mechanism (B6)', 'pattern 16; sequence B5'],
  ['Pan', '§3.4.3 UC1 browse listings, UC2 register and sign in', '§3.5.3 Template Method: outbox transports', 'backup B19'],
  ['Lim', '§3.4.4 UC8 my orders and my teams, UC7 share attribution', '§3.5.4 Strategy: UC1 pricing', 'backup B20'],
  ['Liu Jin', '§3.4.5 UC9 listing administration (first version by Eric, PR #72), UC5 team lifecycle with the team statechart', '§3.5.5 State: listing lifecycle (full form)', 'patterns 17–18; statechart B13'],
], { fs: 16.5, w: [110, 330, 400], m: quick(5) }) + `<div class="a" data-a="up" style="--d:${after(5)}ms;margin-top:12px;font:400 16px/1.4 Inter">Each member’s analysis class, analysis sequence, design class and design sequence diagrams are in report §3.4.1–§3.4.5 (template RT 3.4); the deck shows UC4 only (rubric G8). Owners: #463, decided 24 Oct; notifications are SWE5001 scope.</div>`;
const b16erd = () => scroll('full-erd', 2320, 5038, 1150);
// B16 prints only its first viewport, so the PDF also carries the ERD 1:1 (labels 24 px, rubric V3): each context
// frame of full-erd.svg cut into 1164 × 568 windows, left to right then top to bottom, as pages B16-1 … B16-n.
const ERD_FRAMES = [['identity', 12, 80, 2296, 844], ['catalog', 12, 974, 2296, 486], ['groupbuy + share', 12, 1510, 2296, 1016],
  ['payment', 12, 2576, 2296, 1172], ['order', 12, 3798, 1136, 648], ['notification', 1172, 3798, 1136, 418],
  ['integration · scheduling', 12, 4496, 2296, 482]];
const steps = (at, len, view, max) => {
  if (len <= view) return [Math.max(0, Math.min(max - view, Math.round(at - (view - len) / 2)))];
  const n = Math.ceil(len / view);
  return Array.from({ length: n }, (_, i) => Math.round(at + i * (len - view) / (n - 1)));
};
const erdWin = (x, y) => svg('full-erd').replace(/<svg\b[^>]*>/, t => t.replace(/\s(width|height|viewBox)="[^"]*"/g, '')
  .replace('<svg', `<svg width="1164" height="568" viewBox="${x} ${y} 1164 568" style="display:block"`));
const erdPages = () => ERD_FRAMES.flatMap(([ctx, fx, fy, fw, fh]) => {
  const xs = steps(fx, fw, 1164, 2320), ys = steps(fy, fh, 568, 5038);
  return ys.flatMap((y, r) => xs.map((x, c) => ({ ctx, part: [xs.length > 1 ? ['left', 'right'][c] : '', ys.length > 1 ? `part ${r + 1}/${ys.length}` : ''].filter(Boolean).join(', '), x, y })));
}).map((w, i, all) => ({ id: `B16-${i + 1}`, title: `ERD 1:1 · ${w.ctx}${w.part ? ` (${w.part})` : ''} · ${i + 1}/${all.length}`,
  body: `<div style="position:absolute;left:0;top:0;width:1164px;height:568px;background:#fff;overflow:hidden;border-radius:6px;box-shadow:0 0 0 2px #1B1A18">${erdWin(w.x, w.y)}</div>` }));

// ---- FX, F1…: every unique diagram at full size (review round 5) ----
// Presenting, the diagram is 1:1 (labels 24 px, as in the crops) in a viewport that runs to the slide's left, right and
// bottom edges and scrolls both ways; in the PDF, screenshots and the overview it is scaled to fit that viewport.
const FULL = [
  ['logical-layers', 'Logical view: COLA layers and packages', 9], ['transition-pair', 'Transition strategies: the UC4 pair', 'B17'],
  ['use-case', 'Use case diagram', 12], ['uc4-analysis-class', 'UC4 analysis class diagram', 13], ['uc4-analysis-seq', 'UC4 analysis sequence', 13],
  ['uc4-design-class', 'UC4 design class diagram', 14], ['uc4-design-seq', 'UC4 design sequence, paid join by webhook', 15],
  ['team-statechart', 'Team-formation statechart', 'B18'], ['fm-start', 'Factory Method: start a team', 'B21'], ['observer-expiry', 'Observer: team outcome', 16],
  ['template-method-outbox', 'Template Method: outbox transports', 'B19'], ['strategy-pricing', 'Strategy: pricing', 'B20'],
  ['state-before', 'Listing lifecycle before State (brute force)', 17], ['state-after', 'Listing lifecycle after State', 18],
  ['core-erd', 'Core schema', 19], ['cicd-pr-vs-merge', 'CI/CD in detail: every step, trigger and path filter', 24], ['dev-deployment', 'Dev deployment', 26],
  ['expiry-refund-sequence', 'Expiry and refund sequence (ECS)', 'B5'], ['physical-deployment', 'SWE5001 production topology', 'B10'],
  ['listing-statechart', 'UC9 listing statechart', 'B13'], ['full-erd', 'Entity-relationship diagram, all tables', 'B16'],
];
const viewBox = name => svg(name).match(/viewBox="[\d.]+ [\d.]+ ([\d.]+) ([\d.]+)"/).slice(1).map(Number);
const FV_PAD = 24, FV_H = 720 - 108 - 2; // viewport inner height: body top 108, 2 px top border
const FV_CSS = `<style>.fv{position:absolute;left:-58px;right:-58px;top:0;bottom:-44px;z-index:5;background:#fff;border-top:2px solid #DDD5C8;overflow:hidden}
.fv>div{box-sizing:content-box;padding:${FV_PAD}px;width:max-content;margin:0 auto}.fv svg{display:block}
.fvbar{position:absolute;right:0;top:-54px;display:none;gap:8px;align-items:center;z-index:6}
.fvbar button{display:inline-flex;align-items:center;gap:6px;height:34px;padding:0 12px;border:1px solid #DDD5C8;border-radius:8px;background:#fff;color:#1B1A18;font:600 15px Inter;cursor:pointer}
.fvbar [data-pct]{min-width:52px;text-align:right;font:600 15px Inter;color:#6B655C}
@media screen{body.present .fv{overflow:auto}body.present .fvbar{display:flex}}</style>`;
const FV_JS = `<script>
window.fvZoom = function (id, W, H) {
  var v = document.getElementById(id), w = v.firstElementChild, g = w.querySelector('svg'), P = ${FV_PAD}, s = 1;
  var bar = document.getElementById(id + '-bar'), pct = bar.querySelector('[data-pct]'), slide = v.closest('.slide');
  function fit() { return Math.min((v.clientWidth - 2 * P) / W, (v.clientHeight - 2 * P) / H); }
  // the view centre as a fraction of the padded wrapper (its offset includes the margin that centres it)
  function centre(pos, view, off, size) { return size > view ? Math.max(0, Math.min(1, (pos + view / 2 - off) / size)) : 0.5; }
  function set(k, keep) {
    var cx = centre(v.scrollLeft, v.clientWidth, w.offsetLeft, w.offsetWidth), cy = centre(v.scrollTop, v.clientHeight, w.offsetTop, w.offsetHeight);
    s = Math.max(0.1, Math.min(4, k));
    g.style.width = Math.round(W * s) + 'px'; g.style.height = Math.round(H * s) + 'px';
    pct.textContent = Math.round(s * 100) + '%';
    v.scrollLeft = keep ? w.offsetLeft + cx * w.offsetWidth - v.clientWidth / 2 : 0;
    v.scrollTop = keep ? w.offsetTop + cy * w.offsetHeight - v.clientHeight / 2 : 0;
  }
  bar.addEventListener('click', function (e) {
    var b = e.target.closest('button'); if (!b) return;
    var a = b.dataset.z;
    if (a === 'fit') set(fit()); else if (a === '1') set(1, true); else set(a === '+' ? s * 1.25 : s / 1.25, true);
    b.blur();
  });
  if (v.clientWidth) set(fit());
  // re-fit each time the slide opens or presenting resumes on it (after the overview), so it starts whole
  var shown = false;
  new MutationObserver(function () {
    var on = slide.classList.contains('active') && document.body.classList.contains('present');
    if (on && !shown) set(fit());
    shown = on;
  }).observe(document.body, { attributes: true, attributeFilter: ['class'], subtree: true });
};
</script>`;
const fvBtn = (z, ic, label, tip) => `<button data-z="${z}" title="${tip}">${icon(ic)}${label ? `<span>${label}</span>` : ''}</button>`;
// Each F page opens fitted to its viewport (padded, centred when narrower); the toolbar on the title row switches to
// actual size or zooms in steps around the view centre. The PDF, screenshots and the overview show the fit without
// the toolbar. The bar is data-scroll and its controls are buttons, so the runtime never treats a press as a step.
const fullView = (name, i) => {
  const [W, H] = viewBox(name), id = `fv${i}`, k = Math.min((1280 - 2 * FV_PAD) / W, (FV_H - 2 * FV_PAD) / H);
  return `<div id="${id}-bar" class="fvbar a" data-a="fade" style="--d:60ms" data-scroll>
 ${fvBtn('fit', 'compress', 'Fit', 'Fit the whole diagram')}${fvBtn('1', 'up-right-and-down-left-from-center', 'Actual size', 'Actual size (1:1)')}${fvBtn('-', 'magnifying-glass-minus', '', 'Zoom out')}${fvBtn('+', 'magnifying-glass-plus', '', 'Zoom in')}
 <span data-pct>${Math.round(k * 100)}%</span>
</div>
<div id="${id}" class="fv a" data-a="fade" style="--d:120ms" data-scroll><div>${svg(name).replace('<svg ', `<svg style="width:${Math.round(W * k)}px;height:${Math.round(H * k)}px" `)}</div></div>
<script>if (window.fvZoom) fvZoom('${id}', ${W}, ${H});</script>`;
};
const where = n => (typeof n === 'number' ? `slide ${n}` : n);
// Index grid (MOTION.md): rows of three from 100, +30 per row; the note closes 80 ms after the last row.
const gridD = i => 100 + 30 * Math.floor(i / 3), noteD = n => gridD(n - 1) + 80;
const menu = (rows, note) => `<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px 16px">${rows.join('')}</div><div ${A('up', noteD(rows.length), 'margin-top:10px;font:italic 400 15px Inter;color:var(--muted);')}>${note}</div>`;
const menuRow = (go, tag, title, sub, d) => `<div data-go="${go}" ${A('up', d, 'min-width:0;cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:5px 14px;')}>
   <span class="tag a" data-a="pop" style="min-width:44px;text-align:center;--d:${d}ms">${tag}</span><div ${A('up', d)}><div style="font:600 17px Inter">${title}</div><div style="font:400 15px/1.25 Inter;color:var(--muted)">${sub}</div></div></div>`;
const fx = () => menu(FULL.map(([, t, n], i) => menuRow(`F${i + 1}`, `F${i + 1}`, t, where(n), gridD(i))),
  'Click a diagram. It opens fitted; the toolbar switches to actual size or zooms; scroll with the trackpad or mouse wheel (arrow keys still change slides). Press B for the backup index.');
// DX: the 1:1 crops, one row per slide, one chip per crop. In the HTML deck they repeat the crops the slides step
// through; the PDF, which cannot step, carries them only here (rubric V3).
const dx = details => {
  const by = [];
  for (const d of details) { const g = by.find(x => x.n === d.n); if (g) g.d.push(d); else by.push({ n: d.n, t: d.slideTitle, d: [d] }); }
  return `<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px 16px">${by.map((g, i) => `<div ${A('up', gridD(i), 'min-width:0;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:5px 12px;')}>
   <div style="font:600 15px/1.25 Inter;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Slide ${g.n} · ${g.t}</div>
   <div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:4px">${g.d.map(d => `<span data-go="${d.id}" class="tag a" data-a="pop" style="cursor:pointer;--d:${gridD(i)}ms">${d.id}</span>`).join('')}</div></div>`).join('')}</div>
<div ${A('up', noteD(by.length), 'margin-top:8px;font:italic 400 15px Inter;color:var(--muted);')}>Each page is one crop at 1:1 with its caption: the same view the slide steps to while presenting.</div>`;
};

// Backup slides follow the last main slide; press B in the deck for the index (BX). Rows link with data-go.
// Each segment adds its backups to BACKUPS (content.js) and a matching entry below, in index order.
function list() {
  const B = [];
  B.push({ id: 'BX', title: 'Backup slides: index', body: `
<div style="display:grid;grid-template-columns:1fr 1fr;gap:3px 22px;margin-top:0">
 ${C.BACKUPS.map((b, i) => { const d = 100 + 30 * Math.floor(i / 2); return `<div data-go="${b[0]}" ${A('up', d, 'cursor:pointer;display:flex;gap:14px;align-items:center;background:#fff;border:1px solid #DDD5C8;border-radius:8px;padding:2px 14px;')}>
   <span class="tag a" data-a="pop" style="min-width:44px;text-align:center;--d:${d}ms">${b[0]}</span><div ${A('up', d)}><div style="font:600 16px/1.15 Inter">${b[1]}</div><div style="font:400 14px/1.15 Inter;color:var(--muted)">${b[2]}</div></div>
 </div>`; }).join('')}
</div><div ${A('up', 100 + 30 * Math.ceil(C.BACKUPS.length / 2) + 50, 'margin-top:8px;font:italic 400 15px Inter;color:var(--muted);')}>Click a row (or type #B6 or #D14-2 in the URL). Slide 34 stays up during questions.</div>` });
  B.push({ id: 'B1', title: 'Agile artefacts appendix', speaker: 'Lim', body: b1() });
  B.push({ id: 'B2', title: 'Sprint 2 and 3 burndowns', speaker: 'Lim', body: b11() });
  B.push({ id: 'B3', title: 'ADR index', speaker: 'Eric', body: b8() });
  B.push({ id: 'B4', title: 'Database schema, all tables', speaker: 'Liu Jin', body: b5() });
  B.push({ id: 'B5', title: 'Expiry and refund sequence', speaker: 'Yorik', body: b4() });
  B.push({ id: 'B6', title: 'UC6 refund recovery: candidates and implementation decisions', speaker: 'Yorik', body: b6() });
  B.push({ id: 'B7', title: 'Test inventory', speaker: 'Liu Jin', body: b10() });
  B.push({ id: 'B8', title: 'Workflows, required checks and timings', speaker: 'Pan', body: b12() });
  B.push({ id: 'B9', title: 'Known gaps and limitations', speaker: 'Pan', body: b14() });
  B.push({ id: 'B10', title: 'SWE5001 production topology', speaker: 'Pan', body: b13() });
  B.push({ id: 'B11', title: 'Stripe Sandbox refund, local evidence (20 Sep)', speaker: 'Yorik', body: b15() });
  B.push({ id: 'B12', title: 'Scale-out and load results (SWE5001 ECS + local baseline)', speaker: 'Yorik', body: b16() });
  B.push({ id: 'B13', title: 'UC9 listing statechart', speaker: 'Liu Jin', body: bLs() });
  B.push({ id: 'B14', title: 'UC4 design class and sequence diagrams, complete', speaker: 'Eric', body: bUc4() });
  B.push({ id: 'B15', title: 'Every member’s use case and design problem', speaker: 'Eric', body: bMembers() });
  const erd = erdPages();
  B.push({ id: 'B16', title: `Entity-relationship diagram, all 29 tables (scroll; 1:1 pages B16-1 to B16-${erd.length} follow)`, speaker: 'Liu Jin', body: b16erd() });
  for (const e of erd) B.push({ ...e, speaker: 'Liu Jin' });
  const moved = require('./slides-main').moved;
  B.push({ id: 'B17', title: 'One strategy pair: UC4 analysis objects become design classes', speaker: 'Eric', body: moved.B17() });
  B.push({ id: 'B18', title: 'Team-formation statechart', speaker: 'Eric', body: moved.B18() });
  B.push({ id: 'B19', title: 'Notifications: Template Method', speaker: 'Pan', body: moved.B19() });
  B.push({ id: 'B20', title: 'UC1 pricing: Strategy', speaker: 'Lim', body: moved.B20() });
  B.push({ id: 'B21', title: 'UC3 start a team: Factory Method', speaker: 'Eric', body: moved.B21() });
  // D pages: each 1:1 crop the talk steps through, so the PDF carries readable diagrams (rubric V3).
  const details = require('./slides-main').details();
  B.push({ id: 'DX', title: 'Diagram details, 1:1: one page per crop', body: dx(details) });
  for (const d of details) {
    B.push({ id: d.id, title: `Slide ${d.n} · detail ${d.k}/${d.of}: ${d.label}`, speaker: d.speaker, body: d.body });
  }
  B.push({ id: 'FX', title: 'Diagrams at full size', body: fx() });
  FULL.forEach(([name, t], i) => B.push({ id: `F${i + 1}`, title: t, body: (i ? '' : FV_CSS + FV_JS) + fullView(name, i + 1) }));
  return B;
}

module.exports = { list, tbl };
