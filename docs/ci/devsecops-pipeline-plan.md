# DevSecOps Pipeline Plan — Backend and Repository Automation

Status: approved. Quality/security Steps 1–5 and existing-EC2 delivery Step 6 are implemented.
Scope: backend quality gates and delivery, security scanning, repository process automation, and
live TypeSafe Jev integration verification.
Out of scope here: frontend and Terraform pipelines (see `docs/design/repository-structure.md`).

## Goals

| Standard | Tooling | Enforcement point |
|---|---|---|
| Java 21 LTS runtime | Maven Wrapper 3.9.x + Enforcer | every build, local and CI |
| Code formatter | Spotless + google-java-format, AOSP style (4-space indent) | `validate` phase, CI fail-fast |
| Code-style linting | Checkstyle (Google checks, 120 cols) | `verify` phase |
| Static bug analysis | SpotBugs | `verify` phase |
| Code coverage | JaCoCo | report every build; gate via SonarQube quality gate |
| Code quality gate | SonarQube / SonarCloud | CI job + server-side quality gate |
| Architecture enforcement | ArchUnit (COLA/DDD layering) | unit test phase |
| Secret / vulnerability / image scanning | Gitleaks, Trivy, Dependabot | CI + weekly cron |
| Live AI integration compatibility | TypeSafe Jev System One smoke | applicable PRs, `v*`, manual; non-required |
| Immutable application delivery | ECR digest + GitHub OIDC + SSM | green push to protected `dev` |

## Current State → Target State

| Item | Before | After Steps 1–6 |
|---|---|---|
| Checkstyle + ArchUnit + Enforcer | ✅ configured | unchanged |
| Spotless (AOSP) | ❌ | `spotless:check` at `validate`; `spotless:apply` for devs |
| SpotBugs | ❌ | `spotbugs:check` at `verify`, `spotbugs-excludes.xml` filter |
| JaCoCo | ❌ | per-module reports + aggregate in `blocbuy-bootstrap` |
| SonarQube | ❌ | `sonar` CI job with artifact reuse (SonarCloud scan + quality gate) |
| GitHub workflows | ❌ none | `ci-backend.yml`, `security.yml`, `pr-labeler.yml`, `typesafe-smoke.yml`, `rollback-backend.yml` |
| Dependabot | ❌ | `.github/dependabot.yml` (maven, github-actions, docker) |
| Docker image | ❌ (only `application-docker.yml`) | `backend/Dockerfile` multi-stage, non-root |
| Secret scanning | ❌ | Gitleaks in `security.yml` + `.gitleaks.toml` |
| TypeSafe Jev | ❌ | optional listing-search reranker + deterministic tests + live contract smoke |
| Existing EC2 delivery | ❌ | backend-only bundle, ECR digest, SSM deploy, health and rollback |

## Step 1 — Local Quality Tooling (POM + format)

All versions pinned in `blocbuy-parent` properties, verified against Maven Central 2026-08-31:

| Plugin | Version |
|---|---|
| spotless-maven-plugin | 3.10.1 |
| google-java-format | 1.36.1 |
| spotbugs-maven-plugin | 4.10.4.0 (spotbugs 4.10.4) |
| jacoco-maven-plugin | 0.8.15 |

1. **Spotless**: google-java-format `AOSP` style (4-space indent), import order `\#,*` (static block, blank line, then everything alphabetical — matches Checkstyle `CustomImportOrder`), `removeUnusedImports`, trailing-whitespace/newline cleanup. Bound to `validate` → `check` so every `verify` gates on formatting. Devs fix with `./mvnw spotless:apply`.
2. **Checkstyle conflict resolution (decision)**: Spotless/AOSP is the single source of truth for formatting. Checkstyle `Indentation` module moved from Google 2-space to AOSP values (`basicOffset=4, caseIndent=4, throwsIndent=8, lineWrappingIndentation=8, arrayInitIndent=4`). Line length stays 120 (D13); google-java-format wraps at 100, which is a subset — no conflict. Everything non-formatting (naming, imports, Javadoc, blocks) stays enforced by Checkstyle.
3. **One-time reformat**: `./mvnw spotless:apply` over the whole codebase, landed as a single formatting commit recorded in `.git-blame-ignore-revs` so `git blame` stays useful.
4. **SpotBugs**: `check` at `verify`, `effort=Max`, `threshold=Medium`, exclusions in `backend/spotbugs-excludes.xml` (bootstrap main class, Lombok/Spring-known false positives only).
5. **JaCoCo**: `prepare-agent` in every module; aggregate report in `blocbuy-bootstrap` → `blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`. Sonar owns the new-code coverage threshold.

