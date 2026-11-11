#import "../lib.typ": *

= DevOps and Development Lifecycle

#let delivery-source(path, body) = link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/" + path, body)

// Local layout scope: do not change the other report owners' typography.
#show raw: set text(size: 8.5pt)
#show table.cell: set block(breakable: false)

== Source Control Strategy
<sec-source-control>

BlocBuy keeps application code, infrastructure, contracts and evidence in one Git repository. Contributors integrate through reviewed pull requests into `dev`; SWE5001 releases move through a separate `dev` → `prod` promotion, so merging ordinary development work is not itself permission to rebuild the SWE5001 environment.

=== Repository and configuration items

Verified in the source inspected on 9 November 2026, the monorepo contains the following configuration items. Keeping them together lets a pull request change an API, its contract/client and its operational evidence in one reviewable revision; path-aware CI avoids treating every documentation change as an application rebuild. The trade-off is a shared history and the need to distinguish service boundaries from repository boundaries. See §3 for architecture and §5.2 for the actual validation paths.

#figure(
  table(
    columns: (1.4fr, 2.6fr),
    table.header([Configuration item], [Versioned content and purpose]),
    [`backend/`, `frontend/`], [Java modules and service assemblies; React application, client-generation configuration and lockfile. Separate build tools operate within the same revision.],
    [`contracts/openapi.json`], [Committed API contract. HTTP surface changes regenerate it; frontend generation consumes it rather than a separately maintained API description.],
    [`infra/`, `deployment/`, `.github/`], [Terraform, deployment bundles, workflows, review ownership and automation. Runtime credentials/state are not ordinary source artefacts.],
    [`perf/`, `docs/`], [Smoke/load scripts, design decisions, reports, issue-linked sprint evidence and dated measurements. Editable diagram sources accompany their renderings.],
  ),
  kind: table,
  caption: [Monorepo configuration items, checked on 9 November 2026. Sources: #delivery-source("CONTRIBUTING.md", [contribution rules]), #delivery-source("docs/agile/README.md", [evidence policy]) and the corresponding repository directories.],
) <tbl-source-control-items>

The documented workflow uses focused topic branches off `dev`, conventional commit messages and pull requests targeting `dev`. Branch names such as `feature/swe5001-…` describe a topic; they are a convention, not an enforced name restriction. Review combines the diff, issue acceptance criteria, applicable checks and the relevant owner from `.github/CODEOWNERS`. For example, the ownership map routes workflow changes to Pan, backend changes to Liu Jin and frontend changes to Lim; more-specific payment/group-buy rules add their domain owners. It has no universal catch-all owner, so CODEOWNERS does not mean every file has an assigned specialist. #delivery-source("docs/swe5001/impl-github-configuration.md", [Branching model]) · #delivery-source("CONTRIBUTING.md", [Commit and PR rules]) · #delivery-source(".github/CODEOWNERS", [Ownership map]).

=== Branch and review controls

Verified through read-only GitHub queries on 9 November 2026: `dev` is the default branch and the repository is public. Repository-wide settings permit squash, merge and rebase, but the active branch rulesets narrow that choice as follows. The local #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-source-control-delivery/README.md")[dated readback] records the API commands and selected fields; it is not a new ruleset change.

