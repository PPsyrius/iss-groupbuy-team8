// SWE5006 Team 8 — Project Progress Report, Sprint 2
// Build: typst compile sprint2-progress-report.typ ../SWE5006-Team8-Project-Progress-Report-Sprint2.pdf

#let accent = rgb("#0a6fa8")
#let muted = rgb("#5f6b78")
#let hdr = rgb("#dde8f2")
#let ok = rgb("#1e7b3a")
#let part = rgb("#a86a00")
#let bad = rgb("#b3261e")

#let done = text(fill: ok, weight: "bold")[Completed]
#let partial = text(fill: part, weight: "bold")[Partially completed]
#let notdone = text(fill: bad, weight: "bold")[Not completed]
#let resolved = text(fill: ok, weight: "bold")[Resolved]
#let open = text(fill: bad, weight: "bold")[Open]
#let partres = text(fill: part, weight: "bold")[Partially resolved]
#let late = text(fill: part, weight: "bold")[Delayed]

#set document(title: "Project Progress Report for BlocBuy — Sprint 2", author: "SWE5006 Team 8")
#set page(
  paper: "a4",
  margin: (x: 2.2cm, top: 2.3cm, bottom: 2.3cm),
  footer: context {
    if counter(page).get().first() > 1 {
      set text(8pt, fill: muted)
      [BlocBuy — Sprint 2 Progress Report #h(1fr) Page #counter(page).display()]
    }
  },
)
#set text(font: ("Arial", "Helvetica"), size: 10.5pt, lang: "en")
#set par(justify: true, leading: 0.62em, spacing: 0.95em)
#set heading(numbering: "1.1")
#show heading.where(level: 1): it => {
  v(0.5em)
  set text(15pt, fill: accent)
  it
  v(0.25em)
}
#show heading.where(level: 2): it => {
  v(0.35em)
  set text(12pt, fill: accent)
  it
  v(0.1em)
}
#show heading.where(level: 3): it => {
  v(0.25em)
  set text(10.5pt)
  it
}
#show raw: set text(font: ("Menlo", "Courier New"), size: 0.88em)
#set list(indent: 0.6em, spacing: 0.6em)
#set enum(indent: 0.6em, spacing: 0.6em)

#set table(
  stroke: 0.5pt + rgb("#a7b4c2"),
  inset: (x: 5pt, y: 4.5pt),
  fill: (x, y) => if y == 0 { hdr },
  align: left + horizon,
)
#show table.cell.where(y: 0): set text(weight: "bold")
#show table: set text(8.8pt)
#show table: set par(justify: false, leading: 0.5em)
#show figure: set block(breakable: true)
#show figure.where(kind: table): set figure.caption(position: bottom)
#show figure.caption: set text(9pt, fill: muted, style: "italic")

// ---------------------------------------------------------------- Title page
#page(footer: none)[
  #v(1.2cm)
  #align(center, image("nus-iss-logo.png", width: 7.5cm))
  #v(2.2cm)
  #align(center)[
    #text(26pt, weight: "bold", fill: accent)[Project Progress Report for BlocBuy]
    #v(0.3cm)
    #text(15pt)[Sprint 2]
    #v(0.9cm)
    #text(12pt)[Practice Module for Certificate in Designing Modern Software Systems]
    #v(0.2cm)
    #text(12pt)[SWE5006 — Team 8]
    #v(0.2cm)
    #text(11pt, fill: muted)[Reporting period: 15 September 2026 to 26 September 2026]
  ]
  #v(2cm)
  #align(center)[
    #text(12pt, weight: "bold")[Members]
    #v(0.2cm)
    #set text(11pt)
    Lim Jiechao (limjiechao\@u.nus.edu) \
    Liu Jin (losgai\@u.nus.edu) \
    Panpakorn Siripanich (Pan) (panpakorn.s\@u.nus.edu) \
    Wang Ziang (Eric) (ziangw\@u.nus.edu) \
    Yu Qianshuo (Yorik) (qianshuoyu\@u.nus.edu)
  ]
]

#counter(page).update(2)
#outline(title: [Contents], depth: 3, indent: 1.2em)
#pagebreak()

// ---------------------------------------------------------------- 1
= Introduction

== Project Name and Description
BlocBuy is a community group-buying platform developed by NUS-ISS Team 8 for the SWE5006 Practice Module. It helps buyers discover products, compare individual and team prices, initiate or join group purchases, and track orders and team status. Sellers and administrators manage product listings, while the platform coordinates payment, team formation, expiry and refund workflows. During Sprint 2 the React frontend moved into the same repository as the Java backend, the OpenAPI contract, the database migrations and the AWS deployment assets, so the whole product is now built, reviewed and released from one monorepo.