## Step 2 — CI Workflows

### `.github/workflows/ci-backend.yml`

Triggers: PRs and pushes to `dev` touching the backend/contract/deployment paths, `v*` tag pushes,
and `merge_group` for forward compatibility with GitHub Merge Queue.

Pull requests are the authoritative pre-merge validation path. The `dev` repository ruleset requires
selected status checks to pass against the latest `dev` state before merging. `merge_group` retains
the same applicable validation so Merge Queue can be enabled later without redesigning the
workflows.

After GitHub updates `dev`, the resulting push runs a lightweight delivery path only: package the
merged application, assemble the runtime image, publish to ECR, and deploy. It does not repeat the
expensive quality/security gates. Production `v*` tag pushes retain the full release validation path.

Stale ordinary runs may be cancelled, but merge-group validation must not be cancelled as stale.

- **Job `build-quality`** — temurin 21, Maven cache:
  1. `./mvnw -B spotless:check checkstyle:check` (fail fast)
  2. `./mvnw -B verify` (unit tests + ArchUnit + Failsafe ITs + SpotBugs + JaCoCo)
  3. Upload `jacoco.xml` + SpotBugs XML + checkstyle results as artifacts (7-day retention)
  4. Upload Sonar inputs: compiled classes, test results, JaCoCo reports (1-day retention)
- **Job `contract-check`** — regenerate the OpenAPI contract then `git diff --exit-code -- contracts/`; fails the PR if the committed `openapi.json` drifts from the code. On PRs (Step 5) it also runs `openapi-diff --fail-on-incompatible` against the base branch's committed contract, failing on backward-incompatible API changes unless the PR carries the `contract-change-approved` label (explicit human approval; diff then runs report-only).
- **Job `sonar`** — see Step 3. Runs after `build-quality` when Sonar credentials/context permit it.
- **Job `docker`** — applicable PR, merge-group and production-tag runs: download the exact boot-jar produced by successful `build-quality`, assemble it with `Dockerfile.ci`, smoke test with the EC2 profile against PG 18 + Redis 8, and run the Trivy image gate. SARIF is preserved as an artifact; Code Scanning ingestion is push-only when enabled. Enabled production tag pushes preserve the tested image for ECR publication.
- **Post-merge dev delivery** — after an up-to-date validated PR lands on `dev`, rebuild the delivery image from the merged source without repeating the pre-merge quality/security gates, publish it to ECR by immutable digest, then run `deploy-existing-ec2`.
- **Job `deploy-existing-ec2`** — enabled `dev` delivery pushes only: protected environment, GitHub OIDC, exact SSM bundle allowlist, single-instance deployment, health check and automatic application rollback.

### Pull-request and post-merge delivery decision

The repository ruleset makes pull requests the normal path into `dev`; direct pushes and
force-pushes are not part of the normal development flow.

The required status checks are currently:

- `build-quality`;
- `sonar`;
- `docker`;
- `gitleaks`.

The ruleset also requires branches to be up to date before merging. If `dev` changes after a PR has
passed its required checks, the PR must be updated and the required validation must pass against the
latest `dev` state before it can merge.

`merge_group` support is retained in the workflows but is not the repository's current authoritative
gate. If the repository later becomes eligible for GitHub Merge Queue, the same applicable gates
can validate GitHub's speculative integrated state.

