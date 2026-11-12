#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Legacy helper name; these evidence paths are published at the branch snapshot, not dev.
#let dev-src(path, body) = link(repo + "/blob/0405b1f0cbfd5442cb3dfef084c69f785b272d82/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

= Project Conduct

== Project Plan

The plan was 50 person-days in seven work packages, the briefing's guideline of about ten person-days for each of the five members. The estimate covers only the SWE5001 work: the business use cases UC1–UC9 already existed from SWE5006 and are excluded (@tbl-wbs).

#figure(
  table(
    columns: (0.35fr, 1.6fr, 0.45fr, 2.6fr, 1.1fr),
    table.header([WBS], [Work package], [Est. (pd)], [Main tasks (estimate in person-days)], [Delivered by]),
    [1], [Planning and reporting], [3.5], [Proposal and architecture baseline (2.5); fortnightly progress reports (1)], [#pr(259), #iss(462)],
    [2], [Platform foundation], [6.5], [Shared platform module (3); outbox, SNS/SQS publishing and idempotent consumption (3.5)], [#pr(471), #pr(473), #pr(485), #pr(513)],
    [3], [Service decomposition], [13.5], [`notification-svc` (3), `identity-svc` (4), `commerce-svc` (2); databases and migrations per service (3); demo and load data (1.5)], [#pr(470), #pr(472), #pr(479), #pr(490)],
    [4], [Infrastructure as code], [11], [`foundation` stack (2); network, ECS and the golden-path module (4); RDS, ElastiCache and messaging (2); `edge` stack (1); scaling, dashboard and alarms (2)], [#pr(378)–#pr(380), #pr(487), #pr(489)],
    [5], [DevSecOps pipeline], [6], [Per-service CI with image scanning (2); Terraform plan and apply with Checkov (2); ECS deployment with circuit breaker, environment workflows (2)], [#pr(384), #pr(405)–#pr(418)],
    [6], [Verification], [4.5], [k6 scripts and runs (2); resilience drills (1); end-to-end regression on the deployed system (1.5)], [#pr(506), #pr(518), #pr(500), #iss(460)],
    [7], [Presentation and report], [5], [Slides, demonstration and rehearsal (2.5); report (2.5)], [#iss(461), #iss(462)],
    table.cell(colspan: 2)[*Total*], [*50*], table.cell(colspan: 2)[],
  ),
  kind: table,
  caption: [Work breakdown structure with estimated effort in person-days (pd). Source: #src("docs/swe5001/project-proposal.typ")[proposal, Table 7].],
) <tbl-wbs>

Three choices kept the plan inside the guideline. The split stopped at three services, because the five- or six-service alternative alone was estimated at 50–55 person-days (AD-02, §3.1.1). Business code was not moved into new source trees; the services are assembled from the existing modules (AD-01, AD-18). And on 11 October the team cut work that would not change the result: the broken-image drill, a forced-failure promotion, the internal-call token, WAF and tracing (#src("docs/swe5001/HANDOFF.md")[handoff record], #src("docs/swe5001/architecture-options-study/README.md")[study README]\; see also @tbl-not-built).

*Scheduling.* SWE5001 ran inside the team's two-week SWE5006 sprints on the same repository and board: Phase A in Sprint 3 (to 11 October) and Phases B and C in Sprint 4 (12–23 October). #src("docs/agile/sprint-04/plan.md")[Sprint 4 plan] · #iss(465).

== Project Status

Every use case and platform capability in scope is built; dated AWS records verify building, routing, scale-out and release, not every business flow or acceptance gate. The 10 November 2026 SGT record adds data-subnet placement and required connectivity. V3 R4 on 11 November records successful browse delivery and automatic scaling; canonical collection sign-off, controlled linearity and several availability drills remain open. @tbl-status gives the evidence kind of each capability. Timings and measurements are stated once, in the sections the table points to.

#figure(
  table(
    columns: (2fr, 1fr, 2.3fr),
    table.header([Capability], [Status], [Evidence and section]),
    [Permanent stacks: `foundation` (ECR, OIDC roles) and `edge` (CloudFront, S3)], [Verified on AWS], [Applied 5–6 October; present on 8 November (#dev-src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record])],
    [Unchanged monolith on ECS Fargate (strangler step 0)], [Verified on AWS], [First green promotion, 6 October, #run("37470479078")[run 37470479078]],
    [Promotion pipeline: build, scan, create, smoke, release, destroy], [Verified on AWS], [Green on 6, 17, 19 and 20 October and 9 November (AD-19, §5.3). On 9 November the eleventh promotion (#pr(757)) failed at the `edge` step and still destroyed the runtime (#dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[record], §6.4)\; the twelfth (#pr(770)), the same evening, built 120 resources from zero, passed, released and destroyed them (#dev-src("docs/swe5001/evidence/20261109-promotion-770/README.md")[record]). The smoke suite is nine read-only, unauthenticated checks over five GET requests through CloudFront: health is UP, the product list is not empty, `auth/me` and the notification list answer 401 without a session, an internal path is not served by a service, and each of the four API answers carries an `X-Trace-Id` header (#src("perf/smoke/smoke.sh")[`smoke.sh`]\; all nine passed in #run("39435280952")[run 39435280952] and #run("42653347818")[run 42653347818])],
    [Three services running on ECS (`distributed` topology)], [Verified on AWS], [#run("39008208244")[Run 39008208244], 17 October; repeated in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October) and #run("42653347818")[run 42653347818] (9 November)],
    [HTTP target-tracking auto-scaling and the CloudWatch dashboard], [R4 workload and scale-out verified on AWS], [The selected 11 November V3 R4 result and source-preserving observations are in §4.1. Native workload thresholds passed; the canonical analyzer ended on incomplete observation coverage. Maximum capacity and controlled linearity remain unproven (AISS-12)],
    [Runtime and generator close-out, 12 November SGT], [Verified on AWS], [Separate operator recovery after the failed nightly job removed 127 Terraform-managed runtime resources by 12:13:20 and retired the generator by 12:06:02. Empty managed runtime state, a no-change destroy plan, normal `env-status.sh` exit 0 and 51 direct AWS checks establish the recorded close-out (§5.3). Private S3 archives and one encrypted generator-disk snapshot remain chargeable; foundation/edge and SWE5006 were not targeted],
    [Outbox, SNS/SQS fan-out and the idempotent consumer], [Built; routing verified on AWS], [Messaging readiness check passed in the promotions; no end-to-end notification on AWS is recorded],
    [Stripe test-mode Checkout in `commerce-svc`], [Built; deployed 20 October], [#pr(565), #pr(574)\; no payment on AWS recorded],
    [Identity outage and poison message to the dead-letter queue], [Verified locally only], [15 October rehearsal (§4.2, AISS-13)],
    [Self-healing after a stopped task (V5)], [Not run], [Drill pending (#iss(459))],
    [Safe deployment rollback with a broken image (V6)], [Not run], [Drill cut on 11 October; the circuit breaker is configured (§4.2)],
    [Queue-driven scaling demonstration (V4) and the connection budget], [V4 open; budget calculated], [Queue scaling remains undemonstrated (#iss(458)). The selected R4 configuration retains five-connection pools and the budgeted steady/replacement envelope (§4.1); arithmetic is not pool-wait instrumentation],
    [Buyer journey end to end on the deployed system], [Open], [Smoke suite only; manual checks not recorded (#iss(460))],
    [Data subnets for RDS and Valkey (AD-15)], [Verified on AWS, 10 November SGT], [The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/a05c33fd2b3646ed5aa9a383ee5a0b73bd3eb137/docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated session record] binds actual task/data ENIs, disjoint subnet sets, local-only data routes, security groups, public health/catalogue and operator-observed signed-in identity/notification responses to released source `93984f61`. Negative packet and failover tests are excluded. The operator closed #iss(589); AISS-07 is resolved for that session. #run("42722097786")[Scheduled cleanup] later destroyed runtime; §5.3 records the SQL-budget/pool experiment and its close-out, without a new AWS session],
  ),
  kind: table,
  caption: [Project status reconciled through the 12 November 2026 operator close-out. Status words follow the evidence rules of this report: "verified on AWS" needs a dated AWS record; "verified locally" never stands in for it.],
) <tbl-status>

*Outstanding issues.* Each open item has an owner and a proposed date in §3.4. AISS-07 now has dated placement/connectivity evidence; the remaining drills and startup-recovery evidence (AISS-13, AISS-14) stay subject to the team's R0 decision, not an automatic session authorisation. R4's canonical collection limitation and unproven maximum capacity/linearity remain under AISS-12, separate from its successful measured workload. Two project items sit outside the AISS register: progress report 2, due 18 October, has no record (#iss(462)), and #iss(620) asks for milestone and effort records, which this section answers in part.

== Project Metrics

=== Milestones

Most delivery milestones were met on time or early. The platform module and outbox slipped by about five days, which the split absorbed by finishing four days early, and the real misses are in verification on AWS (@tbl-milestones).

#figure(
  table(
    columns: (2fr, 1.2fr, 1.2fr, 2fr),
    table.header([Milestone], [Planned], [Achieved], [Evidence]),
    [Architecture options study and proof of concept (phase 0)], [Before the proposal], [25 Sep – 1 Oct], [#src("docs/swe5001/architecture-options-study/README.md")[Study]\; #src("docs/swe5001/architecture-options-study/poc.md")[proof of concept]],
    [Proposal submitted], [4 Oct], [4 Oct], [#pr(259)],
    [`foundation`, `edge` and `runtime` stacks in Terraform], [28 Sep – 4 Oct], [4 Oct], [#pr(377)–#pr(380)],
    [Monolith on ECS, first green promotion (phase A)], [9 Oct], [6 Oct], [#run("37470479078")[Run 37470479078], #pr(418)],
    [Platform module, outbox and `notification-svc`], [5–11 Oct], [12–16 Oct], [#pr(471), #pr(473), #pr(485), #pr(513)],
    [`identity-svc`, `commerce-svc`, databases per service, routing (phase B)], [12–18 Oct], [12–14 Oct], [#pr(470), #pr(472), #pr(479)],
    [Messaging and auto-scaling in Terraform], [12–18 Oct], [14 Oct], [#pr(487), #pr(489)],
    [Load baseline and dashboard on AWS], [15 Oct], [16 Oct], [#pr(518)],
    [First green three-service promotion], [19–20 Oct], [17 Oct], [#run("39008208244")[Run 39008208244], #pr(534)],
    [Auto-scaling session on AWS (V3)], [19 Oct], [11 Nov R4 selected; workload and scale-out verified, canonical collection limitation retained], [§4.1; #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/0405b1f0cbfd5442cb3dfef084c69f785b272d82/docs/swe5001/evidence/20261111-v3-r4/README.md")[R4 record]],
    [Drills on AWS (V4, V5, V7, identity outage)], [19–22 Oct], [Not achieved; local only], [#iss(458), #iss(459)],
    [Presentation (phase C ends)], [23 Oct, moved to 21 Oct], [21 Oct], [#iss(393)],
    [Remarks answered: report framework, data subnets, diagrams (phase D)], [—], [22 Oct – 6 Nov], [#pr(593), #pr(740), #pr(743), #pr(745), #pr(746)],
    [Section drafts; report submitted and all stacks destroyed], [11 Nov; 16 Nov], [In progress], [#iss(462)],
  ),
  kind: table,
  caption: [Planned and achieved milestones, 2026. Planned dates: #src("docs/swe5001/project-proposal.typ")[proposal, Table 8] and #iss(465)\; the repository has no GitHub milestones.],
) <tbl-milestones>

=== Effort per member

The team estimated about 50 person-days on SWE5001 *through 19 October*, inside the briefing's guideline of about ten person-days for each member (briefing Q8). All five agreed the historical split in @tbl-effort and declared it in the presentation. This is not the total including post-presentation work.

#figure(
  table(
    columns: (1.3fr, 0.6fr, 3fr),
    table.header([Member], [Effort (pd)], [Main SWE5001 contributions]),
    [Liu Jin], [12.5], [AWS stacks and the release pipeline; the service split; physical design (§3.2)],
    [Panpakorn Siripanich (Pan)], [11.5], [Messaging infrastructure, auto-scaling and the dashboard; shared login session; load tests],
    [Yu Qianshuo (Yorick)], [9], [Outbox and relay; failure drills; detailed design (§3.2.4)],
    [Lim Jiechao], [8.5], [Team lead; AWS Organization, SCP guardrails and Identity Center; architecture options study and proof of concept; presentation],
    [Wang Ziang (Eric)], [8.5], [Notification consumer and dead-letter redrive; demonstration lead; logical and DDD design (§3.1)],
    [*Total*], [*≈ 50*], [],
  ),
  kind: table,
  caption: [Effort per member in person-days (pd), agreed by all members on 19 October 2026. Source: #src("docs/swe5001/presentation/src/slides-main.js")[presentation slide 6].],
) <tbl-effort>

*How the figures were obtained.* Nobody logged hours, although the plan asked each pull request to record them. The team therefore summed the planned hours of the issues each member delivered and agreed the split from that sum. The figures are an agreed estimate, not a measurement; the range of 8.5–12.5 person-days reflects that Liu Jin carried phase A and Pan most of the messaging and scaling work of phase B.

*Pan's Phase D reconstruction (\#789).* For identified work from 22 October through 12 November, a work-package reconstruction assumes 12 h for report/explanation work, 6 h for subnet/edge/publication verification, 10 h for cache/rerun tooling, 16 h for the 11 November sessions/diagnosis/generator work, and 4 h for final audit/handoff: 48 h. At the sprint plan's 6 h per ideal day, that is *8 additional person-days*, or *19.5 including the earlier 11.5*. The assumed range is 37–64 h (about 6.2–10.7 additional days); board estimates and automated runtime do not measure human effort. This is a rounded reconstructed estimate, not timesheets or a new all-team agreement. Other members' Phase D work and future work through submission are not calculated here; the full source inventory and assumptions accompany the local \#789 handoff.
