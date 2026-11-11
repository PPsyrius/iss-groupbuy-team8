#import "../lib.typ": *

= Quality Attributes

@tbl-qa-status summarises each quality attribute, the tactic and decision behind it, how far we verified it and the issue that limits it.

#figure(
  table(
    columns: (0.95fr, 1.5fr, 1.6fr, 1.3fr, 1.15fr, 0.85fr, 0.6fr),
    table.header([Quality attribute], [Target], [Tactics (AD)], [Evidence kind and date], [Status], [Limit], [Section]),
    [Performance], [Browse p95 < 500 ms per plateau up to 200 req/s, errors < 1%], [CloudFront static cache; Redis offer cache; ALB behind CloudFront (AD-09)], [On AWS, 16 Oct and 11 Nov], [Met as a lower bound], [AISS-12], [§4.1],
    [Scalability], [V3 to 600 req/s: errors < 1%, no drops, p95 < 800 ms; one-to-four linearity ≥ 80%], [ECS target tracking at 12,000 requests per target per minute (AD-20); Fargate and Fargate Spot (AD-07, AD-08)], [On AWS, 19 Oct, 10 Nov and 11 Nov (V3 r1–r3)], [Not met: every V3 run failed], [AISS-12, AISS-05], [§4.1],
    [Availability], [No committed payment or event lost; rebuild under 30 min], [Task replacement and circuit breaker; outbox → SNS → SQS with DLQ (AD-04); fail-closed identity call (AD-06); disposable runtime (AD-12)], [Tested locally, 15 Oct; rebuilds on AWS, 17 Oct – 11 Nov], [Partly met: rebuild 31–34 min; no drill on AWS], [AISS-01, AISS-06, AISS-13], [§4.2],
    [Security], [Only CloudFront reaches the load balancer; data stores have no route out; no stored cloud keys], [Origin prefix list and secret header (AD-09); own account with SCPs (AD-14); data subnets (AD-15); OIDC roles (AD-17)], [On AWS, 10 Nov (subnet placement); tested in CI (scans)], [Partly met: origin path is HTTP; secrets in Terraform state], [AISS-08, AISS-09, AISS-15], [§4.3],
    [Extensibility], [A new consumer, service or operation is a bounded edit that the pipeline checks], [Split where no transaction crosses (AD-02); COLA layers and ArchUnit rules (AD-18)], [Tested in CI (ArchUnit rules)], [Met in the source], [AISS-02], [§4.4],
    [Cost], [Runtime billed only while a session runs], [Disposable runtime (AD-12); fck-nat (AD-10); Fargate Spot (AD-08)], [Cost Explorer read (§6.3)], [Met (§6.3)], [—], [§6.3],
  ),
  kind: table,
  caption: [Quality-attribute status on 11 November 2026. Source: §4.1–§4.4 and §6.3.],
) <tbl-qa-status>

#include "04-1-performance.typ"
#include "04-2-availability.typ"
#include "04-3-security.typ"
#include "04-4-extensibility.typ"