To conserve GitHub Actions minutes, the resulting `push/dev` does not repeat Maven verification,
Sonar, contract validation, Docker smoke/Trivy validation, `security.yml`, or the live TypeSafe
smoke. It only performs the minimum work needed to create and deliver the artifact from the merged
source: package, assemble the image, publish by immutable ECR digest, and deploy.

This intentionally changes the earlier artifact-provenance model: the Docker image produced during
pre-merge validation is not promoted across workflow runs. The delivery path rebuilds the deployable
artifact from the exact merged source after the up-to-date PR passes. This accepts a small
reproducibility/provenance trade-off in exchange for eliminating a full duplicate CI/security run.
Production `v*` tag releases continue to execute the full release gate chain.

### `.github/workflows/typesafe-smoke.yml`

The TypeSafe workflow performs a live compatibility check against the Jev System One API used by
the optional listing-search reranker.

Normal `build-quality` verification remains deterministic and uses tests/stubs rather than a live
API key. The live smoke complements those tests by verifying that BlocBuy's expected external Jev
wire contract is still accepted by the real service.

The smoke uses the same `choice` shape as the production listing-search reranker:

- state contains `shopper_query`;
- question `best_match` uses `type: choice`;
- criteria contain representative listing candidates;
- the response must contain a `choice` answer and probabilities for every expected candidate;
- every probability must be a number in `[0,1]`;
- model identity and usage metadata must be present.

The smoke does not assert which candidate should win. It is a contract/availability test, not a
model-quality benchmark.

Triggers:

- applicable backend pull requests where the Actions secret is available;
- `merge_group` when GitHub Merge Queue is available;
- production `v*` tag pushes;
- manual `workflow_dispatch`.

Fork and Dependabot PRs do not receive the TypeSafe API secret and therefore skip the live call.
Normal `push/dev` does not run the smoke because post-merge `dev` runs are delivery-only.

`typesafe-smoke` is deliberately not a required repository status check. The production
listing-search integration treats Jev as a best-effort enhancement: if the live call fails, the
application preserves the deterministic PostgreSQL shortlist order and total.

### `.github/workflows/pr-labeler.yml`

`actions/labeler` on PR open/sync — wires up `.github/labeler.yml`.

### `.github/workflows/board-snapshot.yml` (added 2026-09-18)

Daily at 15:30 UTC (23:30 Singapore) and on `workflow_dispatch`: runs
`.claude/skills/agile-evidence-capture/scripts/snapshot-board.sh`, uploads the day's CSV as a
90-day artifact, rebuilds the bot branch `metrics/board-snapshots` from `dev` plus any snapshot
days not merged yet, force-updates that branch (its own, lease-protected against a concurrent
run), and keeps one open PR to `dev` titled `docs(agile): board snapshots through <date>`. Asked
for in the PR #113 review: the first CSV (`docs/agile/metrics/2026-09-17.csv`) was a hand run.

Alternatives rejected: (a) commit straight to `dev`: the `dev-require-review` ruleset has no
bypass actors and requires a squash-merged PR with four checks; (b) one branch and PR per day:
a review per CSV and stale branches, since `delete_branch_on_merge` is off; (c) artifact only:
the burndown source has to be in the git archive (`docs/agile/metrics/README.md`); (d) keep
appending to the bot branch: after a squash merge the next PR's three-dot diff re-shows the
already-merged days, so the branch is rebuilt from `dev` instead.

Degraded mode without the `BOARD_SNAPSHOT_TOKEN` secret (off-repo TODO 8): `github.token` cannot
read the user-owned Projects v2 board, so the board columns are blank and the script warns; the
commits it pushes start no `pull_request` workflows (GitHub's rule for GITHUB_TOKEN-driven
events), so the required checks appear only after a human closes and reopens the PR; and
`gh pr create` needs "Allow GitHub Actions to create and approve pull requests", failing which
the run prints the compare URL in its summary.

### `.github/dependabot.yml`

Weekly, grouped PRs: Maven (`backend/`), GitHub Actions (`/`), Docker (`backend/`), plus configured
frontend dependency updates where supported.