#figure(
  table(
    columns: (0.65fr, 1.6fr, 2.75fr),
    table.header([Branch], [Integration rule], [Required review and status controls]),
    [`dev`], [Squash merge only; required checks must be up to date with the base branch.], [One approving review, CODEOWNERS where applicable, stale-approval dismissal and resolved review threads. Required contexts: `build-quality`, `SonarCloud Backend`, `docker`, `gitleaks`, `frontend-check`, `SonarCloud Frontend`.],
    [`prod`], [Merge commit only; promotion-source validation accepts this repository's `dev` as the head. Required checks are non-strict.], [One approving review, CODEOWNERS where applicable, stale-approval dismissal and resolved threads. Required contexts: `promotion-source`, `service-images`, `infra-plan`.],
  ),
  kind: table,
  caption: [Live GitHub branch controls at the 9 November readback. Both rulesets restrict deletion, block force pushes and have no bypass actors. The committed #delivery-source("infra/swe5001/github/prod-release-ruleset.json", [prod ruleset]) documents the release design; the readback owns current settings.],
) <tbl-source-control-rules>

Squashing topic work keeps the integration history concise. Promotion instead preserves `dev` commits through a merge commit: squashing or rebasing the release would rewrite that shared history and complicate later promotions. Non-strict checks on `prod` avoid requiring `dev` to absorb each preceding promotion merge; promotion validation runs on the PR merge result. Fixes still enter through `dev`, not direct hotfix pushes to `prod`. Other applicable jobs, such as contract compatibility, remain required by the contribution policy even when they are not named ruleset contexts. Neither active ruleset requires a merge queue; a workflow's `merge_group` support is not evidence that one is enabled. #delivery-source("docs/swe5001/impl-github-configuration.md", [Promotion rationale]) · #delivery-source(".github/workflows/swe5001-release-gate.yml", [Source guard]) · #delivery-source("docs/ci/backend-pipeline.md", [Applicable-check policy]).

Verified at the same GitHub readback, milestone tags `v0.1.0-sprint1` through `v0.1.0-sprint4` identify sprint snapshots. They are not the SWE5001 deployment trigger: that path uses `prod`, with image source SHA and digest recorded per promotion. The separate `v*` EC2-production workflow must not be mistaken for the SWE5001 service release or triggered merely to label report progress.

=== Developer authentication and remaining confirmation

GitHub contributor identity and AWS deployment identity are separate concerns. Repository changes are attributed to individual GitHub accounts and subjected to the branch/review controls above; a commit's author/email text alone is not proof of an authenticated push or a signature. The current branch rulesets do not require signed commits. Team-wide GitHub two-factor authentication (2FA), the chosen commit-signing policy and each developer's Git credential/transport method have not been confirmed by this report review. Issue #link("https://github.com/PPsyrius/iss-groupbuy-team8/issues/621")[\#621] explicitly owns those confirmations and the full `dev` ruleset export. This section does not invent a shared SSH/PAT setup or claim all members have verified 2FA.

For AWS, the dated governance audit records team IAM Identity Center sessions, and workflows use environment-scoped OpenID Connect roles rather than stored AWS access keys. Those cloud controls, owner-confirmed enabled Identity Center MFA and the IAM-user exceptions are described in §4.3. AWS sign-in controls do not establish members' GitHub 2FA settings. #delivery-source("docs/deploy/aws-account-governance/README.md", [Governance audit]).

#pending[Pan / team: confirm the developer-authentication and commit-signing decisions in \#621; Liu Jin to review §5.1.]

== Continuous Integration

*Yes: CI scans the built container image, not only its dependencies.* The SWE5001 image workflow builds each ARM64 image, loads it into Docker, and runs Trivy against that exact local image before exporting it for publication. A fixable HIGH or CRITICAL vulnerability makes the scan exit with code 1, failing the image job and blocking publication and deployment. This answers assessor remark F2. The sequence below is verified in the checked-in #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/swe5001-service-images.yml")[service-image workflow]; dated execution evidence follows separately.

*Triggers and gates.* Pull requests (PRs) into `dev` are the main application-validation path. Path filters select the applicable work; an unrelated documentation change is not a fresh execution of application tests. The workflows retain `merge_group` support, but this does not mean a merge queue is enabled. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/ci-backend.yml")[backend], #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/ci-frontend.yml")[frontend] and #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/security.yml")[security] definitions distinguish these paths:

#figure(
  table(
    columns: (1fr, 1.5fr, 1.5fr),
    table.header([Trigger / path], [Jobs and checks], [Gate or retained result]),
    [Applicable backend PR, merge group or `v*` tag], [`build-quality`: formatting, Checkstyle, Maven verify (unit, architecture and integration tests, SpotBugs, JaCoCo); `contract-check`: OpenAPI regeneration and compatibility; `SonarCloud Backend`.], [Failed applicable checks prevent approval. `docker` builds the verified application, boots it with PostgreSQL/Redis, then scans its image. Image results are retained as SARIF.],
    [Applicable frontend PR or merge group], [`frontend-check`: generated client, types, lint, formatting, unit tests and unused-code checks; `e2e-preview`: browser tests; `SonarCloud Frontend`.], [Applicable quality checks must pass; browser reports retain test evidence. SonarCloud provides static analysis and coverage; CodeQL is disabled at the 7 November checkpoint.],
    [Security workflow: PR, tag, weekly schedule or manual dispatch; lightweight secret scan on `dev` pushes], [Gitleaks; applicable workflow, dependency and configuration checks. Trivy dependency/filesystem and IaC scans are separate from image scans.], [A dependency scan does not prove an image was scanned. Event-selected secret scans do not prove the complete history is clean.],
    [SWE5001 image-input PR into `dev`, or reusable call], [Matrix: `monolith`, `identity-svc`, `commerce-svc`, `notification-svc`. Package without rerunning tests → build ARM64 image → Trivy image scan.], [PR validation does not export images. Promotion validation calls with `upload-images=false`; deployment calls export the scanned images.],
  ),
  kind: table,
  caption: [Configured CI paths, checked against source at `b80debcd` on 9 November 2026. Disabled CodeQL and secret-scan limits follow the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/docs/quality/nfr-sweep-2026-11-07.md")[7 November audit].],
) <tbl-ci-paths>

#import "../diagrams/d10-cicd-pipeline.typ": d10, d10-validation
#pagebreak(weak: true)
#figure(
  block(breakable: false, align(center, d10-validation)),
  kind: image,
  caption: [D10a — Reusable service-image job (UML activity view). Build precedes image scan; only a passing scan can export an archive. PR/promotion validation requests no export. Job success is not whole-PR success; packaging/build/export failures also fail the job.],
) <fig-d10-image-validation>

#pagebreak(weak: true)
#figure(
  block(breakable: false, align(center, d10)),
  kind: image,
  caption: [D10b — Enabled SWE5001 deployment on `prod` (UML activity view). An unsuccessful prerequisite skips dependent jobs but still reaches the configured cleanup/evidence attempts. `always()` schedules an attempt; it guarantees neither successful destruction nor retained evidence.],
) <fig-d10-cicd-pipeline>

*Artifact identity and failure handling.* @fig-d10-image-validation shows the scan gate; @fig-d10-cicd-pipeline shows the downstream deployment dependencies. For each matrix service, Trivy scans `swe5001-<service>:<github.sha>` using `scan-type=image`, `scanners=vuln`, `severity=HIGH,CRITICAL`, `ignore-unfixed=true` and `exit-code=1`. This excludes unfixed vulnerabilities and lower severities from the failing gate; it is not a vulnerability-free guarantee. On the deployment path, `docker save` exports the scanned image, and the `publish` job loads and retags it rather than rebuilding it. ECR returns an immutable digest recorded as the candidate for that service. In the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/swe5001-deploy.yml")[deployment workflow], `publish` needs `images`, `up` needs `publish`, and `release` needs successful `up`. A scan failure therefore blocks that chain. The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/b80debcd8b8cdeb1272219758789e1a9927b9d7d/.github/workflows/swe5001-release-gate.yml")[promotion gate] also reports `service-images` as failed unless its called image workflow succeeds.

*Delivery is not always a fresh scan.* The shared monolith pipeline scans `blocbuy-backend:ci-<github.sha>` after its image build and container health smoke test on applicable PRs, merge groups and release tags. Its enabled tag-publication path exports that verified image. By contrast, a merged `dev` push uses `package-dev` → `publish-ecr-dev` → `deploy-existing-ec2`: it rebuilds a delivery image from the merged application and relies on pre-merge validation, without a fresh post-merge image smoke test or Trivy scan. SWE5001 deployment rebuilds and rescans its own images; the EC2 fast path must not be described as doing so.

