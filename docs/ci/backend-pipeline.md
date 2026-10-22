# CI/CD Pipeline Runbook

This runbook covers PR validation, `dev` analysis and delivery, releases, and rollback. See the [EC2 operator guide](../deploy/backend-ec2-pipeline-operator-guide.md) for AWS setup and the [pipeline plan](devsecops-pipeline-plan.md) and [quality gates](../quality/quality-gates.md) for rationale and thresholds.

## Pipeline shape

PR checks against current `dev` are authoritative before merge. Code-relevant `dev` pushes refresh Sonar baselines on the merged SHA; every `dev` push runs Gitleaks, while packaging and deployment run separately when enabled. `merge_group` validation is configured for future Merge Queue use.

2026-10-14 scope correction: Gitleaks runs on every applicable event but selects revisions through its action, not merely through checkout depth. The observed dev scan checked one commit; the [full-history target and unresolved historical findings](../quality/quality-gates.md#secret-scan-scope-checkpoint--2026-10-14) are tracked separately. Green PR/dev checks do not establish a clean full history or deployed acceptance.

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
├── gitleaks          event-selected secret scan (full history available, not necessarily scanned)
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

## Backend verification concurrency (2026-10-20)

Decision: PR/tag `build-quality` and dev Sonar coverage use the Maven `ci` profile to suppress application SQL DEBUG output and run bootstrap integration-test classes in two reusable, isolated JVMs. Reverse-alphabetical class order starts `V4ExpiryPipelineIT` early, overlapping its real 60-second expiry deadline and 2,000-event pipeline with the remaining ITs instead of leaving it as a serial tail. Tests within a class remain sequential; each class retains its existing isolated PostgreSQL/Redis/ElasticMQ fixtures and Spring context lifecycle. We do not share mutable fixture databases, parallelize tests inside one JVM (Sa-Token and AWS credentials use JVM-global state), shorten the rehearsal, omit modules, or add a new runner/check/artifact handoff.

Ordinary local verification retains one IT JVM and unchanged logging. Use `./mvnw -B -T 2 verify -Pci` to reproduce CI, `-Dtest.log.level=DEBUG` to restore SQL diagnostics, and `-Dbootstrap.it.forks=1` to force serial bootstrap ITs. Setting `STRIPE_TEST_DB_URL` automatically keeps bootstrap ITs serial even with `-Pci`; never override that to multiple forks with the shared private database. JaCoCo 0.8.15 appends coverage under an inter-process file lock, so both forks retain the existing `target/jacoco.exec` and aggregate/report paths; Sonar thresholds, test selection, security gates and required-check contexts are unchanged. The [October 20 sprint log](../agile/sprint-04/log/2026-10-20.md) records before/after verification; GitHub-hosted runtime and memory impact still require a run of this branch.

`OutboxRelayEnabledContextTest` is a bean-wiring test, not a database/transport integration test: its scheduler lease port is mocked and its context closes after the class. Previously the cached real relay timer kept trying its placeholder database, producing a 20-second scheduler shutdown timeout and a further 30-second Surefire forced-exit wait. Real scheduler, lease and delivery behavior remain covered by the existing timer tests, PostgreSQL/ElasticMQ ITs and unchanged V4 pipeline rehearsal.

## Actions storage controls (2026-10-08)

Decision: `.github/actions/trivy-image-db` manages an image-only trusted default-branch database baseline, avoiding both per-run duplication and the Trivy action's shared config/dependency key. It caches only `.cache/trivy-image/db` and `.cache/trivy-image/java-db` under `trivy-image-db-shared-v1-v0.74.0-<OS>-<architecture>-<UTC date>`. A new namespace prevents reuse of legacy PR-written DB entries. GitHub's default-branch cache visibility allows other PRs to restore the baseline without copying it into their own refs. Binary caching is retained separately; the image scan's built-in cache/setup are disabled. Database freshness checks and vulnerability gates remain enabled.

An existing `sonar-backend` runner seeds the baseline after successful coverage on code/build-relevant trusted `dev` pushes; there is no additional scheduled warmup job. Both the caller and composite guard restrict publication to default-branch pushes. An exact daily lookup skips binary setup and payload transfer on later producer calls. On a miss, restore the latest compatible baseline, download/check both DBs, validate payload/metadata structure and save immediately rather than losing valid DB downloads to later scan failures. Seeding is advisory and does not block Sonar if a registry/cache operation fails. PR/merge-group/tag image jobs are restore-only for these DBs, although separate binary and other dependency caches retain their original behavior. Before initial seeding, after eviction or when metadata expires, normal Trivy downloads still run; an immutable daily baseline is not refreshed again on the same day. Legacy caches expire through eviction or closed-PR cleanup, not a blanket purge.

`cleanup-pr-caches.yml` deletes only exact `refs/pull/<number>/merge` caches whose PR is closed at the state check. Closure events clean promptly; the daily 04:43 UTC sweep reconciles late writes and missed events. Manual sweeps default to dry run and, like scheduled sweeps, must run on the default branch. Branch, tag and open-PR caches are preserved; workflow runs, artifacts and evidence are never deleted. All inventory pages are read before deletion. API failures fail the job except deletion 404s for already-removed caches. State checks do not atomically lock reopening, so a concurrent reopen can lose a disposable PR cache. Separate PR concurrency groups avoid dropping another PR's pending cleanup.

The cleanup module is loaded from an explicit `github.sha` checkout with `persist-credentials: false`. For `pull_request_target`, this is trusted base-branch source, never PR head/merge code. Permissions are `contents: read`, `actions: write` and `pull-requests: read`; event/default-branch guards also run inside the module.

Once merged into the default branch, closure and scheduled cleanup are automatic. To preview or run an additional sweep:

```bash
gh workflow run cleanup-pr-caches.yml --ref dev -f dry_run=true
# After reviewing the completed dry-run summary:
gh workflow run cleanup-pr-caches.yml --ref dev -f dry_run=false
```

SWE5001 PR validation builds and scans images but skips export/upload: the release gate passes `upload-images=false`, and the reusable workflow also rejects PR/merge-group export regardless of that input. Only deployment callers need the same-run `swe5001-image-<service>` handoff; their default upload behavior and two-day publication/retry window remain unchanged. No count-based pruning or AWS/ECR change is included. Deployment evidence, SARIF and test-report retention are unchanged. After a deployment image expires, rerun the full promotion rather than only downstream publication.

One-time removal of existing unused PR images must review exact artifact IDs and confirm the owning run's path is `.github/workflows/swe5001-release-gate.yml`, event is `pull_request` and status is `completed`; do not infer safety from a `dev` branch label alone. Never delete deployment-run images, reports, evidence or running-job artifacts. The [October 8 log](../agile/sprint-03/log/2026-10-08.md) records the authorized four-artifact cleanup and verified remaining inventory. Deletion stops future storage accrual, not already-recorded charges; workflow prevention takes effect only after merge.

Verify locally with `node --test .github/tests/cleanup-pr-caches.test.cjs`, `python3 -m unittest discover -s .github/tests -p '*_test.py'` and the pinned CI actionlint Docker image. Tests cover cleanup behavior, trusted publication/restore-only policy and structural DB validation without live writes. [The local Docker benchmark](trivy-cache-benchmark-20261008.md) measured 99–104 s cold scans versus approximately 5 s with warm DBs, but local archive extraction adds approximately 9.5 s and GitHub transfer/producer costs remain unmeasured. [The sprint log](../agile/sprint-03/log/2026-10-08.md) records inventory and verification. After merge, compare cache summaries and total producer/consumer durations before claiming measured savings. Cleanup adds runner overhead; storage reductions are not automatically minute or billing savings.

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

### Observed CI failures and scoped repairs (2026-10-21)

The [run-by-run audit](ci-failure-audit-20261021.md) distinguishes code defects, resolved dependency-update failures and external blockers. CodeQL is not usable in this repository: its September 15 ingestion failed, `ENABLE_CODEQL=false`, and the Code Scanning API still returns 403. The October 20 environment job never started because an Actions budget prevented use. Neither blocker is fixed by adding a required check; no ruleset, billing setting, deployment or credential was changed.

The SpotBugs 4.10.5 engine requires BCEL 6.13.0, while plugin 4.10.4.1 supplies BCEL 6.12.0; [PR #559's failed run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39463373272) and a local reproduction both fail with `NoClassDefFoundError: org/apache/bcel/classfile/PermittedSubclasses`. Use plugin 4.10.5.0 and engine/annotations 4.10.5 together, matching their published Maven Central POMs, rather than adding another transitive override or disabling a detector. Dependabot groups the SpotBugs artifacts. Cheap POM policy tests run in the existing `build-quality` job; POM changes do not allocate another workflow-lint runner. Actionlint/tooling tests cover every local composite action, Dependabot configuration and secret-scan configuration changes.

The weekly Gitleaks report contains twenty provable SHA-256 file-digest false positives and four historical Sonar badge-token findings. `.gitleaksignore` records only the twenty exact commit/path/rule/line fingerprints, after recomputing their values from the baseline source commit. New findings in the same file remain detectable. The four badge-token findings are deliberately not ignored or declared safe without disposition; the weekly scan remains red. See the audit for commands and limits.

Scope boundary: preserve existing required contexts, concurrency, scan parallelism, test selection, coverage paths and CodeQL opt-in behavior. This repair adds neither aggregate gates nor whole-workflow serialization. It is not a claim of measured hosted speedup or a fix for the historical Actions-budget rejection; current budget availability was not checked.

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

## Delivery metrics (DORA)

Deployment frequency, lead time, change success rate and time to recover of dev are measured from Actions history by `.github/scripts/dora-metrics.py`; the [22 October snapshot](dora-metrics-20261022.md) holds the definitions, results, proposed targets and limits.
