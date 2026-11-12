// Security: part of 04-quality-attributes.typ (split for parallel editing; output unchanged).
#import "../lib.typ": *

== Security
<sec-security>

BlocBuy layers organisation guardrails, federated access, restricted network paths, service-level authorisation and CI security gates. The 10 November 2026 SGT record verifies dedicated data-subnet placement and required connectivity for that session. These controls do not establish end-to-end encryption or complete least privilege: the origin path uses HTTP and some secrets enter Terraform state.

#let security-source(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

#[
#show raw: set text(size: 8.5pt)
#show table.cell: set block(breakable: false)

=== Assets, threats and assessment scope

The assessed deployment is an academic demonstration, not a trading service: there is no real inventory offered for sale and no real payment acceptance. The seeded catalogue supplies test data; Stripe test-mode flows demonstrate payment integration rather than commercial settlement. That reduces direct financial exposure, but cloud credentials, developer accounts, session data and deployment authority still require protection. It is not a blanket acceptance of the residual risks below.

#figure(
  table(
    columns: (1fr, 1.8fr, 1.7fr),
    table.header([Asset / threat], [Implemented or configured response], [Evidence and remaining exposure]),
    [Deployment authority / unauthorised release], [Reviewed promotion, branch checks and environment-scoped OIDC roles restrict the path from source to cloud changes.], [§5 records GitHub readback and dated jobs. Broad apply privileges and unconfirmed developer-account controls remain.],
    [Payment and resource integrity / forged callback or another buyer's data], [Signature-verified webhooks, server-fixed purchase terms and application ownership checks complement login.], [Appendix A.6, A.10 and A.12 trace the design; §4.2 distinguishes local failure tests from AWS acceptance.],
    [Sessions and internal services / compromised account or peer], [Shared role snapshots and network refusals bound access.], [Local authorisation regressions are retained; an internal peer still lacks authenticated service identity (AISS-04).],
    [Secrets and personal data / interception or privileged read], [TLS on viewer/data connections, encrypted stores and restricted state access protect different boundaries.], [@tbl-security-boundaries identifies the HTTP gap; authorised state readers still see some secrets (AISS-08/09).],
    [Auditability / inability to reconstruct misuse], [Dated operational receipts retain selected actions and results.], [They are not continuous security-event retention; the newer governance handoff below still needs its retained basis.],
  ),
  kind: table,
  caption: [Threat-to-control argument for the demonstration scope. Configuration and design are not penetration-test evidence; detailed mechanisms and dated checks follow.],
) <tbl-security-threats>

=== Organisation guardrails and human access

Verified historically on AWS, not rechecked in this drafting session: the 16–17 October 2026 #security-source("docs/deploy/aws-account-governance/README.md", [account-governance audit]) records the project as a member account under a separately administered AWS Organization. IAM Identity Center gives team members single sign-on (SSO) and one-hour sessions through administrator or power-user permission sets. Organisation administration remains with the management-account owner. This separates workload administration from the authority to relax organisation-wide controls (AD-14).

Identity Center sign-in has multi-factor authentication (MFA) enabled, owner-confirmed by Pan on 9 November 2026. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-source-control-delivery/README.md#owner-confirmation-identity-center-mfa")[confirmation record] is owner testimony, not an independent AWS audit. MFA addresses account sign-in; it does not reduce the permissions available after sign-in.

The October access description is not a November least-privilege finding. Lim's #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/756")[8 November owner handoff] reports an empty developers group, administrator access for members of the admins group, and no configured CloudTrail trail, GuardDuty, Config or Access Analyzer in either account. These are attributable owner-reported observations, not independently reverified here. No configured trail is not the same as no CloudTrail event history. #pending[Lim / Liu Jin: supply or reconcile the retained basis for the 8 November privilege and audit-service findings, including account/region scope, before treating them as the current governance baseline.]

The recorded service control policies (SCPs) are permission ceilings, not grants. `Org-Baseline` blocks member-account exit/closure, creation of a separate Identity Center instance and protected SSO-role changes. `Region-Pin` restricts regional work to Singapore with specified global/scoped exceptions. `Sandbox-Guardrails` restricts costly services and instance classes; it does not impose a spending cap. SCPs also constrain member-account administrator sessions, but not the management account or service-linked roles. On 16 October, project-account probes allowed Singapore EC2 inspection and CloudFront metric reads in `us-east-1`, denied corresponding regional calls in `eu-west-1`, and denied tagging the caller's protected SSO role. Account-exit and Identity Center creation were checked in policy content, not attempted destructively. #security-source("docs/swe5001/evidence/20261016-scp-consolidation/README.md", [Dated SCP probes]).

