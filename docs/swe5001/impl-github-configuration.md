# SWE5001 Implementation Plan: GitHub Repository Configuration and Pipelines

Detailed GitHub work for the [refactoring plan](microservice-refactoring-plan.md), section 3.4. Companion plans: [backend architecture](impl-backend-architecture.md), [AWS infrastructure](impl-aws-infrastructure.md), [verification and observability](impl-verification-observability.md).

Status: **Draft, revised 2026-10-05** (first draft 2026-09-29; 2026-10-04: deployment moved from `dev` pushes to one `dev` → `prod` merge per sprint, and each promotion run creates, validates and destroys the runtime environment; 2026-10-05: the four SWE5001 workflows are implemented, see section 6). Current settings were read with `gh` on 2026-09-29 and rechecked on 2026-10-04 (names only; no values).

---

## 1. Principles

- **Add, never repurpose.** Every SWE5001 variable, environment, role and workflow is new and prefixed `SWE5001_` / `swe5001-`. Existing SWE5006 names (`*_DEV`, `*_PROD`, `backend-dev`, `frontend-dev`, …) are not changed or reused.
- **No application secrets in GitHub.** Stripe, Google and database credentials live in AWS SSM Parameter Store and are injected into ECS tasks. GitHub holds only role ARNs and non-secret identifiers. The plan adds **zero** GitHub secrets.
- **OIDC only.** Workflows assume AWS roles through the existing GitHub OIDC provider; no AWS keys are stored.
- **Release from `prod`, integrate on `dev`.** Nothing that changes the SWE5001 AWS environment runs from `dev`. Deployment starts only when a `dev` → `prod` promotion PR merges (refactoring plan, section 3.4).
- **Feature switches.** Each SWE5001 delivery path has an `ENABLE_SWE5001_*` variable, mirroring `ENABLE_EC2_DEPLOY`, so it can be frozen without editing workflows.

---

## 2. Current state (2026-09-29)

