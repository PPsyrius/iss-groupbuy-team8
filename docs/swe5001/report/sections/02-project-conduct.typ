#import "../lib.typ": *

= Project Conduct

== Project Plan

The plan was 50 person-days in seven work packages, the briefing's guideline of about ten person-days for each of the five members. The estimate covers only the SWE5001 work: the business use cases UC1–UC9 already existed from SWE5006 and are excluded (@tbl-wbs).

#figure(
  table(
    columns: (0.35fr, 1.6fr, 0.45fr, 2.6fr, 1.1fr),
    table.header([WBS], [Work package], [Est. (pd)], [Main tasks (estimate in person-days)], [Delivered by]),
    [1], [Planning and reporting], [3.5], [Proposal and architecture baseline (2.5); fortnightly progress reports (1)], [#pr(259), #pr(372), #iss(462)],
    [2], [Platform foundation], [6.5], [Shared platform module (3); outbox, SNS and SQS publishing and idempotent consumption (3.5)], [#pr(471), #pr(473), #pr(485), #pr(513)],
    [3], [Service decomposition], [13.5], [`notification-svc` (3), `identity-svc` (4), `commerce-svc` (2); databases and migrations per service (3); demonstration and load data (1.5)], [#pr(470), #pr(472), #pr(479), #pr(490)],
    [4], [Infrastructure as code], [11], [`foundation` stack (2); network, ECS and the golden-path module (4); RDS, ElastiCache and messaging (2); `edge` stack (1); scaling, dashboard and alarms (2)], [#pr(377)–#pr(380), #pr(487), #pr(489)],
    [5], [DevSecOps pipeline], [6], [Per-service continuous integration (CI) with image scanning (2); Terraform plan and apply with Checkov (2); ECS deployment with circuit breaker, environment workflows (2)], [#pr(384), #pr(405), #pr(407), #pr(409), #pr(411), #pr(416), #pr(417), #pr(418)],
    [6], [Verification], [4.5], [k6 scripts and runs (2); resilience drills (1); end-to-end regression on the deployed system (1.5)], [#pr(506), #pr(518), #pr(500), #iss(460)],
    [7], [Presentation and report], [5], [Slides, demonstration and rehearsal (2.5); report (2.5)], [#iss(461), #iss(462)],
    table.cell(colspan: 2)[*Total*], [*50*], table.cell(colspan: 2)[],
  ),
  kind: table,
  caption: [Work breakdown structure with estimated effort in person-days (pd). Source: #src("docs/swe5001/project-proposal.typ")[proposal, Table 7].],
) <tbl-wbs>

Three choices kept the plan inside the guideline. The split stopped at three services, because a split into five or six services (about one per bounded context) was estimated at 50–55 person-days (AD-02, §3.1.1). Business code was not moved into new source trees; the services are assembled from the existing modules (AD-01, AD-18). And on 11 October we cut work that would not change the result: the broken-image drill, a forced-failure promotion, the internal-call token, WAF and tracing (#src("docs/swe5001/HANDOFF.md")[handoff record], #src("docs/swe5001/architecture-options-study/README.md")[study README]\; see also @tbl-not-built).

*Scheduling.* SWE5001 ran inside our two-week SWE5006 sprints on the same repository and board: Phase A in Sprint 3 (28 September to 9 October) and Phases B and C in Sprint 4 (12–23 October; #src("docs/agile/sprint-04/plan.md")[Sprint 4 plan]). Phase D followed the presentation. @tbl-sprint-architecture shows what changed in the architecture in each period.

#figure(
  table(
    columns: (1fr, 2.4fr, 1.2fr),
    table.header([Period], [Architecture change], [Decisions]),
    [Sprint 3, 28 Sep – 9 Oct], [Options study and proof of concept (phase 0); `foundation`, `edge` and `runtime` stacks; the unchanged monolith on ECS Fargate (strangler step 0, first green promotion 6 October)], [AD-01, AD-12, AD-17, AD-19],
    [Sprint 4, 12–23 Oct], [Three services assembled from the existing modules; one database per service; outbox → SNS → SQS; one synchronous call; request-count auto-scaling; first three-service promotion 17 October; presentation 21 October], [AD-02, AD-03, AD-04, AD-06, AD-18, AD-20],
    [Phase D, 22 Oct – 11 Nov], [Separate data subnets verified on AWS on 10 November; promotions of 10 and 11 November; load reruns], [AD-15],
  ),
  kind: table,
  caption: [Architecture changes per period. Source: @tbl-milestones and the AD register (§3.2.1, §3.3).],
) <tbl-sprint-architecture>

== Project Status

Every use case and platform capability in scope is built; dated AWS records verify building, routing, scale-out and release, not every business flow or acceptance gate. The 10 November record adds data-subnet placement and required connectivity. On 11 November a promotion rebuilt the runtime from zero and a new session reran the load tests; V3 r1, V3 r2 and V3 r3 failed, and R4, with its load generator on AWS, delivered the whole browse schedule and scaled commerce from one to six tasks (§4.1). A complete observation record, maximum capacity, linear scaling and several availability drills remain open. @tbl-status gives the evidence kind of each capability. Timings and measurements are stated once, in the sections the table points to; the verification scenarios V1–V8 are defined in §4.1.

#figure(
  table(
    columns: (2fr, 1fr, 2.3fr),
    table.header([Capability], [Status], [Evidence and section]),
    [Permanent stacks: `foundation` (Amazon Elastic Container Registry (ECR) repositories, OpenID Connect (OIDC) roles) and `edge` (CloudFront, S3)], [Verified on AWS], [Applied 5–6 October: the plan, apply and deploy roles and the four `swe5001/*` ECR repositories were created at 22:35 on 5 October and the web bucket at 10:11 on 6 October (read on AWS); present on 8 November (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record])],
    [Unchanged monolith on ECS Fargate (strangler step 0)], [Verified on AWS], [First green promotion, 6 October, #run("37470479078")[run 37470479078]],
    [Promotion pipeline: build, scan, create, smoke, release, destroy], [Verified on AWS], [Green on 6, 17, 19 and 20 October and 10 and 11 November (AD-19, §5.3). On 9 November the eleventh promotion (#pr(757), #run("42587537517")[run 42587537517]) failed at the `edge` step and still destroyed the runtime (#src("docs/swe5001/evidence/20261109-promotion-757/README.md")[record], §6.4)\; its retry (#run("42642746273")[run 42642746273]) also failed. The twelfth (#pr(770), #run("42653347818")[run 42653347818]), 00:18–01:11 SGT on 10 November, built 120 resources from zero, passed, released and destroyed them (#src("docs/swe5001/evidence/20261109-promotion-770/README.md")[record]). The thirteenth (#pr(785), #run("42947289936")[run 42947289936]) on 11 November built 120 resources from zero and passed. The smoke suite is nine read-only, unauthenticated checks over five GET requests through CloudFront: health is UP, the product list is not empty, `auth/me` and the notification list answer 401 without a session, an internal path is not served by a service, and each of the four API answers carries an `X-Trace-Id` header (#src("perf/smoke/smoke.sh")[`smoke.sh`]\; all nine passed in #run("39435280952")[run 39435280952] and #run("42653347818")[run 42653347818])],
    [Three services running on ECS (`distributed` topology)], [Verified on AWS], [#run("39008208244")[Run 39008208244], 17 October; repeated in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October) and #run("42653347818")[run 42653347818] (10 November)\; #run("42665267510")[run 42665267510] (10 November session, #src("docs/swe5001/evidence/20261110-subnet-verification/README.md")[subnet record]) and, on 11 November, promotion #run("42947289936")[run 42947289936] and session #run("42966614196")[run 42966614196] (#src("docs/swe5001/evidence/20261111-load-reruns/README.md")[load-rerun record])],
    [HTTP target-tracking auto-scaling and the CloudWatch dashboard], [R4 workload and scale-out verified on AWS; analyser verdict missing], [On 11 November R4 delivered 726,000 requests up to 600 req/s with no errors or drops while commerce scaled one, two, three, four, six (#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[R4 record]); the k6 gates passed, but the canonical observer ended early, so the analyser gave no verdict. The earlier V3 runs (19 October, 10 November, V3 r1–r3) scaled out but dropped work and failed. No evidence record covers scale-in. Maximum capacity and linear scaling are unproven (§4.1, AISS-12)],
    [Monolith load baseline (V2)], [Verified on AWS], [Passed every plateau up to 200 req/s on 16 October and again on 11 November; 200 req/s is a tested lower bound, not the capacity (§4.1)],
    [Outbox, SNS and SQS fan-out and the idempotent consumer], [Built; routing verified on AWS], [The messaging readiness check (the SNS hop and its filter policy) passed in the promotions; the outbox relay, dead-letter redrive and an end-to-end notification are not recorded on AWS],
    [Stripe test-mode Checkout in `commerce-svc`], [Built; deployed 20 October], [#pr(565), #pr(574)\; no payment on AWS recorded],
    [Identity outage and poison message to the dead-letter queue], [Verified locally only], [15 October rehearsal (§4.2, AISS-13)],
    [Self-healing after a stopped task (V5)], [Not run], [Drill pending (#iss(459))],
    [Safe deployment rollback with a broken image (V6)], [Not run], [Drill cut on 11 October; the circuit breaker is configured (§4.2)],
    [Queue-driven scaling demonstration (V4) and the connection budget], [V4 open; budget calculated], [Queue scaling remains undemonstrated (#iss(458)). The #src("docs/swe5001/evidence/20261110-load-preflight/README.md")[10 November preflight] rejected ten-connection pools (110 connections at maxima against 74 unreserved slots) and recorded the approved five-connection experiment (55 + 3 + 1 + 15 = 74); #pr(784) made pools of five the committed setting for the distributed topology, and R4 ran with them (§4.1, §5.3); arithmetic is not load acceptance],
    [Buyer journey end to end on the deployed system], [Open], [Smoke suite only; manual checks not recorded (#iss(460))],
    [Data subnets for RDS and Valkey (AD-15)], [Verified on AWS, 10 Nov], [Placement and connectivity verified on 10 November for one session (§3.2, AISS-07; #src("docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated session record], released source `93984f61`). CloudTrail records the read sequence between 02:18:08 and 02:18:22 on 10 November. We closed #iss(589); AISS-07 is resolved. #run("42722097786")[Scheduled cleanup] destroyed that runtime at 07:52 on 10 November and ended the session's pool and scaling experiment (§5.3). The 11 November session is in §4.1 and §5.3],
    [Runtime at the status date], [Up], [The 11 November session runtime stayed up for the load reruns; after R4 the commerce bounds were restored to two to six and the load generator was stopped, not terminated (§4.1, §5.3). The nightly safety net or the final destroy on 16 November removes the runtime; no record of that run is retained yet],
  ),
  kind: table,
  caption: [Project status on 11 November 2026. Status words follow the evidence rules of this report: "verified on AWS" needs a dated AWS record; "verified locally" never stands in for it.],
) <tbl-status>

*Outstanding issues.* Each open item has an owner and a proposed date in @tbl-aiss and @tbl-roadmap (§3.4). AISS-07 is resolved; the drills (AISS-13) and the start-up fix (AISS-14) move to R1 (§3.4). R4's incomplete observation record and the unproven maximum capacity and linear scaling remain open under AISS-12. Two project items sit outside the AISS register: progress report 2, due 18 October, has no record (#iss(462)), and the request for milestone and effort records (#iss(620)) is answered by §2.3.

== Project Metrics

=== Milestones

Most delivery milestones were met on time or early. Three slipped: the monolith on ECS by two days, messaging by three and the platform module and outbox by about five; the split absorbed them by finishing four days early. The real misses are in verification on AWS (@tbl-milestones).

#figure(
  table(
    columns: (2fr, 1.2fr, 1.2fr, 2fr),
    table.header([Milestone], [Planned], [Achieved], [Evidence]),
    [Architecture options study and proof of concept (phase 0)], [Before the proposal], [25 Sep – 1 Oct], [#src("docs/swe5001/architecture-options-study/README.md")[Study]\; #src("docs/swe5001/architecture-options-study/poc.md")[proof of concept]],
    [Proposal written], [28 Sep – 4 Oct], [30 Sep (merged); last edit 3 Oct], [#pr(259), #pr(372)],
    [`foundation`, `edge` and `runtime` stacks in Terraform], [28 Sep – 4 Oct], [4 Oct], [#pr(377)–#pr(380)],
    [Monolith on ECS, first green promotion (phase A)], [28 Sep – 4 Oct], [6 Oct], [#run("37470479078")[Run 37470479078], #pr(418)],
    [Platform module, outbox and `notification-svc`], [5–11 Oct], [12–16 Oct], [#pr(471), #pr(473), #pr(485), #pr(513)],
    [`identity-svc`, `commerce-svc`, databases per service, routing (phase B)], [12–18 Oct], [12–14 Oct], [#pr(470), #pr(472), #pr(479)],
    [Messaging in Terraform], [5–11 Oct], [14 Oct], [#pr(487)],
    [Auto-scaling in Terraform], [12–18 Oct], [14 Oct], [#pr(489)],
    [Load baseline and dashboard on AWS], [15 Oct], [16 Oct], [#pr(518)],
    [First green three-service promotion], [19–20 Oct], [17 Oct], [#run("39008208244")[Run 39008208244], #pr(534)],
    [Auto-scaling session on AWS (V3)], [19 Oct], [11 Nov (R4): workload delivered and scale-out shown, observation record incomplete; 19 Oct run failed], [#pr(555); #src("docs/swe5001/evidence/20261111-v3-r4/README.md")[R4 record]],
    [Drills on AWS (V4, V5, V7, identity outage)], [19–22 Oct], [Not achieved; local only], [#iss(458), #iss(459)],
    [Presentation (phase C ends)], [23 Oct, moved to 21 Oct], [21 Oct], [#iss(393)],
    [Remarks answered: report framework, data subnets, diagrams (phase D)], [—], [22 Oct – 6 Nov], [#pr(593), #pr(740), #pr(743), #pr(745), #pr(746)],
    [Section drafts; report submitted and all stacks destroyed], [11 Nov; 16 Nov], [In progress], [#iss(462)],
  ),
  kind: table,
  caption: [Planned and achieved milestones, 2026. Planned dates: #src("docs/swe5001/project-proposal.typ")[proposal, Table 8] and #iss(465)\; the repository has no GitHub milestones.],
) <tbl-milestones>

=== Effort per member

We spent about 50 person-days on SWE5001 up to 19 October, which matches the briefing's guideline of about ten person-days for each of the five members in total (briefing Q8); Liu Jin (12.5) and Pan (11.5) are above ten. All five members agreed the split in @tbl-effort on 19 October and declared it in the presentation. The split does not cover phase D (22 October to 16 November: the answers to the remarks and this report), for which no estimate was agreed.

#figure(
  table(
    columns: (1.3fr, 0.6fr, 3fr),
    table.header([Member], [Effort (pd)], [Main SWE5001 contributions]),
    [Liu Jin], [12.5], [AWS stacks and the release pipeline; the service split; physical design (§3.2)],
    [Panpakorn Siripanich (Pan)], [11.5], [Messaging infrastructure, auto-scaling and the dashboard; shared login session; load tests],
    [Yu Qianshuo (Yorik)], [9], [Outbox and relay; failure drills; detailed design (§3.2.4)],
    [Lim Jiechao], [8.5], [Team lead; AWS Organization, SCP guardrails and Identity Center; architecture options study and proof of concept; presentation],
    [Wang Ziang (Eric)], [8.5], [Notification consumer and dead-letter redrive; demonstration lead; logical and DDD design (§3.1)],
    [*Total*], [*≈ 50*], [],
  ),
  kind: table,
  caption: [Effort per member in person-days (pd), agreed by all members on 19 October 2026. Source: #src("docs/swe5001/presentation/src/slides-main.js")[presentation slide 6].],
) <tbl-effort>

*How the figures were obtained.* Nobody logged hours, although the plan asked each pull request to record them. We therefore summed the planned hours of the issues each member delivered and agreed the split from that sum. The figures are an agreed estimate, not a measurement; the range of 8.5–12.5 person-days reflects that Liu Jin carried phase A and Pan most of the messaging and scaling work of phase B.
