# SWE5001 final report: AD and AISS registers

Phase 0 output, settled on 4 November 2026. These are the only `AD-nn` and `AISS-nn` numbers the report uses ([`AGENTS.md` § IDs and traceability](AGENTS.md#ids-and-traceability)). Section owners write the full trade-off text in their sections; this file fixes the numbering, the home section and the source.

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
| AD-01 | Strangler migration from the SWE5006 modular monolith to three services; the monolith stays as the fallback topology | Keep the monolith; rewrite as six services | 3.1.1 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | study AD-01 |
| AD-02 | Split where no transaction crosses, not by nouns: identity, commerce, notification | Five services (50–55 person-days); split groupbuy and order | 3.1.1 | [0001](../../../backend/docs/adr/0001-payment-business-integration.md), [0002](../../../backend/docs/adr/0002-team-lifecycle-events.md) | study AD-02; main slide AD-02 |
| AD-03 | One database and one database role per service on one server; grants and architecture tests enforce ownership | Shared schema; one server per service | 3.1.1 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | study AD-07 |
| AD-04 | Transactional outbox → SNS → SQS with dead-letter queues; idempotent consumers | EventBridge; Amazon MQ; MSK; direct REST callbacks | 3.1.1 | [0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md) | study AD-03, AD-09; main slide AD-02 (second use) |
| AD-05 | One shared login session in Valkey with roles inside it | JWT at the edge; Cognito | 3.1.1 | — | study AD-06; main slide AD-04 |
| AD-06 | One synchronous call, commerce → identity, failing closed | Cached identity data; asynchronous lookup | 3.1.1 | — (#468) | main slide AD-05 |
| AD-07 | ECS Fargate on ARM | EKS; App Runner; Lambda | 3.2.1 | — | study AD-08 |
| AD-08 | Fargate Spot above the regular-Fargate minimum (1 : 3) | All on-demand; all Spot | 3.2.1 | — | — |
| AD-09 | CloudFront → ALB restricted by origin prefix list and a secret header | API Gateway with VPC link; public ALB | 3.2.1 | — | study AD-05 |
| AD-10 | fck-nat instance, not a NAT Gateway | NAT Gateway per zone; VPC endpoints only | 3.2.1 | — | main slide AD-11 |
| AD-11 | One RDS server holding three databases | One server per service; Aurora | 3.2.1 | — | main slide AD-12 |
| AD-12 | Permanent edge stack, disposable runtime stack | Always-on runtime | 3.2.1 | — | main slide AD-07 |
| AD-13 | No Kubernetes and no Argo CD | EKS + Argo CD (8–12 days of set-up) | 3.2.1 | — | — |
| AD-14 | Own member account in an AWS Organization with SCP guardrails and Identity Center | Shared course account; IAM users | 3.2.1 | — | main slide AD-10 (OIDC part moves to AD-17) |
| AD-15 | Separate data subnets for RDS and Valkey, with no route out of the VPC | Same private subnets as the services, separated only by security groups | 3.2.1 | — (#589, F6) | new |
| AD-16 | Scheduled jobs stay in the service that owns the data, under a lease | EventBridge Scheduler → Lambda | 3.3 | [0002](../../../backend/docs/adr/0002-team-lifecycle-events.md) addendum | study AD-10 |
| AD-17 | GitHub OIDC roles and SSM Parameter Store; no stored cloud keys | Long-lived access keys in GitHub secrets | 3.3 | — | main slide AD-10 |
| AD-18 | COLA layers and ArchUnit rules keep boundaries inside each service | Package convention only | 3.3 | [0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) | — |
| AD-19 | Release only by a `dev` → `prod` promotion PR that builds, deploys, tests, releases and destroys | Continuous deployment from `dev`; manual applies | 3.3 | — | — |

Not carried over from the study (designed, not built; the section owner may list them under 1.4 "out of scope"): stock reservation (study AD-04), ADOT/X-Ray tracing (study AD-11).

Every AD row in the report must state context, options, choice, trade-off and consequence ([`AGENTS.md`](AGENTS.md) writing rule 3). The 3.2.1 owner checks every cost figure against the evidence records before writing.

## AISS register (section 3.4)

Base: the six items on backup slide B2 keep their numbers. Items found since then follow. Each owner checks the wording against the evidence and confirms the target date at the first report meeting.

| ID | Limitation | Impact | Resolution (roadmap) | Owner | Status | Was |
|---|---|---|---|---|---|---|
| AISS-01 | RDS, Valkey and the NAT instance each run in one zone | One zone failure stops writes, sessions or egress | Multi-AZ RDS, Valkey replica, NAT per zone (production phase) | Liu Jin | Open: accepted for cost | slide B2; study AISS-01/02 |
| AISS-02 | The commerce service is large: payment shares the settlement transaction | Payment scales and deploys with team purchase | Extract payment with a saga for refunds | Eric | Open | slide B2; study AISS-03 |
| AISS-03 | Checkout stops while identity is down | Webhook returns 500; Stripe retries | By design; later a cache or circuit breaker | Yu Qianshuo | Open: by design | slide B2 |
| AISS-04 | `/internal/**` relies on network rules only | A compromised task inside the VPC can call internal endpoints | Service token or mutual TLS | Pan | Open | slide B2 |
| AISS-05 | One shared database server | Shared capacity and blast radius | Move a database to its own server; no code change | Liu Jin | Open | slide B2 |
| AISS-06 | Building the environment takes about 32 minutes (create, deploy and smoke in promotion [run 39008208244](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244), 17 Oct 2026), about 15 of them a deliberate 900-second wait for SNS filter policies to apply ([16 Oct 2026 startup record](../evidence/20261016-http-baseline-dashboard/README.md)) | A session must start early | Start early; edge stays permanent | Pan | Open: mitigated | slide B2 |
| AISS-07 | Database and application tasks shared the same private subnets | Data tier reachable on any route the services had, including NAT egress | Data subnets (AD-15) | Liu Jin | Configured and tested offline on 4 Nov 2026; not yet applied on AWS | F6, #589 |
| AISS-08 | CloudFront → ALB is plain HTTP | Origin traffic is not encrypted inside AWS | HTTPS listener with an ACM certificate | Pan | Open | AGENTS.md claims table |
| AISS-09 | The Valkey auth token and the origin secret are in Terraform state | State readers can see them | Rotate per session; move to write-only arguments when the provider allows it | Pan | Open: accepted | HANDOFF decisions |
| AISS-10 | Shared session store in Valkey | Valkey loss signs everyone out | Short-lived tokens with refresh at the edge | Eric | Open | study AISS-04 |
| AISS-11 | Events are eventually consistent (about 2 s) | A notification can lag the action | UI states that it is updating; the API is the source of truth | Eric | Open: by design | study AISS-05 |
| AISS-12 | V3 linear scaling not proven: the load generator saturated at about 455 req/s with three tasks | Capacity above three tasks is projected, not measured | Distributed load generator; rerun V3 to four tasks | Pan | Open | 19 Oct evidence |
| AISS-13 | The identity outage and a poison message were tested locally only; V4 burst and V5 self-healing were not run | Availability claims rest on local evidence | Run the drills on AWS | Yu Qianshuo | Open | AGENTS.md claims table |
| AISS-14 | An interrupted-bootstrap retry can restore scaling suspension (the SCP that denied a CloudFront metric read was fixed on 16 Oct 2026) | Scaling may stay paused after a retry | Retry regression and fix | Liu Jin | Open | HANDOFF known gaps |
| AISS-15 | `CloudWatchFullAccessV2` on the apply role is not fenced to SWE5001 | Could change non-SWE5001 alarms or dashboards | Narrow to SWE5001 names | Pan | Open: accepted | HANDOFF decisions |

Retired from the study: AISS-06 (stock reservation; not built).
