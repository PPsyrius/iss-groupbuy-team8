// 3.4 Architectural Limitation. Owner: Liu Jin (#742). Numbering fixed in ../registers.md.
#import "../lib.typ": *

== Architectural Limitation

The solution has fifteen known limitations. Most are deliberate cost choices for a system that runs a few hours a week, and each has a resolution on the roadmap below. Six are single points of failure or missing protections that a production deployment must close first (phase R1).

#todo([Confirm the roadmap phases and dates with the team at the report meeting; they are proposals until then.])

*Roadmap.* Each open item is assigned to one phase:

#figure(
  table(
    columns: (auto, auto, 1fr),
    [Phase], [Target], [Content],
    [R0 — next AWS session], [November 2026], [Prove what is configured but not yet shown on AWS: data-subnet placement, failure drills, linear scaling (AISS-07, -12, -13, -14)],
    [R1 — production readiness], [Q1 2027], [Remove single points of failure and close the security gaps before real users: multi-zone data tier and NAT, HTTPS to the origin, internal service token, narrower permissions (AISS-01, -04, -08, -09, -15)],
    [R2 — scale-out], [Q2 2027], [Split the large commerce service and the shared server when load requires it (AISS-02, -05, -10)],
    [Accepted by design], [—], [Kept unless the business need changes (AISS-03, -06, -11)],
  ),
  caption: [Roadmap phases for open limitations (proposed, 4 November 2026).],
) <tab-roadmap>

The key outstanding architectural issues in the solution are as follows:

#figure(
  table(
    columns: (auto, 1fr, 1.3fr, 1.1fr, auto, auto),
    [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
    [AISS-01], [RDS, Valkey and the NAT instance each run in one zone. A zone failure stops writes, sign-ins or outbound calls], [RDS is single-AZ with no backups; Valkey has one node; one fck-nat instance in zone a serves both zones (AD-10, AD-11)], [Multi-AZ RDS, a Valkey replica with automatic failover, a NAT per zone or VPC interface endpoints. R1], [Liu Jin], [Open (cost choice)],
    [AISS-02], [The commerce service is large. Payment scales and deploys with team purchase], [Team, order and payment share the settlement transaction, so they stay in one service (AD-02)], [Extract payment with a saga for refunds once its load differs from commerce's. R2], [Eric], [Open],
    [AISS-03], [Checkout stops while identity is down], [Commerce checks the buyer with identity synchronously and fails closed (AD-06); the webhook returns 500 and Stripe retries], [Accepted: no wrong refunds or admissions. Later, a short-lived cache or circuit breaker], [Yu Qianshuo], [Accepted by design],
    [AISS-04], [Internal endpoints rely on network rules only. A compromised task in the VPC could call them], [`/internal/**` is blocked at the ALB and skipped by session checks, but carries no service credential], [A service token or mutual TLS on internal calls. R1], [Pan], [Open],
    [AISS-05], [One shared database server. The services share capacity and one failure stops all three], [Three databases on one `db.t4g.micro` (AD-11)], [Move a database to its own server; only connection settings change. R2], [Liu Jin], [Open],
    [AISS-06], [A rebuild takes about 32 min, so a session must start early], [Create, deploy and smoke took about 32 min in promotion run 39008208244 (17 Oct 2026); about 15 min of that is the deliberate 900 s wait for SNS filter policies to apply (16 Oct 2026 startup record)], [Start sessions early; the edge stays permanent (AD-12)], [Pan], [Accepted (mitigated)],
    [AISS-07], [Data subnets are not yet proven on AWS], [Database and services shared subnets until 4 Nov 2026; separate data subnets are configured and tested offline (AD-15)], [Record RDS and Valkey subnet placement and the data route table in the next session. R0], [Liu Jin], [In progress],
    [AISS-08], [CloudFront → ALB is plain HTTP. Origin traffic is unencrypted inside AWS], [The ALB listens on port 80; access is limited by prefix list and header (AD-09)], [An HTTPS listener with an ACM certificate and HTTPS-only origin policy. R1], [Pan], [Open],
    [AISS-09], [The Valkey AUTH token and the origin secret are in Terraform state. Anyone who can read state can read them], [Database passwords are write-only, but these two have no write-only path in the provider], [Rotate each session (already true: runtime is rebuilt); move to write-only arguments when available. R1], [Pan], [Accepted],
    [AISS-10], [All sessions are in one Valkey node. A restart signs everyone out], [Services read the shared Sa-Token session instead of calling identity (AD-05)], [Short-lived signed tokens with refresh at the edge, or a replicated session store. R2], [Eric], [Open],
    [AISS-11], [Events are eventually consistent. A notification can lag its action by seconds], [Outbox → SNS → SQS adds relay and polling delay (AD-04)], [Accepted: the UI shows progress and the API is the source of truth], [Eric], [Accepted by design],
    [AISS-12], [Linear scaling beyond three tasks is not proven], [On 19 Oct 2026 commerce scaled 1 → 3 tasks at about 455 req/s, the load generator's limit (4.1)], [A distributed load generator; rerun V3 to four or more tasks. R0], [Pan], [Open],
    [AISS-13], [Burst, self-healing, poison-message and identity-outage drills ran locally only], [V4, V5, V7 and the identity outage have local results (4.2), not AWS results], [Run the drills on AWS. R0], [Yu Qianshuo], [Open],
    [AISS-14], [An interrupted start-up can leave scaling suspended; an SCP denied a CloudFront metric read], [Recorded on 16 Oct 2026 and repaired by hand (evidence `20261016-http-baseline-dashboard`)], [A retry regression test and fix; settle metric access with the Organization policy owner. R0], [Liu Jin], [Open],
    [AISS-15], [`CloudWatchFullAccessV2` on the apply role is not limited to SWE5001], [The role could change alarms or dashboards outside this project], [Restrict to `blocbuy-swe5001-*` names. R1], [Pan], [Accepted],
  ),
  caption: [Architectural limitations (AISS register; numbering fixed in `registers.md`).],
) <tab-aiss>
