# BlocBuy microservice architecture study (SWE5001)

Date: 2026-09-25. Status: **proposal, not implemented.** Nothing here changes code or the locked SWE5006 stack. The event backbone (transactional outbox + SNS/SQS) is a message broker in the sense of the backend constraints (decision of 2026-08-30), so it needs **team approval and a new ADR 0003** before any code lands.

## Purpose

This folder is the architecture study for the SWE5001 practice module. It asks whether BlocBuy's COLA modular monolith (Java 21, Spring Boot, PostgreSQL, Redis, Stripe) should become a cloud-native platform of services, and at what cost and effort. It feeds the proposal (due 4 Oct), the conduct window (8-20 Oct), the presentations (23-26 Oct) and the project report (due 16 Nov).

## Executive summary

**1. Viability** (01). A full split into six services is not viable in 50 man-days and would put the funds-safety guarantees (NFR-13/27/30) at risk. Groupbuy, order and payment form one consistency cluster: the last-seat race is a row lock, and team resolution runs `BEFORE_COMMIT`. The verdict is **Option B, a modular-monolith core plus extracted services (strangler)**, with a weighted score of 4.10, against 3.80 for keeping the monolith and 2.70 for full microservices. **2. Target architecture** (02). `team-purchase-service` (groupbuy + order, with the payment module co-deployed until P5.1), `catalog-service` (extracted first, the read-heavy path that scales on its own, with stock reservations), and a new `notification-service`. `growth-service` analytics needs no code (SNS -> Firehose -> S3 -> Athena). `payment-service` and `identity-service` are stretch items. The services integrate through a transactional outbox, one SNS topic and one SQS queue with a DLQ per consumer. Each service owns a schema on one RDS instance. ALB path rules act as the gateway, and Sa-Token sessions are shared through ElastiCache. **3. Kubernetes + Argo CD** (03). Not adopted. The runtime is **ECS on Fargate (ARM64)**, defined in Terraform and deployed by GitHub Actions through OIDC, by image digest, with circuit-breaker rollback ("GitOps-style, push-based"). A k3s + Argo CD side demo is optional stretch (P5.6). **4. AWS cost** (04, ap-southeast-1). The steady state is **USD 220.77 per month** at 24x7. The recommended plan over the 47-day module window costs **USD 271.93**, or USD 191.79 if the stack is torn down after the presentations. For comparison, the current monolith costs USD 87.81 per month and EKS + Argo CD would cost USD 296.55. **5. Effort** (05). One **agent-day** is what one supervised Claude Code-class agent completes in a focused working day: about one reviewed M-sized PR (300-800 lines) or 3-6 S-sized PRs, calibrated on this repository's merged PRs. The core plan is **47 ± 6 agent-days** (PERT) plus 16.9 for stretch. It costs about **23 human man-days** against the 50 man-day guideline, and about 95 man-days without agents. The calendar is the binding constraint: it fits only with **five parallel agent streams from 5 Oct**, finishing on 20-21 Oct. **6. EventStorming and DDD** (06). A simulated three-level EventStorming (34 timeline events, 10 hot spots) finds that **groupbuy and order are one bounded context, Team Purchase**. It also names six services (identity, catalog, team-purchase, payment, notification, growth). 02 adopts those boundaries but extracts catalog first instead of payment. **7. DigitalOcean** (07). DigitalOcean is roughly half the cost (USD 63.15-118.45 per month, USD 97.57-183.01 for the window), but it is not easier: it has no managed messaging, WAF, GitHub OIDC or managed tracing, and rebuilding the working AWS assets costs about 6-10 agent-days. The recommendation is to stay on AWS and trim cost; DOKS is an option for the Kubernetes side demo.

## Documents

| File | Content |
| --- | --- |
| [01-microservice-viability.md](01-microservice-viability.md) | Current code, seams and coupling, transaction boundaries, data ownership, risks, options scored, verdict |
| [02-proposed-architecture.md](02-proposed-architecture.md) | Platform framing, **logical architecture** (UML component view), service catalogue, integration and messaging choice, sagas (UML sequence), identity, data per service, observability and security, scalability scenarios, **physical architecture** (UML deployment view), AD-01 to AD-11, migration phases P0-P5, limitations AISS-01 to AISS-06 |
| [03-k8s-argocd-assessment.md](03-k8s-argocd-assessment.md) | EKS + Argo CD against ECS Fargate, ECS on EC2, App Runner, Lambda and k3s. ECS realisation, Terraform layout, CI/CD, optional Kubernetes side demo, AD-08 |
| [04-aws-cost-estimate.md](04-aws-cost-estimate.md) | Unit prices (Price List API, 2026-09-25), steady-state, demo-hours and load-test variants, baselines, spending plan, cost levers. **Authoritative for prices.** |
| 05-effort-estimate.md (not committed, see the amendment below) | Agent-day definition and calibration, WBS with PERT, human man-days, calendar simulation, compress-and-cut plan. **Authoritative for effort.** |
| [06-eventstorming-ddd.md](06-eventstorming-ddd.md) | Big Picture, Process Level, subdomains, bounded contexts, context maps, tactical design per context, recommended service boundaries, ubiquitous language |
| [07-digitalocean-comparison.md](07-digitalocean-comparison.md) | DigitalOcean service mapping, priced options (DOKS, App Platform, droplet), credits, ease comparison, AWS rebuild cost in agent-days, hybrid options, recommendation |

