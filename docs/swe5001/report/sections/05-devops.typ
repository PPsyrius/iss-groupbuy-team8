#import "../lib.typ": *
#import "../figures.typ": afig

= DevOps and Development Lifecycle

== Source Control Strategy
<sec-source-control>

BlocBuy keeps application code, infrastructure, contracts and evidence in one Git repository. We integrate through reviewed pull requests (PRs) into `dev`; SWE5001 releases move through a separate promotion from `dev` into `prod`, so merging ordinary work does not rebuild the SWE5001 environment (AD-19, §3.3).

=== Repository and configuration items

The monorepo holds the configuration items in @tbl-source-control-items. One PR can change an API, its contract, the generated client and the evidence in one reviewable revision; path filters keep a documentation change from rebuilding the application. The cost is a shared history, and service boundaries are not repository boundaries.

#figure(
  table(
    columns: (1.4fr, 2.6fr),
    table.header([Configuration item], [Versioned content and purpose]),
    [`backend/`, `frontend/`], [Java modules and service assemblies; the React application, its client-generation configuration and lockfile. Two build tools work on the same revision.],
    [`contracts/openapi.json`], [The committed API contract. A change to the HTTP surface regenerates it, and the frontend client is generated from it.],
    [`infra/`, `deployment/`, `.github/`], [Terraform, deployment bundles, workflows, review ownership and automation. Credentials and Terraform state are not in the repository.],
    [`perf/`, `docs/`], [Smoke and load scripts, decisions, reports, sprint evidence and dated measurements. Editable diagram sources sit beside their renderings.],
  ),
  kind: table,
  caption: [Monorepo configuration items, checked on 9 November 2026. Source: #src("CONTRIBUTING.md")[contribution rules], #src("docs/agile/README.md")[evidence policy] and the repository directories.],
) <tbl-source-control-items>

