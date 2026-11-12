// Security: part of 04-quality-attributes.typ.
#import "../lib.typ": *

== Security
<sec-security>

BlocBuy layers organisation guardrails, federated access, restricted network paths, service-level authorisation and CI security gates. These controls do not give end-to-end encryption or complete least privilege: the origin path uses HTTP and some secrets enter Terraform state.

=== Assets, threats and assessment scope

The assessed deployment is an academic demonstration, not a trading service: it offers no real inventory and accepts no real payments. The seeded catalogue supplies test data, and Stripe test mode shows the payment integration without commercial settlement. That reduces direct financial exposure, but cloud credentials, developer accounts, session data and deployment authority still need protection. @tbl-security-threats maps each asset to its controls; it does not accept the residual risks listed at the end of this section.

#figure(
  table(
    columns: (1fr, 1.8fr, 1.7fr),
    table.header([Asset and threat], [Implemented or configured response], [Evidence and remaining exposure]),
    [Deployment authority: unauthorised release], [Reviewed promotion, branch checks and environment-scoped OIDC roles restrict the path from source to cloud changes.], [§5 records the GitHub readback and dated jobs. Broad apply privileges, a long-lived IAM access key and unconfirmed developer two-factor sign-in remain (AISS-15, AISS-17, AISS-19).],
    [Payment and resource integrity: forged callback or another buyer's data], [Signature-verified webhooks, server-fixed purchase terms and application ownership checks complement login.], [§3.2.4 traces the design; §4.2 separates local failure tests from AWS acceptance.],
    [Sessions and internal services: compromised account or peer], [Shared role snapshots and network refusals bound access.], [Local authorisation regressions are retained; an internal peer still lacks authenticated service identity (AISS-04).],
    [Secrets and personal data: interception or privileged read], [TLS on viewer and data connections, encrypted stores and restricted state access protect different boundaries.], [@tbl-security-boundaries shows the HTTP gap; authorised state readers still see some secrets (AISS-08, AISS-09).],
    [Auditability: inability to reconstruct misuse], [Dated operational records keep selected actions and results.], [They are not continuous security-event retention; the project account had no trail, GuardDuty, Config recorder or Access Analyzer on 11 November (AISS-18).],
  ),
  kind: table,
  caption: [Assets, threats and controls in the demonstration scope. Source: the configuration and dated records cited in this section.],
) <tbl-security-threats>

=== Organisation guardrails and human access

On 16–17 October on AWS, the #src("docs/deploy/aws-account-governance/README.md")[account-governance audit] recorded the project as a member account under a separately administered AWS Organization. IAM Identity Center gives each of us single sign-on (SSO) through administrator or power-user permission sets. Sessions last one hour: on 12 November on AWS, all three permission sets had a session duration of one hour, and CloudTrail showed no change to any of them in the previous 90 days. Organisation administration stays with the management-account owner. This separates workload administration from the authority to relax organisation-wide controls (AD-14).