## Diagrams

Each Mermaid source in `diagrams/` has a rendered `.svg` beside it, and the same source is embedded in the document that uses it.

| Diagram | Kind | Used in |
| --- | --- | --- |
| [current-vs-target](diagrams/current-vs-target.svg) | Current monolith against the target deployables | 01 §8 |
| [logical-architecture](diagrams/logical-architecture.svg) | **Logical** architecture, UML component view | 02 §2 |
| [logical-sequence-join-settlement](diagrams/logical-sequence-join-settlement.svg) | **Logical**, UML sequence: join and settlement saga | 02 §5.1 |
| [logical-sequence-team-expiry-refund](diagrams/logical-sequence-team-expiry-refund.svg) | **Logical**, UML sequence: team expiry and refund | 02 §5.2 |
| [physical-deployment](diagrams/physical-deployment.svg) | **Physical** architecture, UML deployment view on AWS | 02 §10 |
| [aws-cost-breakdown](diagrams/aws-cost-breakdown.svg) | Steady-state cost by group | 04 §1 |
| effort-gantt (not committed) | Core WBS with five agents | 05 §6 |
| [eventstorming-legend](diagrams/eventstorming-legend.svg) | EventStorming colour legend | 06 §1 |
| [eventstorming-big-picture](diagrams/eventstorming-big-picture.svg), [board 2](diagrams/eventstorming-big-picture-2.svg) | Big Picture timeline | 06 §2.2 |
| [eventstorming-process-join-settlement](diagrams/eventstorming-process-join-settlement.svg) | Process level, UC3/UC4 | 06 §3.1 |
| [eventstorming-process-lifecycle-refund](diagrams/eventstorming-process-lifecycle-refund.svg) | Process level, UC5/UC6 | 06 §3.2 |
| [context-map](diagrams/context-map.svg) | Context map today | 06 §4.5 |
| [aggregates](diagrams/aggregates.svg) | Class diagram of the built domain model | 06 §5.5 |
| [context-map-target](diagrams/context-map-target.svg) | Context map, recommended service boundaries (end state) | 06 §6 |

## Mapping to the SWE5001 report template

| Template section | Source in this study |
| --- | --- |
| 2.1 WBS and effort | 05 §4 (WBS, PERT), §5 (human man-days), §6 (calendar, Gantt); 02 §12 (phases) |
| 3.1 Logical architecture | 02 §1-§4 and the logical component diagram; 01 §2 (current state) |
| 3.1.1 Architectural decisions | 02 §11 (AD-01 to AD-11); 03 §6 (AD-08 in detail); 02 §4.1 (messaging options) |
| 3.1.4 Platform design / DDD | 06 §2-§6; 02 §1 (seed, producers, consumers) |
| 3.2 Physical architecture | 02 §10 (deployment diagram); 03 §4 (ECS runtime, Terraform); 04 (bill of materials priced) |
| 3.2.3 Persistence | 02 §7 (schema per service, projections, replicas, retention); 01 §2.3 (table ownership) |
| 3.4 Limitations | 02 §13 (AISS-01 to AISS-06) |
| 4 Quality attributes | 02 §8 (observability, security controls) and §9 (scalability scenarios); 01 §5 (risks); 04 §8 (cost as a quality) |
| 5 DevOps | 03 §4.2-§4.3 (Terraform, GitHub Actions matrix, promotion); 05 D1, D6 (DevSecOps, cost controls) |

## Open decisions for the team

1. **Approve or reject the broker.** Record ADR 0003 "Outbox + SNS/SQS" as proposed and decide by 4 Oct. If it is rejected, the plan falls back to Option A plus a catalog read replica (01 §8).
2. **Extraction order.** Confirm catalog first and payment as stretch (02 alignment table), rather than payment first (06 §6).
3. **Reservation semantics.** Stock is held from intent creation (`ReserveUnit(intentId)`), so abandoned checkouts hold a unit until the TTL (AISS-06). This is a visible behaviour change.
4. **Agent streams.** Confirm that all five members can supervise an agent stream from 5 Oct. With fewer streams, decide on cut 7 (Option A+) by 9 Oct (05 §7).
5. **Budget and account.** Confirm the AWS account, any course credits and the budget alerts at USD 150 and 250. Decide whether to tear the stack down on 27 Oct (04 §7).
6. **Kubernetes side demo.** Decide whether the k3s + Argo CD demo (P5.6) is worth about 2.6 agent-days, and whether to run it on DOKS instead of k3s on EC2 (free control plane, possibly covered by the DigitalOcean new-account credit; 07 §8 H3).
7. **Where this study lives.** The folder is currently excluded from git locally. Decide whether it is committed to the repository or kept as a private working copy.

