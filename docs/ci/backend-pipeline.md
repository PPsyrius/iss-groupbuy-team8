# CI/CD Pipeline Runbook

This runbook covers PR validation, `dev` analysis and delivery, releases, and rollback. See the [EC2 operator guide](../deploy/backend-ec2-pipeline-operator-guide.md) for AWS setup and the [pipeline plan](devsecops-pipeline-plan.md) and [quality gates](../quality/quality-gates.md) for rationale and thresholds.

## Pipeline shape

PR checks against current `dev` are authoritative before merge. Code-relevant `dev` pushes refresh Sonar baselines on the merged SHA; every `dev` push runs Gitleaks, while packaging and deployment run separately when enabled. `merge_group` validation is configured for future Merge Queue use.

```text
ci-backend.yml
├── build-quality     PR/tag: Spotless -> Checkstyle -> Maven verify -> reports + Sonar inputs + OpenAPI candidate
├── contract-check    OpenAPI candidate -> drift (Maven fallback for contracts-only changes)
│                     └── PR only: compatibility + `contract-change-approved` handling
├── sonar-backend     PR/tag: reuse build-quality artifacts; dev: Maven verify (coverage only) -> scan + quality gate
├── docker            image build -> EC2-profile smoke -> Trivy image gate
│                     └── enabled production tag push: preserve tested image for ECR publication
└── deploy-existing-ec2 (dev push + ENABLE_EC2_DEPLOY=true)
                       OIDC -> protected environment -> SSM -> health/auto rollback

ci-frontend.yml
├── frontend-check    generated client -> types -> lint/format -> tests (+ PR LCOV) -> build
└── sonar-frontend    reuse PR LCOV (generate fresh on dev push) -> SonarCloud scan + quality gate

security.yml
├── dev push          Gitleaks only (updates the Security badge)
├── workflow-lint     Actionlint + ShellCheck for embedded workflow shell
├── deployment-bundle ShellCheck + backend-only Compose + exact archive policy
├── gitleaks          full-history secret scan
├── trivy-deps        Maven HIGH/CRITICAL vulnerability gate
├── trivy-config      Docker/config HIGH/CRITICAL misconfiguration gate
└── codeql            Java SAST when ENABLE_CODEQL=true and Code Scanning is available

typesafe-smoke.yml
└── live Jev System One `choice` contract smoke
    ├── backend PRs, production v* tags and manual runs
    ├── merge_group when Merge Queue is available
    └── non-required: Jev availability does not gate ordinary search or merging

rollback-backend.yml  manual, confirmed, reasoned, environment-controlled SSM rollback
```

The security workflow also scans weekly on Monday at 02:17 UTC for newly disclosed issues in unchanged dependencies.

## Runtime base image maintenance (2026-10-01)

Both runtime Dockerfiles pin the same `eclipse-temurin:21-jre-noble` multi-architecture digest: Java 21, Ubuntu 24.04 and fixed base contents. Dependabot checks daily against `dev`, exempts Temurin from cooldown and retains the Java-major restriction. Base updates must pass the existing Trivy image gate and EC2-profile smoke test; update both Dockerfiles together.

The previous temporary CVE-2026-84782 repair required `libssl3t64` and `openssl` to be at least `3.0.13-0ubuntu3.16`; it was removed after verification of the official Noble rebuild. Dependabot still monitors the pinned Temurin digest without a cooldown, and the image scan remains the gate for later updates.

**2026-10-02 upstream replacement:** Both Dockerfiles now use the official `21-jre-noble` multi-architecture digest `sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766` without the APT repair. Linux/amd64 contains `3.0.13-0ubuntu3.16` for both OpenSSL packages; a fresh JDK 21 Maven verify, both image builds, Trivy 0.74.0 OS/config scans (zero HIGH/CRITICAL) and EC2-profile health/non-root smoke passed locally. A full local image scan could not finish because both Trivy Java DB registries returned unexpected EOF; require the unchanged full CI image scan before merge. A full revert of #314 would instead restore floating `21-jre`, which currently points at Ubuntu 26.04, and remove digest update controls. [October 1 evidence](../agile/sprint-03/log/2026-10-01.md) records the earlier temporary repair; [October 2 evidence](../agile/sprint-03/log/2026-10-02.md) records the replacement. No AWS deployment was checked.

## Required PR checks

The `dev` ruleset requires PRs, up-to-date branches and these checks:

- `build-quality`, `SonarCloud Backend` (`sonar-backend`), `docker`;
- `gitleaks`;
- `frontend-check`, `SonarCloud Frontend` (`sonar-frontend`).

