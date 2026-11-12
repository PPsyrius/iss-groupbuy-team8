# SWE5001 final report: AD and AISS registers

These are the only `AD-nn` and `AISS-nn` numbers the report uses ([`AGENTS.md` § IDs and traceability](AGENTS.md#ids-and-traceability)). This file fixes the numbering, the home section and the source; the full text of each item is in its home section.

## Why a new numbering

Three sources used the same IDs for different things:

| Source | Example of the clash |
|---|---|
| [`02-proposed-architecture.md` §12–§13](../architecture-options-study/02-proposed-architecture.md) (pre-proposal study) | AD-02 = "groupbuy and order form one service"; AISS-02 = "single NAT gateway" |
| Presentation v3 main slides (21 October) | AD-02 = "one payment, one database transaction"; AD-10 = "no stored cloud keys"; AD-14 = "measured cost" |
| Presentation v3 backup slides B1–B2 | AD-02 = "split where no transaction crosses"; AD-10 = "small NAT instance"; AD-14 = "own account in an AWS Organization" |

Rule: the **backup slides B1–B2** are the base, because they are the most complete and describe what was built. AD-01 comes from the study. New decisions get numbers from AD-15 onward. The study's IDs and the main-slide IDs are not used in the report; the mapping columns below exist only so reviewers can trace old references.

## AD register

| ID | Decision (short) | Rejected options (minimum) | Home section | ADR | Was (study / main slide) |
|---|---|---|---|---|---|
| AD-01 | Strangler migration from the SWE5006 modular monolith to three services; the monolith stays as the fallback topology | One monolithic deployment; separate repositories and pipelines per service | 3.1.1 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | study AD-01 |
| AD-02 | Split where no transaction crosses, not by nouns: identity, commerce, notification | Five or six services (50–55 person-days); split groupbuy and order | 3.1.1 | [0001](../../../backend/docs/adr/0001-payment-business-integration.md), [0002](../../../backend/docs/adr/0002-team-lifecycle-events.md), [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | study AD-02; main slide AD-02 |
| AD-03 | One database and one database role per service on one server; grants and architecture tests enforce ownership | Shared schema; one server per service | 3.1.1 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | study AD-07 |
| AD-04 | Transactional outbox → SNS → SQS with dead-letter queues; idempotent consumers | Synchronous REST or AFTER_COMMIT publish; Step Functions saga; Kafka on MSK or RabbitMQ on Amazon MQ; point-to-point SQS | 3.1.1 | [0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md) | study AD-03, AD-09; main slide AD-02 (second use) |
| AD-05 | One shared login session in Valkey with roles inside it | JWT at the edge; Cognito | 3.1.1 | — | study AD-06; main slide AD-04 |
| AD-06 | One synchronous call, commerce → identity, failing closed | Replicate `sys_user` by events; fail open | 3.1.1 | — (#468) | main slide AD-05 |
| AD-07 | ECS Fargate on ARM | EKS; App Runner; Lambda | 3.2.1 | — | study AD-08 |
| AD-08 | Fargate Spot above the regular-Fargate minimum (1 : 3) | All on-demand; all Spot | 3.2.1 | — | — |
| AD-09 | CloudFront → ALB restricted by origin prefix list and a secret header | API Gateway with VPC link; public ALB | 3.2.1 | — | study AD-05 |
| AD-10 | fck-nat instance, not a NAT Gateway | NAT Gateway per zone; VPC endpoints only | 3.2.1 | — | main slide AD-11 |
| AD-11 | One RDS server holding three databases | One server per service; one shared schema | 3.2.1 | — | main slide AD-12 |
| AD-12 | Permanent edge stack, disposable runtime stack | Always-on runtime | 3.2.1 | — | main slide AD-07 |
| AD-13 | No Kubernetes and no Argo CD | EKS + Argo CD (8–12 days of set-up) | 3.2.1 | — | — |
| AD-14 | Own SWE5001 stack in the shared team account 402178232802, fenced by the apply role's Deny on resources without SWE5001 tags; SCPs and Identity Center govern the account; the `swe5001-poc` member account is closed | Separate member account (`swe5001-poc`, tried and closed); shared account without a fence; IAM users | 3.2.1 | — | main slide AD-10 (OIDC part moves to AD-17) |
| AD-15 | Separate data subnets for RDS and Valkey, with no route out of the VPC | Same private subnets as the services, separated only by security groups | 3.2.1 | — (#589, F6) | new |
| AD-16 | Scheduled jobs stay in the service that owns the data, under a lease | EventBridge Scheduler → Lambda | 3.3 | [0002](../../../backend/docs/adr/0002-team-lifecycle-events.md) addendum | study AD-10 |
| AD-17 | GitHub OIDC roles and SSM Parameter Store; no stored cloud keys in the pipeline | Long-lived access keys in GitHub secrets | 3.3 | — | main slide AD-10 |
| AD-18 | COLA layers and ArchUnit rules keep boundaries inside each service | Package convention only | 3.3 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | — |
| AD-19 | Release only by a `dev` → `prod` promotion PR that builds, deploys, tests, releases and destroys | Continuous deployment from `dev`; manual applies; a long-lived staging runtime | 3.3 | — | — |
| AD-20 | Commerce scales on ALB requests per task: target tracking on `ALBRequestCountPerTarget` at 12,000 requests per target per minute, two to six tasks (one in baseline sessions), cooldowns 60 s out and 180 s in; notification scales on queue backlog per task, target 50, one to four tasks. Driver: scalability | CPU target tracking; step scaling; fixed task count | 3.2.1 | — (16 October policy record) | new |

Not carried over from the study (designed in the options study, not built; report Table 1.7 uses the same wording): stock reservation (study AD-04), ADOT/X-Ray tracing (study AD-11).

## AISS register (section 3.4)

Base: the six items on backup slide B2 keep their numbers. Items found since then follow. Every item names the decision that accepted it ("Accepted in"). Target dates are those of the roadmap table (§3.4). The status words are the same as in the report.

| ID | Limitation | Impact | Accepted in | Resolution (roadmap) | Owner | Status | Was |
|---|---|---|---|---|---|---|---|
| AISS-01 | RDS, Valkey and the NAT instance each run in one zone | One zone failure stops writes, sessions or egress | AD-10, AD-11 | Multi-AZ RDS, Valkey replica, NAT per zone (R1, 31 Mar 2027) | Liu Jin | Open (cost choice) | slide B2; study AISS-01/02 |
| AISS-02 | The commerce service is large: payment shares the settlement transaction | Payment scales and deploys with team purchase | AD-02 | Extract payment with a saga for refunds (R2, 30 Jun 2027) | Eric | Open | slide B2; study AISS-03 |
| AISS-03 | Checkout stops while identity is down | Webhook returns 500; Stripe retries | AD-06 | By design; later a cache or circuit breaker (review 31 Mar 2027) | Yorick | Accepted by design | slide B2 |
| AISS-04 | `/internal/**` relies on network rules only | A compromised task inside the VPC can call internal endpoints | AD-06, AD-09 | Service token or mutual TLS (R1, 31 Mar 2027) | Pan | Open | slide B2 |
| AISS-05 | One shared database server (`db.t4g.micro`; the V3 r2, r3 and r4 runs used a session-only `db.t4g.small`) | Shared capacity and blast radius | AD-11 | Move a database to its own server; no code change (R2, 30 Jun 2027) | Liu Jin | Open | slide B2 |
| AISS-06 | Building the environment takes about 32 min (31 min 37 s on 17 Oct, create, deploy and smoke in promotion [run 39008208244](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244)), of which 15 min (900 s) is the SNS filter wait ([16 Oct startup record](../evidence/20261016-http-baseline-dashboard/README.md)) | A session must start early | AD-12 | Start early; edge stays permanent (review 31 Mar 2027) | Pan | Accepted (mitigated) | slide B2 |
| AISS-07 | Earlier configuration gave data stores and application tasks the same eligible subnet set | Placement and connectivity needed live evidence beyond security-group declarations | AD-15 | AD-15; placement and connectivity verified on 10 Nov ([subnet record](../evidence/20261110-subnet-verification/README.md)); negative packet-isolation and failover tests not run | Liu Jin (design), Pan (verification) | Resolved (10 Nov) | F6, #589/#775 |
| AISS-08 | CloudFront to ALB is plain HTTP | Origin traffic is not encrypted inside AWS | AD-09 | HTTPS listener with an ACM certificate (R1, 31 Mar 2027) | Pan | Open | AGENTS.md claims table |
| AISS-09 | The Valkey auth token and the origin secret are in Terraform state | State readers can see them | AD-09, AD-17 | Restrict state access; agreed rotation and a write-only secret path where supported. A runtime rebuild does not rotate the persistent origin secret (R1, 31 Mar 2027) | Pan | Open (accepted) | HANDOFF decisions |
| AISS-10 | Shared session store in Valkey | Valkey loss signs everyone out | AD-05 | Short-lived tokens with refresh at the edge (R2, 30 Jun 2027) | Eric | Open | study AISS-04 |
| AISS-11 | Events are eventually consistent (lag not measured; estimated from `OutboxRelayJob`: the relay alone adds up to about 2 s) | A notification can lag the action | AD-04 | UI states that it is updating; the API is the source of truth (review 31 Mar 2027) | Eric | Accepted by design | study AISS-05 |
| AISS-12 | Maximum capacity and controlled 80% linearity are not proven. V3 r4 (11 Nov) met the k6 gates up to the tested 600 req/s with no analyser verdict (§4.1); no record covers scale-in | A dynamic one-to-six ramp is not a fixed one-task and four-task capacity pair | AD-20 | Keep V3 r4's results with their limits; measure fixed one-task and four-task throughput and repeat recovery separately (R1, 31 Mar 2027) | Pan | Open | [V3 r4](../evidence/20261111-v3-r4/README.md); earlier: [19 Oct](../evidence/20261019-v3-autoscaling/README.md), [10 Nov](../evidence/20261110-v3-autoscaling/README.md), [11 Nov](../evidence/20261111-load-reruns/README.md), [r3](../evidence/20261111-v3-r3/) |
| AISS-13 | Burst, self-healing, rollback, poison-message and identity-outage drills are not proven on AWS: the identity outage and a poison message were tested locally only; V4 queue-driven scaling, V5 self-healing and V6 rollback (cut 11 Oct) were not run | Availability claims rest on local evidence | AD-04, AD-06, AD-19 | Run the drills on AWS (R1, 31 Mar 2027; moved from R0) | Yorick | Open | AGENTS.md claims table |
| AISS-14 | An interrupted-bootstrap retry can restore scaling suspension | Scaling may stay paused after a retry | AD-12, AD-14 | Retry regression and fix (R1, 31 Mar 2027; moved from R0) | Liu Jin | Open | HANDOFF known gaps |
| AISS-15 | `CloudWatchFullAccessV2` on the apply role is not fenced to SWE5001 | Could change non-SWE5001 alarms, dashboards or metrics (log groups are fenced by name) | AD-17 | Narrow to SWE5001 names (R1, 31 Mar 2027) | Pan | Open (accepted) | HANDOFF decisions |
| AISS-16 | Alarm delivery is untested: no promotion ran with all three email recipients confirmed (1 of 3 confirmed on 17 Oct, 2 of 3 on 19 and 20 Oct, 0 of 3 on 9 and 10 Nov, 1 of 3 on 11 Nov, as that day's topic read also showed), and both up workflows default `SWE5001_ALLOW_UNCONFIRMED_ALERTS` to `true` | A dead-letter or queue-age alarm may reach no one | AD-04, AD-19 | Set the variable to `false`, confirm recipients, trigger one test alarm (R1, 31 Mar 2027) | Pan | Open | #774 |
| AISS-17 | A long-lived IAM access key exists in the project account (two IAM users, one with an active key), beside the keyless pipeline | A leaked key bypasses the OIDC design | AD-14, AD-17 | Remove the key or move its user to Identity Center (R1, 31 Mar 2027) | Lim | Open | §4.3 governance audit |
| AISS-18 | The 11 Nov account read found no CloudTrail trail, GuardDuty detector, Config recorder or Access Analyzer; only the default 90-day event history | A misuse would leave no organisation-wide record | AD-14 | Organisation trail, GuardDuty and Access Analyzer (R1, 31 Mar 2027) | Lim | Open (cost choice) | §4.3, 11 Nov account read |
| AISS-19 | Team-wide GitHub two-factor sign-in is unconfirmed and signed commits are not required | A stolen GitHub account could open a promotion pull request | AD-19 | Confirm two-factor sign-in for all five members; decide on commit signing (R1, 31 Mar 2027) | Pan | Open | #621 |
| AISS-20 | The shared `dev` delivery rebuilds the monolith image and deploys it to the SWE5006 EC2 host without a fresh scan, relying on the pre-merge scan of the same source | A vulnerability published after the merge reaches the shared host unnoticed | AD-19 | Scan the rebuilt image before `publish-ecr-dev`, or promote the scanned pull-request image (R1, 31 Mar 2027) | Pan | Open (accepted) | §5.2 delivery boundary |
| AISS-21 | Released image digests are not scanned again: a later session reuses the recorded release digests (`swe5001-env.yml`, `up` input `release`), and the weekly `security.yml` jobs scan no images | A vulnerability published after a promotion reaches a later session unnoticed | AD-19 | Rescan the release digests before each `up` (R1, 31 Mar 2027) | Liu Jin | Open | §5.2 image gaps (F2) |
| AISS-22 | No workflow signs a service image or records a provenance attestation | The digest identifies the scanned image but does not prove where it was built | AD-19 | Sign images in `swe5001-service-images.yml`, attest their provenance and verify both before deployment (R1, 31 Mar 2027) | Liu Jin | Open | §5.2 image gaps (F2) |

Retired from the study: AISS-06 (stock reservation; not built).
