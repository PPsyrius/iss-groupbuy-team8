// 3.4 Architectural Limitation. Owner: Liu Jin (#742). Numbering fixed in ../registers.md.
#import "../lib.typ": *

== Architectural Limitation

The solution has fifteen known limitations. Most are deliberate cost choices for a system that runs a few hours a week. Three need evidence that only an AWS session can give (phase R0), six must be closed before real users (phase R1), three wait until load requires them (phase R2), and three are accepted by design.

*Roadmap.* Each open item is assigned to one phase. The dates below are proposed and *pending team review*; they are derived from the project calendar and the open GitHub issues, as follows:

- The course ends on 16 November 2026 with the final `terraform destroy` (Sprint 4 plan, calendar). No AWS session has run since 19 October 2026; the November runs of `swe5001-env` were nightly `down` runs only. R0 is therefore one evidence session between the section-draft deadline (11 November) and the destroy, which also needs a `dev` → `prod` promotion first, because the data subnets (AD-15) exist only on `dev`.
- The drills behind AISS-13 are scoped in \#459 (estimate 4 h) and \#448 (poison message and backlog scaling); both were due on 20 October and are still open. AISS-12 is moved out of R0: it needs a distributed load generator that \#458 does not provide, so one session cannot close it.
- There is a precedent for a dated acceptance: \#227 accepted the origin-TLS risk for `dev` with an end date of 16 November 2026. R1 and R2 apply only if the platform continues after the course; their dates assume a go-live decision in December 2026.