Two qualifications prevent an overbroad claim. First, the governance audit records two project-account IAM users, including one active access key; federated team/CI access does not mean the entire account has no long-lived credentials. Second, an SCP deny cannot be overridden by a member-account IAM allow. The October CloudFront metric denial was corrected and probed in the #security-source("docs/swe5001/evidence/20261016-scp-consolidation/README.md", [dated SCP consolidation]); the unavailable `us-east-1` CloudTrail read in the November report investigation is a separate audit-access limit. Neither proves unrestricted global-service access. #security-source("docs/deploy/aws-account-governance/README.md", [IAM-user exceptions]).

=== Machine identities, permissions and secrets

Configured in the source inspected on 9 November 2026: GitHub Actions uses OpenID Connect (OIDC) to obtain temporary AWS role credentials instead of stored AWS access keys. Trust checks both audience `sts.amazonaws.com` and the exact environment subject built from immutable repository/owner IDs. The `swe5001-plan` environment reaches the foundation-plan role; `swe5001-demo` reaches separate apply/deploy roles and admits only `prod`. The branch restriction is a GitHub environment control, not a branch name embedded in that environment-scoped OIDC subject. GitHub readback on 9 November confirmed the demo environment has a selected `prod` branch policy and no required environment reviewer; the promotion PR is the review gate, not a second environment approval. #security-source("infra/swe5001/foundation/github-oidc.tf", [Role trust and policies]) · #security-source("infra/swe5001/foundation/main.tf", [Immutable subject construction]) · #security-source("docs/swe5001/impl-github-configuration.md", [Approval model]).

The configured plan role can read foundation state but explicitly denies other state-object reads, including secret-bearing runtime and edge state. Apply-created workload roles must remain under `/swe5001/` with the operator-managed permissions boundary. That boundary caps access; each execution/task role still needs its own grant. A shared ECS execution role reads the configured Parameter Store scopes for startup injection, while service task roles carry runtime permissions such as event publication or queue consumption. These are scoped controls, not proof that every permission is least-privilege: the apply role retains broad managed policies, including `CloudWatchFullAccessV2` (AISS-15). Dedicated live secret-denial and cross-stack isolation checks remain outstanding under NFR-51. #security-source("infra/swe5001/foundation/boundary.tf", [Workload boundary]) · #security-source("infra/swe5001/modules/ecs-cluster/main.tf", [Shared execution role]) · #security-source("docs/swe5001/HANDOFF.md", [Recorded isolation limits]).

Application credentials are configured as AWS Systems Manager (SSM) Parameter Store `SecureString` values and injected into ECS through parameter references, not copied into committed task configuration. Database passwords use ephemeral generation and Terraform write-only arguments. In contrast, the Valkey AUTH token is retained in runtime state and the origin secret is retained in edge/runtime state. Encrypted storage and restricted state access do not make those values invisible to authorised state readers (AISS-09). The origin secret is operator-seeded; rebuilding runtime is not evidence that it rotated. Raw plans/state and full environment dumps must therefore remain private. #security-source("infra/swe5001/modules/rds-postgres/main.tf", [Write-only database passwords]) · #security-source("infra/swe5001/modules/cache/main.tf", [Valkey token]) · #security-source("infra/swe5001/edge/main.tf", [Persistent origin-secret reference]) · #security-source("infra/swe5001/runtime/ecs.tf", [Task secret injection]).

=== Network and database isolation

Configured, not freshly probed on AWS: the public ALB accepts HTTP from CloudFront's origin-facing managed prefix list; service forwarding additionally requires the matching `X-Origin-Secret` header, with a default refusal. ECS tasks have no public IP and accept their service port from the ALB or peer-service security group. An explicit ALB rule blocks `/internal/*` and `/api/internal/*`; CloudFront sends only `/api/*` to the API origin. These restrictions reduce direct external access, but neither the prefix list nor the shared header authenticates an individual internal caller. A compromised peer remains inside the trusted network (AISS-04). #security-source("infra/swe5001/runtime/security-groups.tf", [Network rules]) · #security-source("infra/swe5001/runtime/alb.tf", [Listener refusals]) · #security-source("infra/swe5001/modules/ecs-service/main.tf", [Task placement and forwarding]).