The account owner confirmed on 9 November that Identity Center sign-in requires multi-factor authentication (MFA) (#src("docs/swe5001/evidence/20261109-source-control-delivery/README.md#owner-confirmation-identity-center-mfa")[confirmation record]); this is a statement, not an AWS audit. MFA protects sign-in; it does not reduce the permissions available after sign-in. Two-factor sign-in to GitHub is not yet confirmed for all five of us (AISS-19).

On 11 November on AWS, a read of the project account (Singapore region) found no CloudTrail trail, no GuardDuty detector, no Config recorder and no IAM Access Analyzer (AISS-18). We did not read the management account, because its session had expired, so organisation-level trails are unknown. No configured trail is not the same as no CloudTrail event history: the default 90-day event history remains.

The recorded SCPs are permission ceilings, not grants. `Org-Baseline` blocks member-account exit and closure, creation of a separate Identity Center instance and changes to protected SSO roles. `Region-Pin` restricts regional work to Singapore, with listed global and scoped exceptions. `Sandbox-Guardrails` restricts costly services and instance classes; it sets no spending cap. SCPs also constrain member-account administrator sessions, but not the management account or service-linked roles. On 16 October on AWS, project-account probes (#src("docs/swe5001/evidence/20261016-scp-consolidation/README.md")[dated SCP probes]) allowed Singapore EC2 inspection and CloudFront metric reads in `us-east-1`, denied the same regional calls in `eu-west-1`, and denied tagging the caller's protected SSO role. Account exit and Identity Center creation were checked in the policy text, not attempted. On 11 November on AWS, a re-probe gave the same two regional results. On 12 November on AWS, we re-read the SCP text in the management account, and it matches this description: `Org-Baseline` and `Region-Pin` are attached at the root, and `Sandbox-Guardrails` at the Sandbox organisational unit that holds the project account. `Sandbox-Guardrails` allows only t3, t3a and t4g EC2 instances and db.t3 and db.t4g database classes, and it denies Amazon EKS among other services. We deleted four superseded, unattached SCPs that day, so these three are the organisation's only custom SCPs.

Two qualifications limit this claim. First, the #src("docs/deploy/aws-account-governance/README.md")[governance audit] recorded two project-account IAM users, one with an active access key; federated access for us and for CI does not mean the account has no long-lived credentials (AISS-17). Second, a member-account IAM allow cannot override an SCP deny. The October CloudFront metric denial was corrected in the #src("docs/swe5001/evidence/20261016-scp-consolidation/README.md")[dated SCP consolidation]\; `Region-Pin` still denies CloudTrail event lookups (`cloudtrail:LookupEvents`) in `us-east-1` (read on 11 November), because the corrected exception covers only CloudFront metrics.

=== Machine identities, permissions and secrets

Configured, not tested (read on 9 November): as AD-17 (§3.3) decided, GitHub Actions obtains temporary AWS role credentials through OIDC (#src("infra/swe5001/foundation/github-oidc.tf")[role trust and policies]) instead of stored access keys. Trust checks both the audience `sts.amazonaws.com` and the exact environment subject, built from immutable repository and owner IDs (#src("infra/swe5001/foundation/main.tf")[subject construction]). The `swe5001-plan` environment reaches the foundation-plan role; `swe5001-demo` reaches separate apply and deploy roles and admits only `prod`. The branch restriction is a GitHub environment control, not part of the OIDC subject. GitHub readback on 9 November confirmed that the demonstration environment has a selected `prod` branch policy and no required reviewer (#src("docs/swe5001/impl-github-configuration.md")[approval model]); the promotion pull request is the review gate.

The plan role can read foundation state but denies other state-object reads, including the secret-bearing runtime and edge state. Workload roles created by apply must stay under `/swe5001/` with the permissions boundary that foundation manages (#src("infra/swe5001/foundation/boundary.tf")[workload boundary]). That boundary caps access; each execution or task role still needs its own grant. A shared ECS execution role (#src("infra/swe5001/modules/ecs-cluster/main.tf")[execution role]) reads the configured Parameter Store scopes at start-up, while each service task role carries runtime permissions such as event publication or queue consumption. These scopes are not complete least privilege: the apply role keeps broad managed policies, including `CloudWatchFullAccessV2` (AISS-15), and live secret-denial and cross-stack isolation checks remain outstanding under NFR-51 (#src("docs/swe5001/HANDOFF.md")[recorded isolation limits]).

As AD-17 decided, application credentials are SSM Parameter Store `SecureString` values that ECS injects through parameter references (#src("infra/swe5001/runtime/ecs.tf")[task secret injection]); none is copied into committed task configuration. Database passwords use ephemeral generation and Terraform write-only arguments (#src("infra/swe5001/modules/rds-postgres/main.tf")[write-only passwords]). The Valkey AUTH token (#src("infra/swe5001/modules/cache/main.tf")[Valkey token]) and the origin secret (#src("infra/swe5001/edge/main.tf")[origin-secret reference]) stay in Terraform state, so authorised state readers can see them despite encrypted storage (AISS-09). The origin secret is seeded once; rebuilding the runtime does not rotate it. Raw plans, state and full environment dumps therefore stay private.

=== Network and database isolation

Configured, not tested: the public ALB accepts HTTP only from CloudFront's origin-facing managed prefix list (#src("infra/swe5001/runtime/security-groups.tf")[network rules]), and service forwarding also requires the matching `X-Origin-Secret` header, refusing by default. ECS tasks have no public IP and accept their service port only from the ALB or a peer-service security group (#src("infra/swe5001/modules/ecs-service/main.tf")[task placement and forwarding]). An explicit ALB rule blocks `/internal/*` and `/api/internal/*` (#src("infra/swe5001/runtime/alb.tf")[listener refusals]), and CloudFront sends only `/api/*` to the API origin. Neither the prefix list nor the shared header authenticates an individual internal caller, so a compromised peer stays inside the trusted network (AISS-04).

Data-subnet placement was verified on AWS on 10 November (AD-15, AISS-07; full statement in §3.2). On 11 November on AWS, before V3 r2, the committed read-only subnet collector and a separate local reviewer (#src("docs/swe5001/evidence/20261111-load-reruns/v3-r2/prechecks/subnet-review.json")[subnet review]) again matched all four application-task ENIs to the application subnets and both store ENIs to the disjoint data subnets; local-only data routes and restricted security-group sources passed. That collector did not retest signed-in connectivity or negative packet isolation. The session's sizing differed from the committed design (§4.1.6); it did not change the network placement. The session used the existing Identity Center `AdministratorAccess` assignment, so its read-only command allowlist was not IAM least privilege (#src("docs/swe5001/evidence/20261111-load-reruns/README.md")[session record]).

Configured, not tested (read on 9 November): each service has its own database and app and owner roles on one RDS server (§3.2.3). The full Flyway migration stream runs in every database, and the grants callback (#src("backend/blocbuy-platform/src/main/resources/db/swe5001/afterMigrate__service_grants.sql")[grant callback]) restricts each app role to its own tables. This is an access-control boundary: foreign tables still exist, and the services share one server. Tested locally, not on AWS: the 12 October record (#src("docs/agile/sprint-04/log/2026-10-12.md")[local grant tests]) reports foreign-table permission denials. Flyway owner credentials are also injected into the service process for migration, so the app-role restriction alone does not contain a compromised process.

=== Encryption boundaries

Configured, not tested (read on 9 November): TLS, encryption at rest and source restrictions address different threats, and one does not establish the others (@tbl-security-boundaries).

On 10 November on AWS, three anonymous static HTTPS responses at the Singapore edge `SIN3-P4` each matched all nine expected security-header values (#src("docs/swe5001/evidence/20261110-spa-security-headers/README.md")[header record]). The sampled root, login fallback and JavaScript asset show those values only, not API coverage, browser CSP compatibility or global propagation.

#figure(
  table(
    columns: (1.1fr, 2.2fr, 1.7fr),
    table.header([Boundary], [Configured protection], [Limit]),
    [Browser to CloudFront], [HTTP redirects to HTTPS. The edge applies shared content-security, framing, content-type, referrer and transport-security headers.], [Viewer minimum remains TLSv1, not TLS 1.2 only. No WAF is configured.],
    [CloudFront to ALB to task], [CloudFront origin is `http-only`; the ALB listener and target group use HTTP. Origin prefix-list and header restrictions apply.], [These HTTP hops are not encrypted. The header restricts forwarding but gives no confidentiality (AISS-08).],
    [Task to PostgreSQL], [RDS sets `rds.force_ssl=1`; the task JDBC URL uses `sslmode=require`. Database storage encryption is enabled.], [This sets the encryption requirement; it does not verify the JDBC certificate or hostname.],
    [Task to Valkey], [Transit and at-rest encryption enabled, AUTH token required, Spring Redis SSL enabled.], [The token persists in Terraform state (AISS-09).],
    [Domain events and static storage], [The domain SNS topic uses `alias/aws/sns`; SQS consumer and dead-letter queues use managed server-side encryption. The consumer queue policy denies insecure transport. The SPA bucket uses AES256 server-side encryption, blocks public access and admits only its distribution through origin access control.], [The `alerts` SNS topic for alarms has no encryption key. Managed encryption does not prove consumer authorisation.],
  ),
  kind: table,
  caption: [Encryption and access boundaries as configured. Source: #src("infra/swe5001/edge/cloudfront.tf")[edge policy], #src("infra/swe5001/modules/ecs-service/main.tf")[HTTP target], #src("infra/swe5001/modules/rds-postgres/main.tf")[RDS], #src("infra/swe5001/runtime/ecs.tf")[client SSL settings], #src("infra/swe5001/modules/cache/main.tf")[Valkey], #src("infra/swe5001/modules/messaging/main.tf")[messaging] and #src("infra/swe5001/edge/s3.tf")[SPA bucket].],
) <tbl-security-boundaries>

=== Application and delivery controls

Configured, not tested (read on 9 November): Sa-Token (#src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SaTokenConfig.java")[login and public-path policy]) checks login on protected `/api/v1/**` routes, while catalogue and invite reads, OAuth entry and callbacks and signature-authenticated provider webhooks have defined public paths. Business services still check resource ownership, because login alone is not authorisation. Outside identity, `SessionStpInterface` (#src("backend/blocbuy-infrastructure/src/main/java/com/blocbuy/common/config/SessionStpInterface.java")[permission snapshots]) reads one validated role and permission snapshot per request; a missing or malformed snapshot gives no permissions. Internal identity endpoints (#src("backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/internal/InternalUserController.java")[internal endpoints]) are distributed-only and hidden from the public contract, but hidden documentation is not caller authentication. Tested locally, not on AWS: the 12 October record (#src("docs/agile/sprint-04/log/2026-10-12.md")[local verification]) reports Redis-backed authorisation regressions, not authenticated acceptance on ECS (NFR-58).

Configured, not tested: each task has a read-only root file system and a writable `/tmp` volume (#src("infra/swe5001/modules/ecs-service/main.tf")[container hardening]). The image scan and its dated runs are explained in §5.2 and @fig-d10c-image-scan-gate: the exact built image is scanned before export, and a fixable HIGH or CRITICAL finding fails the job. Dependency, secret and static-analysis gates are separate controls, and §5.2 states their limits (NFR-02, NFR-03 and NFR-51).

*Detection and testing.* On 11 November on AWS, the SWE5001 CloudFront distribution had no WAF and the project account had no GuardDuty detector, so there is no managed intrusion detection or prevention (IDS/IPS). The only alerting is two CloudWatch alarms, on the notification dead-letter queue and on queue age; they have never fired, and their email delivery is untested (§6.5, AISS-16). No dynamic application security test (DAST) or penetration test ran against the SWE5001 runtime; the SWE5006 ZAP scan targeted the SWE5006 monolith on EC2. No password is stored or hashed: Google OAuth is the only sign-in (`AuthController`), and `sys_user.password_hash` stays null. Stripe webhooks are authenticated by the HMAC in their `Stripe-Signature` header.

=== Residual risks and verification still required

The owning decisions and the roadmap are in §3.4; @tbl-security-residual states the security consequence of each open limit.

#figure(
  table(
    columns: (0.7fr, 2.1fr, 2.2fr),
    table.header([AISS], [Current limit], [Required follow-up]),
    [04], [Internal calls rely on network trust, without a service token or mutual TLS.], [Add authenticated service identity; test denial from an untrusted peer and from outside.],
    [07 (resolved)], [Resolved for placement and connectivity (§3.2).], [Recheck placement in a future deployment; destroyed subnet IDs are not current state.],
    [08], [Origin and ALB-to-task HTTP hops are unencrypted.], [Deploy and verify TLS on these hops; a prefix list or secret header is not encryption.],
    [09], [Origin secret and Valkey token are visible to authorised state readers.], [Restrict state and plan access; agree rotation; use write-only secrets where supported.],
    [15], [Apply-role CloudWatch privileges are not fenced to SWE5001.], [Narrow permissions and test the intended cross-stack denials.],
    [16], [Alarm email delivery is untested.], [Set `SWE5001_ALLOW_UNCONFIRMED_ALERTS` to `false`, confirm all recipients and trigger one test alarm.],
    [17], [A long-lived IAM access key exists beside the keyless pipeline.], [Remove the key or move its user to Identity Center.],
    [18], [No CloudTrail trail, GuardDuty, Config or Access Analyzer in the project account.], [Add an organisation trail, GuardDuty and Access Analyzer.],
    [19], [Two-factor sign-in to GitHub is unconfirmed for all five of us.], [Confirm it for everyone; decide on commit signing.],
    [20], [The shared `dev` delivery deploys a rebuilt image without a fresh scan.], [Scan the rebuilt image before it is pushed, or promote the scanned pull-request image.],
  ),
  kind: table,
  caption: [Open security limitations and their follow-up. Source: the AISS register (§3.4).],
) <tbl-security-residual>

The most consequential gaps are privileged control-plane access, secret-bearing state and the unencrypted origin path, because they can affect deployment authority or disclose credentials or data. Missing peer identity raises the impact of a compromised task. The absent audit services weaken detection and investigation. The unencrypted alarm topic is a narrower confidentiality concern. This order ranks technical exposure; we have not yet decided which risks we accept for the course and which must block real-user operation.