### Branch protection (manual, repo settings)

`dev`: require PRs; require `build-quality`, `sonar`, `docker`, and `gitleaks`; require branches to
be up to date before merging; require human review (including CODEOWNERS where ownership is
intentional), dismiss stale approvals, require approval of the latest reviewable push, require
conversation resolution, restrict deletion, and block force-push.

`Restrict updates` remains off: the PR requirement, rather than bypass-only updates, defines the
normal path into `dev`.

`merge_group` is retained in workflow configuration for forward compatibility but Merge Queue is
not currently required by the repository ruleset.

These settings are configured off-repo in the repository ruleset.

## Step 3 — SonarQube (CI-based analysis)

SonarCloud analysis runs as the `sonar` CI job using the pinned Sonar Maven scanner. The
`build-quality` job uploads compiled classes, test results, and JaCoCo reports as artifacts; the
`sonar` job restores them and invokes `org.sonarsource.scanner.maven:sonar-maven-plugin` without
repeating the full Maven verification build. This preserves the coverage/quality gate while
avoiding duplicate compilation and tests.

Quality gate configured server-side: coverage ≥ 80% on new code, no new blocker/critical issues,
A ratings on new code. The `sonar`/SonarCloud status used by the repository ruleset must report
reliably before it is required.

## Step 4 — Security Tooling

