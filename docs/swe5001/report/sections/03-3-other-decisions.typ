// Other Architectural Decisions: part of 03-solution-overview.typ (split for parallel editing; output unchanged).
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

== Other Architectural Decisions

// Section 3.3 (owner: Lim). Links pinned to dev at b80debcd (8 November 2026).
#let lim-src(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

Four further decisions shape how the platform runs and changes, rather than what it is made of: where scheduled work runs, how the pipeline reaches AWS, how module boundaries are enforced, and how a release reaches the cloud. Each is stated in full here; §4.3, §4.4 and §5.3 refer back to them.

The key architectural decisions taken are as follows:

#figure(
  ad-table(
    ("AD-16", [
      *Scheduled jobs stay in the service that owns the data, under a database lease (lease-based leader election).* \
      *Driver:* Consistency (@tbl-drivers). \
      *Context:* five jobs must each run on exactly one instance even when several tasks run: the team-expiry sweep, the refund worker, purchase-intent expiry, the outbox relay and outbox retention. Two of them move money. \
      *Options considered:* (a) EventBridge Scheduler invoking Lambda functions; (b) a Redis lock; (c) a lease row in PostgreSQL, taken by every job through one runner. \
      *Choice:* (c). Every job runs through `LeasedJobRunner` on a `sys_scheduler_lock` row (migration V13), taken by one upsert with a 3-second statement timeout. The holder renews it, and a lapsed lease passes to another instance. On ECS only `commerce-svc` enables scheduling. \
      *Trade-off:* no second packaging and deployment path, and the jobs reuse the transactions and tests of the service. Option (b) was rejected because a lock that decides whether money is refunded is business state, and PostgreSQL is the only system of record. The price is that the jobs scale with the service rather than on their own, and a lease takeover can overlap one in-flight item. The status-guarded update and the unique `payment_id` on `biz_refund` remain the backstop. \
      *Consequence:* each job passes its work as a `LeaseAwareWork` command (Command pattern), so lease, metrics and trace handling live once in `LeasedJobRunner`\; the refund worker and the outbox relay reused it unchanged. The relay is a single active copy, about 50 events per second (calculated in ADR 0007 from 100 rows per 2-second relay tick), and sharding the lease by aggregate is its evolution path. Tested locally, not on AWS: `SchedulerLeaseRepositoryIT` shows one winner in an eight-way race. #lim-src("backend/docs/adr/0002-team-lifecycle-events.md")[ADR 0002 addendum] · #lim-src("backend/blocbuy-bootstrap/src/main/java/com/blocbuy/bootstrap/scheduling/LeasedJobRunner.java")[`LeasedJobRunner`] · #lim-src("infra/swe5001/runtime/services.tf")[scheduling switch per service].
    ]),
    ("AD-17", [
      *The pipeline reaches AWS only through federated, short-lived GitHub OIDC roles, one per stage (least privilege)\; secrets live in SSM Parameter Store.* \
      *Driver:* Security (@tbl-drivers). \
      *Context:* CI must plan, apply and deploy into the shared AWS account without touching the SWE5006 roles. Credentials and secrets are one decision because they answer one concern: no long-lived AWS credential or application secret is kept outside AWS. \
      *Options considered:* (a) long-lived IAM access keys stored as GitHub secrets; (b) OIDC federation to short-lived roles; for application secrets, (c) AWS Secrets Manager or (d) SSM Parameter Store SecureString. \
      *Choice:* (b) and (d). Three roles (plan, apply, deploy) each trust exactly one GitHub environment: `swe5001-plan` for pull-request plans, and `swe5001-demo`, reachable only from `prod`, for apply and deploy. Tasks receive secrets as ECS `secrets` references, never as plain values. \
      *Trade-off:* no key to rotate or leak, and every credential expires within the run. The cost is IAM wiring per role, and a broad apply role (eight AWS managed policies). It is fenced by denies on resources not tagged or named for SWE5001 (EC2, load balancers, CloudFront, RDS and ElastiCache) and by its inability to edit its own policies, but its CloudWatch access is not fenced (AISS-15). Secrets Manager was rejected because it charges per secret per month for features the stack does not use, such as rotation, while Parameter Store SecureString is free at the standard tier. \
      *Consequence:* on 8 November 2026 on AWS, the three roles trust only the two environment subjects (read back with `aws iam get-role`; #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record]), and no workflow references an AWS key; the only secrets they reference are the SonarCloud, board-snapshot and Typesafe tokens and GitHub's built-in token. The security layering around this decision is in §4.3. #lim-src("infra/swe5001/foundation/github-oidc.tf")[OIDC roles] · #lim-src("docs/swe5001/impl-github-configuration.md")[GitHub configuration].
    ]),
    ("AD-18", [
      *Layers (COLA) and executable architecture rules (fitness functions) keep the boundaries inside one codebase.* \
      *Driver:* Modifiability (@tbl-drivers). \
      *Context:* one Maven reactor builds both the monolith and the three services (AD-01). Before the split the code had concrete couplings across contexts, for example a commerce mapper that joined `sys_user` and fifteen direct calls from commerce into identity. \
      *Options considered:* (a) package conventions checked only by review; (b) moving business code into separate source trees per service; (c) one image for every service with the role chosen by a property; (d) COLA modules plus ArchUnit rules that fail the build. \
      *Choice:* (d). 17 ArchUnit rules in `ArchitectureTest` (8 for layers and coding style, 9 for service boundaries), plus two plain tests for what ArchUnit cannot see: `InternalPathsContractTest` (no `/internal` path in the public contract) and `IdentityTablesStayInIdentityMappersTest` (no identity table in another service's SQL). \
      *Trade-off:* a boundary violation fails the first pull request, even in the monolith, instead of surfacing at an AWS session. Option (b) was rejected as the same end state with a far larger change to the SWE5006 code two weeks before its presentation; option (c) was kept only as a fallback because it weakens the split by assembly. The cost is a set of synchronisation duties: a grant for every new table, and every commerce-to-identity call through `UserAppService`. \
      *Consequence:* topology is a deployment property, and the three service modules stay small (§4.4). Built and tested: the rules run in `mvnw verify` in the required `build-quality` check of every backend pull request. #lim-src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008] · #lim-src("backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java")[`ArchitectureTest`].
    ]),
    ("AD-19", [
      *Ephemeral, immutable environments: a release reaches AWS only through a `dev` → `prod` promotion pull request that builds from zero, deploys, tests, releases and destroys.* \
      *Driver:* Cost (@tbl-drivers). \
      *Context:* `dev` is the trunk shared with SWE5006 and must keep its EC2 deployment unchanged, and the ECS runtime should exist only while it is used (AD-12). \
      *Options considered:* (a) continuous deployment from `dev`; (b) a long-lived SWE5001 fork; (c) manual `terraform apply` by operators; (d) a promotion pull request into `prod` as the normal deploy trigger. \
      *Choice:* (d). A release gate with three required checks runs on every pull request into `prod` (source is `dev`, service images, infrastructure plan); the `prod-release` ruleset requires one approval and allows merge commits only. The merge runs `swe5001-deploy.yml`: build and scan images, publish by digest, create the runtime from zero, smoke-test through CloudFront (the suite is described in §2.2), release the digests and the web application, and always destroy. The workflow also accepts a manual `workflow_dispatch` run, used once on `prod` (#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42642746273")[run 42642746273]). The deploy role trusts only runs in the deployment's GitHub environment, which limits which runs can assume it, not who starts one. \
      *Trade-off:* a release creates the runtime when none exists and always destroys it, so it is tested on a fresh environment unless a session left one running: the promotions of 17 October and of the evening of 9 November built from zero, while those of 19 and 20 October updated a runtime that a session had created earlier the same day. A failed run still attempts teardown, after which `env-status.sh` fails the run if any tagged runtime resource (`Stack=runtime`) or the course dashboard remains, so such a leftover is visible to an operator. Untagged resources that AWS services create themselves are not detected: on 8 November on AWS, a Container Insights log group (0 bytes) had survived a destroy unreported (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record]). The cost is 46–54 minutes per release (the green runs of 17, 19 and 20 October and 9 November) and a cadence of about once per sprint. Continuous deployment from `dev` was rejected because `dev` also carries SWE5006 and every SWE5001 deployment is a full environment build. The fork was tried on 28 September and dropped the next day after seven conflicts on its first sync; ad-hoc applies are excluded because the apply and deploy roles trust only the `prod`-only environment (AD-17). Manual sessions for drills still exist, but they run through the environment workflow on `prod` with the same roles, and they deploy digests that a promotion has already built. \
      *Consequence:* on AWS, twelve promotion pull requests were merged between 6 October and 9 November; the release runs of 17, 19 and 20 October and the second of 9 November were green. The first release of 9 November failed at the `edge` step, and the always-run teardown still destroyed all 120 runtime resources and released nothing, which is the behaviour this design relies on (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-promotion-757/README.md")[9 November record], §6.4); the next promotion that evening built and released from zero (#link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-promotion-770/README.md")[record]). A release that starts from no runtime also rehearses environment recovery (§4.2). Run timings are in §4.2 and the pipeline itself in §5.3. #lim-src(".github/workflows/swe5001-deploy.yml")[Deploy workflow] · #lim-src(".github/workflows/swe5001-release-gate.yml")[release gate] · #lim-src("infra/swe5001/github/prod-release-ruleset.json")[`prod` ruleset].
    ]),
  ),
  kind: table,
  caption: [Other architectural decisions (AD-16 to AD-19). Numbering follows the report's single AD register.],
) <tbl-ad-other>