== Project Methodology
The team continued with Scrum. Sprint 2 was a two-week sprint from 15 to 26 September 2026, tracked on the GitHub Projects Kanban board through user stories, assigned issues and the Sprint 2 container issue (\#93). Acting on the Sprint 1 retrospective, the team kept one dated log file per working day under `docs/agile/sprint-02/log/`, and a scheduled workflow committed a daily board snapshot to `docs/agile/metrics/` from 17 September. Development followed the dev-branch workflow with Conventional Commits, CODEOWNERS review, a GitHub merge queue and automated review by CodeRabbit. The backend standard remained Java 21 with the Maven Wrapper, Spotless (google-java-format AOSP), Checkstyle, SpotBugs, ArchUnit and the SonarCloud quality gate. The frontend added its own `pnpm check` gate (TypeScript, Oxlint, Oxfmt, Vitest and Knip), Playwright end-to-end tests and a separate SonarCloud project. Every real choice between alternatives was written down as an architecture decision record (ADR) or in the owning design document.

== Project Summary
Sprint 2 set out to connect the parts that Sprint 1 had proved separately into one idempotent chain from money to team to order, and to let the buyer see the result. That chain now exists as production code. A buyer receives a server-priced purchase intent and pays through Stripe-hosted Checkout. The signed webhook is deduplicated and settles the payment. Settlement admits the buyer into a new or existing team and writes exactly one order in the same transaction. When a team fills or reaches its deadline, a published lifecycle event confirms or cancels its orders and records refund obligations, and a leased scheduler resolves overdue teams on one instance only. The buyer can view their orders in the web application and their teams through the API.

Of the eleven committed stories (150 hours), nine met the Definition of Done. US24 (web application) is partially complete: the My Teams page was not built. US23 (performance baseline) was not started. Together they leave 16 hours to carry into Sprint 3. The team also delivered significant unplanned work: the complete frontend walking skeleton running on the development CloudFront distribution with automated deployment, the refund worker originally planned for Sprint 3, and the share-attribution backend originally planned for Sprint 4. The backend and frontend quality gates stayed green throughout.

// ---------------------------------------------------------------- 2
= Project Progress Report

== Reporting Period
15 September 2026 to 26 September 2026 (ten working days).

== Sprint Number
Sprint 2

== Sprint Objectives
The Sprint 2 goal was: _by 26 September 2026 a buyer can pay through Stripe, become the leader of a new team or the newest paid member of an existing one, see the resulting order and team in their own account, and have the team resolved automatically to SUCCEEDED or EXPIRED without manual intervention — with every step in that chain safe to replay._ The sprint committed to the following objectives:

- Expose the group-buy initiate and join use cases over authenticated HTTP endpoints backed by verified payment.
- Complete Stripe webhook processing so that a verified event is deduplicated, transitions the payment and drives team creation.
- Make Payment and Order a real system of record, so that every paid membership carries an auditable order.
- Resolve overdue teams automatically and record a durable refund obligation for each affected payment.
- Give the buyer owner-scoped My Orders and My Teams views, in the API and in the web application.
- Establish one in-process domain event and scheduling seam so that cross-domain reactions stay decoupled and single-flight.
- Measure the two-seconds-at-100-users performance requirement for the first time and record the baseline.

== Sprint Accomplishment
#figure(
  table(
    columns: (1.25fr, 3fr, 0.95fr),
    table.header[Work Plan][Actual][Status],
    [Authenticated initiate and join over verified payment], [Buyers obtain a server-owned purchase intent (`POST /api/v1/payments/intents`) and a Stripe-hosted Checkout session; the amount and team terms are frozen server-side. Admission into a new or existing team happens only after the signed webhook settles the payment (PRs \#126, \#147).], [#done],
    [Webhook processing and payment state], [Signature verification on the raw body, insert-before-process deduplication on the provider event id, payment transitions on the aggregate, and a 2xx acknowledgement for unknown events or payments. Replay and an eight-thread webhook race are covered by PostgreSQL integration tests (\#126, \#139).], [#done],
    [Payment and Order system of record], [Flyway migrations V9 to V19 add Stripe checkout, refunds, orders, purchase intents, team terms, scheduler lease, retry and share-attribution tables. Order and Payment aggregates refuse illegal transitions, and a unique index on the payment id guarantees one order per payment (\#149, \#147).], [#done],
    [Automatic team expiry and refund obligations], [`GroupDeal.expire` and the `DealStatus` transition matrix, `TeamSucceeded` and `TeamExpired` events, one refund obligation per paid member, a leased one-minute sweeper with durable retry backoff, and the refund worker that sends obligations to Stripe (\#148, \#162, \#173, \#184).], [#done],
    [My Orders and My Teams], [`GET /api/v1/orders/me` and `GET /api/v1/group-deals/me` are owner-scoped from the session. The order history is live on `/account` (\#149, \#181, \#182). The My Teams page is not yet built.], [#partial],
    [Event and scheduling seam], [In-process publication of immutable event records with explicit transaction phases (ADR 0002 and ADR 0003) and a PostgreSQL scheduler lease shared by both jobs (\#148, \#162, \#173).], [#done],
    [Contract and quality gates], [Every new endpoint was regenerated into `contracts/openapi.json` with additive changes only. SonarCloud now scans backend and frontend separately; backend line coverage was raised to about 99%; the merge queue is enforced (\#85, \#90, \#110, \#123, \#175).], [#done],
    [Performance baseline], [No load-test script or `docs/perf/sprint2-baseline.md` exists. Issue \#180 records the scope and the open decisions on authenticated test sessions and the target environment.], [#notdone],
    [Buyer web application], [Frontend walking skeleton, catalog, detail, filter, Google sign-in, logout, Checkout return and receipt pages, quote sheet before *Start a bloc*, and My Orders; automated deployment to dev CloudFront with security headers (\#106–\#122, \#164–\#168, \#181, \#189).], [#partial],
    [Agile tracking and documentation], [Container issue \#93, daily log files, automated board snapshots, an NFR register of 39 rows, 14 frontend and 3 backend ADRs, and this report.], [#done],
  ),
  caption: [Sprint 2 planned work against actual delivery],
)

== Sprint Burndown Chart

#let burndown() = {
  let labels = ("Start", "15 Sep", "16 Sep", "17 Sep", "18 Sep", "19 Sep", "22 Sep", "23 Sep", "24 Sep", "25 Sep", "26 Sep")
  let ideal = (150, 135, 120, 105, 90, 75, 60, 45, 30, 15, 0)
  let actual = (150, 150, 150, 150, 150, 150, 124, 74, 40, 16, 16)
  let w = 14.2cm
  let h = 6.6cm
  let ymax = 160
  let n = labels.len()
  let ox = 1.5cm
  let oy = 1.1cm
  let px(i) = ox + w * i / (n - 1)
  let py(v) = oy + h - h * v / ymax
  let blue = rgb("#0a72b0")
  let grey = rgb("#7d8793")
  block(width: 100%, height: h + 2.4cm, {
    // title and legend
    place(dx: ox, dy: 0cm, text(10pt, weight: "bold")[Sprint 2 Burndown Chart])
    place(dx: ox, dy: 0.45cm, text(8pt, fill: muted)[15–26 September 2026, remaining effort in hours])
    place(dx: ox + 8.2cm, dy: 0.2cm, line(length: 0.8cm, stroke: (paint: grey, thickness: 1.5pt, dash: "dashed")))
    place(dx: ox + 9.1cm, dy: 0.05cm, text(8pt)[Ideal remaining])
    place(dx: ox + 11.3cm, dy: 0.2cm, line(length: 0.8cm, stroke: 2.2pt + blue))
    place(dx: ox + 12.2cm, dy: 0.05cm, text(8pt)[Actual remaining])
    // grid and y ticks
    for t in range(0, 161, step: 20) {
      place(dx: ox, dy: py(t), line(length: w, stroke: 0.4pt + rgb("#d5dde5")))
      place(dx: ox - 1cm, dy: py(t) - 0.17cm, box(width: 0.85cm, align(right, text(7.5pt)[#t])))
    }
    place(dx: ox, dy: oy, line(angle: 90deg, length: h, stroke: 0.6pt + rgb("#a7b4c2")))
    place(dx: ox, dy: oy + h, line(length: w, stroke: 0.6pt + rgb("#a7b4c2")))
    place(dx: 0cm, dy: oy + h / 2 + 1.3cm, rotate(-90deg, origin: top + left, text(8pt)[Remaining effort (hours)]))
    // x labels
    for i in range(n) {
      place(dx: px(i) - 0.65cm, dy: oy + h + 0.15cm, box(width: 1.3cm, align(center, text(7.5pt, labels.at(i)))))
    }
    // ideal line
    for i in range(n - 1) {
      place(line(start: (px(i), py(ideal.at(i))), end: (px(i + 1), py(ideal.at(i + 1))), stroke: (paint: grey, thickness: 1.5pt, dash: "dashed")))
    }
    for i in range(n) {
      place(dx: px(i) - 1.6pt, dy: py(ideal.at(i)) - 1.6pt, circle(radius: 1.6pt, fill: grey, stroke: none))
    }
    // actual line
    for i in range(n - 1) {
      place(line(start: (px(i), py(actual.at(i))), end: (px(i + 1), py(actual.at(i + 1))), stroke: (paint: blue, thickness: 2.2pt, cap: "round")))
    }
    for i in range(n) {
      place(dx: px(i) - 2.8pt, dy: py(actual.at(i)) - 2.8pt, circle(radius: 2.8pt, fill: blue, stroke: none))
    }
    place(dx: px(n - 1) - 0.55cm, dy: py(16) - 0.6cm, text(8pt, weight: "bold", fill: blue)[16 h])
    place(dx: ox, dy: oy + h + 0.75cm, text(7.5pt, fill: muted, style: "italic")[134 of 150 planned hours met the Sprint 2 Definition of Done; US23 (12 h) and the My Teams page of US24 (4 h) carry over.])
  })
}

#figure(burndown(), caption: [Sprint 2 burndown from 15 to 26 September 2026], kind: image)

The sprint opened with 150 estimated hours over ten working days, an ideal burn of 15 hours per day. Actual remaining effort stayed at 150 hours for the first week. This was not idle time: the payment slice, the order system of record and the frontend were being built on long-lived branches, but no story met the Definition of Done until those branches merged. The curve then fell steeply as the Stripe checkout slice merged on 22 September, the payment, order and lifecycle layers on 23 September, the scheduling layer on 24 September and the read views on 25 September. It closed at 16 hours.

The daily remaining-hours record that the Sprint 2 plan promised was not kept in hours. The board estimate field stayed empty, and the daily logs recorded work rather than effort. The actual line above was therefore reconstructed from the merge date of the pull request that satisfied each story's acceptance criteria. Story effort was burned in full on that date, except US24, whose checkout-return part (4 h) burned on 22 September and whose My Orders part (4 h) burned on 25 September. The daily board snapshots independently confirm the late-sprint shape: the number of issues in *Done* stayed between 6 and 13 until 23 September, then rose to 20 on 24 September, 41 on 25 September and 46 of 58 on 26 September.

#figure(
  table(
    columns: (0.8fr, 0.9fr, 0.9fr, 3.6fr),
    align: (left, center, center, left),
    table.header[Date][Ideal Remaining h][Actual Remaining h][Completed Work],
    [Start], [150], [150], [Baseline agreed at Sprint Planning],
    [15 Sep], [135], [150], [Secure cookie attributes (\#84), security pipeline fix (\#88); story branches opened],
    [16 Sep], [120], [150], [OAuth callback redirect and `/auth/me` enrichment (\#89), merge queue (\#90); container \#93 and blockers filed],
    [17 Sep], [105], [150], [Frontend toolchain, CI and ADRs (\#106); keyword search (\#107)],
    [18 Sep], [90], [150], [Backend coverage raised to about 99% (\#110); product list and detail pages (\#114, \#115)],
    [19 Sep], [75], [150], [Catalog filter and session routes (\#119, \#120); Stripe slice in review],
    [22 Sep], [60], [124], [US18 verified checkout and Checkout-return pages merged (\#126)],
    [23 Sep], [45], [74], [US9, US19 and US20 merged through the stacked order and payment PRs (\#147, \#148, \#149)],
    [24 Sep], [30], [40], [US10 and US21 (sweeper \#162, hardening \#173); US22 gates (\#175)],
    [25 Sep], [15], [16], [US11 My Orders (\#181), US12 My Teams API (\#182); refund worker (\#184)],
    [26 Sep], [0], [16], [Sprint Review; US23 and the My Teams page carried over],
  ),
  caption: [Daily burndown data],
)

== Detailed Requirements Analysis
The Sprint 2 backlog combined the four product stories carried from the approved backlog (US9 to US12) with seven enabling stories added at Sprint Planning (US18 to US24), because none of the product stories could be demonstrated without them. The table below records the result of each story, who delivered it, and the evidence that it meets the Definition of Done.

#figure(
  table(
    columns: (0.35fr, 2.1fr, 1.15fr, 1.25fr, 0.45fr, 0.95fr),
    align: (center, left, left, left, center, left),
    table.header[ID][User Story][Planned Owner][Delivered By][Effort h][Status],
    [9], [As a buyer, I want to join a forming team after payment, so that I receive the team price.], [Wang Ziang], [Yu Qianshuo, Liu Jin], [20], [#done],
    [10], [As a buyer, I want overdue teams to expire automatically, so that timed-out purchases are resolved.], [Wang Ziang, Yu Qianshuo], [Liu Jin, Panpakorn, Yu Qianshuo], [16], [#done],
    [11], [As a buyer, I want to view my orders, so that I can track purchase status.], [Lim Jiechao], [Liu Jin (API), Lim Jiechao (page)], [10], [#done],
    [12], [As a buyer, I want to view my teams, so that I can track forming, successful and expired teams.], [Lim Jiechao], [Liu Jin (API)], [10], [#done],
    [18], [As a buyer, I want the checkout I start to become the payment the platform records, so that team creation always follows verified money.], [Yu Qianshuo], [Yu Qianshuo], [22], [#done],
    [19], [As the platform, I want one confirmed payment to produce exactly one order, so that every paid membership is auditable.], [Liu Jin], [Liu Jin], [12], [#done],
    [20], [As a developer, I want Payment and Order to be a real system of record, so that the webhook has something correct to write to.], [Liu Jin], [Liu Jin, Yu Qianshuo], [18], [#done],
    [21], [As a developer, I want one in-process event and scheduling seam, so that cross-domain reactions stay decoupled and single-flight.], [Panpakorn], [Liu Jin, Panpakorn], [10], [#done],
    [22], [As an approver, I want every Sprint 2 endpoint under the agreed contract and quality gates, so that release control is preserved.], [Panpakorn], [Panpakorn, all], [8], [#done],
    [23], [As an operator, I want key actions measured under two seconds at 100 concurrent users, so that the performance requirement has evidence.], [Panpakorn], [—], [12], [#notdone],
    [24], [As a buyer, I want the web application to show checkout return, my orders and my teams, so that the flow is usable end to end.], [Lim Jiechao, Wang Ziang], [Lim Jiechao, Yu Qianshuo], [12], [#partial],
    [], [*Total*], [], [], [*150*], [134 h done],
  ),
  caption: [Sprint 2 committed backlog and outcome],
)

=== Acceptance Criteria Verification
#figure(
  table(
    columns: (1fr, 3.2fr, 2.1fr),
    table.header[Story][Result Against Acceptance Criteria][Evidence or Deviation],
    [US9 Paid join], [A signed-in buyer receives a Checkout URL for an open team; membership is added only by the settled webhook; a replayed webhook adds no member; a twenty-buyer race for the last seat produces exactly one succeeded team.], [`StripeLocalIntegrationTest` against PostgreSQL (13 cases at merge, 17 by 25 Sep); PRs \#126 and \#147.],
    [US10 Automatic expiry], [An open team past its deadline becomes expired on the next one-minute sweep; later joins are rejected; one refund obligation per paid payment, with no duplicate on a second sweep; only one instance holds the lease.], [`TeamExpirySchedulingIT`, `SchedulerLeaseRepositoryIT` (eight-way race, one winner). Scheduling is opt-in per deployment and has not yet been enabled under shared load.],
    [US11 View my orders], [`GET /api/v1/orders/me` returns only the caller's orders with paging, ignores any client-supplied user id and rejects anonymous requests; the page renders them on `/account`.], [`OrderRepositoryIT` (14), controller tests; \#149, \#181. Offset paging was used instead of the planned keyset paging.],
    [US12 View my teams], [`GET /api/v1/group-deals/me` returns the caller's teams with status, sizes, deadline and the caller's role; product titles are resolved in one batch; the whole page is read from one `REPEATABLE_READ` snapshot.], [`TeamMembershipRepositoryIT`, `GroupDealControllerTest`; \#182. Offset paging, keyset deferred.],
    [US18 Verified checkout], [The amount comes from the server-owned purchase intent; the raw body reaches signature verification; an unknown event or payment returns 200 with no side effect; one event id writes one receipt row; no Stripe type leaves the gateway package.], [Five real Stripe Sandbox payments and one SGD 10 refund recorded in `stripe-sandbox-acceptance-20260920.md`; \#126, \#139.],
    [US19 One payment, one order], [Each accepted participation creates exactly one order bound to its payment, enforced by `uq_biz_order_payment_id`; team success confirms and expiry cancels all of its orders idempotently; the membership's order id is backfilled.], [`OrderTest` (39), lifecycle integration tests; \#148, \#149.],
    [US20 System of record], [Migrations apply cleanly to an empty and to a Sprint 1 database; aggregates refuse illegal transitions; a version conflict surfaces as a 409 conflict.], [Flyway V1 to V19 on PostgreSQL 18 in CI; the plan's V9 to V11 grew to eleven migrations.],
    [US21 Event and scheduling seam], [Lifecycle subscribers run before commit and roll back with their publisher; the share-credit subscriber runs after commit and cannot undo a join; the sweep runs on one instance.], [ADR 0002 and ADR 0003. The lease uses a single upsert with a three-second statement timeout instead of the planned `FOR UPDATE NOWAIT`.],
    [US22 Contract and gates], [Every Sprint 2 endpoint is in `contracts/openapi.json` with additive-only changes; Spotless, Checkstyle, SpotBugs, ArchUnit (8 rules), Gitleaks, Trivy and both SonarCloud gates pass on `dev`.], [The contract was regenerated in the same PR as each controller rather than one day ahead.],
    [US23 Performance baseline], [Not met: no 100 virtual-user run was executed and NFR-15 still has no measured value.], [Issue \#180; carried over.],
    [US24 Web application], [Checkout, return, receipt polling and My Orders work in the browser with distinct cancel and failure messages, using a client generated from the contract. The My Teams page is missing.], [\#126, \#181, \#189; Playwright on dev 6 passed, 2 signed-in cases skipped.],
  ),
  caption: [Acceptance criteria per Sprint 2 story],
)

=== Team Lifecycle Rules as Implemented
#figure(
  table(
    columns: (1fr, 1.5fr, 0.9fr, 3fr),
    table.header[Current State][Trigger][Next State][Implemented Result],
    [No team], [Settled START payment], [OPEN], [Team, leader membership, participation receipt and order are written in one transaction; the purchase intent is consumed once.],
    [OPEN], [Settled JOIN below quota], [OPEN], [One membership and one order; `MemberJoined` is published and the sharer, if any, is recorded.],
    [OPEN], [Settled JOIN reaches quota], [SUCCEEDED], [`TeamSucceeded` is published before commit and every order of the team is confirmed.],
    [OPEN], [Deadline passed, found by the sweeper], [EXPIRED], [`TeamExpired` is published before commit; orders are cancelled and one refund obligation is written per paid payment.],
    [EXPIRED or full], [Payment settles after resolution], [Unchanged], [The payment is marked as a business rejection and a refund obligation is recorded; the buyer is never charged for a team they did not join.],
    [SUCCEEDED or EXPIRED], [Replayed webhook, event or sweep], [Unchanged], [Status-guarded updates affect zero rows; unique indexes on payment id reject duplicate orders, refunds and credits.],
  ),
  caption: [Team lifecycle transitions as delivered],
)

=== Data and Quality Requirements
#figure(
  table(
    columns: (0.9fr, 2.6fr, 2.3fr),
    table.header[Area][Requirement][Sprint 2 Result],
    [System of record], [PostgreSQL stores payments, orders, refunds, purchase intents, scheduler leases and attribution.], [Met. Eleven new Flyway migrations (V9 to V19).],
    [Caching], [Redis is never the only record or the lock for a business decision.], [Met. The sweeper and refund worker lease in PostgreSQL.],
    [Security], [Signed callbacks, session-derived owner scope, no committed secrets.], [Met for the application. The CloudFront-to-ALB leg is still HTTP (NFR-37), and a disclosed origin secret was rotated on 25 Sep.],
    [Correctness], [Idempotent, transactionally consistent capacity, payment, order, expiry and refund operations.], [Met, with replay and race tests on PostgreSQL. Global lock order: team, intent, order, payment.],
    [Performance], [Key actions under two seconds at 100 users; expiry within one minute.], [Not measured. The sweep interval is one minute; the receipt read was reduced from four queries to one (\#191).],
    [Operations], [Health checks, trace-id logs, rollback, per-job logging.], [Met. Each job run carries its own trace id and Micrometer counters.],
  ),
  caption: [Data and quality requirements for Sprint 2],
)

== Sprint Completion Plan
#figure(
  table(
    columns: (0.55fr, 2fr, 2.6fr, 0.8fr),
    table.header[Date][Planned Work][Actual Work][Status],
    [15 Sep], [Freeze backlog, write contract entries, merge event port, draft migrations.], [Backlog frozen; secure cookie attributes and pipeline fixes merged. Migrations were drafted on branches and merged 20 to 23 Sep.], [#late],
    [16 Sep], [Payment and Order aggregates with unit tests.], [OAuth redirect flow and merge queue merged; Sprint 2 container and blocker issues filed; aggregates in progress on branches.], [#late],
    [17 Sep], [Payment and Order persistence with Testcontainers.], [Frontend scaffold, quality gate and CI merged; keyword search merged.], [#late],
    [18 Sep], [Checkout Session creation and group-deal intent endpoints.], [Hosted Checkout and server-owned intents built on the payment branch; product list and detail pages merged.], [#late],
    [19 Sep], [Complete webhook processing with replay tests.], [Webhook dedup and replay done on branch; Sandbox acceptance run on 20 Sep.], [#late],
    [22 Sep], [Confirmation subscriber; end-to-end webhook-to-order test.], [Stripe slice merged; the order-payment integration split into four stacked PRs; frontend live on dev.], [#done],
    [23 Sep], [Expiry transition, sweeper, lock and refund obligation.], [Payment ports, lifecycle events, expiry refunds and order system of record merged; end-to-end test on real implementations.], [#done],
    [24 Sep], [My Orders and My Teams API and pages.], [Sweeper and its hardening merged; frontend production hygiene; read views merged the next day.], [#late],
    [25 Sep], [Concurrency and performance runs; gate fixes.], [Last-seat race already in the suite; refund worker, My Orders page, My Teams API, Terraform import merged. No performance run.], [#partial],
    [26 Sep], [Sprint Review, burndown close, contribution records, report.], [Share-attribution backend and release-gate alignment merged; board snapshot; this report.], [#done],
  ),
  caption: [Planned against actual day-by-day completion],
)

== Definition of Done
The Sprint 2 Definition of Done, carried forward from Sprint 1 with three additions, was applied to every story:

- Acceptance criteria are satisfied and linked to the relevant story or issue.
- The endpoint exists in `contracts/openapi.json`, and the contract-drift check passes without a waiver label.
- Implementation matches the approved contract and the COLA and DDD boundaries enforced by ArchUnit.
- Automated tests cover the main path, validation failures, and the replay and concurrency cases specific to the story.
- Every cross-domain reaction states whether it runs inside or after the publisher's transaction, and a test proves it.
- Maven build, Spotless, Checkstyle, SpotBugs, ArchUnit, Gitleaks, Trivy and the SonarQube quality gate pass.
- The change has been reviewed and contains no secret, unresolved blocking defect or unapproved API break.
- Where deployment applies, the health check passes and rollback remains available.
- The Kanban board, remaining-effort record, contribution record and report are updated before completion is counted.

Two items were applied more loosely than written. Contract entries were regenerated in the same pull request as their controller rather than one day ahead, which kept the drift check green but did not let other members work against the contract early. The remaining-effort record was kept as a work log rather than in hours, which is why the burndown was reconstructed.

== Sprint Completion Summary
#figure(
  table(
    columns: (1.4fr, 3fr),
    table.header[Measure][Final Result],
    [Sprint 2 stories], [9 of 11 completed; US24 partially completed; US23 not started],
    [Planned effort], [150 hours],
    [Completed effort], [134 hours],
    [Remaining effort on 26 Sep], [16 hours],
    [Carry-over], [US23 performance baseline (12 h); My Teams page of US24 (4 h)],
    [Pull requests merged into dev], [72 (58 by team members, 14 automated dependency and snapshot PRs)],
    [Board on 26 Sep], [46 of 58 tracked issues in *Done*; 2 in progress, 2 ready, 7 in backlog],
    [Unplanned work delivered], [Frontend walking skeleton on dev CloudFront with automated deployment; refund worker (Sprint 3 scope); share-attribution backend (Sprint 4 scope); Terraform import of the dev frontend edge; production release gates],
    [Sprint result], [Sprint goal substantially met: the payment-to-team-to-order chain and automatic resolution are complete and tested, and orders are visible in the web application. Teams are visible through the API only.],
  ),
  caption: [Sprint 2 completion summary],
)

== Problems Encountered
#figure(
  table(
    columns: (2fr, 3fr, 0.9fr),
    table.header[Problem][Action Taken][Status],
    [The dev backend deployment failed on 16 September after an image publish, leaving the ALB without a healthy target and blocking OAuth and frontend probes.], [Redeployed through SSM and re-ran the health and OAuth probes; the result was recorded in the day log and on \#93.], [#resolved],
    [Frontend delivery waited six days on AWS and repository-owner actions (A1 to A4: SPA fallback, security headers, deploy role, environment).], [The frontend owner took scoped AWS access and executed the runbook; the repository owner created the environment on 23 September; the automated deploy and dev end-to-end tests then passed.], [#resolved],
    [The Stripe slice (\#126, about 10,000 added lines) shipped payment ports whose only implementation was a test fixture reading other modules' tables, which violated the COLA boundary rule.], [Recorded ADR 0001, moved the purchase intent into the order module, added four cross-domain application facades, deleted the fixture and split the integration into four stacked PRs (\#146 to \#149).], [#resolved],
    [Parallel branches claimed the same Flyway version numbers.], [Reserved V12 to V16 for the order stack in the day log and renumbered; migrations now run V1 to V19 in order.], [#resolved],
    [Board snapshots for 19 to 24 September were published with blank status columns because the workflow token lacked `read:project`.], [Reconstructed the columns from dated project events, replaced the token, and made the workflow fail before writing a CSV when the board is unreadable (\#188).], [#resolved],
    [Integration landed later than planned: the chain merged on 23 September instead of 22, and the read views missed the 24 September code freeze.], [Accepted a one-day freeze exception for the My Teams API and My Orders page; the late merge shape is visible in the burndown.], [#partres],
    [The performance baseline needs 100 authenticated sessions without Google, and Checkout is disabled on dev by its rollout profile.], [Scope, environment options and the rule that no login bypass reaches a deployed profile were written into \#180.], [#open],
    [An origin secret was disclosed in a pasted Terraform diff, and the CloudFront-to-ALB leg still uses HTTP.], [The secret was rotated on 25 September with zero-downtime dual admission; origin TLS waits for a hostname and regional certificate (NFR-37).], [#partres],
  ),
  caption: [Sprint 2 problems and actions],
)

== Sprint Retrospective

=== What went well
The central risk of the sprint, joining payment, team and order without a message broker and without breaking module boundaries, was settled by recorded decisions (ADR 0001 to 0003) and proved by tests on a real PostgreSQL database, including replays, a twenty-buyer last-seat race and concurrent schedulers. The frontend went from an empty directory to a deployed, tested application with automated delivery and enforced security headers. Evidence discipline improved sharply over Sprint 1: every change has a day-log entry, NFR changes have register rows, and board history is captured automatically.

=== What could be improved
Work merged late and in very large units. The Stripe slice alone added about 10,000 lines, and the first week closed no story, so the burndown shows a flat line followed by a cliff. Effort was not recorded in hours, so the burndown had to be reconstructed. Work also drifted from the plan's assignment table: several stories were delivered by members other than their planned owners, and the one verification task scheduled for the last day, the performance run, did not happen.

=== What the team will try next
In Sprint 3 each story will be sliced so that a pull request stays below roughly 1,500 changed lines and merges within two days of opening. Remaining hours will be entered in the board's estimate field every working day, so the snapshot workflow produces the burndown directly. A mid-sprint check on day five will compare planned owners with the actual work and rebalance openly. Verification work, especially the performance baseline, will be scheduled in the first half of the sprint.

// ---------------------------------------------------------------- 3
= Project Team Members Contribution Summary

== Contribution Summary Table
#figure(
  table(
    columns: (0.95fr, 1.1fr, 2.8fr, 1.05fr, 1.25fr),
    table.header[Project Member Name][Selected Use Case][Activity Description][Achievement Status][Remarks],
    [Lim Jiechao], [UC8 View Orders and Teams (frontend); UC1, UC2 and UC3 frontend], [Built the frontend in the monorepo: toolchain, `pnpm check` gate, CI, generated API client, catalog list, detail and filter, Google sign-in and logout, end-to-end tests, the first dev CloudFront deployment with SPA fallback and security headers, production-build hygiene, the My Orders page and the quote sheet. Authored 14 frontend ADRs, the evidence conventions and the agile-evidence-capture skill. 20 merged PRs.], [#partial], [My Teams page (T3-03) carried to Sprint 3.],
    [Liu Jin], [UC10 Payment and Order System of Record; UC5 Team Lifecycle Resolution; UC8 and UC7 backend], [Delivered the order system of record, the purchase-intent and settlement ports (ADR 0001), team lifecycle events and expiry refunds (ADR 0002), the leased expiry sweeper, the My Teams endpoint and the share-attribution backend (ADR 0003); reviewed the Stripe slice and the refund worker. 9 merged PRs, including four stacked integration layers.], [#done], [Took over the order read services planned for Lim Jiechao and the lifecycle work planned for Wang Ziang.],
    [Panpakorn Siripanich], [Cross-cutting architecture, scheduling and DevSecOps], [Merge queue, backend coverage to about 99%, separate frontend SonarCloud gate, Gitleaks and Dependabot policy, hardened scheduling with renewable leases and durable retry, board-snapshot integrity, Terraform import of the dev frontend edge, origin-secret rotation and production release gates; reviewed most backend PRs. 26 merged PRs.], [#partial], [US23 performance baseline not started; carried to Sprint 3.],
    [Wang Ziang (Eric)], [UC3 Initiate a Team; UC4 Join a Team], [His Sprint 1 domain core (`GroupDeal`, `PaidGroupDealService`) was reused unchanged as the admission step of the payment-driven path. In Sprint 2 he reduced the buyer receipt from four payment reads to one (\#191), reviewed the My Teams endpoint, and opened the refactor that returns team completion from the aggregate (\#187).], [#partial], [Planned intent endpoints and expiry transition were delivered by other members; \#187 is in review.],
    [Yu Qianshuo (Yorik)], [UC6 Stripe Payment and Refund on Expiry], [Delivered the Stripe slice: server-owned purchase intents, idempotent Hosted Checkout, signed and deduplicated webhooks, buyer-owned receipts, product-page START and JOIN, refund safeguards and Sandbox acceptance evidence (\#126); added the leased refund worker (\#184).], [#done], [Refund execution was pulled forward from Sprint 3.],
  ),
  caption: [Sprint 2 contribution by member],
)

== Use Case Analysis, Design and Design Pattern
This section presents the analysis, design and design patterns of each use case worked on in Sprint 2, in use case order. It describes what was built rather than what was planned; where the delivered design differs from the Sprint 2 plan, the difference and its reason are stated. Each subsection names the responsible member or members.

=== UC3 and UC4 Initiate and Join a Team — Wang Ziang (Eric)
*Analysis.* Sprint 1 left the team domain complete but unreachable. The Sprint 2 analysis confirmed that the browser must never call the trusted-payment service directly, so initiation and joining split into an unpaid intent and a paid admission. A later review found a remaining design weakness: whether a join completed a team was decided in the application layer by re-reading the team's status after admission, rather than by the aggregate that made the decision. The receipt page also read the same payment row four times.

*Design.* The admission core is unchanged: `PaidGroupDealService.startConfirmed` creates the team through the Sprint 1 Factory Method, and `joinConfirmed` admits a member under a row lock. It is now driven by the settled payment through `GroupDealSettlementSupport`. Refactor \#187 makes `GroupDeal.joinPaid` return both the new immutable snapshot and a completion fact, so that `TeamSucceeded` follows the aggregate's own decision with no lock or transaction change. PR \#191 serves the buyer receipt from one read-model projection on the existing repository port.

*Design Pattern.* Factory Method (`PaidGroupDealCreator` and `StandardPaidGroupDealCreator`) and Template Method (`startConfirmed`) are retained. The Aggregate Root pattern is strengthened because completion is now decided inside `GroupDeal`. Observer connects the final join to the order and payment subscribers. The Pessimistic Offline Lock serialises the last-seat race, and a CQRS-lite Read Model serves the receipt.

=== UC5 Team Lifecycle Resolution — Liu Jin, with Panpakorn Siripanich
*Analysis.* Before this sprint nothing in production ever wrote `expired`. The order confirm and cancel methods had no callers, so a paid order could stay pending forever. A team's resolution must reach the order and payment domains without either domain reaching into the other's aggregates. The refund owed for an expired team must be one fact with the expiry itself, so it cannot be recorded after the commit. The sweep must also run on only one of several instances, and one bad team must not stall the others.

*Design.* Legal transitions live only in `DealStatus.canTransitionTo`, and `GroupDeal.expire(now)` is guarded by that matrix. Groupbuy publishes the immutable records `TeamSucceeded` and `TeamExpired`. `OrderLifecycleSubscriber` and `TeamExpiryRefundSubscriber` react at `BEFORE_COMMIT` in a fixed order that follows the global lock order, so the whole resolution commits or rolls back as one. `GroupDealExpirySweeper` reads up to 100 due teams and expires each one in its own transaction. `LeasedJobRunner` takes a PostgreSQL lease with a single upsert and a three-second statement timeout and renews it during long runs. Pan hardened the design with durable five-minute retry backoff (V17), a deployment-level opt-in switch and interruption-safe renewal.

*Design Pattern.* State (the `DealStatus` transition matrix), Observer (lifecycle events with ordered subscribers), a Lease or single-flight lock for leader election through the database, and Unit of Work (one transaction per team). Status-guarded updates and unique indexes act as Idempotent Receivers.

=== UC6 Stripe Payment and Refund on Expiry — Yu Qianshuo (Yorik)
*Analysis.* The Sprint 1 webhook handler only logged events. The team chose Hosted Checkout over in-page payment so that card data never touches BlocBuy's frontend. The analysis required that the amount is fixed by the server before any provider call, that the raw body reaches signature verification unchanged, that a duplicate or unknown event is acknowledged with no side effect, and that a refund can be retried safely even when its outcome is unknown.

*Design.* A server-owned purchase intent freezes the price and terms before Checkout is created with an idempotency key. The webhook inserts the provider event id before processing, loads the payment by its provider reference, applies the transition on the aggregate and settles the business side through the payment ports. Refunds are durable rows keyed uniquely by payment id; permanent failures are parked for operator review rather than retried every 30 seconds (\#132). The leased `RefundWorkerJob` claims one due obligation per tick and never holds a database transaction across the Stripe call. The frontend polls the buyer's receipt instead of trusting the return URL (frontend ADR 0014).

*Design Pattern.* Adapter as an anti-corruption layer (`PaymentGateway` implemented by the Stripe adapter), Idempotent Receiver (insert-before-process event log), State (payment and refund lifecycles), Builder (Stripe request parameters), Proxy (transactional boundaries) and Optimistic Offline Lock (version fencing on refund rows).

=== UC7 Share Attribution — Liu Jin
*Analysis.* This use case was planned only as groundwork for Sprint 2 but was delivered in full. A join made through an invite must record exactly one sharer, a buyer cannot refer themselves, and crediting must not be able to undo or delay the join itself. This is the opposite transaction choice from UC5.

*Design.* Members create an idempotent invite (`POST /api/v1/group-deals/{dealId}/invites`). A JOIN intent's optional `ref` is resolved under the team lock and frozen on the purchase intent (V19), then written on the membership at admission (V18). Groupbuy publishes `MemberJoined` for each accepted join. `ShareCreditSubscriber` runs after commit in a new transaction, writes one immutable credit row that is unique per payment, and never throws. A credit's status (pending, earned or void) is derived from the team's status and never stored.

*Design Pattern.* Observer with an after-commit subscriber, Idempotent Consumer (a unique credit per payment), and Derived State instead of a mutable status column (backend ADR 0003).

=== UC8 View Orders and Teams — Lim Jiechao (frontend) and Liu Jin (backend)
*Analysis.* Both views must be strictly owner-scoped from the Sa-Token session. A client-supplied user id would be an insecure direct object reference. The team view must agree with the orders behind it, and one malformed product row must not break a whole page. On the frontend, an empty state must not imply that the buyer has never ordered, and loading, failure and retry states must be distinct.

*Design.* `GET /api/v1/orders/me` and `GET /api/v1/group-deals/me` take the user id only from the session and project read models. My Teams resolves titles through a groupbuy-owned `ListingDisplayGateway` implemented by catalog, which avoids a module cycle. It uses a narrow display projection and reads rows, totals and titles from one `REPEATABLE_READ` snapshot. Offset paging was chosen for now and keyset paging deferred. The `/account` route loads the order history through the generated client, with URL-synced paging, a skeleton and a retry alert.

*Design Pattern.* Repository and Facade on the backend; CQRS-lite Query Object read models and DTOs; Dependency Inversion through the `ListingDisplayGateway` port; on the frontend, route loaders with a TanStack Query cache and an Adapter that unwraps the result envelope into typed data or an `ApiError`.

=== UC10 Payment and Order System of Record — Liu Jin
*Analysis.* Order and Payment were placeholders, and `biz_order` was not yet in Flyway. The system needed one order per paid participation guaranteed by the database, the price actually charged rather than the current catalog price, and order status that follows its team. The purchase intent, the server's frozen answer to what a checkout costs, had no owning module.

*Design.* The purchase intent became an order-module aggregate (V15). Team terms became two nullable columns on the product (V14). Four application facades (`CatalogPurchaseSupport`, `GroupDealPurchaseSupport`, `GroupDealSettlementSupport` and `UserAppService`) are the only cross-domain paths. Order implements both payment ports on top of them. Deadlines are read from the database clock, and fulfilment evidence is derived from rows keyed by payment id, so a replay after any intermediate state gives the same answer. Every rejecting check runs before the first write, so a late failure rolls back rather than committing a membership next to a refund.

*Design Pattern.* Aggregate Root and State (`Order`, `PurchaseIntent`), Repository and Data Mapper (data object, mapper, converter and repository implementation), Facade (one application facade per direction of need), Unit of Work (receipt, membership and order in one transaction) and Optimistic Offline Lock (version columns).

=== Cross-cutting Architecture, Scheduling and DevSecOps — Panpakorn Siripanich (Pan)
*Analysis.* Sprint 2 added the first scheduled jobs and the first money-moving background work, a second deployable (the frontend), and infrastructure that had so far been created by hand. Each of these needed a controlled release path, and the quality gates had to keep pace with a much larger change volume.

*Design.* A merge queue serialises merges into `dev`. SonarCloud runs as separate backend and frontend projects and refreshes its baseline on every push to `dev`. Scheduling is off in every profile until an operator counts overdue work and sets `BLOCBUY_SCHEDULING_ENABLED`. The existing dev frontend edge (nine resources) was imported into Terraform with a zero-change plan, and production uses two tag-only environments with an operator-controlled deploy switch. Board snapshots now fail closed rather than publishing unreadable data.

*Design Pattern.* Layered Architecture with Hexagonal Ports and Adapters, enforced by eight ArchUnit rules; Chain of Responsibility in the request pipeline; Lease for scheduled jobs; Infrastructure as Code with import-before-manage for existing resources.

== Design Pattern Summary
#figure(
  table(
    columns: (1.2fr, 2fr, 2fr, 1.1fr),
    table.header[Pattern][Applied In][Purpose][Owner],
    [Factory Method, Template Method], [`PaidGroupDealCreator`, `startConfirmed`], [Encapsulate team creation around fixed invariant checks.], [Wang Ziang],
    [Aggregate Root], [`GroupDeal`, `Order`, `PurchaseIntent`, `Payment`], [Each aggregate is the only entry point for mutating its state.], [Wang Ziang, Liu Jin, Yu Qianshuo],
    [State], [`DealStatus`, `OrderStatus`, payment and refund status], [The object refuses illegal transitions.], [Liu Jin, Yu Qianshuo],
    [Observer], [`TeamSucceeded`, `TeamExpired`, `MemberJoined`], [Cross-domain reactions without compile-time coupling or a broker.], [Liu Jin],
    [Idempotent Receiver or Consumer], [Webhook event log; unique order, refund and credit per payment], [Retries and replays apply exactly once.], [Yu Qianshuo, Liu Jin],
    [Adapter (anti-corruption layer)], [`PaymentGateway` and the Stripe adapter; frontend envelope unwrapping], [Keep provider and transport types out of the core.], [Yu Qianshuo, Lim Jiechao],
    [Facade], [Catalog, groupbuy and identity purchase facades; query services], [One entry point per direction of need.], [Liu Jin],
    [Repository and Data Mapper], [Order, intent, payment and membership repositories], [Keep rows out of the domain.], [Liu Jin, Yu Qianshuo],
    [Unit of Work], [Receipt, membership and order transaction; per-team expiry], [One commit boundary per business fact.], [Liu Jin],
    [Pessimistic and Optimistic Offline Lock], [Deal row lock; version columns], [Serialise the last seat; detect concurrent edits.], [Wang Ziang, Liu Jin],
    [Lease (single-flight)], [`sys_scheduler_lock` with `LeasedJobRunner`], [One instance runs each job.], [Liu Jin, Panpakorn],
    [CQRS-lite Read Model], [My Orders, My Teams, receipt projection], [Read paths project instead of loading aggregates.], [Liu Jin, Wang Ziang, Lim Jiechao],
    [Builder], [Stripe request parameters], [Assemble complex provider requests safely.], [Yu Qianshuo],
    [Proxy], [Spring `@Transactional`; the `_authenticated` route guard], [Declarative commit boundaries; UX-only access guard.], [All],
  ),
  caption: [Design patterns applied across Sprint 2],
)

// ---------------------------------------------------------------- 4
= Other Things to Be Highlighted

#figure(
  table(
    columns: (1fr, 3.4fr, 0.9fr),
    table.header[Area][Sprint 2 Outcome][Status],
    [Monorepo frontend], [The React 19, Vite and TanStack frontend now lives beside the backend. It consumes a client generated from `contracts/openapi.json`, so contract drift fails the build instead of reaching users.], [#done],
    [Automated frontend delivery], [The frontend is built once and promoted to S3 behind CloudFront with an OIDC deploy role, an SPA fallback function, a strict security-header policy and post-deploy Playwright checks on dev.], [#done],
    [Quality evidence], [The backend suite reached 1,192 tests including PostgreSQL Testcontainers suites, with line coverage of about 99%. The frontend suite has 211 tests. Both SonarCloud gates pass on `dev`.], [#done],
    [Decision records], [Three backend ADRs (payment integration, lifecycle events, share credit) and fourteen frontend ADRs; the NFR register grew to 39 rows.], [#done],
    [Infrastructure as Code], [Nine dev frontend-edge resources imported into Terraform with a zero-change plan; the production plan is prepared and gated on release tags.], [#done],
    [Work pulled forward], [The refund worker (US13, Sprint 3 scope) and share-attribution crediting (US15, Sprint 4 scope) are merged, and both are disabled until an operator enables scheduling.], [#done],
  ),
  caption: [Other Sprint 2 outcomes],
)

#figure(
  table(
    columns: (2.2fr, 2.6fr, 0.8fr),
    table.header[Carry-forward Item][Reason][Target],
    [US23 performance baseline (\#180)], [Not started; needs a test-only session fixture and a decision on the target environment.], [Sprint 3],
    [My Teams page and share or invite UI (T3-03, T3-05)], [Backend endpoints are merged; the pages were not built before the sprint ended.], [Sprint 3],
    [Team-completion refactor (\#187, \#186)], [In review.], [Sprint 3],
    [Price shown against price charged (\#183)], [START re-prices from the latest policy; the team must decide whether the intent accepts the quote id.], [Sprint 3],
    [Checkout and scheduling rollout on dev], [Both are switched off by default; they need an overdue-work count and a named operator recovery owner.], [Sprint 3],
    [Backend follow-ups (\#96, \#130, \#134)], [Secure cookie on EC2, Stripe sandbox gate consolidation, expiry of failing sessions.], [Sprint 3],
    [Origin TLS between CloudFront and ALB (NFR-37)], [Needs a hostname and a regional ACM certificate.], [Backlog],
  ),
  caption: [Work carried forward from Sprint 2],
)

Sprint 2 turned BlocBuy from a set of proven parts into one connected, replay-safe flow from payment to team to order, and put a real web application in front of it. It closed with 134 of 150 planned hours complete and 16 hours carried into Sprint 3, where the first priorities are the performance baseline, the remaining buyer pages and a controlled rollout of Checkout and scheduling on the development environment.