- **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds `blocbuy-bootstrap` (`-am`, tests skipped — they ran in `build-quality`); runtime `eclipse-temurin:21-jre`, non-root user, port 8589, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`. No in-image HEALTHCHECK (JRE image has no curl); orchestrator probes hit `/actuator/health` (already exposed). Profile `docker` selected by compose via `SPRING_PROFILES_ACTIVE`, not baked in. Used by local `docker compose` builds.
- **`backend/Dockerfile.ci`** — runtime-only twin used by the `docker` CI job: successful `build-quality` uploads the boot jar packaged by `mvn verify`; `docker` downloads that exact artifact and assembles the image without repeating an in-container Maven build. The current `docker` job requires `build-quality` success, so it does not use a multi-stage fallback when that artifact is absent.
- **`.github/workflows/security.yml`** — pull requests and merge groups affecting backend, deployment, or workflow files; production `v*` tag pushes; plus weekly scheduled and manual scans:
  - **Gitleaks** full-history secret scan. `.gitleaks.toml` extends defaults and allowlists reviewed placeholders.
  - **Trivy** separates Maven vulnerability and deployment-configuration gates at HIGH/CRITICAL.
  - **Actionlint/ShellCheck** validates workflows, embedded shell and EC2 lifecycle scripts.
  - **Deployment policy** enforces one backend service and an exact non-secret SSM archive allowlist.
  - **CodeQL** provides optional Java SAST when repository Code Scanning is enabled.
- **Dependabot** (Step 2) closes the loop on remediation.

Trivy SARIF reports are preserved as workflow artifacts even when a Trivy gate fails. Security-tab
ingestion is enabled only when the repository's Code Scanning capability is available; the Trivy
exit code remains the CI gate independently of SARIF ingestion.

## Step 5 — Pipeline Hardening (implemented 2026-09-02)

Fixes found during pipeline reviews; all landed in-repo:

1. **Trivy actually gates now.** `aquasecurity/trivy-action` defaults to `exit-code: 0` — both fs and image scans previously only *reported* HIGH/CRITICAL without failing the job, contradicting the "0 unfixed HIGH/CRITICAL" gate. Both scans now set `exit-code: '1'` + `ignore-unfixed: true` (the latter aligns with the "unfixed" wording so CVEs with no released patch don't break the build).
2. **`docker` job runs on PRs.** Image build, smoke test, and scan now gate *before* merge.
3. **Boot smoke test in `docker` job.** After `docker build`, the image boots against real `postgres:18` + `redis:8.10` containers and CI polls the application health endpoint until `status=UP`. Catches startup-time failures (bad Flyway migration, missing env, broken Spring context) that a successful `docker build` hides. Container logs are dumped on failure.
4. **API breaking-change check in `contract-check`** (PR only). `openapi-diff-cli` 2.1.7 (`--fail-on-incompatible`) compares the base branch's committed `contracts/openapi.json` against the PR's regenerated spec; drift check still catches "forgot to commit", this catches "broke consumers". Escape hatch (added 2026-09-09): a PR labeled `contract-change-approved` downgrades the check to report-only — used for deliberate spec tightening. The label is the recorded approval; apply it only after reviewing the diff output.
5. **CodeQL SAST job in `security.yml`.** Opt-in via repo variable `ENABLE_CODEQL=true`; repository-side GitHub Code Scanning must also be available/enabled for result ingestion. `build-mode: none` avoids a separate Maven build.
6. **`setup-java cache: maven`** replaces hand-rolled Maven cache blocks.
7. **Fixed a silent no-op in the drift check.** `git diff --exit-code -- contracts/` previously ran under `working-directory: backend`, where the pathspec matched nothing. The step now runs from `${{ github.workspace }}`.
8. **Pre-merge validation + delivery-only `push/dev`.** Required up-to-date PR checks are the authoritative pre-merge boundary; merged `dev` pushes no longer repeat the expensive validation chain.
9. **Live TypeSafe Jev contract smoke.** The real System One endpoint is exercised separately from deterministic Maven verification using the same `choice` response contract consumed by the listing-search reranker. It remains non-required because Jev is an optional runtime enhancement with deterministic fallback.

## Step 6 — Existing EC2 application delivery (implemented 2026-09-04)

The selected first deployment phase deliberately reuses one existing EC2 host and its existing
PostgreSQL and Redis services. Terraform and Bitovi are not executed.

1. `application-ec2.yml` uses host-loopback PostgreSQL/Redis settings; the backend-only Compose model uses Linux host networking and declares no database, cache or volume.
2. The runtime bundle provides preflight, health, locked deploy, automatic rollback and manual rollback scripts. Runtime secrets remain `/etc/blocbuy/backend.env` with `0600` permissions.
3. After an up-to-date PR has passed the required checks and merged, the lightweight `dev` delivery push packages/assembles the image without repeating quality/security or live TypeSafe gates. A branch-scoped OIDC role pushes it to private ECR; Docker's pushed digest is cross-checked with ECR and only the digest is deployed.
4. A separate environment-scoped OIDC role can send `AWS-RunShellScript` only to the configured EC2 instance. SSM transfers only the allowlisted non-secret bundle.
5. `rollback-backend.yml` requires `dev`, a reason, explicit confirmation and the protected `backend-dev` environment. It uses the recorded previous digest unless an operator supplies a reviewed digest from the configured repository.
6. Deployment and rollback share a non-cancellable concurrency group. Neither operation reverses Flyway migrations or manages PostgreSQL/Redis.

Canonical setup and incident procedures:
[`docs/deploy/backend-ec2-pipeline-operator-guide.md`](../deploy/backend-ec2-pipeline-operator-guide.md).

## Off-Repo TODO (cannot be done from the codebase)

These need GitHub repo settings / external accounts; do them once, then the matching CI jobs activate automatically:

| # | Action | Where | Unblocks |
|---|---|---|---|
| 1 | Set repo **secret** `SONAR_TOKEN` (SonarCloud scanner token) | Repo Settings → Secrets and variables → Actions | `sonar` CI job and server-side quality gate |
| 2 | Set repo **variable** `ENABLE_CODEQL=true` and enable/confirm repository-side GitHub Code Scanning | Repo Settings → Secrets and variables / Code security | `codeql` SAST result ingestion |
| 3 | Configure the `dev` **ruleset**: require PR, `build-quality`, `sonar`, `docker`, `gitleaks`, branches up to date, review requirements, deletion restriction and no force-push | Repo Settings → Rules | Enforces the authoritative pre-merge validation path |
| 4 | **Gitleaks license**: `gitleaks-action` needs a free `GITLEAKS_LICENSE` secret for organization-owned repos | gitleaks.io → add as repo secret | `gitleaks` job on org repos |
| 5 | Confirm Code Scanning result ingestion works before making CodeQL/code-scanning results a repository ruleset requirement | Repo Settings → Code security | Prevents a ruleset from waiting on results the repository cannot accept |
| 6 | Configure ECR, two OIDC roles, EC2 SSM/ECR role, `backend-dev` protections and deployment variables from the operator guide | AWS + Repo Settings | Immutable deploy and controlled rollback |
| 7 | Keep repo **secret** `TYPESAFE_API_KEY` configured | Repo Settings → Secrets and variables → Actions | Live `typesafe-smoke` contract verification |
| 8 | Set repo **secret** `BOARD_SNAPSHOT_TOKEN` (added 2026-09-18): a classic PAT with `repo` + `read:project` from someone who can see the Kanban (#103), or a fine-grained PAT with Contents and Pull requests read-write, Issues read, and account Projects read. Optional fallback: enable "Allow GitHub Actions to create and approve pull requests" | Repo Settings → Secrets and variables → Actions; Settings → Actions → General | `board-snapshot` fills the board columns, opens its PR, and gets the required checks without a manual close/reopen |

## Quality Gates Summary

| Gate | Threshold | Where enforced |
|---|---|---|
| Formatting | 100% Spotless-clean | `validate` / CI step 1 |
| Style | 0 Checkstyle errors | `verify` |
| Bugs | 0 SpotBugs findings ≥ Medium | `verify` |
| Architecture | ArchUnit suite green | `test` |
| Coverage | ≥ 80% on new code | `sonar` CI job (SonarCloud quality gate) |
| Vulnerabilities | 0 unfixed HIGH/CRITICAL | Trivy in CI |
| Secrets | 0 leaks in history | Gitleaks in CI |
| Contract drift | `contracts/openapi.json` == generated | `contract-check` job |
| Contract compatibility | no backward-incompatible change vs base, or `contract-change-approved` label on PR | `contract-check` job (PR only, openapi-diff) |
| Boot smoke | Actuator health = `UP` against PG 18 + Redis 8 | `docker` job |
| SAST | 0 CodeQL alerts | `security.yml` `codeql` job (opt-in) |
| Deployment bundle | exactly one backend service and allowlisted archive entries | `security.yml` and `ci-backend.yml` |
| Jev live compatibility | expected System One `choice` wire contract | `typesafe-smoke.yml` (non-required) |
| Release identity | production tested ECR image pinned by SHA-256 digest | `ci-backend.yml` |
| Deployment health | Actuator reports `UP`; otherwise application rollback | EC2 lifecycle scripts |

## Local Developer Loop

```bash
cd backend
./mvnw spotless:apply          # fix formatting before commit
./mvnw verify                  # deterministic backend verification
```

Normal local verification does not require `TYPESAFE_API_KEY`; Jev-dependent application tests use
controlled test doubles/stubs. The live external contract check is owned by `typesafe-smoke.yml`.

Pre-push hook optional; CI is the backstop. Full runbook: `docs/ci/backend-pipeline.md`. Thresholds:
`docs/quality/quality-gates.md`.

## Risks / Notes

- **One-time formatting churn**: `spotless:apply` touches nearly every file. Land it alone, record in `.git-blame-ignore-revs`.
- **JDK 26 on dev machines**: Enforcer rejects `[21,22)`-outside JDKs; set `JAVA_HOME` to a JDK 21 (`/usr/libexec/java_home -v 21` on macOS).
- **Code-quality services are external dependencies**: Sonar requires its repository variables/token, and CodeQL result ingestion depends on repository-side Code Scanning being enabled.
- **TypeSafe Jev is an external optional dependency**: the live smoke verifies compatibility, but a TypeSafe outage must not prevent deterministic listing search or make the live smoke a required merge gate.
- **Infrastructure IaC is deferred**: application delivery is versioned, but EC2/VPC/IAM drift remains an operational risk until a later import/migration phase is approved.
