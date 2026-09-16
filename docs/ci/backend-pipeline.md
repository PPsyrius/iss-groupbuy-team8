# Backend CI/CD Runbook

This runbook describes the implemented backend quality, security, release, deployment and rollback
workflows. AWS/EC2 setup is in the
[existing EC2 operator guide](../deploy/backend-ec2-pipeline-operator-guide.md); gate rationale and
thresholds are in [devsecops-pipeline-plan.md](devsecops-pipeline-plan.md) and
[`quality-gates.md`](../quality/quality-gates.md).

## Pipeline shape

Pull requests give fast feedback, and the `dev` merge queue repeats the same applicable
validation against GitHub's speculative merge (`merge_group`) before the change can land.
The `merge_group` run is the authoritative final quality/security gate. After it passes and
GitHub updates `dev`, the `push` workflow performs delivery work only: package the merged
application, assemble the image, publish it to ECR, and deploy it to EC2.

```text
pull_request                 merge_group                     push/dev
├── build-quality            ├── build-quality               ├── package merged JAR
├── contract-check           ├── contract-check              ├── assemble image
├── sonar                    ├── sonar                       ├── publish ECR
├── docker                   ├── docker                      └── deploy EC2
└── security                 └── security
                                  ↑
                           final pre-merge gate

ci-backend.yml
├── build-quality     Spotless -> Checkstyle -> Maven verify -> reports + Sonar inputs
├── contract-check    OpenAPI regeneration -> drift
│                     └── PR only: compatibility + `contract-change-approved` handling
├── sonar             SonarCloud Maven scan + quality gate (artifact reuse from build-quality)
├── docker            image build -> EC2-profile smoke -> Trivy image gate
│                     └── enabled dev push: preserve tested image for ECR publication
└── deploy-existing-ec2 (dev push + ENABLE_EC2_DEPLOY=true)
                      OIDC -> protected environment -> SSM -> health/auto rollback

security.yml
├── workflow-lint     Actionlint + ShellCheck for embedded workflow shell
├── deployment-bundle ShellCheck + backend-only Compose + exact archive policy
├── gitleaks          full-history secret scan
├── trivy-deps        Maven HIGH/CRITICAL vulnerability gate
├── trivy-config      Docker/config HIGH/CRITICAL misconfiguration gate
└── codeql            Java SAST when ENABLE_CODEQL=true and Code Scanning is available

rollback-backend.yml  manual, confirmed, reasoned, environment-controlled SSM rollback
```

The security workflow also runs every Monday at 02:17 UTC to detect newly disclosed issues in
unchanged dependencies.

## Required PR checks

Configure `dev` with merge queue enabled (`Settings > General > Pull Requests > Allow merge
queue`) and a `dev` ruleset that requires pull requests, required CI checks and the merge queue.
The queue's `merge_group` run is the final pre-merge validation of the speculative `dev` state.

Require the same applicable CI gates for PRs and merge groups:

- `build-quality`, `contract-check`, `docker`, `SonarCloud Code Analysis`;
- `workflow-lint`, `deployment-bundle`, `gitleaks`, `trivy-deps`, `trivy-config`;
- `codeql` only when GitHub Code Scanning is enabled and reporting reliably.

Recommended `dev` ruleset protections also include at least one approval, stale-approval dismissal,
approval of the latest reviewable push, CODEOWNERS review where ownership is intentional,
conversation resolution, deletion restriction and force-push blocking. Do not use `Restrict
updates` merely to force PRs; the pull-request and merge-queue requirements establish that path.

`deploy-existing-ec2` and `publish-ecr-*` stay `push`-only (the queue SHA is temporary and OIDC
trusts only `refs/heads/dev`) and must not be queue-required checks.

### Post-merge delivery boundary

Direct changes to `dev` are prevented by the repository ruleset: normal changes must arrive through
a pull request and the required merge queue. Because `merge_group` validates the speculative
integrated state immediately before GitHub updates `dev`, expensive quality/security gates are not
repeated on the resulting `push/dev`.

The delivery run rebuilds/packages the deployable JAR and assembles the runtime image from the
merged `dev` source without rerunning tests, formatting/style checks, SpotBugs, JaCoCo, Sonar,
contract validation, smoke tests, Trivy, CodeQL, Gitleaks, or configuration scans. This deliberately
trades exact promotion of the queue-built Docker image for substantially lower GitHub Actions
usage. Production `v*` tag releases retain the full release validation path.

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

The `security` workflow additionally runs pinned Actionlint, ShellCheck and Trivy versions. The SSM
bundle may contain exactly `compose.yml` and the four lifecycle scripts; application secrets are not
transported by CI.

## Release and deployment behavior

On PR, the `docker` job builds and boots the image with the `ec2` Spring profile against PostgreSQL
and Redis, then fails on fixable HIGH/CRITICAL image vulnerabilities.

After a merge-queue-validated change lands on `dev`, when `ENABLE_EC2_DEPLOY=true`:

1. the delivery path packages the merged application without rerunning the pre-merge gates;
2. the runtime image is assembled and GitHub OIDC assumes the ECR publisher role;
3. the image is pushed with a run-specific tag and Docker's resulting digest is checked against ECR;
4. the protected `backend-dev` environment gates the deploy job;
5. a separate OIDC role sends the allowlisted bundle and digest to one EC2 instance via SSM;
6. the host preflight, deploy and health scripts run;
7. failure triggers an automatic application rollback where a previous digest exists.

The workflow never provisions or operates PostgreSQL, Redis, EC2 or Terraform state.

## Manual rollback

Use **Actions -> rollback-backend -> Run workflow** from `dev`. Provide a reason, confirm the warning,
and normally leave `target_image` blank to use the recorded previous image. An explicit target must
be a digest in the configured account, Region and repository.

Deployment and rollback share one non-cancellable concurrency group. A rollback cannot reverse
Flyway migrations; deployed migrations must remain backward-compatible.

## Fixing a red check

| Check | Response |
|---|---|
| `spotless:check` | Run `./mvnw spotless:apply` and commit the formatting |
| Checkstyle/SpotBugs/ArchUnit | Fix the reported code or architecture violation; suppressions require a written reason |
| `contract-check` | Regenerate and commit OpenAPI drift; revert or explicitly approve incompatible API changes |
| `sonar`/`codeql` | Investigate the server/Security finding; document only genuine false positives |
| `gitleaks` | Rotate the credential first, then remove it from current code and Git history |
| `trivy-deps`/image Trivy | Upgrade the dependency/base image or record time-bounded risk acceptance |
| `trivy-config` | Correct the Docker/config control; do not blanket-ignore policies |
| `workflow-lint` | Fix workflow syntax, expressions or embedded shell diagnostics |
| `deployment-bundle` | Restore the one-service boundary or remove unexpected archive content |
| smoke test | Inspect `smoke-app` logs and reproduce with the `ec2` profile |
| ECR publication | Verify OIDC branch trust, repository variables and ECR immutability |
| SSM deployment | Verify environment trust, instance ID, managed-node status and restricted IAM policy |
| post-deploy health | Inspect SSM output and host container logs; confirm whether automatic rollback succeeded |

## Rules

- Do not skip tests or security gates in the release path.
- Do not add long-lived AWS keys or application secrets to GitHub.
- Do not deploy mutable image tags.
- Do not add PostgreSQL/Redis services or volumes to the backend deployment bundle.
- Record any approved exception, deploy approval and rollback reason in durable project evidence.