#figure(
  table(
    columns: (1fr, 1fr, 2.6fr, 0.8fr),
    [Phase], [Target (pending review)], [Content], [Issues],
    [R0 — final evidence session], [Promote by Thu 12 Nov 2026; session on Fri 13 Nov 2026], [Show on AWS what is configured or tested only locally: data-subnet placement (AISS-07), self-healing, poison-message and identity-outage drills (AISS-13). Fix the scaling-restore defect first, so the session does not need manual repair (AISS-14)], [\#589, \#459, \#448],
    [R1 — production readiness], [31 Mar 2027], [Remove single points of failure and close security gaps before real users: multi-zone data tier and NAT (AISS-01), internal service token (AISS-04), HTTPS to the origin (AISS-08), secrets out of state (AISS-09), narrower monitoring permissions (AISS-15); prove linear scaling with a distributed load generator (AISS-12)], [\#227 (NFR-37), \#458],
    [R2 — scale-out], [30 Jun 2027, or earlier if a trigger fires], [Split payment out of commerce (AISS-02), give a database its own server (AISS-05), replace the shared session store (AISS-10). Trigger: commerce needs more than three tasks for a sustained hour, or database CPU stays above 70% at peak], [—],
    [Accepted by design], [Review with R1, 31 Mar 2027], [Kept unless the business need changes (AISS-03, AISS-06, AISS-11)], [—],
  ),
  caption: [Roadmap phases for open limitations. Dates proposed on 5 November 2026 and pending team review.],
) <tab-roadmap>

The key outstanding architectural issues in the solution are as follows:

#figure(
  table(
    columns: (0.7fr, 1.2fr, 1.4fr, 1.3fr, 0.7fr, 0.8fr),
    [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
    [AISS-01], [RDS, Valkey and the NAT instance each run in one zone. A zone failure stops writes, sign-ins or outbound calls], [RDS is single-AZ with no backups; Valkey has one node; one fck-nat instance in zone a serves both zones (AD-10, AD-11)], [Multi-AZ RDS, a Valkey replica with automatic failover, a NAT per zone or VPC interface endpoints. *R1, by 31 Mar 2027* (pending review)], [Liu Jin], [Open (cost choice)],
    [AISS-02], [The commerce service is large. Payment scales and deploys with team purchase], [Team, order and payment share the settlement transaction, so they stay in one service (AD-02)], [Extract payment with a saga for refunds once its load differs from commerce's. *R2, by 30 Jun 2027* (pending review)], [Eric], [Open],
    [AISS-03], [Checkout stops while identity is down], [Commerce checks the buyer with identity synchronously and fails closed (AD-06); the webhook returns 500 and Stripe retries], [Accepted: no wrong refunds or admissions. Later, a short-lived cache or circuit breaker. *review 31 Mar 2027* (pending review)], [Yu Qianshuo], [Accepted by design],
    [AISS-04], [Internal endpoints rely on network rules only. A compromised task in the VPC could call them], [`/internal/**` is blocked at the ALB and skipped by session checks, but carries no service credential], [A service token or mutual TLS on internal calls. *R1, by 31 Mar 2027* (pending review)], [Pan], [Open],
    [AISS-05], [One shared database server. The services share capacity and one failure stops all three], [Three databases on one `db.t4g.micro` (AD-11)], [Move a database to its own server; only connection settings change. *R2, by 30 Jun 2027* (pending review)], [Liu Jin], [Open],
    [AISS-06], [A rebuild takes about 32 min, so a session must start early], [Create, deploy and smoke took about 32 min in promotion run 39008208244 (17 Oct 2026); about 15 min of that is the deliberate 900 s wait for SNS filter policies to apply (16 Oct 2026 startup record)], [Start sessions early; the edge stays permanent (AD-12). *review 31 Mar 2027* (pending review)], [Pan], [Accepted (mitigated)],
    [AISS-07], [Data subnets are not yet proven on AWS], [Database and services shared subnets until 4 Nov 2026; separate data subnets are configured and tested offline (AD-15)], [Record RDS and Valkey subnet placement and the data route table in the next session. *R0, 13 Nov 2026* (pending review)], [Liu Jin], [In progress],
    [AISS-08], [CloudFront → ALB is plain HTTP. Origin traffic is unencrypted inside AWS], [The ALB listens on port 80; access is limited by prefix list and header (AD-09)], [An HTTPS listener with an ACM certificate and HTTPS-only origin policy. *R1, by 31 Mar 2027* (pending review)], [Pan], [Open],
    [AISS-09], [The Valkey AUTH token and the origin secret are in Terraform state. Anyone who can read state can read them], [Database passwords are write-only, but these two have no write-only path in the provider], [Rotate each session (already true: runtime is rebuilt); move to write-only arguments when available. *R1, by 31 Mar 2027* (pending review)], [Pan], [Accepted],
    [AISS-10], [All sessions are in one Valkey node. A restart signs everyone out], [Services read the shared Sa-Token session instead of calling identity (AD-05)], [Short-lived signed tokens with refresh at the edge, or a replicated session store. *R2, by 30 Jun 2027* (pending review)], [Eric], [Open],
    [AISS-11], [Events are eventually consistent. A notification can lag its action by seconds], [Outbox → SNS → SQS adds relay and polling delay (AD-04)], [Accepted: the UI shows progress and the API is the source of truth. *review 31 Mar 2027* (pending review)], [Eric], [Accepted by design],
    [AISS-12], [Linear scaling beyond three tasks is not proven], [On 19 Oct 2026 commerce scaled 1 → 3 tasks at about 455 req/s, the load generator's limit (4.1)], [A distributed load generator; rerun V3 to four or more tasks. *R1, by 31 Mar 2027* (pending review)], [Pan], [Open],
    [AISS-13], [Burst, self-healing, poison-message and identity-outage drills ran locally only], [V4, V5, V7 and the identity outage have local results (4.2), not AWS results], [Run the drills on AWS. *R0, 13 Nov 2026* (pending review)], [Yu Qianshuo], [Open],
    [AISS-14], [An interrupted start-up can leave scaling suspended; an SCP denied a CloudFront metric read], [Recorded on 16 Oct 2026 and repaired by hand (evidence `20261016-http-baseline-dashboard`)], [A retry regression test and fix; settle metric access with the Organization policy owner. *R0, before the 13 Nov 2026 session* (pending review)], [Liu Jin], [Open],
    [AISS-15], [`CloudWatchFullAccessV2` on the apply role is not limited to SWE5001], [The role could change alarms or dashboards outside this project], [Restrict to `blocbuy-swe5001-*` names. *R1, by 31 Mar 2027* (pending review)], [Pan], [Accepted],
  ),
  caption: [Architectural limitations (AISS register; numbering fixed in `registers.md`). Target dates are pending team review.],
) <tab-aiss>