| Kind | Existing names |
| --- | --- |
| Repository variables | `AWS_ACCOUNT_ID`, `AWS_REGION`, `AWS_ECR_PUSH_ROLE_ARN`, `AWS_FRONTEND_DEPLOY_ROLE_ARN_DEV`, `ECR_REPOSITORY_DEV`, `ENABLE_CODEQL`, `ENABLE_EC2_DEPLOY`, `ENABLE_S3_DEPLOY`, `FRONTEND_CLOUDFRONT_DIST_ID_DEV`, `FRONTEND_S3_BUCKET_DEV`, `FRONTEND_URL_DEV`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` |
| Repository secrets | `BOARD_SNAPSHOT_TOKEN`, `SONAR_TOKEN`, `TYPESAFE_API_KEY` (`SONAR_TOKEN` also exists in the Dependabot scope) |
| Environments | `backend-dev`, `backend-prod`, `backend-prod-publish`, `frontend-dev` (all with branch policies), `copilot` |
| Branches | Default `dev`. `main` exists but is stale (only the initial commit) and unused. There is no `prod` branch yet |
| Rulesets | `dev-require-review` (branch), `main-no-forcepush` (branch) |
| Required PR checks | `build-quality`, `contract-check`, `frontend-check` |
| OIDC subject format | Immutable-ID form: `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:<suffix>`; trust policies must use this form, not `repo:PPsyrius/iss-groupbuy-team8:*` |

---

## 3. Environments

| Environment | Deployment branches | Reviewers | Used by | OIDC subject suffix |
| --- | --- | --- | --- | --- |
| `swe5001-plan` | any (PRs) | none | `terraform plan` on PRs | `environment:swe5001-plan` |
| `swe5001-demo` | `prod` only | none (the promotion PR review is the approval; the demo environment is disposable); add 1 reviewer for `env-up` in the week of 20 Oct if the team wants a gate | promotion run (image publish, `runtime` create, ECS deploy, SPA deploy, `runtime` destroy), manual `up`/`down`/`status` | `environment:swe5001-demo` |

Environment-scoped subjects keep the plan role usable from PRs into `dev` and `prod`, while the apply and deploy roles are reachable only from runs on `prod`.

2026-10-05 (#397): both environments were created and read back through `gh`; `swe5001-plan` has no branch policy or reviewers, and `swe5001-demo` has exactly one selected branch policy (`prod`) and no reviewers. The three environment role ARNs and repository ECR prefix (`swe5001`), deploy switch (`false`) and nightly-down switch (`true`) are configured. The state-bucket switch remains unset until the optional-budget change is merged and the operator initiates the end-to-end AWS plan; edge variables, `prod` and its ruleset remain pending. The operator owns all AWS execution.

---

## 4. Variables

All are **non-secret**. Values come from `foundation` Terraform outputs (see the AWS plan, section 4).

### Repository level

| Variable | Example value | Used by |
| --- | --- | --- |
| `SWE5001_TF_STATE_BUCKET` | `blocbuy-tfstate-<account>` | all Terraform jobs |
| `SWE5001_ECR_REPOSITORY_PREFIX` | `swe5001` (repos `swe5001/monolith`, `swe5001/identity-svc`, `swe5001/commerce-svc`, `swe5001/notification-svc`) | image build and publish |
| `ENABLE_SWE5001_DEPLOY` | `false` until `foundation` exists, then `true`; while `false`, a promotion merge runs no deploy job | `swe5001-deploy.yml` |
| `ENABLE_SWE5001_NIGHTLY_DOWN` | `true` (safety net for forgotten manual sessions) | `swe5001-env.yml` schedule |

`AWS_ACCOUNT_ID` and `AWS_REGION` are reused as-is (same account, `ap-southeast-1`).

### Environment `swe5001-plan`

| Variable | Purpose |
| --- | --- |
| `SWE5001_TF_PLAN_ROLE_ARN` | Foundation-only PR plan role: no managed `ReadOnlyAccess`; read `foundation.tfstate`, foundation metadata and the non-secret budget-recipient parameter only; plans run with `-lock=false` |

### Environment `swe5001-demo`

| Variable | Purpose |
| --- | --- |
| `SWE5001_TF_APPLY_ROLE_ARN` | Creates and destroys `runtime`, re-applies `edge`; scoped to resources tagged `Course=swe5001` where the service supports tag conditions |
| `SWE5001_DEPLOY_ROLE_ARN` | ECR push to `swe5001/*`, `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`, `iam:PassRole` on the task roles, `ssm:PutParameter` on `/blocbuy/swe5001/candidate/*` and `/blocbuy/swe5001/release/*`, S3 sync and CloudFront invalidation for the SWE5001 SPA bucket |
| `SWE5001_FRONTEND_URL` | `https://<swe5001 distribution>.cloudfront.net`, used by smoke tests and the frontend build |
| `SWE5001_SPA_BUCKET` | SWE5001 SPA bucket name |
| `SWE5001_CLOUDFRONT_DIST_ID` | SWE5001 distribution ID |

### Secrets

| Secret | Change |
| --- | --- |
| `SONAR_TOKEN` | none; new modules are analysed in the existing SonarCloud project |
| any new secret | **none** |

---

## 5. Release branch `prod`

| Item | Setting |
| --- | --- |
| Creation | Once, by an operator, from the `dev` commit that first carries `swe5001-deploy.yml` (`git push origin dev:prod`), before the ruleset below is enabled. `main` is left as is |
| Purpose | SWE5001 release branch only. Its head is what runs on ECS (or what the next `env-up` deploys). It is not the SWE5006 production release, which stays on `v*` tags from `dev` |
| Who opens promotion PRs | The SWE5001 release owner, after the changes to release are green on `dev` |
| Promotion PR | Head `dev`, base `prod`, title `chore(release): promote dev to prod (<yyyy-mm-dd>)`; body lists the included PRs |
| Merge method | Merge commit only. Squash or rebase would rewrite the `dev` commits on `prod`, so every later promotion would conflict |
| Hotfixes | None on `prod`. Fix on `dev` through a normal PR, then promote |

Ruleset `prod-release` (branch, target `refs/heads/prod`, enforcement active), committed as [`infra/swe5001/github/prod-release-ruleset.json`](../../infra/swe5001/github/prod-release-ruleset.json) and created with one `gh api` call ([`infra/swe5001/README.md`](../../infra/swe5001/README.md), "Create `prod`"):

| Rule | Value |
| --- | --- |
| Restrict deletions | on |
| Block force pushes | on |
| Require a pull request | 1 approval from CODEOWNERS, dismiss stale approvals, allowed merge methods: `merge` |
| Required status checks | `promotion-source`, `service-images`, `infra-plan` (from `swe5001-release-gate.yml`), not strict (head need not be up to date) |
| Bypass list | empty |

The checks are not strict: each promotion leaves a merge commit on `prod` that `dev` never receives, so a strict policy would block every promotion after the first. The `pull_request` checks already run on the merge result of `dev` into `prod`.

---

## 6. Workflows

| File | Status | Trigger | Environment | Jobs |
| --- | --- | --- | --- | --- |
| `swe5001-service-images.yml` | implemented, reusable (`workflow_call`) | called by `swe5001-release-gate.yml` and `swe5001-deploy.yml` | none | Matrix of runtime images (strangler step 0: `monolith` only; PR 9 adds `identity-svc`, `commerce-svc`, `notification-svc`). Packages the jar without a dependency cache, builds the ARM64 image with QEMU, Trivy scan (HIGH and CRITICAL, unfixed ignored), uploads the image as artifact `swe5001-image-<service>`. Builds only; never pushes |
| `ci-backend.yml` | unchanged for now | existing (PRs into `dev`, `dev` pushes, `v*` tags) | none | Gains a `service-images` job in PR 9, once the service assemblies exist; until then the monolith image is already built and smoke-tested by its `docker` job, and the SWE5006 path stays untouched |
| `infra-swe5001.yml` | implemented, also reusable | PRs into `dev` on `infra/swe5001/**`, `perf/smoke/**` or the workflow itself; `workflow_call` from the release gate | `swe5001-plan` for foundation only | `fmt -check`, offline `init`, `validate` and tests for `foundation`, `edge` and `runtime`, and shellcheck for the environment scripts and the smoke suite; `foundation` account-backed plan/checkov with a redacted plan summary once the operator bootstraps it. Never plans secret-bearing runtime state or applies |
| `swe5001-release-gate.yml` | implemented | PRs into `prod` (`opened`, `synchronize`, `reopened`) | `swe5001-plan` for foundation only (through `infra-swe5001.yml`) | `promotion-source`: fail unless `github.head_ref == 'dev'` and the head repository is this repository. `images` calls the reusable image workflow and `infra` calls `infra-swe5001.yml`; the jobs `service-images` and `infra-plan` (`if: always()`) turn their results into checks with fixed names for the ruleset. No path filters, so required checks report |
| `swe5001-deploy.yml` | implemented | push to `prod` (one promotion per sprint); manual on the `prod` ref only, with input `force_failure` | `swe5001-demo` | The promotion run (refactoring plan, section 3.4), all jobs gated by `ENABLE_SWE5001_DEPLOY` and the `prod` ref. `images`: the reusable workflow on the merge commit. `publish` (deploy role): push the scanned images as `prod-<sha>-run-<attempt>`, write `/blocbuy/swe5001/candidate/<service>`. `up` (apply role): `env-up.sh --candidate` creates `runtime` from zero with the candidate digests, so the services start behind the circuit breaker; the script waits for steady state and runs the smoke suite through CloudFront. `release` (deploy role, only after a green `up`): build the SPA and publish it on every promotion (S3 sync without `--delete`, so earlier hashed assets stay reachable; `index.html`; CloudFront invalidation), then copy the candidates to `/blocbuy/swe5001/release/<service>`; a failed SPA publication leaves the previous release digests, and a failed digest write restores those already promoted. `down` (apply role, `if: always()`): `env-down.sh`, which ends with `env-status.sh` and fails the run if any runtime resource remains. `evidence` (`if: always()`): merges digests, `env-up` and `env-down` logs (smoke output included), step timings and ECS service events into artifact `swe5001-promotion-<sha>` (90 days). `force_failure` fails `up` after the environment is up, to prove that `down` still runs. A separate rolling deploy and the `broken-health` drill are not part of a promotion; they run in manual sessions |
| `swe5001-env.yml` | implemented | manual (`status`, `up`, `down`; `up` takes `release` or `candidate` digests) on the `prod` ref; schedule 23:00 SGT | `swe5001-demo` (except `dispatch`) | Thin wrappers around `infra/swe5001/scripts/env-up.sh`, `env-down.sh` and `env-status.sh` for manual sessions; `up` writes the CloudFront URL and step timings to the job summary. `status` is green only when nothing tagged `Stack=runtime` remains. `dispatch` (schedule only, runs on `dev`, no AWS access, `actions: write`, gated by `ENABLE_SWE5001_NIGHTLY_DOWN`): `gh workflow run swe5001-env.yml --ref prod -f action=down`; skipped while `prod` does not exist, and a quick no-op destroy when `runtime` does not exist |
| `swe5001-perf.yml` | new | manual on the `prod` ref | `swe5001-demo` | Run k6 scenarios against `SWE5001_FRONTEND_URL`; upload results (see verification plan) |

Rules for all new workflows:

- `permissions: {}` at the top; each job grants only `contents: read`, `id-token: write` where it assumes a role, `pull-requests: write` for plan comments, and `actions: write` for the scheduled `dispatch` job.
- Every job that assumes `swe5001-demo` roles also checks `github.ref == 'refs/heads/prod'`, so a manual run on another ref fails fast instead of waiting on the environment branch policy. Scheduled runs execute on the default branch (`dev`), which is why they only dispatch.
- Actions pinned by commit SHA, as in the existing workflows.
- `concurrency: swe5001-tfstate` (no cancel) for every run that writes Terraform state, so a promotion run and a manual `up` or `down` never overlap. Local script runs are serialised against them by the Terraform S3 state lock. GitHub keeps only one pending run per group, so a third queued run replaces the pending one; start manual sessions only when no promotion is running.
- No dependency cache in jobs whose output is published (images, SPA), so a cache written by a PR run cannot poison a release (zizmor `cache-poisoning`).
- No secret-bearing Terraform output in logs or artifacts: `env-up.sh` suppresses the `edge` plan, whose origin diff carries `ORIGIN_SECRET` in clear, and `runtime` marks its secrets sensitive.
- No `v*` tag triggers. Milestone tags `swe5001-*` trigger nothing.
- Cron uses UTC: 23:00 SGT = `0 15 * * *`.

---

## 7. Repository settings

| Setting | Change |
| --- | --- |
| `CODEOWNERS` | Add `/infra/swe5001/`, `/.github/workflows/swe5001-*` (including the release gate), `/.github/workflows/infra-swe5001.yml`, `/backend/services/`, `/backend/blocbuy-platform-starter/` → SWE5001 owners plus the devsecops owner |
| `labeler.yml` | Add label `swe5001` for the paths above and `docs/swe5001/**` |
| Dependabot | Add `terraform` ecosystem for `/infra/swe5001/*` (weekly, grouped). Docker and Maven entries already cover the new modules if their directories are listed |
| Rulesets | New `prod-release` (section 5). `dev` unchanged now; after two green weeks, add `service-images` and `infra-swe5001 / plan` as required checks on `dev` |
| Branch naming | Existing pattern with `swe5001` in the name, for example `feature/swe5001-outbox-20261006` |

---

## 8. External app configuration (operator, not Terraform)

| System | Change | When |
| --- | --- | --- |
| Google Cloud OAuth client | Add redirect URI `https://<swe5001 distribution>.cloudfront.net/api/v1/auth/oauth2/google/callback` and the JavaScript origin | After `edge` first apply |
| Stripe (test mode) | New webhook endpoint `https://<swe5001 distribution>.cloudfront.net/api/v1/webhooks/stripe` with the same event types as dev; store its signing secret in SSM `/blocbuy/swe5001/commerce/STRIPE_WEBHOOK_SECRET` | After `edge` first apply |
| SonarCloud | None | — |

The CloudFront domain is stable because `edge` is never destroyed, so these are one-time steps.

---

## 9. Work packages

| WP | Content | WBS | Depends on |
| --- | --- | --- | --- |
| G1 | Environments `swe5001-plan`, `swe5001-demo` (branch policy `prod`); repository variables with placeholder values; `ENABLE_SWE5001_*` set to `false` | 5.2 | none |
| G2 | `infra-swe5001.yml` (plan only). Job `static` (fmt, validate, checkov) needs no AWS access; its foundation-only `plan` starts once `SWE5001_TF_STATE_BUCKET` is set | 5.2 | AWS F1 (plan role, state bucket) |
| G3 | Reusable `swe5001-service-images.yml` (done, monolith only); the `service-images` job in `ci-backend.yml` and the service images (PR 9) | 5.1 | backend WP-B2 |
| G3a | `swe5001-release-gate.yml`; create `prod` from `dev`; ruleset `prod-release` | 5.3 | G2, G3 |
| G4 | `swe5001-deploy.yml` (push to `prod`) | 5.3 | G3a, AWS F1 (ECR, deploy role) |
| G5 | `swe5001-env.yml` with the scheduled `dispatch` job; `up` and `down` jobs in `swe5001-deploy.yml`; both call the AWS plan's environment scripts | 5.3 | AWS R1–R6, AWS S1 |
| G6 | CODEOWNERS, labeler, Dependabot | 5.1 | none |
| G7 | Google and Stripe endpoint registration | 5.3 | AWS E1 |
| G8 | Promote new checks to required on `dev` | 5.1 | two green weeks |

Each workflow PR is validated with `actionlint` and `zizmor` locally before review, matching existing practice.

## 10. Risks

| Risk | Mitigation |
| --- | --- |
| A new workflow accidentally runs on SWE5006 paths and slows every PR | Path filters on every new trigger; `service-images` runs only when `backend/**` changes |
| Trust policy uses the name-based subject and fails | Use the immutable-ID subject from section 2; verify with a dry `aws sts get-caller-identity` job before any apply |
| A promotion PR is opened from a topic branch or merged by squash, so `prod` diverges from `dev` | `promotion-source` required check; `prod-release` allows merge commits only and blocks direct and force pushes |
| A workflow fix on `dev` does not take effect for scheduled or manual SWE5001 runs | Expected: those runs execute the `prod` copy. Promote the fix; the scheduled `dispatch` job itself runs from `dev` and only needs the workflow name and inputs to stay stable |
| `dev` advances while a promotion PR is open | The PR head is `dev`, so new commits re-run the release gate and are included in the merge; open the promotion PR when the intended changes are green and merge promptly |
| Scheduled `down` destroys the environment during a rehearsal | Rehearsal and presentation end well before 23:00 SGT; if a session must run late, set `ENABLE_SWE5001_NIGHTLY_DOWN=false` for that day only |
| A failed promotion run leaves resources running | The `down` job runs with `if: always()` and `env-status.sh` fails the run on leftovers, which is visible on the promotion PR's merge commit |
