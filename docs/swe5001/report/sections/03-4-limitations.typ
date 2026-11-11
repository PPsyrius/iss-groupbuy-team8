// Architectural Limitation: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *
#import "../diagrams/d5-physical-deployment.typ": d5
#import "../diagrams/d6-network.typ": d6
#import "../diagrams/d9-data-models.typ": d9-identity, d9-notification, d9-commerce-team, d9-commerce-payment

// AD and AISS numbers come from ../registers.md. Replace each short description with
// context, options, choice, trade-off and consequence (AGENTS.md writing rule 3).
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  [Identifier], [Description],
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

== Architectural Limitation

The register retains fifteen architectural limitations, including resolved AISS-07. Most remaining limits are deliberate cost choices for a system that runs a few hours a week. The placement/connectivity record does not close the remaining availability drills, startup recovery or performance acceptance. The R0 decision and proposed dates remain subject to Liu Jin's review in \#774; this report does not authorise another AWS session.

*Roadmap.* Each open item is assigned to one phase. #pending[The dates below are proposed and *pending team review*;] they are derived from the project calendar and the open GitHub issues, as follows:

- The course ends on 16 November 2026 with the final `terraform destroy` (Sprint 4 plan, calendar). The second promotion (\#770) built from zero and passed smoke. The later session supplied the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-subnet-verification/README.md")[10 November SGT placement/connectivity evidence] (AISS-07); §4.1 records the failed load campaign. R0's remaining scope is subject to \#774, not a requirement to repeat already retained placement evidence.
- The drills behind AISS-13 are scoped in \#459 (estimate 4 h) and \#448 (poison message and backlog scaling); both were due on 20 October and are still open. #pending[AISS-12 is moved out of R0: it needs a distributed load generator that \#458 does not provide, so one session cannot close it.]
- There is a precedent for a dated acceptance: \#227 accepted the origin-TLS risk for `dev` with an end date of 16 November 2026. #pending[R1 and R2 apply only if the platform continues after the course; their dates assume a go-live decision in December 2026.]

#figure(
  table(
    columns: (1fr, 1fr, 2.6fr, 0.8fr),
    [Phase], [Target #pending[(pending review)]], [Content], [Issues],
    [R0 — final evidence session], [#pending[Promote by Thu 12 Nov 2026; session on Fri 13 Nov 2026]], [Proposed remaining evidence: self-healing, poison-message and identity-outage drills (AISS-13), after startup-recovery correction (AISS-14). AISS-07 already has dated placement/connectivity evidence. Scope/date require the \#774 decision and separate operator approval], [\#774, \#459, \#448],
    [R1 — production readiness], [#pending[31 Mar 2027]], [Remove single points of failure and close security gaps before real users: multi-zone data tier and NAT (AISS-01), internal service token (AISS-04), HTTPS to the origin (AISS-08), secrets out of state (AISS-09), narrower monitoring permissions (AISS-15); prove linear scaling with a distributed load generator (AISS-12)], [\#227 (NFR-37), \#458],
    [R2 — scale-out], [#pending[30 Jun 2027, or earlier if a trigger fires]], [Split payment out of commerce (AISS-02), give a database its own server (AISS-05), replace the shared session store (AISS-10). Trigger: commerce needs more than three tasks for a sustained hour, or database CPU stays above 70% at peak], [—],
    [Accepted by design], [#pending[Review with R1, 31 Mar 2027]], [Kept unless the business need changes (AISS-03, AISS-06, AISS-11)], [—],
  ),
  caption: [Roadmap phases for open limitations. #pending[Dates proposed on 5 November 2026 and pending team review.]],
) <tbl-roadmap>

The key outstanding architectural issues in the solution are as follows:

#figure(
  table(
    columns: (0.7fr, 1.2fr, 1.4fr, 1.3fr, 0.7fr, 0.8fr),
    [Identifier], [Issue and impact], [Description], [Resolution], [Owner], [Status],
    [AISS-01], [RDS, Valkey and the NAT instance each run in one zone. A zone failure stops writes, sign-ins or outbound calls], [RDS is single-AZ with no backups; Valkey has one node; one fck-nat instance in zone a serves both zones (AD-10, AD-11)], [Multi-AZ RDS, a Valkey replica with automatic failover, a NAT per zone or VPC interface endpoints. #pending[*R1, by 31 Mar 2027* (pending review)]], [Liu Jin], [Open (cost choice)],
    [AISS-02], [The commerce service is large. Payment scales and deploys with team purchase], [Team, order and payment share the settlement transaction, so they stay in one service (AD-02)], [Extract payment with a saga for refunds once its load differs from commerce's. #pending[*R2, by 30 Jun 2027* (pending review)]], [Eric], [Open],
    [AISS-03], [Checkout stops while identity is down], [Commerce checks the buyer with identity synchronously and fails closed (AD-06); the webhook returns 500 and Stripe retries], [Accepted: no wrong refunds or admissions. Later, a short-lived cache or circuit breaker. #pending[*review 31 Mar 2027* (pending review)]], [Yu Qianshuo], [Accepted by design],
    [AISS-04], [Internal endpoints rely on network rules only. A compromised task in the VPC could call them], [`/internal/**` is blocked at the ALB and skipped by session checks, but carries no service credential], [A service token or mutual TLS on internal calls. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-05], [One shared database server. The services share capacity and one failure stops all three], [Three databases on one `db.t4g.micro` (AD-11)], [Move a database to its own server; only connection settings change. #pending[*R2, by 30 Jun 2027* (pending review)]], [Liu Jin], [Open],
    [AISS-06], [A rebuild takes about 32 min, so a session must start early], [On AWS on 17 Oct 2026, create, deploy and smoke took about 32 min in promotion #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[run 39008208244]\; about 15 min of that is the deliberate 900 s wait for SNS filter policies to apply (on AWS on 16 Oct 2026, evidence `20261016-http-baseline-dashboard`)], [Start sessions early; the edge stays permanent (AD-12). #pending[*review 31 Mar 2027* (pending review)]], [Pan], [Accepted (mitigated)],
    [AISS-07], [The earlier configuration gave database and application tasks the same eligible subnet set], [AD-15 introduced disjoint data/application subnets. On 10 Nov 2026 SGT, the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated AWS record] verified task/data ENIs, local-only data routes, restricted security groups and required connectivity], [Placement/connectivity evidence retained for \#589. Negative packet-isolation and failover tests are outside this result], [Liu Jin], [Resolved for the recorded session],
    [AISS-08], [CloudFront → ALB is plain HTTP. Origin traffic is unencrypted inside AWS], [The ALB listens on port 80; access is limited by prefix list and header (AD-09)], [An HTTPS listener with an ACM certificate and HTTPS-only origin policy. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-09], [The Valkey AUTH token and the origin secret are in Terraform state. Anyone who can read state can read them], [Database passwords are write-only, but these two have no write-only path in the provider. A runtime rebuild does not rotate the persistent origin secret], [Restrict state access; use an approved rotation procedure and a write-only/alternative secret path where supported. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open (accepted)],
    [AISS-10], [All sessions are in one Valkey node. A restart signs everyone out], [Services read the shared Sa-Token session instead of calling identity (AD-05)], [Short-lived signed tokens with refresh at the edge, or a replicated session store. #pending[*R2, by 30 Jun 2027* (pending review)]], [Eric], [Open],
    [AISS-11], [Events are eventually consistent. A notification can lag its action by seconds], [Outbox → SNS → SQS adds relay and polling delay (AD-04)], [Accepted: the UI shows progress and the API is the source of truth. #pending[*review 31 Mar 2027* (pending review)]], [Eric], [Accepted by design],
    [AISS-12], [Capacity and 80%-linearity are not proven], [October V3 scaled one to three but dropped arrivals. The 10 Nov SGT campaign reached four desired/running tasks but failed error/drop gates and lost final ECS observation coverage (§4.1)], [An explicitly reviewed capacity/linearity design and separately authorised campaign; four tasks alone are not acceptance. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open],
    [AISS-13], [Burst, self-healing, poison-message and identity-outage drills are not proven on AWS], [Tested locally only: the identity outage and a poison message on a local queue (4.2). Not run: V4 burst and V5 self-healing (\#459, \#448)], [Run the drills on AWS. #pending[*R0, 13 Nov 2026* (pending review)]], [Yu Qianshuo], [Open],
    [AISS-14], [An interrupted start-up can leave scaling suspended], [Recorded on AWS on 16 Oct 2026 and repaired by hand (evidence `20261016-http-baseline-dashboard`). The SCP that denied a CloudFront metric read the same day was fixed and probed on 16 Oct 2026 (evidence `20261016-scp-consolidation`)], [A retry regression test and fix. #pending[*R0, before the 13 Nov 2026 session* (pending review)]], [Liu Jin], [Open],
    [AISS-15], [`CloudWatchFullAccessV2` on the apply role is not limited to SWE5001], [The role could change alarms or dashboards outside this project], [Restrict to `blocbuy-swe5001-*` names. #pending[*R1, by 31 Mar 2027* (pending review)]], [Pan], [Open (accepted)],
  ),
  caption: [Architectural limitations (AISS register; numbering fixed in `registers.md`). #pending[Target dates are pending team review.]],
) <tbl-aiss>