*Network separation was verified on AWS on 10 November 2026 SGT.* The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated record] binds AD-15 to released source `93984f61`: all four running application-task ENIs used the application subnets; RDS and Valkey managed ENIs used the separate data subnets (`10.50.20.0/24`, `10.50.21.0/24`). Effective data routes were local-only. PostgreSQL ingress was TCP 5432 from the service/database-init groups; Valkey ingress was TCP 6379 from the service group only. Public health/catalogue and operator-observed signed-in identity/notification requests succeeded. This resolves AISS-07's recorded placement/connectivity gap (NFR-70), not negative packet-isolation or failover acceptance. The runtime was later destroyed by scheduled cleanup; these are historical observations, not current cloud placement. See §3.2 and @fig-d6-network.

Source inspection also confirms a database and app/owner roles per service on one RDS server. The full Flyway migration stream runs in every database; the grants callback restricts each app role to its service-owned tables. This is an access-control boundary, not a claim that foreign tables are absent or that services have separate database servers. The 12 October local record reports foreign-table permission denials; that is not a fresh restricted-role HTTP test on ECS. Flyway owner credentials are also injected into the service process for migration, so the app-role restriction alone is not a complete process-compromise boundary. #security-source("backend/blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql", [Grant/revoke callback]) · #security-source("docs/agile/sprint-04/log/2026-10-12.md", [Local grant tests]) · §3.2.3.

*Fresh metadata check, 11 November 2026.* Before the larger-task V3 r2 campaign, the committed read-only subnet collector and separately hashed local reviewer again matched all four application-task ENIs to the application subnet set and both store ENIs to the disjoint data subnet set. Local-only data routes and restricted security-group sources passed. This supplements the historical result with new placement/control metadata; signed-in identity/notification connectivity and negative packet isolation were not retested by that collector. RDS was now `db.t4g.small`, not the historical micro class, with PostgreSQL 18.3 unchanged. The session's CLI used the existing Identity Center `AdministratorAccess` assignment: a read-only observer command allowlist is not IAM least privilege. No IAM permission/session-policy change is claimed. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/v3-r2/prechecks/subnet-review.json")[Fresh subnet review] · #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261111-load-reruns/README.md")[Session provenance and boundaries].

=== Encryption boundaries

The following are source/configuration observations as of 9 November 2026, not new transport probes. Transport Layer Security (TLS), encryption at rest and source restrictions address different threats; one does not establish the others.

One narrow control was observed directly: on 10 November SGT, three anonymous static HTTPS responses at Singapore edge `SIN3-P4` each matched all nine expected security-header values. The sampled root, login fallback and JavaScript asset establish those response values, not API coverage, browser CSP compatibility or global propagation. The HTML/asset metadata did not establish a new November frontend build. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261110-spa-security-headers/README.md")[Retained header observations].

#figure(
  table(
    columns: (1.1fr, 2.2fr, 1.7fr),
    table.header([Boundary], [Configured protection], [Limit]),
    [Browser → CloudFront], [HTTP redirects to HTTPS. The edge applies shared content-security, framing, content-type, referrer and transport-security headers.], [Viewer minimum remains TLSv1, not TLS 1.2-only. No web application firewall (WAF) is configured.],
    [CloudFront → ALB → task], [CloudFront origin is `http-only`; the ALB listener and target group use HTTP. Origin prefix-list/header restrictions apply.], [No encryption of these HTTP hops. The header restricts forwarding but supplies no confidentiality (AISS-08).],
    [Task → PostgreSQL], [RDS sets `rds.force_ssl=1`; task JDBC URL uses `sslmode=require`. Database storage encryption is enabled.], [This establishes the declared encryption requirement, not a claim of JDBC certificate/hostname verification or a fresh live TLS check.],
    [Task → Valkey], [Transit and at-rest encryption enabled, AUTH token required, Spring Redis SSL enabled.], [Token persists in Terraform state; authentication does not remove state-reader exposure.],
    [Domain events and static storage], [Domain SNS topic uses `alias/aws/sns`; SQS consumer/dead-letter queues use managed server-side encryption. Consumer queue policy denies insecure transport. SPA bucket uses AES256 server-side encryption with public access blocked and distribution-scoped origin access control.], [Alarm-metadata SNS topic has no encryption key. Managed encryption does not prove consumer authorisation.],
  ),
  kind: table,
  caption: [Configured encryption and access boundaries. Sources: #security-source("infra/swe5001/edge/cloudfront.tf", [edge policy]), #security-source("infra/swe5001/modules/ecs-service/main.tf", [HTTP target]), #security-source("infra/swe5001/modules/rds-postgres/main.tf", [RDS]), #security-source("infra/swe5001/runtime/ecs.tf", [client SSL settings]), #security-source("infra/swe5001/modules/cache/main.tf", [Valkey]), #security-source("infra/swe5001/modules/messaging/main.tf", [messaging]) and #security-source("infra/swe5001/edge/s3.tf", [SPA bucket]).],
) <tbl-security-boundaries>

