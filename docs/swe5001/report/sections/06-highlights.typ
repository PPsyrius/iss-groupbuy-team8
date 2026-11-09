#import "../lib.typ": *

// Links in Lim's chapters are pinned to dev at b80debcd (8 November 2026).
#let repo = "https://github.com/PPsyrius/iss-groupbuy-team8"
#let src(path, body) = link(repo + "/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)
// Files added by this change are linked through dev until it merges.
#let dev-src(path, body) = link(repo + "/blob/dev/" + path, body)
#let iss(n) = link(repo + "/issues/" + str(n))[\##n]
#let pr(n) = link(repo + "/pull/" + str(n))[\##n]
#let run(id, body) = link(repo + "/actions/runs/" + id, body)

= Other Things to Be Highlighted

This chapter gathers what the other chapters leave scattered: where each remark from the 21 October presentation is answered, how the briefing's success criteria are met, what the cloud actually cost, what only the real cloud revealed, what the platform measures, and where the system can be seen working.

== Assessor Remarks and Where They Are Answered

The briefing asks the report to incorporate the remarks gathered at the presentation. #iss(596) lists the lecturers' twelve remarks of 21 October 2026, and the report framework records a thirteenth, F12, on the logical diagram. @tbl-remarks gives each remark the section and the artefact that answer it, so that an assessor can check every one.