#figure(
  table(
    columns: (1fr, 1fr, 2fr),
    table.header([Dated execution], [Revision], [Observed result and limit]),
    [#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244/job/117324930271")[17 October 2026 promotion, commerce image job]], [`5e83d63e`], [All four image jobs passed build → scan → export → upload; publish, up, release and down passed. The commerce job is linked as an example. This proves the historical release path, not a deployment of the November revision.],
    [#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42238100131/job/127960937656")[6 November 2026 service-image PR run]], [Head `4bcfb312` \ Tested merge `46f983e4`], [All four ARM64 images passed build and Trivy scan. Export/upload were skipped as intended for a PR; no AWS publication or deployment is established.],
    [#link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42238099796/job/127965258101")[6 November 2026 monolith PR image job]], [Head `4bcfb312` \ Tested merge `46f983e4`], [Build, EC2-profile container health smoke, image scan and SARIF upload passed. This is PR validation, not production release or current dev health.],
  ),
  kind: table,
  caption: [Completed jobs read back on 9 November 2026. The promotion scanned its push SHA; the PR jobs scanned the synthetic merge SHA, distinct from the reported head. Linked logs record the build and scan tags. Historical short-retention artifacts are not claimed to remain downloadable.],
) <tbl-image-scan-evidence>

These successful runs show scan ordering and the historical publication path. Scan-failure blocking is established from workflow dependencies, not a deliberately vulnerable-image experiment.

== Continuous Delivery
<sec-continuous-delivery>

BlocBuy automates delivery to two distinct operated targets: the shared SWE5006 development deployment and the disposable SWE5001 service environment. SWE5001 promotes reviewed `dev` changes into `prod`, then builds/scans, publishes by digest, provisions, smoke-tests, records the release and tears runtime down. GitHub environments define permission contexts, not consecutive deployment stages; SWE5001 is not an always-on production service.

=== Targets and approval boundary

Configured in the inspected workflows, with GitHub environment branch policies read back on 9 November 2026:

#block(breakable: false)[
#figure(
  table(
    columns: (1.15fr, 1.6fr, 2.25fr),
    table.header([Target / GitHub environment], [Trigger and ownership], [Delivery and verification boundary]),
    [Shared development / `backend-dev`, `frontend-dev`], [Applicable `dev` pushes, with `ENABLE_EC2_DEPLOY` / `ENABLE_S3_DEPLOY` enabled. Environment policies admit `dev`.], [Backend builds/pushes the merged monolith and deploys by SSM to the existing EC2 host. Frontend builds the merged SPA, publishes S3 assets/index and invalidates CloudFront; a browser-smoke job follows. Neither recreates the SWE5001 runtime.],
    [SWE5001 / `swe5001-demo`], [`prod` push or explicit `prod` workflow dispatch, gated by `ENABLE_SWE5001_DEPLOY`; the environment admits `prod`.], [Promotion owner opens the release PR; required reviewers and checks approve its merge. The deployment uses separate publish/apply roles and candidate/release digests. Runtime is temporary; edge/foundation remain.],
    [Validation / `swe5001-plan`], [PR infrastructure checks; not an application target.], [Foundation-only AWS plan access plus offline stack checks. This does not grant access to secret-bearing runtime/edge state or deploy an application.],
  ),
  kind: table,
  caption: [Configured delivery targets and validation context. Sources: #delivery-source(".github/workflows/ci-backend.yml", [backend]), #delivery-source(".github/workflows/ci-frontend.yml", [frontend]), #delivery-source(".github/workflows/swe5001-deploy.yml", [SWE5001 promotion]) and the #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-source-control-delivery/README.md")[dated GitHub readback].],
) <tbl-delivery-targets>
]

The additional `backend-prod` / `backend-prod-publish` names belong to the separately gated EC2-production design, which was cancelled rather than demonstrated (NFR-14); their existence is not evidence of another operated tier. No staging deployment is claimed. For the three active delivery environments above, GitHub readback found branch policies but no required environment reviewers. Approval for SWE5001 is therefore the reviewed promotion PR in §5.1, not a second manual environment gate. Manual session dispatch is an operator action that must follow the session runbook, not a substitute for promotion review. The four development/SWE5001/nightly enablement switches were `true` at readback; this is configuration, not evidence of a newly executed deployment.

=== SWE5001 release sequence

The following sequence describes workflow/script source at released revision `93984f61`, including \#769's publication-tag correction. Older image/control source citations in §5.2 retain their inspected revision; they do not redefine the current tag format. @fig-d10-image-validation and @fig-d10-cicd-pipeline separate the reusable image job from its deployment caller. Restored evidence URLs follow §4.1's draft/publication and final-pinning policy.

1. *Build and publish.* Build and scan the four service/fallback images. Publication loads those verified artefacts instead of rebuilding them, pushes immutable tags containing SHA, run ID and attempt, resolves each digest and writes `/blocbuy/swe5001/candidate/<service>`. The format is `prod-<sha>-run-<run-id>-attempt-<run-attempt>`; a new deployment scans its actual promotion merge SHA, not merely an earlier PR head.
2. *Create and initialise.* `env-up.sh --candidate` reads those digests and converges the runtime with services stopped. A one-off database-init task must exit successfully before startup proceeds. Distributed startup checks messaging readiness before enabling the commerce relay, then starts services, updates the persistent CloudFront origin and waits for ECS stability. This is deployment through environment creation, not a rolling upgrade of an always-running service. An interrupted/retried session is not automatically a clean rebuild; §4.2 distinguishes the clean-run timings.
3. *Verify before release.* The smoke suite checks health `UP`, non-empty catalogue data in the successful response envelope, anonymous 401 responses on protected routes, trace headers and a non-service response for the internal path. A 401 proves an authentication envelope, not that the request reached the owning identity/notification service; signed-in routing checks remain separate. These smoke checks do not prove payment settlement, recovery drills or load-test acceptance.
4. *Record the release.* Only after successful `up`, build the SPA without a dependency cache for the published build, upload hashed assets before `index.html`, and invalidate the index. The SWE5001 sync does not delete earlier hashed assets, keeping cached older pages usable. Only then copy candidate digests to `/blocbuy/swe5001/release/<service>` for later sessions. This does not prove a post-publication browser or authenticated business-flow test.
5. *Preserve and clean up.* Capture service events and service logs before deleting their runtime log groups; exclude database-init logs because failing SQL may expose credentials. `down` is scheduled with `always()` after the delivery chain and calls `env-status.sh` after destroy. A failed lookup remains failure, not evidence of absence. The evidence job merges the per-stage receipts with a configured 90-day retention; configuration is not a promise that every historical artefact is still downloadable.

Sources at `93984f61`: #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/.github/workflows/swe5001-deploy.yml")[job order, publication and evidence], #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/infra/swe5001/scripts/env-up.sh")[startup], #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/perf/smoke/smoke.sh")[smoke scope] and #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/93984f615df79666f4c3ba62f4d6ae90fbf1f4e1/infra/swe5001/scripts/env-down.sh")[destroy/absence check].

*Lifecycle benefit and cost.* The automation standardises image identity, runtime creation, readiness checks, smoke verification and cleanup across release attempts. Reviewers can connect a source revision to candidate/release digests and stage receipts rather than rely on an operator remembering every step. Separate foundation/edge stacks preserve the stable entry point while runtime is disposable, limiting idle expenditure at the cost of startup delay and loss of runtime data/logs unless retained beforehand. The historical timings below quantify elapsed startup, not engineering hours saved: no timed manual baseline was measured. Operator-owned edge policy changes, session coordination and acceptance decisions remain outside the automated chain. The shared `dev` fast path trades some repeated validation for quicker delivery; it provides a weaker fresh-image assurance than SWE5001's rescan (§5.2).

=== Failure handling and operator sessions

Configured failure controls do not make the release atomic. A failed image job blocks publication; failed startup/smoke blocks `release`. Failed SPA publication leaves previous release digests in place, although S3 may already contain some newly uploaded objects. If a digest write fails, an error handler attempts to restore earlier values already changed; it cannot restore a nonexistent previous value on the first release, and a restore can itself fail. ECS also has a health-based deployment circuit breaker, but rolling back a task definition does not reverse database migrations. These source-level safeguards are not fault-injection results. See §4.2 for the recovery limits and §3.4 for AISS-14's interrupted-bootstrap defect.

Promotion and manual up/down operations share the non-cancelling `swe5001-tfstate` queue. A scheduled safety net dispatches `down` on `prod` at 23:00 Singapore time. Manual sessions normally use released digests and stay up until an operator or the safety net tears them down; a promotion can otherwise destroy an active test session, so the operator must reserve a no-promotion window and inspect queued teardown jobs. Cleanup scheduling is not proof that cleanup succeeded: record the destroy and absence result separately. #delivery-source(".github/workflows/swe5001-env.yml", [Session and safety-net workflow]) · #delivery-source("docs/swe5001/runbook-environment-session.md", [Operator procedure]).

The shared `dev` path is deliberately different. It rebuilds delivery artefacts from merged source after pre-merge validation; it does not promote a PR-built image across runs or repeat all validation in its delivery jobs. Its EC2 application rollback and frontend publication must not be described as the SWE5001 create/test/destroy chain. §5.2 explains the missing fresh dev image scan. #delivery-source("docs/ci/backend-pipeline.md", [Post-merge delivery boundary]).

=== Recorded execution and follow-up

Verified by historical GitHub execution, read back on 9 November: the 17 October 2026 promotion at source `5e83d63e` completed all image, publish, up, release, down and evidence jobs successfully. Its recorded measurements are:

#figure(
  table(
    columns: (1.25fr, 0.8fr, 2.45fr),
    table.header([Measure], [17 October result], [Meaning and limit]),
    [Initial runtime apply], [520 s; 115 added], [Provisioning only; not the whole startup or a database-content restore.],
    [Smoke suite], [5 s], [The anonymous smoke suite above; not a browser/payment/load campaign.],
    [Full up job], [31 min 37 s], [Job start to finish, including readiness. Exceeded the under-30-minute target.],
    [Runtime destroy], [709 s], [Terraform destroy step; subsequent absence checking and job overhead are additional.],
  ),
  kind: table,
  caption: [Historical SWE5001 delivery, not November deployment proof. Source: #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244")[17 October run] and #delivery-source("docs/swe5001/impl-verification-observability.md", [retained V8 timing record]); §4.2 compares the other clean rebuilds.],
) <tbl-delivery-timings>

On 9 November, promotion #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/757")[\#757] at `e177a3fb` failed at the edge apply: the GitHub apply role was denied `cloudfront:UpdateResponseHeadersPolicy`. Release was skipped and cleanup destroyed runtime. The retained #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/a05c33fd2b3646ed5aa9a383ee5a0b73bd3eb137/docs/swe5001/evidence/20261109-edge-policy-recovery/README.md")[operator recovery account] records Pan's direct CloudFront API update to the existing policy, with ETag protection and matching API readback. It was not a successful Terraform apply or a new IAM/S3 grant to the GitHub role. Operator ownership is confirmed; the command method comes from the retained account, not a raw CloudTrail event: the workload account's `us-east-1` CloudTrail lookup was unavailable under its SCP. Static-response header checks corroborate the policy values, not global propagation or browser/API acceptance.

A retry then failed independently on an immutable ECR tag collision. \#769 added run identity to publication tags, and #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/dev/docs/swe5001/evidence/20261109-promotion-770/README.md")[promotion \#770] at `93984f61` completed image scan/publication, startup, smoke, release and teardown on 9 November. These are historical release results, not deployment of later report/tooling changes.

*10 November operator session (\#775).* The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/a05c33fd2b3646ed5aa9a383ee5a0b73bd3eb137/docs/swe5001/evidence/20261110-subnet-verification/README.md")[dated placement/connectivity record] retains the task/data subnet groups and actual ENIs, route tables, security groups and required connectivity at released source `93984f61`; §§1.1, 2.2 and 6.1 cite that same record. AISS-07 is resolved for the recorded session, not for negative packet-isolation or failover tests. The separate #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/a05c33fd2b3646ed5aa9a383ee5a0b73bd3eb137/docs/swe5001/evidence/20261110-load-preflight/README.md#10-november-disposition-for-775")[SQL and experiment disposition] identifies the one-off read-only `db-init` connection-limit report: 79 maximum connections, five reserved, 74 unreserved. Its retained output was recorded at 03:00:29 SGT; the issue's approximate 02:53 task time is not an independently retained SQL execution timestamp. It was reporting, not migration or seeding.

That session manually set `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=5` in identity revision 18, commerce 32 and notification 30, preserving released digests. Calculated steady allowance was 55 runtime + three owner + one operator + 15 safety = 74 unreserved connections; full-capacity replacement overlap did not fit that historical micro-class budget. Commerce minimum was temporarily one for measurement, then restored to two, maximum six; recovery observed desired two but three still running, not settled two-task recovery. #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42722097786")[Scheduled down] destroyed 120 runtime resources by *07:52:06 SGT on 10 November*, ending the experiment. This was not a live rollout back to pool ten or removal of registered task-definition revisions.

*Durable follow-up versus session overrides.* PR #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/784")[\#784] subsequently merged as `b911ea63`: the released #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/3a893036d70d028b29ae13ebf820283571c1ae00/infra/swe5001/runtime/ecs.tf")[Terraform environment map] now explicitly sets pool five for `distributed` topology only; the shared backend/monolith default remains ten. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/3a893036d70d028b29ae13ebf820283571c1ae00/infra/swe5001/runtime/variables.tf")[`commerce_min_capacity`] defaults to two and permits one for controlled baseline measurement. PR #link("https://github.com/PPsyrius/iss-groupbuy-team8/pull/785")[\#785] promoted released source `3a893036`; #link("https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42947289936")[11 November promotion] passed scan/publication, startup/smoke, release and cleanup. Its later operator session supplied the fresh V2 and selected V3 R4 results (§4.1), not a new application release for each measurement.

The #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/docs/swe5001-must-fix-evidence/docs/swe5001/evidence/20261111-v3-r4/README.md")[R4 control/provenance record] distinguishes its session overrides—commerce 1 vCPU/2 GiB, RDS small class, request target 6,000/minute, slow start 120 s and CPU guard 50%—from the already committed distributed pool-five setting. Released images and shared timeouts stayed unchanged. Those overrides and hashed orchestration changes are not new merged defaults or a new application release. The dedicated T3a Unlimited generator had no inbound rules and isolated SSM/artifact permissions; existing application roles/boundaries were not widened. It bypassed the unresolved laptop path rather than proving a host/network repair.

R4's reviewed prelaunch runner refused on remaining observer validity. The owner explicitly directed execution; a separate wrapper retained the refusal, invoked the unchanged k6 workload and streamed its raw output through a FIFO to private S3. The original k6 report producer wrote the summary; the wrapper did not manufacture it. Native k6, monitor and raw-stream exits were zero, but the outer SSM command returned Failed/response 1 during final recursive upload, with a special-file skip warning and copy exit 2. Six required downloaded artifacts match retained sizes/hashes; complete-directory upload remains unproven. The canonical analyzer's observation-coverage exit and the derived supplemental union are reported in §4.1, not hidden behind a green k6 exit.

Independent restoration read back normal bounds 2–6, actual desired/running 6/6 and pending zero by 22:52:55 SGT. The wrapper changed the generator timer from 22:50 to 22:54; the generator was verified STOPPED at 22:54:15.876, before the 22:55 close-out deadline. It was not terminated: encrypted disk and private artifacts remain and incur storage charges. The application's 23:00 schedule was unchanged; its later execution was not live-verified. This separates workload completion, restoration settings and actual shutdown observations without claiming settled two-task recovery or owner acceptance. Authentication caches, raw Terraform plans/state/private inputs and application payloads remain outside report evidence. #link("https://github.com/PPsyrius/iss-groupbuy-team8/blob/docs/swe5001-must-fix-evidence/docs/swe5001/evidence/20261111-v3-r4/verification.json")[R4 native/control and close-out projection].

#pending[Liu Jin: review §§5.1–5.3 and D10 against the dated GitHub readback, workflow source and release evidence.]
