# SWE5001 Implementation Plan: GitHub Repository Configuration and Pipelines

Detailed GitHub work for the [refactoring plan](microservice-refactoring-plan.md), section 3.4. Companion plans: [backend architecture](impl-backend-architecture.md), [AWS infrastructure](impl-aws-infrastructure.md), [verification and observability](impl-verification-observability.md).

Status: **Draft, 2026-09-29.** Current settings were read with `gh` on 2026-09-29 (names only; no values).

---

## 1. Principles

- **Add, never repurpose.** Every SWE5001 variable, environment, role and workflow is new and prefixed `SWE5001_` / `swe5001-`. Existing SWE5006 names (`*_DEV`, `*_PROD`, `backend-dev`, `frontend-dev`, …) are not changed or reused.
- **No application secrets in GitHub.** Stripe, Google and database credentials live in AWS SSM Parameter Store and are injected into ECS tasks. GitHub holds only role ARNs and non-secret identifiers. The plan adds **zero** GitHub secrets.
- **OIDC only.** Workflows assume AWS roles through the existing GitHub OIDC provider; no AWS keys are stored.
- **Feature switches.** Each SWE5001 delivery path has an `ENABLE_SWE5001_*` variable, mirroring `ENABLE_EC2_DEPLOY`, so it can be frozen without editing workflows.

---

## 2. Current state (2026-09-29)

| Kind | Existing names |
| --- | --- |
| Repository variables | `AWS_ACCOUNT_ID`, `AWS_REGION`, `AWS_ECR_PUSH_ROLE_ARN`, `AWS_FRONTEND_DEPLOY_ROLE_ARN_DEV`, `ECR_REPOSITORY_DEV`, `ENABLE_CODEQL`, `ENABLE_EC2_DEPLOY`, `ENABLE_S3_DEPLOY`, `FRONTEND_CLOUDFRONT_DIST_ID_DEV`, `FRONTEND_S3_BUCKET_DEV`, `FRONTEND_URL_DEV`, `SONAR_ORGANIZATION`, `SONAR_PROJECT_KEY` |
| Repository secrets | `BOARD_SNAPSHOT_TOKEN`, `SONAR_TOKEN`, `TYPESAFE_API_KEY` (`SONAR_TOKEN` also exists in the Dependabot scope) |
| Environments | `backend-dev`, `backend-prod`, `backend-prod-publish`, `frontend-dev` (all with branch policies), `copilot` |
| Rulesets | `dev-require-review` (branch), `main-no-forcepush` (branch) |
| Required PR checks | `build-quality`, `contract-check`, `frontend-check` |
| OIDC subject format | Immutable-ID form: `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:<suffix>`; trust policies must use this form, not `repo:PPsyrius/iss-groupbuy-team8:*` |

---

## 3. Environments

| Environment | Deployment branches | Reviewers | Used by | OIDC subject suffix |
| --- | --- | --- | --- | --- |
| `swe5001-plan` | any (PRs) | none | `terraform plan` on PRs | `environment:swe5001-plan` |
| `swe5001-demo` | `dev` only | none (the demo environment is disposable); add 1 reviewer for `env-up` in the week of 20 Oct if the team wants a gate | image publish, ECS deploy, `env-up`, `env-down`, `env-cycle`, `terraform apply` | `environment:swe5001-demo` |

Environment-scoped subjects keep the plan role usable from PRs while the apply and deploy roles are reachable only from `dev` runs.

---

## 4. Variables

All are **non-secret**. Values come from `foundation` Terraform outputs (see the AWS plan, section 4).

### Repository level

| Variable | Example value | Used by |
| --- | --- | --- |
| `SWE5001_TF_STATE_BUCKET` | `blocbuy-tfstate-<account>` | all Terraform jobs |
| `SWE5001_ECR_REPOSITORY_PREFIX` | `swe5001` (repos `swe5001/identity-svc`, `swe5001/commerce-svc`, `swe5001/notification-svc`) | image build and publish |
| `ENABLE_SWE5001_DEPLOY` | `false` until `foundation` exists, then `true` | `swe5001-deploy.yml` |
| `ENABLE_SWE5001_NIGHTLY_DOWN` | `true` | `swe5001-env.yml` schedule |
| `ENABLE_SWE5001_ENV_CYCLE` | `true` | `swe5001-env.yml` schedule |

`AWS_ACCOUNT_ID` and `AWS_REGION` are reused as-is (same account, `ap-southeast-1`).

### Environment `swe5001-plan`

| Variable | Purpose |
| --- | --- |
| `SWE5001_TF_PLAN_ROLE_ARN` | Read-only role: `ReadOnlyAccess` plus state-bucket read and lock-file write |

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

## 5. Workflows