After successful change detection, unrelated required jobs skip before starting a runner; skipped *jobs* satisfy required checks, but a path-skipped *workflow* leaves them pending. `build-quality` and `frontend-check` fail if detection fails. Contracts-only PRs still run `contract-check` via its Maven fallback when `build-quality` skips. Confirm the check contexts on the first PR with these changes (CI verification pending).

Other applicable jobs still run, but jobs absent on some PRs must not be required.

`typesafe-smoke` is non-required because Jev is optional and search has deterministic SQL fallback.

Recommended `dev` ruleset protections also include at least one approval, stale-approval dismissal, approval of the latest reviewable push, CODEOWNERS review where ownership is intentional, conversation resolution, deletion restriction and force-push blocking. Do not use `Restrict updates` merely to force PRs; the pull-request requirement establishes that path.

`deploy-existing-ec2` and `publish-ecr-*` are push-only, never required pre-merge checks. PR Sonar results are required; code-, test- and build-relevant `dev` pushes refresh the corresponding dashboard baseline.

### Post-merge delivery boundary

For backend code-, test- or build-relevant `dev` pushes, `sonar-backend` runs Maven `verify` (unit tests, PostgreSQL ITs, ArchUnit and JaCoCo; style and SpotBugs skipped) and scans the same merged SHA. Sonar does not gate `package-dev` or deployment: a failed scan leaves the dashboard stale and the workflow red even if delivery succeeds.

The separate delivery path still builds the deployable JAR from the merged source without rerunning Docker smoke, Trivy, contract compatibility, Gitleaks, CodeQL, configuration scans or the live TypeSafe smoke. It does not promote the PR-built Docker image across workflow runs. Production `v*` tag releases retain their full release validation path.

### Frontend PR coverage reuse (2026-09-29)

On relevant PRs and merge groups, `frontend-check` sets `CI_COVERAGE=true` for its existing `pnpm check` run and uploads `coverage/lcov.info` for one day. `sonar-frontend` waits for validation, verifies the report and scans without a second install/test run; fork and Dependabot PRs still report Sonar as not applicable. Local `pnpm check` has no coverage unless the flag is set. Relevant `dev` pushes generate fresh coverage on the merged SHA.

### Frontend auxiliary-job cost (2026-09-29)

Non-required `intent-stale` runs only when its manifest, package/lockfile, Node/pnpm settings, `frontend/AGENTS.md` or workflow changes. Both Playwright jobs install Chromium's headless shell with system dependencies instead of the unused full browser. Required gates and smoke cases remain unchanged; CI savings have not yet been measured.

### Post-merge Sonar change detection (2026-09-29)

On `dev`, backend Sonar reruns for source/tests/resources, Maven inputs or its workflow; frontend Sonar reruns for source, OpenAPI, dependencies, test/build/client-generation inputs or its workflow. Documentation-only and E2E-only merges skip coverage/analysis, leaving the dashboard at the last code-relevant SHA until the next scan. PR and release-tag gates, and the separate packaging/deployment filters, are unchanged.

### Documentation-only PR and delivery filtering (2026-10-03)

The October 1–3 Actions audit found 796 runs and 1,717 runner jobs; rounding each job to a whole minute gives about 2,838 minutes, of which frontend and backend CI account for 1,259 and 958 respectively. In the 200 most recently merged PRs (September 11–October 3), 132 touched frontend CI inputs, but 19 changed only frontend documentation or agent guidance and other non-frontend files; two of the 76 backend-input PRs were README badge edits that could skip a backend build, too small a sample to justify changing that gate. PR #126 changed 106 files; its full paginated list confirmed that the 14 frontend source/test changes and contract file still require all frontend checks. `ci-frontend.yml` therefore uses `dorny/paths-filter`'s `some-with-excludes` option to prevent `frontend/docs/`, `README.md`, `AGENTS.md` and `CLAUDE.md` changes from triggering application validation, Playwright, Sonar or post-merge packaging. The workflow still starts and its required jobs skip before allocating a runner; the separate Intent drift check still covers `AGENTS.md` and `docs/tanstack-skills.json`. Source, tests, frontend build inputs, contracts and workflow changes retain the existing gates. Backend CI and the Gitleaks check are unchanged. The first documentation-only PR and mixed code-plus-docs PR must confirm required checks resolve and relevant gates still run; billing savings are estimated, not yet observed in CI.

## TypeSafe Jev validation

BlocBuy's optional AI-assisted listing search uses TypeSafe Jev System One to re-rank the deterministic PostgreSQL shortlist. PostgreSQL remains authoritative for filtering, pagination and the fallback result order.

Normal backend verification uses deterministic/stubbed tests and requires no live TypeSafe credential. The separate `typesafe-smoke.yml` workflow performs one live API contract check using the same `choice` response shape consumed by the listing-search reranker.

The live smoke validates the external wire contract rather than model quality:

- the service returns a model identity;
- `answers.best_match` is a `choice` answer;
- expected candidate probabilities are present;
- probability values are numbers in `[0,1]`;
- usage metadata is present.

It deliberately does not assert which candidate Jev must rank first.

The live smoke runs for applicable non-fork, non-Dependabot backend pull requests, production `v*` tags, manual dispatches, and `merge_group` when Merge Queue is available. It does not run on `push/dev`; live Jev checks are not part of the post-merge validation.

Jev availability is not a required merge condition. If the live service is unavailable at runtime, the application preserves the original deterministic SQL order.

## Run backend quality gates locally

From `backend/`, with JDK 21:

```bash
./mvnw spotless:apply
./mvnw spotless:check
./mvnw checkstyle:check
./mvnw spotbugs:check
./mvnw verify
./mvnw verify -Popenapi-export
```

`./mvnw verify` covers the local equivalent of `build-quality`. CI uses the committed Maven wrapper.

## Run deployment policy checks locally

From the repository root:

```bash
bash -n deployment/backend/bin/*.sh
docker compose -f deployment/backend/compose.yml config --no-interpolate
```

The `security` workflow additionally runs pinned Actionlint, ShellCheck and Trivy versions. The SSM bundle may contain exactly `compose.yml` and the four lifecycle scripts; application secrets are not transported by CI.

## Release and deployment behavior

On PR, the `docker` job builds and boots the image with the `ec2` Spring profile against PostgreSQL and Redis, then fails on fixable HIGH/CRITICAL image vulnerabilities.

After an up-to-date, validated pull request lands on `dev`, when `ENABLE_EC2_DEPLOY=true`:

1. the delivery path packages the merged application without rerunning the pre-merge gates;
2. the runtime image is assembled and GitHub OIDC assumes the ECR publisher role;
3. the image is pushed with a run-specific tag and Docker's resulting digest is checked against ECR;
4. the protected `backend-dev` environment gates the deploy job;
5. a separate OIDC role sends the allowlisted bundle and digest to one EC2 instance via SSM;
6. the host preflight, deploy and health scripts run;
7. failure triggers an automatic application rollback where a previous digest exists.

The workflow never provisions or operates PostgreSQL, Redis, EC2 or Terraform state.

Production `v*` releases execute the full backend and security validation path and also run the live TypeSafe compatibility smoke. A TypeSafe smoke failure identifies an external integration problem but does not change the application's deterministic search fallback design.

## Manual rollback

Use **Actions -> rollback-backend -> Run workflow** from `dev`. Provide a reason, confirm the warning, and normally leave `target_image` blank to use the recorded previous image. An explicit target must be a digest in the configured account, Region and repository.

Deployment and rollback share one non-cancellable concurrency group. A rollback cannot reverse Flyway migrations; deployed migrations must remain backward-compatible.

## Fixing a red check

| Check | Response |
|---|---|
| `spotless:check` | Run `./mvnw spotless:apply` and commit the formatting |
| Checkstyle/SpotBugs/ArchUnit | Fix the reported code or architecture violation; suppressions require a written reason |
| `contract-check` | Regenerate and commit OpenAPI drift; revert or explicitly approve incompatible API changes |
| `SonarCloud Backend` / `SonarCloud Frontend` | Investigate the project quality-gate finding; document only genuine false positives |
| `codeql` | Investigate the Security finding; document only genuine false positives |
| `gitleaks` | Rotate the credential first, then remove it from current code and Git history |
| `trivy-deps`/image Trivy | Upgrade the dependency/base image or record time-bounded risk acceptance |
| `trivy-config` | Correct the Docker/config control; do not blanket-ignore policies |
| `workflow-lint` | Fix workflow syntax, expressions or embedded shell diagnostics |
| `deployment-bundle` | Restore the one-service boundary or remove unexpected archive content |
| `typesafe-smoke` | Verify `TYPESAFE_API_KEY`, TypeSafe availability and the live System One `choice` response contract |
| smoke test | Inspect `smoke-app` logs and reproduce with the `ec2` profile |
| ECR publication | Verify OIDC branch trust, repository variables and ECR immutability |
| SSM deployment | Verify environment trust, instance ID, managed-node status and restricted IAM policy |
| post-deploy health | Inspect SSM output and host container logs; confirm whether automatic rollback succeeded |

## Rules

- Do not skip tests or security gates in the release path.
- Do not make optional TypeSafe availability a prerequisite for deterministic listing search.
- Do not add long-lived AWS keys or application secrets to GitHub.
- Do not deploy mutable image tags.
- Do not add PostgreSQL/Redis services or volumes to the backend deployment bundle.
- Record any approved exception, deploy approval and rollback reason in durable project evidence.
