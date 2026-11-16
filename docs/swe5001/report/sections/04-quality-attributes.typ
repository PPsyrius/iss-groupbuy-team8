#import "../lib.typ": *

= Quality Attributes

For each quality attribute we set a target in §1, chose tactics in §3 and then tried to show on AWS that the target holds. In short, performance is met as a lower bound and modifiability in the source; consistency, scalability, availability and security are partly met, each limited by a named issue. @tbl-qa-status gives the tactic, evidence and limit for each.

#figure(
  table(
    columns: (0.95fr, 1.5fr, 1.6fr, 1.3fr, 1.15fr, 0.85fr, 0.6fr),
    table.header([Quality attribute], [Target], [Tactics (AD)], [Evidence kind and date], [Status], [Limit], [Section]),
    [Consistency], [Exactly one outcome per verified payment: seat and order, or refund obligation], [One settlement transaction with row locks in one global order (AD-02, S8); unique indexes that enforce invariants (§3.2.3); transactional outbox and idempotent consumer (AD-04)], [Tested locally (20-buyer last-seat race); tested in CI, 15 Nov (rollback and duplicate suites); on AWS, 15 Nov (two-buyer journey, R11 database check)], [Partly met: normal path and fail-closed initiation shown on AWS; the last-seat race tested locally only], [AISS-13], [§3.2.4, §4.2],
    [Performance], [Browse p95 < 500 ms per plateau up to 200 req/s, errors < 1%], [Valkey offer cache (`RedisOpenDealOfferCache`); ALB behind CloudFront (AD-09); CloudFront caches the SPA, not `/api/*`], [On AWS, 11 Nov (baseline) and 16 Oct], [Met as a lower bound], [AISS-12], [§4.1],
    [Scalability], [V3 to 600 req/s: errors < 1%, no drops, p95 < 800 ms; one-to-four linearity ≥ 80%], [Request-count target tracking for commerce and queue-backlog tracking for notification (AD-20); V3 r4 ran with the session value 6,000 (§4.1); Fargate and Fargate Spot (AD-07, AD-08)], [On AWS, 11 Nov (V3 r4; earlier V3 runs failed) and 14 Nov (V4 R5, experimental expiry configuration)], [Partly met: V3 r4 met the k6 gates up to 600 req/s with no analyser verdict; linearity unproven; V4 R5 passed with an experimental expiry configuration], [AISS-12, AISS-13, AISS-05], [§4.1],
    [Availability], [No committed payment or event lost; rebuild under 30 min], [Task replacement and circuit breaker; outbox to SNS to SQS with DLQ (AD-04); fail-closed identity call (AD-06); disposable runtime (AD-12)], [Local, 15 Oct; AWS, through 15 Nov], [Partly met: V5, V7 and identity R11 passed on AWS with stated limits; other scenarios open (AISS-13); V8 R4 rebuilt in 29 min 54 s once (14 Nov), after five rebuilds over 30 min], [AISS-01, AISS-06, AISS-13], [§4.2],
    [Security], [Only CloudFront reaches the load balancer; data stores have no route out; no stored cloud keys], [Origin prefix list and secret header (AD-09); tag-fenced SWE5001 stack in the shared account, under SCPs (AD-14); data subnets (AD-15); OIDC roles (AD-17)], [On AWS, 10 Nov (subnet placement); tested in CI (scans)], [Partly met: origin path is HTTP; secrets in Terraform state; one IAM user keeps an active access key], [AISS-08, AISS-09, AISS-15, AISS-17], [§4.3],
    [Modifiability (extensibility)], [A new consumer, service or operation is a bounded edit that the pipeline checks], [Split where no transaction crosses (AD-02); COLA layers and ArchUnit rules (AD-18)], [Tested in CI (ArchUnit rules)], [Met in the source], [AISS-02], [§4.4],
    [Cost], [Runtime billed only while a session runs], [Disposable runtime (AD-12); fck-nat (AD-10); Fargate Spot (AD-08)], [Cost Explorer, billed to 10 Nov; 11 Nov estimated (§6.3)], [Calculated to 10 Nov (one term estimated): about US\$11.6; later session estimate in @tbl-cost], [—], [§6.3],
  ),
  kind: table,
  caption: [Quality-attribute evidence through 15 November 2026. Source: §4.1–§4.4, §5.3 and §6.3.],
) <tbl-qa-status>

#include "04-1-performance.typ"
#include "04-2-availability.typ"
#include "04-3-security.typ"
#include "04-4-extensibility.typ"