## Amendment 2026-10-16: adoption into the SWE5001 design

Status: proposal of 2026-09-25, partly adopted. The study was written on 25–26 Sep 2026, before the proposal, and committed on 16 Oct 2026 as decision evidence; the files are unchanged except for this section and two index rows that no longer link to the effort estimate. The effort estimate (05), its Gantt diagram and the five-minute talk script stay outside the repository because the proposal replaced the agent-day model with a 50 person-day WBS. The table records what the as-built design kept, changed or replaced; the cited files are the current source of truth.

| Study item | Outcome | Where it lives now |
| --- | --- | --- |
| AD-08 ECS on Fargate (ARM64), Terraform, GitHub OIDC, deploy by digest; Kubernetes and Argo CD not adopted (03) | Adopted | [`ecs-service` module](../../../infra/swe5001/modules/ecs-service/main.tf), [refactoring plan](../microservice-refactoring-plan.md) |
| AD-03 transactional outbox, one SNS topic, one SQS queue and DLQ per consumer, team approval first | Adopted (recorded as ADR 0007, not 0003) | [ADR 0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md), [messaging module](../../../infra/swe5001/modules/messaging/main.tf) |
| AD-06 shared Sa-Token session store with roles in the session | Adopted; the internal-endpoint token was cut on 11 Oct | [ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) |
| AD-10 leased jobs stay with the service that owns their data | Adopted (scheduling runs only in commerce-svc) | [`runtime/services.tf`](../../../infra/swe5001/runtime/services.tf) |
| fck-nat instead of a NAT gateway; tear down after use (04) | Adopted, and extended to a runtime created and destroyed per session | [`fck-nat.tf`](../../../infra/swe5001/modules/network/fck-nat.tf), [session runbook](../runbook-environment-session.md) |
| Stay on AWS rather than DigitalOcean (07) | Adopted | the whole of `infra/swe5001/` |
| AD-01 strangler towards a modular core plus extracted services | Adapted: one codebase built as two topologies, split by assembly | [ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) |
| AD-07 one RDS instance with a schema per service | Adapted: one instance with a database and runtime role per service | [ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) |
| Consistency cluster and EventStorming hot spots HS-1, HS-5, HS-8 (01, 06) | Adapted: the rationale for keeping settlement and expiry in one transaction, and for the in-transaction outbox | [refactoring plan](../microservice-refactoring-plan.md), [ADR 0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md) |
| Service decomposition: catalog-service first, team-purchase-service, stock reservation and a settlement saga (02) | Replaced on 30 Sep by identity-svc, commerce-svc (catalog, groupbuy, order, payment) and notification-svc, so that no business write spans two services | [refactoring plan](../microservice-refactoring-plan.md), [ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md) |
| WAF, X-Ray tracing, Firehose/Athena analytics, the k3s side demo | Not built (cost and scope cuts of 11 Oct) | [Sprint 4 plan](../../agile/sprint-04/plan.md) |

The diagrams that draw the study's own target design (`logical-architecture`, `logical-sequence-*`, `physical-deployment`, `context-map-target`, `current-vs-target`) show the proposal of 25 Sep, not the as-built services. Review comments on that proposal's reservation and saga flows (lock scope around the catalog call, acknowledging Stripe before a rejection commits, the reservation lifecycle and its key) apply to the replaced decomposition and are left unchanged here; the as-built settlement runs in one commerce-svc transaction.

Errata, recorded here rather than edited into the 25 Sep text:

- 01, 03, 04 and 07 cite `05-effort-estimate.md` or "05 §N"; that file is not committed (see above), and the adopted effort plan is the proposal's 50 person-day WBS (`docs/swe5001/project-proposal.typ` §7).
- 04 §1 and §3: the Variant A line items sum to USD 220.79, not 220.77 (rounding); the module-window total of USD 271.93 is unaffected.
- 06, diagram appendix: the sources are in [`diagrams/`](diagrams/) of this folder, not `docs/SWE5001/architecture-study/diagrams/`.
- 07 §1: "the levers in 04 (§10)" means 04 §8, "Cost-optimisation levers".

## Amendment 2026-10-17: proof of concept

After this study, @limjiechao proved its target platform on AWS in a separate member account (27 Sep–1 Oct): the stack came up from an empty account in about 20 minutes and was torn down cleanly, and seven of its infrastructure elements reappear in the team implementation. The timeline, measurements, AWS cost evidence and a file-level inventory of what reached the team build are in [poc.md](poc.md).
