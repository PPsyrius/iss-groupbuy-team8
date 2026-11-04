#import "../lib.typ": *

= Quality Attributes

== Performance
#owner([Panpakorn Siripanich (Pan)], closes: "F1, briefing Q4")
#prompt[The purpose of this section is to describe the solution handles the performance requirement of the system. You can describe the use of caching, load balancing, any optimization that you plan/have done in the design of the system.]
#todo(
  [Metric definitions table: workload model, client and server metrics, pass thresholds.],
  [Results per run (16 Oct V2, 19 Oct V3) with what each run does and does not prove.],
  [Caching, load balancing, scaling policy (12,000 requests per target per minute).],
  [Capacity: measured, projected, next limit; linear scaling not proven (AISS-12).],
)

== Availability
#owner[Yu Qianshuo]
#prompt[The purpose of this section is to describe how the solution handles high availability requirement.]
#todo(
  [Two zones for services; single-zone data tier (AISS-01).],
  [Self-healing, rollback, failure drills; state "AWS" or "local only" for each result (AISS-13).],
)

== Security
#owner[Panpakorn Siripanich (Pan)]
#prompt[The purpose of this section is to describe the security that will be incorporated into the solution which includes any security services: IAM, encryption, hashing, IDS, IPS, etc.]
#todo(
  [Layered controls: Organization SCPs, Identity Center, OIDC, network isolation incl. data subnets (AD-15), encryption, secrets, pipeline scans.],
  [Known gaps by AISS number (AISS-04, -08, -09, -15).],
)

== Extensibility and Maintainability
#owner([Lim Jiechao], closes: "briefing Q2, Q3")
#prompt[The purpose of this section is to describe the solution handles the maintainability requirement. How the platform and applications can be extended, enhanced and maintained in the future? You can refer to the platform section if necessary.]
#todo(
  [Measured reuse: the audit queue added without changing the producer.],
  [Golden-path `ecs-service` module; architecture rules; API contract checks.],
)