#figure(
  table(
    columns: (0.35fr, 2.2fr, 0.75fr, 2.4fr),
    table.header([ID], [Remark (21 October 2026)], [Section], [Artefact that answers it]),
    [F1], [What are the metrics for load testing?], [§4.1], [Metric definitions and pass thresholds, then the results of each run with its validity limits],
    [F2], [Does CI/CD scan the container image after building?], [§5.2], [Pipeline diagram (D10) with the image scan between build and export, and the gate rule],
    [F3], [How did we do context mapping?], [§3.1.4.3], [EventStorming → subdomains → contexts, then the context map with a pattern on every edge (@fig-d4-context-map, @tbl-context-map)],
    [F4], [How are the aggregates arrived at?], [§3.1.4.4], [Command → invariant → consistency boundary → aggregate, one row per aggregate (@tbl-aggregates)],
    [—], [How did EventStorming help us arrive at the proposed solution?], [§3.1.4.1], [Event timeline for UC3–UC6 and the trace from each finding to a decision (@fig-d4a-timeline, @tbl-es-traceability)],
    [F5], [Resultant detailed object design], [§3.2.4], [Class diagrams with real class names for the two critical use cases (@d11-paid, @d13-expiry)],
    [F6], [The database should be in its own subnet], [§3.2], [Data subnets with no route out of the VPC (AD-15, @tbl-subnets); tested offline, then applied on AWS once, on 9 November, by a run that failed at the `edge` step and destroyed them 37 minutes later. Placement was not read back, so the remark is answered in design only (AISS-07, #dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record])],
    [F7], [Show the group-buy user flow for two users], [§3.2.4], [Two-buyer sequence including the race for the last seat (@d12-paid)],
    [F8], [More perspectives from Architecting Software Solutions, for example deployment], [§3.1, §3.2], [Logical view (@fig-d3-logical-view), deployment view (@fig-d5-deployment), network view (@fig-d6-network), plus the context (@fig-d1-context) and use case (@fig-d2-use-cases) views],
    [F9], [Detailed DDD diagrams (Platform Engineering)], [§3.1.4], [Context map, aggregate diagrams with invariants and life cycles (@fig-d7-aggregates), domain event catalogue (@fig-d8-domain-events)],
    [F10], [Class and sequence diagrams at class or object level for one or two critical use cases (SAD, SDP)], [§3.2.4], [Class and sequence diagrams for UC4 and for team expiry with refund (@d11-paid to @d14-expiry), with the design patterns used],
    [F11], [Data schemas and models for the data stores], [§3.2.3], [ER diagram per database (@fig-d9-identity, @fig-d9-commerce-team), Valkey key model, event envelope, data classification (@tbl-classification)],
    [F12], [The logical architecture diagram is not standard; revise it], [§3.1.3], [Redrawn technology-neutral UML component view (@fig-d3-logical-view)],
  ),
  kind: table,
  caption: [Presentation remarks and their answers. Wording condensed from #iss(596) and the #src("docs/swe5001/report/README.md")[report framework].],
) <tbl-remarks>

#pending[Status on 8 November: F1 (§4.1) and F2 (§5.2) are still scaffolds; check this table against the final text of every section at the reconcile.]

== How the Briefing's Success Criteria Are Met

The briefing gives five success criteria for the platform. @tbl-success shows how BlocBuy meets each one and what kind of evidence backs it; together with §4.1–§4.4 it answers briefing Q5, which asks for the technical and non-technical benefits to be demonstrated.

#figure(
  table(
    columns: (1.6fr, 2.6fr, 1fr),
    table.header([Success criterion (briefing)], [How BlocBuy meets it], [Evidence]),
    [Identify the seed, producers and consumers of the platform], [Seed: curated team-priced offers; producers: the platform administrator, who publishes listings on sellers' behalf, team leaders and sharers (sellers do not use BlocBuy directly; seller self-service is out of scope, §1.4.2); consumers: joining buyers, and on the technical side the event consumers of §4.4 (§1.3, @tbl-platform-roles, @fig-d1-context)], [Designed and built],
    [Identify the use cases that can expand the ecosystem], [Starting a team (UC3) and sharing an invitation (UC7) that credits the sharer for each referred join (UC12) turn a consumer into a producer who brings in the next buyers], [Built in SWE5006; unchanged in the services],
    [Show how the platform scales the number of customers and producers], [Browsing and paying scale out on `commerce-svc` without touching identity or notification. On 19 October on AWS, commerce scaled from one to three tasks under load (§4.1); the run is invalid as acceptance evidence because the load generator dropped iterations (AISS-12)], [Scale-out verified on AWS],
    [Support more seeds in future], [A new vertical is a new service from the golden-path map entry, reusing sign-in, notification and settlement, and a new consumer is one queue entry (§4.4)], [Built; not yet exercised with a second seed],
    [Identify and perform analytics that improve the platform], [Operational analytics are in use and set the scaling target; business analytics are designed on data already stored (§6.5)], [Operational: dashboard verified on AWS (16 October), alarms configured but never exercised; business: designed],
  ),
  kind: table,
  caption: [Briefing success criteria (briefing V2.6, pp. 10–11).],
) <tbl-success>

== What the Cloud Cost

Calculated: the SWE5001 runtime cost about US\$10 from its first session to 8 November 2026. That is the value of the disposable design (briefing Q6): the environment exists only while it is used, and the permanent layer costs under US\$1 a month. The figures below were read from AWS Cost Explorer on 8 November 2026 and are recorded in #dev-src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[the cost record]. The runtime of about 37 minutes on 9 November (#dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record]) came after that read and is not included.

#figure(
  table(
    columns: (2.4fr, 1fr, 1.4fr),
    table.header([Item], [USD], [Kind]),
    [Whole account, September 2026 (SWE5006 and SWE5001)], [78.51], [Measured],
    [Whole account, October 2026 (SWE5006 and SWE5001)], [139.08], [Measured],
    [ECS, RDS and ElastiCache, which exist only in the SWE5001 runtime, October 2026], [7.11], [Measured],
    [Whole SWE5001 runtime, including its share of the load balancer, NAT instance and public addresses, 6 October – 21 October], [≈ 10.15], [Calculated],
    [Permanent SWE5001 layer (ECR images, the web bucket, an idle CloudFront distribution)], [< 1 a month], [Calculated: about 5.1 GB of images at US\$0.10 per GB-month ≈ 0.51],
  ),
  kind: table,
  caption: [Cost of the SWE5001 platform. The runtime figure is each session day's total minus the SWE5006 baseline of that period; the method is in the cost record.],
) <tbl-cost>

The runtime billed on only nine days: 6–7 October and 15–21 October. For comparison, keeping the same runtime on all the time was estimated at about US\$125 a month (AD-12, §3.2.1).

Three things did not go to plan, and each is a lesson for a production rollout:

- *The bill was about twice the estimate.* The plan expected under US\$5 for about 14 runtime hours. On 19 October the team decided to keep the environment up until the presentation (#src("docs/agile/sprint-04/log/2026-10-19.md")[sprint log]), and 19–21 October carry 85% of the cost. The decision was reasonable for a live demonstration, but nothing showed its price at the time.
- *No alert would have shown it.* The optional budget was never enabled, and no Cost Anomaly Detection monitor exists.
- *The cost cannot be split by course.* The `Course` tag is on every SWE5001 resource, but it is not activated as a cost-allocation tag, and only the management account can activate it. So the runtime figure is calculated, not read directly.

The US\$0.49 for seven sessions shown at the presentation had no committed record and is superseded by this section.

== What Only the Real Cloud Revealed

Mocks, local runs and Terraform plans passed before each of these problems appeared on AWS. Each was found on a real deployment and fixed, or is recorded as a limitation (@tbl-findings). They are the strongest argument for testing on the target cloud.

#figure(
  table(
    columns: (1.9fr, 2.2fr, 1.4fr),
    table.header([Finding on AWS], [What happened], [Outcome]),
    [The first promotion failed five times (6 October)], [Image digests, run variables, a tag policy and an early HTTP 503 each failed in turn], [Fixed one at a time; first green run 6 October (#run("37470479078")[run 37470479078])],
    [The read-only container file system broke the web server], [The embedded server could not write its temporary folder], [A writable `/tmp` volume for every service (§4.4)],
    [The AWS tagging API lists deleted resources], [The teardown check reported resources that no longer existed], [The check asks each service directly],
    [The apply role could not tag scaling targets (16 October)], [Creating the auto-scaling targets needs `application-autoscaling:TagResource`], [Permission added, scoped to the project (#pr(527))],
    [Unconfirmed alarm e-mails stopped deployment twice (16 October)], [The messaging gate waited for SNS e-mail subscriptions that nobody had confirmed], [Opt-out for test runs, on by default (#pr(529)). Every later promotion still found an unconfirmed recipient (§6.5)],
    [New SNS subscription filters take up to 15 minutes to apply], [Events published earlier are silently dropped], [The deployment waits 900 seconds (AISS-06)],
    [An organisation SCP blocked CloudFront metrics (16 October)], [CloudFront metrics are served only from `us-east-1`, which the region guardrail denied], [SCPs consolidated with a narrow exception. #src("docs/swe5001/evidence/20261016-scp-consolidation/README.md")[SCP record]],
    [An interrupted start-up left auto-scaling suspended], [The start-up reported success, but the scaling targets stayed paused], [Repaired by hand; no fix yet (AISS-14)],
    [The apply role could not update the edge's response headers policy (9 November)], [#pr(700) (27 October) added a header to the `edge` stack's response headers policy. The required plan check passed, because for `edge` it runs offline with a mocked provider and the live plan covers only `foundation`; the apply role may read the policy but not update it. With no promotion for 13 days, the gap surfaced at the next release, which was denied `cloudfront:UpdateResponseHeadersPolicy` and destroyed its runtime (#dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record])], [Open: on 9 November neither the permission nor an operator apply of the change exists. Lesson: a plan cannot detect a gap in the apply role's permissions],
    [The edge points at a deleted load balancer after teardown (read 8 November)], [`/api/*` on the SWE5001 distribution names the last runtime's load balancer, which no longer exists], [Harmless while the runtime is down: the next start-up passes the new load balancer to the distribution (#src("docs/swe5001/HANDOFF.md")[handoff record])],
  ),
  kind: table,
  caption: [Problems that appeared only on AWS. Sources: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B8]\; #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record]\; the 8 November AWS read, #dev-src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[cost record, last section]\; #dev-src("docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record].],
) <tbl-findings>

== Analytics

The platform measures how it runs today, and stores the data to measure how the business runs; only the first is built.

*Operational analytics, in use.* On 16 October on AWS, the CloudWatch dashboard showed the edge, the services and the messaging in three rows of charts; its CloudFront chart was first denied by an organisation SCP and fixed the same day (@tbl-findings). The load results of that day set the auto-scaling target (§4.1). Two CloudWatch alarms are configured, on the notification dead-letter queue and on the age of the oldest notification message; both notify an SNS topic with three e-mail subscriptions. They have never been exercised. No session record shows an alarm firing or a notification arriving, and the readiness check of every promotion that reached it found an unconfirmed recipient: two of three in #run("39008208244")[run 39008208244] (17 October), one in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October), and all three in #run("42587537517")[run 42587537517] (9 November). Each run continued only because the opt-out `SWE5001_ALLOW_UNCONFIRMED_ALERTS` defaults to `true` in the deployment workflow (@tbl-findings). An unconfirmed recipient receives no alarm. #src("docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md")[16 October record].

*Business analytics, designed.* The data needed for four business measures is already stored, but nothing reports on it yet:

#figure(
  table(
    columns: (1.6fr, 2fr, 2fr),
    table.header([Measure], [Why it matters], [Data already stored]),
    [Team fill rate and time to fill, per product], [Shows which offers and team sizes work, and when deadlines are too short], [`biz_group_deal` and `biz_group_member` with their timestamps],
    [Referral conversion], [Shows whether sharing brings new buyers, the platform's growth engine], [`biz_share_invite`, `biz_share_credit`, `biz_purchase_intent.sharer_user_id`],
    [Start-to-success conversion], [Shows how many started teams succeed], [`biz_group_deal` status and timestamps],
    [Refund rate per product], [Shows offers that repeatedly fail to fill], [Refunds and expired teams],
  ),
  kind: table,
  caption: [Business measures designed on stored data. Source: #src("docs/swe5001/presentation/src/slides-backup.js")[presentation backup slide B11].],
) <tbl-analytics>

*Next step, designed but not implemented.* An analytics consumer is a read-only subscriber on the event topic, built exactly like the audit queue (§4.4), so `commerce-svc` does not change. The study's pipeline from SNS through Firehose into S3, queried with Athena, was estimated at about US\$0.20 a month. #src("docs/swe5001/architecture-options-study/04-aws-cost-estimate.md")[Cost estimate].

== Where to See the System Working

The runtime is destroyed between sessions (AD-12), so the system is shown through recorded runs and can be rebuilt by one promotion (§4.2) once the `edge` change of 27 October is applied or the apply role may update the response headers policy; until then a promotion fails at the `edge` step, as on 9 November (@tbl-findings). @tbl-demo lists where each part can be seen.

#figure(
  table(
    columns: (1.6fr, 2.4fr, 1.2fr),
    table.header([What], [Where], [Kind]),
    [Two buyers forming one team: leader starts and pays, member joins and pays], [Walkthrough recorded on 24 October 2026 on the SWE5006 `dev` deployment (#iss(590), recording #iss(657)); its design is in §3.2.4. That day the only `swe5001-env` runs were the scheduled nightly `down`s, so no three-service runtime existed. No paid buyer journey on the three-service runtime is recorded (§2.2)], [Recorded on the SWE5006 monolith (EC2), same codebase, `monolith` topology\; not the three-service runtime],
    [Storefront served by the three services through CloudFront, in a browser], [#src("docs/swe5001/evidence/20261019-ecs-demo-images/README.md")[19 October record] of the session created by #run("39198123242")[run 39198123242]: the storefront loaded signed out, but all ten product images were broken by the demo seed (#iss(557))], [Verified on AWS (screenshot, signed out, no purchase)],
    [Live session for the 21 October presentation], [Runtime created by #run("39525458726")[run 39525458726] (21 October, 01:07 UTC, succeeded) and destroyed by the nightly `down` #run("39658040903")[run 39658040903] (21 October, 19:01 UTC)], [Session runs verified on AWS\; what was shown live is not recorded],
    [Three services built from zero, smoke-tested through CloudFront, released and destroyed], [#run("39008208244")[Promotion run 39008208244] (17 October); repeated in #run("39259845179")[run 39259845179] (19 October) and #run("39435280952")[run 39435280952] (20 October)], [Verified on AWS],
    [Auto-scaling from one to three tasks under load], [#src("docs/swe5001/evidence/20261019-v3-autoscaling/README.md")[19 October record] (§4.1)], [Scale-out verified on AWS; invalid as acceptance evidence],
    [Pull-request checks: build, architecture rules, contract check, image scan, Terraform plan], [Every pull request into `dev` and `prod` (§5.2)], [Running in CI],
    [Source code and infrastructure], [#link(repo)[github.com/PPsyrius/iss-groupbuy-team8]], [—],
  ),
  kind: table,
  caption: [Where the system can be seen working.],
) <tbl-demo>

After the final `terraform destroy` of 16 November (@tbl-milestones) removes every stack, the GitHub Actions runs, the evidence records under `docs/swe5001/evidence/` and the source remain, and a rebuild needs an operator to re-apply `foundation` (the GitHub OIDC roles the pipeline assumes and the ECR repositories) and `edge` before any promotion can run (#src("docs/swe5001/impl-aws-infrastructure.md")[infrastructure design], sections 4 and 5).

#pending[Team to decide: if the proposed AWS session of 13 November (phase R0, §3.4) runs, add its run link here as a live rebuild of the current release.]