=== Application and delivery controls

Implemented in the inspected source: Sa-Token checks login on protected `/api/v1/**` routes, while catalogue/invite GETs, OAuth entry/callbacks and signature-authenticated provider webhooks have defined public paths. Business services still own resource-ownership checks; login alone is not authorisation. Outside identity, `SessionStpInterface` reads one validated role/permission snapshot per request; a missing or malformed snapshot supplies no permissions. The 12 October record reports local Redis-backed authorisation regressions, not current authenticated ECS acceptance (NFR-58). Internal identity endpoints remain distributed-only and hidden from the public contract, but hiding documentation is not caller authentication. #security-source("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SaTokenConfig.java", [Login/public-path policy]) · #security-source("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SessionStpInterface.java", [Permission snapshots]) · #security-source("backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/internal/InternalUserController.java", [Internal endpoints]) · #security-source("docs/agile/sprint-04/log/2026-10-12.md", [Dated local verification]).

Configured task hardening includes a read-only container root filesystem and a writable `/tmp` volume. The image-security control and its dated executions are explained once in §5.2 and @fig-d10-cicd-pipeline: the exact built image is scanned before SWE5001 export/publication, and fixable HIGH/CRITICAL findings fail that job. Dependency, secret and static-analysis gates are separate controls. Unfixed/lower-severity exclusions, the dev fast path without a fresh image scan, disabled CodeQL and event-selected rather than complete-history secret scanning remain qualified there. A passing scan or promotion does not establish penetration-test coverage or prove the dedicated IAM denial paths. #security-source("infra/swe5001/modules/ecs-service/main.tf", [Container hardening]) · NFR-02, NFR-03 and NFR-51.

=== Residual risks and verification still required

The owning decisions and proposed roadmap remain in §3.4; this table states the security consequence rather than treating a report draft as a remediation.

The most consequential gaps are privileged control-plane access, secret-bearing state and the unencrypted origin path: they can affect deployment authority or disclose credentials/data. Missing authenticated peer identity increases the impact of a compromised task. Audit-service coverage affects detection and investigation and remains subject to the owner reconciliation above. Unencrypted alarm metadata is a narrower confidentiality concern and does not carry the same consequence as a credential-bearing path. This ordering prioritises the technical exposures; the team has not yet confirmed which risks it accepts for the course or which must block real-user operation.

#figure(
  table(
    columns: (0.7fr, 2.1fr, 2.2fr),
    table.header([AISS], [Current limit], [Required follow-up, not implemented by this report]),
    [04], [Internal calls rely on network trust, without a service token or mutual TLS.], [Add authenticated service identity; test denial from an untrusted peer as well as an external source.],
    [07 (resolved)], [Placement/connectivity verified in the 10 November SGT session, not a failover or negative packet test.], [Retain the dated evidence and recheck effective placement for a future deployment; do not reuse destroyed subnet IDs as current state.],
    [08], [Origin and ALB-to-task HTTP hops are unencrypted.], [Design and deploy verified TLS for the relevant hops; do not treat a prefix list or secret header as encryption.],
    [09], [Origin secret and Valkey token are exposed to authorised state readers.], [Restrict state/plan access, rotate through an approved procedure and adopt a write-only/alternative secret path where supported.],
    [15], [Apply-role CloudWatch privileges are not fully fenced. The November CloudTrail read limit is separate from the corrected October metric SCP denial.], [Narrow permissions and test intended cross-stack denials; preserve audit-access limits rather than expanding grants to recover history.],
  ),
  kind: table,
  caption: [Dated security limitations; IDs and owners follow the report's AISS register. Proposed follow-up is not implemented remediation.],
)

#pending[Liu Jin: review §4.3 against the governance record, infrastructure configuration and §3.4 before submission.]

]
