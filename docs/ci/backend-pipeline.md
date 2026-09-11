# Backend CI/CD Runbook

This runbook describes the implemented backend quality, security, release, deployment and rollback
workflows. AWS/EC2 setup is in the
[existing EC2 operator guide](../deploy/backend-ec2-pipeline-operator-guide.md); gate rationale and
thresholds are in [devsecops-pipeline-plan.md](devsecops-pipeline-plan.md) and
[`quality-gates.md`](../quality/quality-gates.md).

## Pipeline shape

Pull requests and pushes to `dev` that touch backend, contracts or backend deployment files run:

```text
ci-backend.yml
├── build-quality     Spotless -> Checkstyle -> Maven verify -> reports
├── contract-check    OpenAPI regeneration -> drift -> compatibility on PR
├── docker            image build -> EC2-profile smoke -> Trivy image gate
│                     └── dev push only: publish the tested image to ECR by digest
└── deploy-existing-ec2 (dev push + ENABLE_EC2_DEPLOY=true)
                      OIDC -> protected environment -> SSM -> health/auto rollback

security.yml
├── workflow-lint     Actionlint + ShellCheck for embedded workflow shell
├── deployment-bundle ShellCheck + backend-only Compose + exact archive policy
├── gitleaks          full-history secret scan
├── trivy-deps        Maven HIGH/CRITICAL vulnerability gate
├── trivy-config      Docker/config HIGH/CRITICAL misconfiguration gate
└── codeql            optional Java SAST gate

rollback-backend.yml  manual, confirmed, reasoned, environment-controlled SSM rollback
```

The security workflow also runs every Monday at 02:17 UTC to detect newly disclosed issues in
unchanged dependencies.

## Required PR checks

Configure `dev` branch protection with:

- `build-quality`, `contract-check`, `docker`;
- `workflow-lint`, `deployment-bundle`, `gitleaks`, `trivy-deps`, `trivy-config`;
- `codeql` only after GHAS is enabled consistently.

`deploy-existing-ec2` is push-only and must not be a PR-required check.

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

On a green push to `dev`, when `ENABLE_EC2_DEPLOY=true`:

1. GitHub OIDC assumes the ECR publisher role;
2. the exact smoke-tested image is pushed with a run-specific immutable tag;
3. Docker's resulting digest is checked against ECR;
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
