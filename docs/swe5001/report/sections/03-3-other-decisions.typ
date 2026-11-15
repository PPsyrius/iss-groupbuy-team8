// Other Architectural Decisions: part of 03-solution-overview.typ.
#import "../lib.typ": *

// AD numbers come from ../registers.md.
#let ad-table(..rows) = table(
  columns: (auto, 1fr),
  table.header([Identifier], [Description]),
  ..rows.pos().map(((id, d)) => ([#id], [#d])).flatten(),
)

== Other Architectural Decisions

Four further decisions shape how the platform runs and changes, rather than what it is made of: where scheduled work runs, how the pipeline reaches AWS, how module boundaries are enforced, and how a release reaches the cloud. @tbl-ad-other states each in full; §4.3, §4.4 and §5.3 refer back to them.

#figure(
  ad-table(
    ("AD-16", [
      *Scheduled jobs stay in the service that owns the data, under a database lease (lease-based leader election).* \
      *Driver:* Consistency (@tbl-drivers). \
      *Context:* Five jobs must each run on exactly one instance even when several tasks run: the team-expiry sweep, the refund worker, purchase-intent expiry, the outbox relay and outbox retention. Two of them move money. \
      *Options considered:* (a) EventBridge Scheduler invoking Lambda functions; (b) a Redis lock; (c) a lease row in PostgreSQL, taken by every job through one runner. \
      *Choice:* (c). Every job runs through #src("backend/blocbuy-bootstrap/src/main/java/com/blocbuy/bootstrap/scheduling/LeasedJobRunner.java")[`LeasedJobRunner`] on a `sys_scheduler_lock` row (migration V13), taken by one upsert with a 3 s statement timeout. The holder renews it, and a lapsed lease passes to another instance. On ECS only `commerce-svc` enables scheduling (#src("infra/swe5001/runtime/services.tf")[switch per service]). \
      *Trade-off:* Option (a) was rejected because each of the five jobs would become a separately packaged and deployed function with its own database access, outside the transactions and tests it shares with the service, so we would keep two deployment paths instead of one. Option (b) was rejected because a lock that decides whether money is refunded is business state, and PostgreSQL is the only system of record. The price is that the jobs scale with the service rather than on their own, and a lease takeover can overlap one in-flight item. The status-guarded update and the unique `payment_id` on `biz_refund` remain the backstop. \
      *Consequence:* Each job passes its work as a `LeaseAwareWork` command (GoF Command, behavioural), so lease, metrics and trace handling live once in `LeasedJobRunner`\; the refund worker and the outbox relay reused it unchanged. One relay run holds the lease at a time. Calculated from ADR 0007’s limits (at most 100 rows per run, then a 2 s delay in each task): one task relays at most about 50 events/s. Each run releases the lease, so with several commerce tasks the next run can start in another task at once. V4 R5 published faster than this: calculated, its 80,000 events were all consumed within 558 s of expiry, at least about 143 events/s (§4.1). ADR 0007 names lease sharding by `aggregate_id` as the relay's evolution path (#src("backend/docs/adr/0007-cross-service-events-sns-sqs.md")[ADR 0007]). In V4 R5 we tried sharding and pacing team expiry with uncommitted changes. #pr(807) later merged them to `dev` as opt-in settings that default to one lease and expiry's one-minute interval (`PT1M`), separate from the relay's 2 s delay (`PT2S`) (#src("backend/docs/adr/0002-team-lifecycle-events.md", rev: "479d3409ba6992a7991953ae3544f559ca614dea")[ADR 0002 addendum], 14 November). The released design `3a893036` is unchanged (§4.1). Tested locally, not on AWS: `SchedulerLeaseRepositoryIT` shows one winner in an eight-way race.
    ]),
    ("AD-17", [
      *The pipeline reaches AWS only through federated, short-lived GitHub OIDC roles, one per stage (least privilege)\; secrets live in SSM Parameter Store.* \
      *Driver:* Security (@tbl-drivers). \
      *Context:* CI must plan, apply and deploy into the shared AWS account without touching the SWE5006 roles. Credentials and secrets are one decision because they answer one concern: the pipeline keeps no long-lived AWS credential or application secret outside AWS. \
      *Options considered:* (a) long-lived IAM access keys stored as GitHub secrets; (b) OIDC federation to short-lived roles; for application secrets, (c) AWS Secrets Manager or (d) SSM Parameter Store SecureString. \
      *Choice:* (b) and (d). Three #src("infra/swe5001/foundation/github-oidc.tf")[OIDC roles] (plan, apply, deploy) each trust exactly one GitHub environment: `swe5001-plan` for pull-request plans, and `swe5001-demo`, reachable only from `prod`, for apply and deploy. Tasks receive secrets as ECS `secrets` references, never as plain values. \
      *Trade-off:* No key to rotate or leak, and every credential expires within the run. The cost is IAM wiring per role, and a broad apply role (eight AWS managed policies). Denies fence it: on resources not tagged for SWE5001 (EC2, load balancers, CloudFront, RDS and ElastiCache), on log groups, SNS topics and SQS queues not named for SWE5001, and on roles outside the `/swe5001/` path. It cannot edit its own policies. Its CloudWatch alarm, dashboard and metric access is not fenced (AISS-15). Secrets Manager was rejected because it charges per secret per month for features the stack does not use, such as rotation, while Parameter Store SecureString is free at the standard tier. \
      *Consequence:* On 8 November on AWS, the three roles trusted only the two environment subjects (read back with `aws iam get-role`; #src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record]), and no workflow references an AWS key; the only secrets they reference are the SonarCloud, board-snapshot and TypeSafe tokens and GitHub's built-in token. Only the pipeline is key-free: an IAM user with an access key remains in the account (AISS-17). The #src("docs/swe5001/impl-github-configuration.md")[GitHub configuration] and the security layering around this decision are in §4.3.
    ]),
    ("AD-18", [
      *Layers (COLA) and executable architecture rules (fitness functions) keep the boundaries inside one codebase.* \
      *Driver:* Modifiability (@tbl-drivers). \
      *Context:* One Maven reactor builds both the monolith and the three services (AD-01). Before the split the code had concrete couplings across contexts, for example a commerce mapper that joined `sys_user` and fifteen direct calls from commerce into identity. \
      *Options considered:* (a) package conventions checked only by review; (b) moving business code into separate source trees per service; (c) one image for every service with the role chosen by a property; (d) COLA modules plus ArchUnit rules that fail the build. \
      *Choice:* (d). Seventeen ArchUnit rules and two plain tests enforce layer, service, SQL-ownership and public-contract boundaries (@tbl-arch-rules, §3.1.2). \
      *Trade-off:* A boundary violation fails the first pull request, even in the monolith, instead of surfacing at an AWS session. Option (b) was rejected as the same end state with a far larger change to the SWE5006 code two weeks before its presentation. Option (c) was rejected because it weakens the split by assembly, and ADR 0008 keeps it only as the fallback to use if the per-service build path ever broke `dev`. Option (a) was rejected because, with five developers working in parallel, review alone would miss boundary violations (§3.1.2). The cost is upkeep: every new identity or notification table must be added to its service's list in the shared grant callback (`afterMigrate__service_grants.sql`), because commerce owns every other table by default, and every commerce-to-identity call must go through `UserAppService`. \
      *Consequence:* Topology is a deployment property (#src("backend/docs/adr/0008-dual-deployment-topology.md")[ADR 0008]), and the three service modules stay small (§4.4). Tested in CI: the rules run in `mvnw verify` in the required `build-quality` check of applicable backend pull requests.
    ]),
    ("AD-19", [
      *Disposable environments: the normal release path is a reviewed `dev` → `prod` promotion that provisions, tests and releases a runtime, then attempts cleanup.* \
      *Driver:* Cost (@tbl-drivers). \
      *Context:* `dev` is the trunk shared with SWE5006 and must keep its EC2 deployment unchanged, and the ECS runtime should exist only while it is used (AD-12). \
      *Options considered:* (a) continuous deployment from `dev`; (b) a long-lived SWE5001 fork; (c) manual `terraform apply` by a team member; (d) a promotion pull request into `prod` as the normal deploy trigger; (e) a long-lived staging runtime with a manual environment approval. \
      *Choice:* (d). A #src(".github/workflows/swe5001-release-gate.yml")[release gate] with three required checks runs on every pull request into `prod` (source is `dev`, service images, infrastructure plan); the #src("infra/swe5001/github/prod-release-ruleset.json")[`prod-release` ruleset] requires one approval and allows merge commits only. The merge runs #src(".github/workflows/swe5001-deploy.yml")[`swe5001-deploy.yml`], which provisions a fresh runtime or updates an existing session, smoke-tests through CloudFront, releases and attempts teardown (§5.3). Manual dispatch is also supported. OIDC limits the workflow role to its deployment environment, but an administrator can still run Terraform outside the workflow. \
      *Trade-off:* A disposable runtime reduces idle cost but takes 46–54 min per three-service release (@tbl-delivery-timings). We rejected continuous deployment from `dev` because `dev` also carries SWE5006. We tried a fork and dropped it after seven conflicts on its first sync (#src("docs/swe5001/microservice-refactoring-plan.md")[refactoring plan]). We rejected routine manual applies as the release path because they bypass repeatable gates. We rejected a staging runtime because it would double running hours; instead, the workflow tests the candidate runtime itself before it copies the release digests. \
      *Consequence:* Successful promotions exercised both fresh provisioning and updates to an existing runtime; failed release attempts also exercised teardown. Execution history and timings are in @tbl-delivery-runs and @tbl-delivery-timings (§5.3). Our load sessions of 10 and 11 November started from the environment workflow and the released digests, and we applied some session values with local Terraform from reviewed saved plans (§4.1.6). The drill runtime of 13–15 November and V8 R4 were created and destroyed with our session scripts run by hand, with separate approval; V4 R5 also used a reviewed but uncommitted image (§4.1, @tbl-delivery-runs). A separate check confirms cleanup: `env-status.sh` fails if tagged runtime resources or the course dashboard survive, but it can miss resources that AWS services create without our tags, such as the Container Insights log group that survived (#src("docs/swe5001/evidence/20261108-cost-explorer/README.md")[8 November record]).
    ]),
  ),
  kind: table,
  caption: [Other architectural decisions (AD-16 to AD-19). Source: `registers.md`.],
) <tbl-ad-other>