| File | Status | Trigger | Environment | Jobs |
| --- | --- | --- | --- | --- |
| `ci-backend.yml` | extended | existing | none | New job `service-images`: matrix `[identity-svc, commerce-svc, notification-svc]`, needs `build-quality`; builds the ARM64 image with `ARG MODULE`, boots it against service containers (PostgreSQL, Redis) with `topology=distributed` stubs, Trivy scan. Existing jobs unchanged except the `-exec` jar path (backend plan WP-B2) |
| `infra-swe5001.yml` | new | PR and `dev` push on `infra/swe5001/**` | `swe5001-plan` | `fmt -check`, `init`, `validate`, `plan` per stack, checkov, plan summary posted as a sticky PR comment |
| `swe5001-deploy.yml` | new | `dev` push on `backend/**` (after `ci-backend` succeeds, via `workflow_run`) and manual | `swe5001-demo` | Build ARM64 images, push by digest, write digests to `/blocbuy/swe5001/candidate/<service>`; if the `runtime` stack is up (ECS cluster exists), render task definitions and deploy with the circuit breaker, wait for steady state on the new task definition, smoke test, and only then copy the candidate to `/blocbuy/swe5001/release/<service>` (a rollback or failed smoke test leaves the release digest unchanged; with `runtime` down the candidate stays pending); frontend build with `VITE` env for the SWE5001 edge, S3 sync, CloudFront invalidation |
| `swe5001-env.yml` | new | manual (`up`, `down`, `cycle`), schedule 23:00 SGT (`down`), schedule every third day 02:00 SGT (`cycle`) | `swe5001-demo` | `up`: apply `runtime` with last known good digests, run the DB-init task, apply `edge`, wait for healthy targets, smoke test, deploy any pending candidate digests with the same promote-on-success steps, write URLs to the job summary. `down`: destroy `runtime` with one retry. `cycle`: up, smoke, down, record durations |
| `swe5001-perf.yml` | new | manual | `swe5001-demo` | Run k6 scenarios against `SWE5001_FRONTEND_URL`; upload results (see verification plan) |

Rules for all new workflows:

- `permissions: {}` at the top; each job grants only `contents: read`, `id-token: write` where it assumes a role, and `pull-requests: write` for the plan comment.
- Actions pinned by commit SHA, as in the existing workflows.
- `concurrency: swe5001-tfstate` (no cancel) for every job that writes Terraform state, so `env-up`, `env-down` and `apply` never overlap.
- No `v*` tag triggers. Milestone tags `swe5001-*` trigger nothing.
- Cron uses UTC: 23:00 SGT = `0 15 * * *`.

---

## 6. Repository settings

| Setting | Change |
| --- | --- |
| `CODEOWNERS` | Add `/infra/swe5001/`, `/.github/workflows/swe5001-*`, `/.github/workflows/infra-swe5001.yml`, `/backend/services/`, `/backend/blocbuy-platform-starter/` → SWE5001 owners plus the devsecops owner |
| `labeler.yml` | Add label `swe5001` for the paths above and `docs/swe5001/**` |
| Dependabot | Add `terraform` ecosystem for `/infra/swe5001/*` (weekly, grouped). Docker and Maven entries already cover the new modules if their directories are listed |
| Rulesets | No change now. After two green weeks, add `service-images` and `infra-swe5001 / plan` as required checks on `dev` |
| Branch naming | Existing pattern with `swe5001` in the name, for example `feature/swe5001-outbox-20261006` |

---

## 7. External app configuration (operator, not Terraform)

| System | Change | When |
| --- | --- | --- |
| Google Cloud OAuth client | Add redirect URI `https://<swe5001 distribution>.cloudfront.net/api/v1/auth/oauth2/google/callback` and the JavaScript origin | After `edge` first apply |
| Stripe (test mode) | New webhook endpoint `https://<swe5001 distribution>.cloudfront.net/api/v1/webhooks/stripe` with the same event types as dev; store its signing secret in SSM `/blocbuy/swe5001/commerce/STRIPE_WEBHOOK_SECRET` | After `edge` first apply |
| SonarCloud | None | — |

The CloudFront domain is stable because `edge` is never destroyed, so these are one-time steps.

---

## 8. Work packages

| WP | Content | WBS | Depends on |
| --- | --- | --- | --- |
| G1 | Environments `swe5001-plan`, `swe5001-demo`; repository variables with placeholder values; `ENABLE_SWE5001_*` set to `false` | 5.2 | none |
| G2 | `infra-swe5001.yml` (plan only) | 5.2 | AWS F1 (plan role, state bucket) |
| G3 | `service-images` job in `ci-backend.yml` | 5.1 | backend WP-B2 |
| G4 | `swe5001-deploy.yml` | 5.3 | G3, AWS F1 (ECR, deploy role) |
| G5 | `swe5001-env.yml` and apply path in `infra-swe5001.yml` | 5.3 | AWS R1–R6 |
| G6 | CODEOWNERS, labeler, Dependabot | 5.1 | none |
| G7 | Google and Stripe endpoint registration | 5.3 | AWS E1 |
| G8 | Promote new checks to required | 5.1 | two green weeks |

Each workflow PR is validated with `actionlint` and `zizmor` locally before review, matching existing practice.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| A new workflow accidentally runs on SWE5006 paths and slows every PR | Path filters on every new trigger; `service-images` runs only when `backend/**` changes |
| Trust policy uses the name-based subject and fails | Use the immutable-ID subject from section 2; verify with a dry `aws sts get-caller-identity` job before any apply |
| Scheduled `down` destroys the environment during a rehearsal | `ENABLE_SWE5001_NIGHTLY_DOWN=false` from 21 to 23 Oct |
