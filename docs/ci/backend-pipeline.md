# Backend CI/CD Runbook

This runbook describes the implemented backend quality, security, release, deployment and rollback workflows. AWS/EC2 setup is in the [existing EC2 operator guide](../deploy/backend-ec2-pipeline-operator-guide.md); gate rationale and thresholds are in [devsecops-pipeline-plan.md](devsecops-pipeline-plan.md) and [`quality-gates.md`](../quality/quality-gates.md).

## Pipeline shape

Pull requests are the authoritative pre-merge validation path. The `dev` ruleset requires selected status checks to pass against the latest `dev` state before a pull request can merge. `merge_group` support remains in the workflows for forward compatibility if the repository later becomes eligible for GitHub Merge Queue.

After an up-to-date pull request passes its required checks and lands on `dev`, backend-relevant pushes run tests, PostgreSQL ITs and JaCoCo in `sonar-backend` on the merged SHA, then refresh its main-branch baseline. Every `dev` push also runs Gitleaks to update the Security badge; the broader security gates stay on PRs, releases and weekly scans. Packaging, ECR publication and EC2 deployment run independently when enabled.

```text
pull_request                    push/dev
├── build-quality               ├── SonarCloud Backend (backend-relevant: tests + coverage → scan)
├── contract-check              ├── Security (Gitleaks only)
├── SonarCloud Backend          └── package merged JAR → assemble image → publish ECR → deploy EC2
├── docker
├── security
└── typesafe-smoke (live, non-required)
        ↑
 authoritative pre-merge validation

merge_group
└── same applicable validation when Merge Queue is available

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
├── frontend-check    generated client -> types -> lint/format -> tests -> build
└── sonar-frontend    SonarCloud Frontend TypeScript scan + LCOV quality gate

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

The security workflow also runs every Monday at 02:17 UTC to detect newly disclosed issues in unchanged dependencies.

## Required PR checks

Configure the `dev` ruleset to require pull requests, required status checks and branches to be up to date before merging.

The currently required checks are:

- `build-quality`;
- `sonar-backend`;
- `docker`;
- `gitleaks`;
- `frontend-check`;
- `sonar-frontend`.

Other applicable CI/security jobs still run and report failures, but conditional jobs should not be made permanently required unless their presence is made unconditional.

`typesafe-smoke` is deliberately not a required status check. It verifies compatibility with the live TypeSafe Jev service, while the application itself treats Jev as an optional enhancement and preserves deterministic SQL ordering if the service is unavailable.

Recommended `dev` ruleset protections also include at least one approval, stale-approval dismissal, approval of the latest reviewable push, CODEOWNERS review where ownership is intentional, conversation resolution, deletion restriction and force-push blocking. Do not use `Restrict updates` merely to force PRs; the pull-request requirement establishes that path.

`merge_group` remains configured in the workflows as dormant forward-compatible support. If the repository later becomes eligible for GitHub Merge Queue, the same applicable validation can run against the speculative integrated state without redesigning the workflows.

`deploy-existing-ec2` and `publish-ecr-*` stay `push`-only. They must not be required pre-merge checks. Backend and frontend SonarCloud jobs also analyze relevant pushes to `dev` so both projects retain a current main-branch baseline; their PR results remain the required contexts.

### Post-merge delivery boundary

Direct changes to `dev` are prevented by the repository ruleset: normal changes arrive through an up-to-date pull request with passing required checks. For backend-relevant pushes, `sonar-backend` generates classes and aggregate JaCoCo coverage using Maven `verify` (unit tests, PostgreSQL ITs and ArchUnit; Spotless, Checkstyle and SpotBugs skipped), then scans the exact merged SHA on the same runner. It refreshes the `dev` dashboard without gating `package-dev`, ECR publication or deployment. Failed analysis leaves the dashboard stale and the push workflow red even if delivery succeeds.

The separate delivery path still builds the deployable JAR from the merged source without rerunning Docker smoke, Trivy, contract compatibility, Gitleaks, CodeQL, configuration scans or the live TypeSafe smoke. It does not promote the PR-built Docker image across workflow runs. Production `v*` tag releases retain their full release validation path.

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