We work on short topic branches off `dev`, write conventional commit messages and open PRs into `dev` (#src("docs/swe5001/impl-github-configuration.md")[branching model], #src("CONTRIBUTING.md")[commit and PR rules]). Branch names such as `feature/swe5001-…` are a convention, not an enforced rule. Review covers the diff, the issue's acceptance criteria, the applicable checks and the owner named in #src(".github/CODEOWNERS")[`CODEOWNERS`]: workflow changes go to Pan, backend changes to Liu Jin and frontend changes to Lim, and payment and group-buying paths add their domain owners. There is no catch-all owner, so not every file has a named reviewer.

=== Branch and review controls

Configured, not tested: on 9 November a read-only GitHub readback showed that `dev` is the default branch and the repository is public. The repository allows squash, merge and rebase, but the two rulesets in @tbl-source-control-rules narrow that choice.

#figure(
  table(
    columns: (0.65fr, 1.6fr, 2.75fr),
    table.header([Branch], [Integration rule], [Required review and status controls]),
    [`dev`], [Squash merge only; required checks must be up to date with the base branch.], [One approving review, `CODEOWNERS` where applicable, stale-approval dismissal and resolved review threads. Required checks: `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`. Also code-quality (errors) and Copilot code-review rules.],
    [`prod`], [Merge commit only; `promotion-source` accepts only this repository's `dev` as the head. Required checks are not strict.], [One approving review, `CODEOWNERS` where applicable, stale-approval dismissal and resolved threads. Required checks: `promotion-source`, `service-images`, `infra-plan`.],
  ),
  kind: table,
  caption: [Branch controls on `dev` and `prod`, read back on 9 November. Source: #src("docs/swe5001/evidence/20261109-source-control-delivery/README.md")[ruleset readback] and the committed #src("infra/swe5001/github/prod-release-ruleset.json")[`prod` ruleset].],
) <tbl-source-control-rules>

Both rulesets restrict deletion, block force pushes and have no bypass actors. Squashing keeps the `dev` history short. A promotion uses a merge commit instead, because squashing or rebasing it would rewrite the shared `dev` history and complicate the next promotion. Checks on `prod` are not strict, so `dev` need not absorb each earlier promotion merge; the release gate validates the PR's merge result (#src(".github/workflows/swe5001-release-gate.yml")[release gate]). Fixes enter through `dev`; nobody pushes a hotfix to `prod`. The contribution policy also requires jobs that are not ruleset checks, such as the contract check (#src("docs/ci/backend-pipeline.md")[applicable-check policy]). Neither ruleset enables a merge queue, although the workflows accept `merge_group` events.

Milestone tags `v0.1.0-sprint1` to `v0.1.0-sprint4` (and `v0.1.9-sprint4`) mark sprint snapshots. The annotated, SSH-signed tag `v0.2.0-swe5001-submission` (4 November, commit `9f3a9cfe`) marks the SWE5001 submission; its push ran the backend CI, which failed, and the security, SBOM and type-safety smoke workflows, which passed. Tags do not deploy SWE5001: that path uses `prod` (§5.3). The backend workflow's tag-publication path for an EC2 production host is disabled (§5.2).

=== Developer authentication

GitHub accounts identify contributors, and the branch controls above apply to every change; the author text of a commit is not proof of who pushed it. The rulesets do not require signed commits, and we have not confirmed GitHub two-factor authentication for every member; §3.4 records this as AISS-19. For AWS, people sign in through IAM Identity Center with MFA, and workflows use environment-scoped OpenID Connect roles instead of stored access keys (AD-17, §4.3; #src("docs/deploy/aws-account-governance/README.md")[governance audit]). AWS sign-in controls say nothing about GitHub two-factor settings.

== Continuous Integration

CI scans each built container image, not only its dependencies (F2). The SWE5001 image workflow builds each ARM64 image, loads it into Docker and runs Trivy against that local image before any export. A fixable HIGH or CRITICAL vulnerability fails the image job, which blocks publication and deployment (#src(".github/workflows/swe5001-service-images.yml")[service-image workflow]).

*Triggers and gates.* PRs into `dev` are the main validation path. Path filters select the applicable jobs, so a documentation change does not rerun application tests. @tbl-ci-paths lists the five paths and their gates.

#figure(
  table(
    columns: (1fr, 1.6fr, 1.4fr),
    table.header([Trigger and path], [Jobs and checks], [Gate or retained result]),
    [Backend PR, merge group or `v*` tag], [`build-quality`: formatting, Checkstyle, Maven verify (unit, architecture and integration tests, SpotBugs, JaCoCo); `contract-check`: OpenAPI regeneration and compatibility; `SonarCloud Backend`.], [Failed required checks block the merge. `docker` builds the verified application, boots it with PostgreSQL and Redis, then scans its image; results are kept as SARIF.],
    [Frontend PR or merge group], [`frontend-check`: generated client, types, lint, formatting, unit tests and unused-code checks; `e2e-preview`: browser tests; `SonarCloud Frontend`.], [Required checks must pass; browser reports are kept. CodeQL is disabled (`ENABLE_CODEQL` is `false`).],
    [Security workflow: PR, tag, weekly schedule or manual run; a light secret scan on `dev` pushes], [Gitleaks; dependency and filesystem scans and IaC configuration scans with Trivy, separate from image scans. Weekly OWASP ZAP baseline (`dast-baseline.yml`) against the shared development frontend, not SWE5001.], [A dependency scan does not show that an image was scanned. Event-selected secret scans do not cover the whole history.],
    [SWE5001 image inputs in a PR into `dev`, or a reusable call], [Matrix: `monolith`, `identity-svc`, `commerce-svc`, `notification-svc`. Package without rerunning tests, build the ARM64 image, scan it with Trivy.], [A PR does not export images. The release gate calls it with `upload-images=false`; the deployment exports the scanned images.],
    [Infrastructure PR into `dev` touching `infra/swe5001/**`, `perf/smoke/**` or its workflow], [Static, per stack (`foundation`, `edge`, `runtime`): `terraform fmt`, `validate`, `terraform test` with a mocked AWS provider, Checkov 3.3.22; Trivy configuration scan (HIGH and CRITICAL fail) of the messaging and observability modules. Plan: a live read-only plan of `foundation` only, in the `swe5001-plan` environment, posting resource addresses and actions to the PR.], [The release gate calls the same workflow as the required `infra-plan` check on `prod`. Limit: `edge` and `runtime` are not planned against AWS, so the apply role's permissions are first used at promotion; that is how the 9 November promotion failed (§5.3).],
  ),
  kind: table,
  caption: [CI paths and their gates, checked against the workflow source on 9 November. Source: #src(".github/workflows/ci-backend.yml")[`ci-backend.yml`], #src(".github/workflows/ci-frontend.yml")[`ci-frontend.yml`], #src(".github/workflows/security.yml")[`security.yml`] and #src(".github/workflows/infra-swe5001.yml")[`infra-swe5001.yml`].],
) <tbl-ci-paths>

@fig-d10a-ci-image-chain shows the scan gate in the checks of a PR, and @fig-d10b-delivery shows the jobs of a promotion that depend on it. The figure leaves out `ci-frontend.yml`, which runs `frontend-check` and `SonarCloud Frontend` on frontend changes (both required on `dev`) and, after a merge, publishes the SPA to the shared development bucket.

@tbl-image-scan-gate states the gate rule.

#figure(
  table(
    columns: (1fr, 3fr),
    table.header([Item], [Setting]),
    [Scanned artefact], [Local image `swe5001-<service>:<github.sha>` for `monolith`, `identity-svc`, `commerce-svc` and `notification-svc`, before export],
    [Policy], [`scan-type=image`, `scanners=vuln`, `severity=HIGH,CRITICAL`, `ignore-unfixed=true`],
    [Failure], [`exit-code=1` fails the image job],
    [Blocked jobs], [`publish` needs `images`, `up` needs `publish`, `release` needs `up`; the release gate reports `service-images` as failed],
    [Paths without a fresh scan], [The shared `dev` EC2 delivery rebuilds and relies on the pre-merge scan (below)],
  ),
  kind: table,
  caption: [Image-scan gate in the SWE5001 image workflow. Source: #src(".github/workflows/swe5001-service-images.yml")[`swe5001-service-images.yml`] and #src(".github/workflows/swe5001-deploy.yml")[`swe5001-deploy.yml`].],
) <tbl-image-scan-gate>

The gate ignores unfixed vulnerabilities and lower severities, so a pass does not mean the image is free of known vulnerabilities. On the deployment path, `docker save` exports the scanned image, and `publish` loads and retags it without rebuilding. ECR returns an immutable digest, which becomes the candidate for that service.

*Delivery is not always a fresh scan.* The monolith pipeline scans `blocbuy-backend:ci-<github.sha>` after its image build and container health check, on PRs, merge groups and release tags. Its tag-publication path would export that verified image, but it is configured and disabled: the export steps require `vars.ENABLE_EC2_DEPLOY_PROD == 'true'` (`ci-backend.yml`), and the variable was unset at the 9 November readback. A merged `dev` push runs `package-dev`, `publish-ecr-dev` and `deploy-existing-ec2`: it rebuilds the image from the merged source and relies on the pre-merge scan, with no fresh health check or Trivy scan. We accept this for the shared SWE5006 host, because the image is built from the source the pre-merge scan checked. SWE5001 deployment rebuilds and rescans its own images.

#figure(
  table(
    columns: (1.1fr, 0.9fr, 2fr),
    table.header([Execution], [Revision], [Observed result]),
    [#run("39008208244")[17 Oct promotion] (commerce image job: #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244/job/117324930271")[log])], [`5e83d63e`], [All four image jobs passed build, scan, export and upload; `publish`, `up`, `release` and `down` passed.],
    [#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42238100131/job/127960937656")[6 Nov service-image PR run]], [Head `4bcfb312`, tested merge `46f983e4`], [All four ARM64 images passed build and Trivy scan. Export and upload were skipped, as designed for a PR.],
    [#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42238099796/job/127965258101")[6 Nov monolith PR image job]], [Head `4bcfb312`, tested merge `46f983e4`], [Build, container health check, image scan and SARIF upload passed.],
  ),
  kind: table,
  caption: [Image-scan executions read back on 9 November. Source: the linked GitHub runs.],
) <tbl-image-scan-evidence>

The promotion scanned its push commit; the PR jobs scanned GitHub's merge commit, not the reported head. These runs show the scan order and the publication path; that a failed scan blocks publication follows from the job dependencies, not from a test with a vulnerable image. Configured, not tested: ECR also scans each pushed image (`scan_on_push`) and refuses to overwrite a tag (`IMMUTABLE`, #src("infra/swe5001/foundation/ecr.tf")[`ecr.tf`]); those findings are not part of the gate. `sbom.yml` produces a source inventory on each `v*` tag and weekly; we keep no per-image SBOM for SWE5001 images.

#afig("d10a-ci-image-chain", [Continuous integration view (UML activity diagram). The applicable checks of all four workflows must pass before the squash merge; the image job stops on a fixable HIGH or CRITICAL finding, and the two SWE5001 workflows run only when their files change.])

#afig("d10b-delivery", [Delivery view (UML activity diagram). A promotion merges into prod only after three green checks and a code-owner approval; the deployment then builds, publishes, creates and smoke-tests the runtime, and always destroys it.])

== Continuous Delivery
<sec-continuous-delivery>

We deliver automatically to two targets: the shared SWE5006 development deployment and the disposable SWE5001 service environment. A SWE5001 promotion merges reviewed `dev` changes into `prod`; the deployment then builds and scans the images, publishes them by digest, creates the runtime, smoke-tests it, records the release and destroys the runtime. GitHub environments are permission contexts, not consecutive stages; SWE5001 is not an always-on production service.

=== Targets and approval boundary

Configured, not tested: @tbl-delivery-targets lists the targets from the workflows, with the environment branch policies read back on 9 November.

#figure(
  table(
    columns: (1.15fr, 1.6fr, 2.25fr),
    table.header([Target and GitHub environment], [Trigger and ownership], [Delivery and verification boundary]),
    [Shared development: `backend-dev`, `frontend-dev`], [Applicable `dev` pushes, with `ENABLE_EC2_DEPLOY` and `ENABLE_S3_DEPLOY` set. The environments admit `dev`.], [The backend builds and pushes the merged monolith and deploys it by SSM to the existing EC2 host. The frontend builds the merged SPA, publishes the S3 assets and index and invalidates CloudFront; a browser smoke job follows. Neither recreates the SWE5001 runtime.],
    [SWE5001: `swe5001-demo`], [A `prod` push or a manual `prod` run, gated by `ENABLE_SWE5001_DEPLOY`; the environment admits `prod` (AD-19, §3.3).], [Pan, owner of `.github/` in `CODEOWNERS`, opens the promotion PR; at least one other member approves it (Liu Jin and Lim approved \#757 and \#785; Liu Jin approved \#770), and the required checks must pass. Publishing and applying use separate roles (AD-17); candidate and release digests are kept apart. The runtime is temporary; the edge and foundation stacks stay.],
    [Validation: `swe5001-plan`], [Infrastructure checks on PRs; not an application target.], [A plan of `foundation` only on AWS, plus offline checks of every stack. It cannot read the edge or runtime state and deploys nothing.],
  ),
  kind: table,
  caption: [Delivery targets and the validation context. Source: #src(".github/workflows/ci-backend.yml")[`ci-backend.yml`], #src(".github/workflows/ci-frontend.yml")[`ci-frontend.yml`], #src(".github/workflows/swe5001-deploy.yml")[`swe5001-deploy.yml`] and the #src("docs/swe5001/evidence/20261109-source-control-delivery/README.md")[GitHub readback].],
) <tbl-delivery-targets>

The `backend-prod` and `backend-prod-publish` environments belong to the cancelled EC2 production design (NFR-14); they are not another operated tier, and we claim no staging deployment. None of the three active delivery environments requires an environment reviewer, so SWE5001 approval is the reviewed promotion PR (§5.1), not a second manual gate. A manual session follows the session runbook; it does not replace the promotion review. The development, SWE5001 and nightly switches were all `true` at the readback.

=== SWE5001 release sequence

The sequence below follows the deployment workflow and scripts of the source released on 10 November. @fig-d10b-delivery shows it; its `images` job is the image chain of @fig-d10a-ci-image-chain with export switched on.

1. *Build and publish.* Build and scan the four images. Publication (AD-17) loads those artefacts without rebuilding them, pushes immutable tags of the form `prod-<sha>-run-<run-id>-attempt-<run-attempt>`, resolves each digest and writes `/blocbuy/swe5001/candidate/<service>`. Each deployment scans its own promotion merge commit.
2. *Create and initialise.* `env-up.sh --candidate` reads those digests and creates the runtime with services stopped. A one-off database-initialisation task must exit successfully first. Start-up then checks that messaging is ready before it enables the commerce relay, starts the services, points the CloudFront origin at the new load balancer and waits for ECS to settle. This creates an environment; it is not a rolling upgrade of a running one. @tbl-delivery-timings gives the clean-run timings.
3. *Verify before release.* The smoke suite (§2.2) runs through CloudFront. A 401 shows the authentication envelope, not that the request reached identity or notification; the suite does not prove payment settlement, recovery drills or load-test acceptance.
4. *Record the release.* Only after a successful `up`, build the SPA without a dependency cache, upload the hashed assets before `index.html` and invalidate the index. Earlier hashed assets stay, so cached older pages keep working. Then copy the candidate digests to `/blocbuy/swe5001/release/<service>` for later sessions.
5. *Preserve and clean up.* Capture service events and logs before deleting their log groups; database-initialisation logs are excluded because failing SQL may expose credentials. `down` runs under `always()` and calls `env-status.sh` after the destroy; a failed lookup counts as a failure, not as absence. `always()` schedules cleanup; it does not prove destruction succeeded. The evidence job merges the per-stage receipts with a 90-day retention.

Source: #src(".github/workflows/swe5001-deploy.yml")[job order, publication and evidence], #src("infra/swe5001/scripts/env-up.sh")[start-up], #src("perf/smoke/smoke.sh")[smoke scope] and #src("infra/swe5001/scripts/env-down.sh")[destroy and absence check].

*Lifecycle benefit and cost (briefing Q7).* The pipeline makes image identity, runtime creation, readiness checks, smoke tests and cleanup the same on every release, and links a source revision to its candidate and release digests and stage receipts. The permanent foundation and edge stacks keep a stable entry point while the runtime is disposable; this limits idle cost but adds start-up time and loses runtime data and logs not captured first. Estimated: one promotion runs nine jobs (four image builds and scans, publish, up, release, down, evidence) and creates and destroys 115–120 resources with no manual step; we did not time a manual rebuild, so we claim no hours saved. Three steps stay manual: changes to the edge response-headers policy, confirming alarm-email subscriptions, and reserving a window with no promotion before a session (@tbl-delivery-timings).

#figure(
  table(
    columns: (1.1fr, 1fr, 2fr, 1.4fr),
    table.header([Stage], [Automated by], [Recorded duration], [Manual step left]),
    [Image build and scan (four in parallel)], [`images` job], [3 min 33 s (11 Nov)], [None],
    [Publish by digest], [`publish`], [1 min 54 s (11 Nov)], [None],
    [Provision, initialise, start and smoke], [`up` (`env-up.sh`)], [31 min 37 s (17 Oct; initial apply 520 s, 115 resources; smoke 5 s); 32 min 17 s (17 Oct, second clean session); 32 min 1 s (10 Nov; apply 517 s, 120 resources; database initialisation 58 s; messaging gate 987 s; smoke 3 s); 33 min 47 s (11 Nov; smoke 4 s); 30 min 59 s (11 Nov session)], [Confirm alarm-email subscriptions (two of three unconfirmed on 11 Nov, AISS-16); reserve a window with no promotion],
    [Release SPA and digests], [`release`], [56 s (11 Nov)], [Edge response-headers policy changes, outside the apply role (9 Nov)],
    [Destroy and absence check], [`down` (`env-down.sh`, `env-status.sh`)], [709 s destroy (17 Oct); 13 min 4 s job (11 Nov); manual teardown on 16 Oct: 113 resources in 784 s, absence at 14:35], [None],
    [Evidence], [`evidence`], [7 s (11 Nov)], [None],
    [Whole release run], [`swe5001-deploy.yml`], [51 min 15 s (17 Oct); 45 min 55 s (19 Oct); 47 min 12 s (20 Oct); 53 min 33 s (10 Nov); 53 min 48 s (11 Nov)], [—],
  ),
  kind: table,
  caption: [Delivery stages and recorded durations, 16 October – 11 November. Source: GitHub runs #run("39008208244")[39008208244], #run("39014054529")[39014054529], #run("42653347818")[42653347818] and #run("42947289936")[42947289936] and the #src("docs/swe5001/impl-verification-observability.md")[V8 timing record].],
) <tbl-delivery-timings>

=== Failure handling and operator sessions

Configured, not tested: the failure controls do not make a release atomic. A failed image job blocks publication; a failed start-up or smoke test blocks `release`. A failed SPA publication leaves the previous release digests in place, although S3 may already hold some new objects. If a digest write fails, an error handler tries to restore the values already changed; it cannot restore a value that never existed on a first release, and the restore can itself fail. ECS also has a health-based deployment circuit breaker, but rolling back a task definition does not reverse database migrations. §4.2 gives the recovery limits, and AISS-14 the interrupted start-up defect (§3.4).

Promotions and manual up and down runs share the non-cancelling `swe5001-tfstate` concurrency group, so one Terraform run holds the state at a time; each stack's S3 backend also takes a lock file (`use_lockfile = true`), and the read-only plan role plans without the lock. A scheduled safety net, set for 23:00 SGT (cron `0 15 * * *`), dispatches `down` on `prod` (the `swe5001-env.yml` lane of @fig-d10b-delivery). GitHub started it hours late in practice (03:01 SGT on 22 October; 07:38 SGT on 10 November), so an operator, the person running a session under the runbook, must not rely on the time. A promotion can destroy an active session, so the operator reserves a window with no promotion and checks queued teardown runs; a scheduled teardown is not proof of cleanup, so we record the destroy and absence result separately (#src(".github/workflows/swe5001-env.yml")[session and safety-net workflow], #src("docs/swe5001/runbook-environment-session.md")[operator procedure]).

The shared `dev` path differs on purpose. It rebuilds artefacts from the merged source after pre-merge validation; it does not promote a PR-built image or repeat the validation (§5.2). Its EC2 rollback and frontend publication are not the SWE5001 create, test and destroy chain (#src("docs/ci/backend-pipeline.md")[post-merge delivery boundary]).

=== Recorded execution and follow-up

On AWS, 13 promotions into `prod` ran the deployment between 6 October and 11 November; six passed (6, 17, 19 and 20 October, 10 and 11 November) and seven failed. Manual runs on `prod` were used twice, on 6 October and 9 November, and both failed. @tbl-delivery-runs lists every promotion and session since 17 October; release success, session configuration and load acceptance each have their own record.

#figure(
  table(
    columns: (0.9fr, 1.4fr, 2.3fr, 1.1fr),
    table.header([Date and time (SGT)], [Run], [Result], [Record]),
    [17 Oct], [Promotion, run #run("39008208244")[39008208244], source `5e83d63e`], [Passed every job], [#src("docs/swe5001/impl-verification-observability.md")[V8 record]],
    [17 Oct], [Clean session, run #run("39014054529")[39014054529]], [`up` passed (32 min 17 s)], [GitHub run],
    [9 Nov, 16:39–17:24], [Promotion #pr(757), run #run("42587537517")[42587537517], `e177a3fb`], [Failed at the edge apply (17:12): the apply role was denied `cloudfront:UpdateResponseHeadersPolicy`; release skipped; `down` destroyed 120 resources], [#src("docs/swe5001/evidence/20261109-promotion-757/README.md")[promotion 757]],
    [9 Nov, evening], [CloudFront API update of policy `blocbuy-swe5001-security-headers` (not Terraform)], [Policy updated with ETag protection; the next promotion's `edge` apply converged on it; no separate zero-drift plan was recorded], [#src("docs/swe5001/evidence/20261109-edge-policy-recovery/README.md")[edge policy recovery]],
    [9 Nov, 23:15–23:20], [Manual retry, run #run("42642746273")[42642746273], `e177a3fb`], [Failed in `publish` on an immutable ECR tag collision; `down` destroyed nothing and confirmed absence at 23:20; #pr(769) then added the run ID to the tag], [#src("docs/swe5001/evidence/20261109-promotion-757/README.md")[promotion 757]],
    [10 Nov, 00:18–01:11], [Promotion #pr(770), run #run("42653347818")[42653347818], `93984f61`], [Passed; security headers matched at one Singapore edge at 00:05, before it (#src("docs/swe5001/evidence/20261110-spa-security-headers/README.md")[headers check])], [#src("docs/swe5001/evidence/20261109-promotion-770/README.md")[promotion 770]],
    [10 Nov, from 02:18], [Session: placement checks, then the V3 campaign], [Placement and connectivity verified (AD-15, §3.2); load failed (§4.1); the scheduled safety net destroyed this runtime at 07:46], [#src("docs/swe5001/evidence/20261110-subnet-verification/README.md")[subnet verification], #src("docs/swe5001/evidence/20261110-load-preflight/README.md")[load preflight]],
    [11 Nov, 10:37–11:31], [Promotion #pr(785), run #run("42947289936")[42947289936], `3a893036`], [Passed; 120 resources created and destroyed; two of three alarm recipients unconfirmed], [GitHub run],
    [11 Nov, from 13:09], [Session: V2, then V3 r1, r2 and r3], [Monolith runtime destroyed (77 resources, absence at 13:09); distributed start-up and smoke passed in 30 min 59 s; we kept the runtime up after V3 r2 and ran V3 r3 on it from 18:39; every V3 run failed (§4.1)], [#src("docs/swe5001/evidence/20261111-load-reruns/README.md")[load reruns], #src("docs/swe5001/evidence/20261111-v3-r3/README.md")[V3 r3]],
    [11 Nov], [R4 and final teardown], [#pending[Pan: R4 outcome and the teardown run with its absence check]], [—],
  ),
  kind: table,
  caption: [Delivery runs and sessions, 17 October – 11 November. Source: the GitHub runs and dated records named in each row.],
) <tbl-delivery-runs>

The 11 November session (r3 session, 11 Nov) changed sizing and scaling through reviewed saved Terraform plans: commerce at 1 vCPU and 2 GiB, the database at `db.t4g.small`, and for V3 r3 a commerce request target of 6,000 per target per minute with 120 s slow start. These changes are not merged configuration, and the nightly teardown schedule was unchanged.
