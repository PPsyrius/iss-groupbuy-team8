#import "../lib.typ": *

= Project Conduct

== Project Plan

The plan was 50 person-days in seven work packages, the briefing's guideline of about ten person-days for each of the five members. The estimate covers only the SWE5001 work: the business use cases UC1–UC9 already existed from SWE5006 and are excluded (@tbl-wbs).

#figure(
  table(
    columns: (0.35fr, 1.6fr, 0.45fr, 2.6fr, 1.1fr),
    table.header([WBS], [Work package], [Est. (pd)], [Main tasks (estimate in person-days)], [Delivered by]),
    [1], [Planning and reporting], [3.5], [Proposal and architecture baseline (2.5); fortnightly progress reports (1)], [#pr(259), #iss(462)],
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

Three choices kept the plan inside the guideline. The split stopped at three services, because a split into five or six services (about one per bounded context) was estimated at 50–55 person-days (AD-02, §3.1.1). We did not move business code into new source trees; the services are assembled from the existing modules (AD-01, AD-18). And on 11 October we cut work that would not change the result: the broken-image drill, a forced-failure promotion, the internal-call token, WAF and tracing (#src("docs/swe5001/HANDOFF.md")[handoff record], #src("docs/swe5001/architecture-options-study/README.md")[study README]\; see also @tbl-not-built).

*Scheduling.* SWE5001 ran inside our two-week SWE5006 sprints on the same repository and board: Phase A in Sprint 3 (28 September to 9 October) and Phases B and C in Sprint 4 (12–23 October; #src("docs/agile/sprint-04/plan.md")[Sprint 4 plan]). Phase D followed the presentation. @tbl-sprint-architecture shows what changed in the architecture in each period.

#figure(
  table(
    columns: (1fr, 2.4fr, 1.2fr),
    table.header([Period], [Architecture change], [Main decisions]),
    [Sprint 3, 28 Sep – 9 Oct (phase 0 from 25 Sep)], [Options study and proof of concept (phase 0); `foundation`, `edge` and `runtime` stacks; the unchanged monolith on ECS Fargate (strangler step 0, first green promotion 6 October)], [AD-01, AD-12, AD-17, AD-19],
    [Sprint 4, 12–23 Oct], [Three services assembled from the existing modules; one database per service; outbox → SNS → SQS; one synchronous call; request-count auto-scaling; first three-service promotion 17 October; presentation 21 October], [AD-02, AD-03, AD-04, AD-06, AD-18, AD-20],
    [Phase D, from 22 Oct], [Separate data subnets (AD-15); promotions of 10 and 11 November; load reruns; V4 R5, V5 R3 and V7 R4 passed on AWS within the limits in §4.1–§4.2], [AD-15],
  ),
  kind: table,
  caption: [Architecture changes per period, with the main decisions of each period only; AD-05, AD-07 to AD-11, AD-13, AD-14 and AD-16 are not placed in a period. Source: @tbl-milestones and the AD tables (§3.1.1, §3.2.1, §3.3).],
) <tbl-sprint-architecture>

== Project Status

The scoped platform is built and deployed, with AWS evidence for routing, load-driven scale-out, task replacement and the normal two-buyer paid journey. Verification remains incomplete: @tbl-status distinguishes observed results from configured behaviour, and @tbl-verification-scenarios defines the scenarios V1–V8. Performance and availability results, including their tested configurations, are in §4.1–§4.2.

#figure(
  table(
    columns: (2fr, 1fr, 2.3fr),
    table.header([Capability], [Status], [Evidence and section]),
    [Permanent stacks: `foundation` (Amazon Elastic Container Registry (ECR) repositories, OpenID Connect (OIDC) roles) and `edge` (CloudFront, S3)], [Verified on AWS], [Applied 5–6 October: the plan, apply and deploy roles and the four `swe5001/*` ECR repositories were created at 22:35 on 5 October and the web bucket at 10:11 on 6 October (read on AWS); present on 8 November (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record]); destroyed after the final campaign on 15 November (§5.3)],
    [Unchanged monolith on ECS Fargate (strangler step 0)], [Verified on AWS], [First green promotion, 6 October, #run("37470479078")[run 37470479078]],
    [Promotion pipeline: build, scan, create, smoke, release, destroy], [Verified on AWS], [Six successful promotions from 6 October to 11 November; the failed 9 November promotion also destroyed its runtime (§5.3, @tbl-delivery-runs). Smoke covers nine read-only assertions over five unauthenticated GET requests: health, catalogue, session refusals, internal-route isolation and trace headers (#src("perf/smoke/smoke.sh")[`smoke.sh`])],
    [Three services running on ECS (`distributed` topology)], [Verified on AWS], [First distributed promotion on 17 October; later promotions and sessions in @tbl-delivery-runs (§5.3), placement evidence in §3.2 and load results in §4.1],
    [HTTP target-tracking auto-scaling and the CloudWatch dashboard], [Partly verified on AWS (11 Nov)], [On 11 November V3 r4 met the k6 gates up to 600 req/s while commerce scaled from one to six tasks (1 vCPU and 2 GiB, `db.t4g.small` and a target of 6,000, set for that session); there is no analyser verdict (#src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record], §4.1, AISS-12). After the load, Application Auto Scaling scaled commerce in to two tasks at 23:09–23:21 SGT, as CloudTrail shows (read on 15 November, `docs/swe5001/evidence/20261115-aws-readback/`); we did not test scale-in],
    [Monolith load baseline (V2)], [Verified on AWS], [Passed every plateau up to 200 req/s on 11 November on the released source, the selected baseline; two runs on 16 October corroborate it. 200 req/s is a tested lower bound, not the capacity (§4.1)],
    [Outbox, SNS and SQS fan-out and the idempotent consumer], [Partly verified on AWS (14–15 Nov)], [V4 R5 showed team expiries producing notifications with an experimental expiry configuration (§4.1); V7 R4 isolated a poison message and its alarm email reached one of us (§4.2); @tbl-followup-15nov checks the event and the notifications of a normal paid team],
    [Stripe test-mode Checkout in `commerce-svc`], [Deployed 20 Oct; normal path verified 15 Nov], [#pr(565), #pr(574)\; paid START and JOIN and team success in @tbl-followup-15nov],
    [Identity outage and poison message to the dead-letter queue], [Partly verified on AWS (14–15 Nov)], [V7 R4 and the focused identity-outage run of 15 November (R11) passed with the limits in §4.2; the 13 November identity attempt failed (AISS-13)],
    [Self-healing after a stopped task (V5)], [Verified on AWS (13 Nov)], [R3 passed after we corrected the commerce size, the connection pool and the warm-up; R1 was interrupted and R2 failed (§4.2, AISS-13)],
    [Safe deployment rollback with a broken image (V6)], [Not run], [Drill cut on 11 October; the circuit breaker is configured (§4.2)],
    [Queue-driven scaling (V4) and the connection budget], [Partly verified on AWS (14 Nov); budget calculated], [With an experimental expiry configuration, the unchanged policy grew `notification-svc` to four tasks on its own, and we checked the events, the notifications and the restored settings. It is not a release or capacity test (§4.1, AISS-13). #pr(784) committed five-connection distributed pools; the connection budget is arithmetic, not load acceptance (§4.1, §5.3)],
    [Two-buyer paid journey on the deployed system], [Partly verified on AWS (15 Nov)], [Two buyers paid, the team succeeded and each notification reached the right buyer; the limits of the test data and of the topology comparison are in @tbl-followup-15nov (#iss(460))],
    [Data subnets for RDS and Valkey (AD-15)], [Verified on AWS, 10 Nov], [Placement and connectivity verified for one session (#src("docs/swe5001/evidence/20261110-subnet-verification/README.md")[subnet record], §3.2, AISS-07). #iss(589) is closed and AISS-07 resolved],
  ),
  kind: table,
  caption: [Capability verification through 15 November 2026. Dates in each row identify the observed deployment or test.],
) <tbl-status>

#figure(
  table(
    columns: (0.35fr, 2fr, 2.2fr),
    table.header([ID], [Scenario], [Status and section]),
    [V1], [Smoke suite through CloudFront after each start-up (topology parity)], [Passed in every green promotion (§5.3)],
    [V2], [Monolith load baseline: ten 60 s plateaus up to 200 req/s], [Passed on AWS, 11 Nov (baseline) and 16 Oct (§4.1)],
    [V3], [HTTP auto-scaling: ramp to 600 req/s while commerce scales out], [V3 r4 met the k6 gates, no analyser verdict (§4.1)],
    [V4], [Queue-driven scaling: an expiry burst of about 2,000 teams grows the notification backlog and its tasks], [R5 passed on AWS, 14 Nov, with an experimental expiry configuration; not a release or capacity test (§4.1, AISS-13)],
    [V5], [Self-healing: a task stopped under steady load], [R3 passed on AWS, 13 Nov, after the size and warm-up correction; R1 was interrupted and R2 failed (§4.2, AISS-13)],
    [V6], [Safe deployment: a broken image rolled back by the circuit breaker], [Cut on 11 Oct (§4.2, AISS-13)],
    [V7], [Poison message: a message moved to the dead-letter queue], [R4 passed on AWS, 14 Nov, with a temporary 10 s consumer visibility; the alarm email reached one of us, and we restored the original setting afterwards. R1–R3 failed (§4.2, AISS-13)],
    [V8], [Rebuild from zero: create and destroy durations], [Promotion timings from October and November kept; R4, run by hand, passed on AWS on 14 Nov, including the final clean-up of the new runtime. R1–R3 failed (§5.3)],
  ),
  kind: table,
  caption: [Verification scenarios V1–V8. Source: #src("docs/swe5001/impl-verification-observability.md")[verification plan] and the results in §4.1–§4.2 and §5.3.],
) <tbl-verification-scenarios>

*Outstanding issues.* Each open item has an owner and a proposed date in @tbl-aiss and @tbl-roadmap (§3.4). The principal verification gaps are capacity (AISS-12) and the remaining availability scenarios (AISS-13); the interrupted start-up defect is still open (AISS-14). Progress report 2 has no record (#iss(462)); §2.3 gives the milestone and effort figures.

== Project Metrics

=== Milestones

Most delivery milestones were met on time or early. Four slipped: the monolith on ECS by two days, messaging by three, the load baseline by one and the platform module and outbox by about five; the split absorbed them by finishing four days early. The real misses are in verification on AWS (@tbl-milestones).

#figure(
  table(
    columns: (2fr, 1.2fr, 1.2fr, 2fr),
    table.header([Milestone], [Planned], [Achieved], [Evidence]),
    [Architecture options study and proof of concept (phase 0)], [Before the proposal], [25 Sep – 1 Oct], [#src("docs/swe5001/architecture-options-study/README.md")[Study]\; #src("docs/swe5001/architecture-options-study/poc.md")[proof of concept]],
    [Proposal written and submitted], [28 Sep – 4 Oct], [30 Sep (merged); last edit 3 Oct; submitted on Canvas 4 Oct], [#pr(259)\; the 3 Oct edit was merged with #pr(372), a team-price fix],
    [`foundation`, `edge` and `runtime` stacks in Terraform], [28 Sep – 4 Oct], [4 Oct], [#pr(377)–#pr(380)],
    [Monolith on ECS, first green promotion (phase A)], [28 Sep – 4 Oct], [6 Oct], [#run("37470479078")[Run 37470479078], #pr(418)],
    [Platform module, outbox and `notification-svc`], [5–11 Oct], [12–16 Oct], [#pr(471), #pr(473), #pr(485), #pr(513)],
    [`identity-svc`, `commerce-svc`, databases per service, routing (phase B)], [12–18 Oct], [12–14 Oct], [#pr(470), #pr(472), #pr(479)],
    [Messaging in Terraform], [5–11 Oct], [14 Oct], [#pr(487)],
    [Auto-scaling in Terraform], [12–18 Oct], [14 Oct], [#pr(489)],
    [Load baseline and dashboard on AWS], [15 Oct], [16 Oct], [#pr(518)],
    [First green three-service promotion], [19–20 Oct], [17 Oct], [#run("39008208244")[Run 39008208244], #pr(534)],
    [Auto-scaling session on AWS (V3)], [19 Oct], [11 Nov (V3 r4, no analyser verdict, §4.1); 19 Oct run failed], [#pr(555)\; #src("docs/swe5001/evidence/20261111-v3-r4/README.md")[V3 r4 record]],
    [Drills on AWS (V4, V5, V7, identity outage)], [19–22 Oct], [Target missed: V5 R3 passed 13 Nov; V4 R5 and V7 R4 passed 14 Nov in their test settings; the 13 Nov identity attempt failed], [#iss(458), #iss(459)\; §4.1–§4.2],
    [Paid-team and focused payment-initiation validation], [Not in original October plan], [15 Nov: normal paid START and JOIN verified, and payment initiation with identity down and back up], [@tbl-followup-15nov],
    [Presentation (phase C ends)], [23 Oct, moved to 21 Oct], [21 Oct], [#iss(393)],
    [Remarks answered: report framework, data subnets, diagrams (phase D)], [—], [22 Oct – 6 Nov], [#pr(593), #pr(740), #pr(743), #pr(745), #pr(746)],
    [All SWE5001 stacks destroyed], [16 Nov], [15 Nov], [@tbl-delivery-runs],
    [Section drafts; report submitted], [11 Nov; 16 Nov], [In progress], [#iss(462)],
  ),
  kind: table,
  caption: [Planned and achieved milestones, 2026. Planned dates: #src("docs/swe5001/project-proposal.typ")[proposal, Table 8] and #iss(465)\; the repository has no GitHub milestones.],
) <tbl-milestones>

=== Effort per member

Estimated effort is 77.5 person-days: about 50 through the presentation and 27.5 for the subsequent validation and report work. This is 55% above the briefing's guideline of about ten person-days per member (briefing Q8). The original plan allowed 2.5 person-days for the report but did not budget for the additional AWS sessions, load-test reruns and design work needed to answer the presentation feedback.

#figure(
  table(
    columns: (1.3fr, 0.55fr, 0.55fr, 0.5fr, 2.6fr),
    table.header([Member], [To 19 Oct (pd)], [Phase D (pd)], [Total (pd)], [Main SWE5001 contributions]),
    [Liu Jin], [12.5], [4], [16.5], [AWS stacks and the release pipeline; the service split; physical design (§3.2); report framework],
    [Panpakorn Siripanich (Pan)], [11.5], [12], [23.5], [Messaging infrastructure, auto-scaling and the dashboard; shared login session; load tests; in phase D the data subnets, AWS validation and V3 reruns, acceptance evidence and report review],
    [Yu Qianshuo (Yorick)], [9], [1.5], [10.5], [Outbox and relay; failure drills; detailed design (§3.2.4)],
    [Lim Jiechao], [8.5], [7], [15.5], [Team lead; AWS Organization, SCP guardrails and Identity Center; architecture options study and proof of concept; presentation; report editor],
    [Wang Ziang (Eric)], [8.5], [3], [11.5], [Notification consumer and dead-letter redrive; demonstration lead; logical design and Domain-Driven Design (DDD) models (§3.1)],
    [*Total*], [*≈ 50*], [*≈ 27.5*], [*≈ 77.5*], [],
  ),
  kind: table,
  caption: [Estimated effort by member and phase, in person-days (pd). Sources: #src("docs/swe5001/presentation/src/slides-main.js")[presentation slide 6] and our work estimates.],
) <tbl-effort>

We estimated effort by work package, using six working hours per person-day (#src("docs/agile/sprint-04/plan.md")[Sprint 4 plan, §5]). When we reconstructed the figures, we counted each piece of work once and did not count automated runs as anyone's effort (#src("docs/swe5001/evidence/20261112-issue-789/effort-estimate.md", rev: "ae3a32a3943b7952abdf71e5c85ffd27a24bf704")[reconstruction method]).
