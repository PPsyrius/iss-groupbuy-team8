# Quality Gates

The numbers the pipeline enforces. Change to any row = team decision, recorded here with a date.

| Gate | Tool | Threshold | Enforced at |
|---|---|---|---|
| Formatting | Spotless + google-java-format (AOSP) | 100% formatted | `validate` phase, CI `build-quality` step 1 |
| Code style | Checkstyle 14.3.0 (Google checks) | 0 errors; line length 120 (D13) | `verify` phase |
| Static bugs | SpotBugs 4.10.5 (Maven plugin 4.10.5.0) | 0 findings, threshold Medium, effort Max | `verify` phase |
| Architecture | ArchUnit 1.5.1 | all current rules green | unit tests (`ArchitectureTest`) |
| Build gates | Maven Enforcer | Java [21,22), Maven ≥3.9, dependency convergence | `validate` phase |
| Coverage | JaCoCo 0.8.15 → SonarCloud Backend | ≥ 80% on new code (server quality gate) | `ci-backend.yml` `sonar-backend` |
| Code quality | SonarCloud Backend | quality gate green: no new blocker/critical, A ratings on new code | `ci-backend.yml` `sonar-backend` |
| Frontend coverage | Vitest V8 LCOV → SonarCloud Frontend | ≥ 80% on new code (server quality gate) | `ci-frontend.yml` `sonar-frontend` |
| Frontend code quality | SonarCloud Frontend TypeScript analysis | quality gate green | `ci-frontend.yml` `sonar-frontend` |
| Dependency CVEs | Trivy fs | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `security.yml` + weekly cron |
| Image CVEs | Trivy image | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `ci-backend.yml` `docker` job |
| Secrets | Gitleaks | Target: 0 leaks in full git history (unchanged) | `security.yml` event-selected scans; full-history gap below |
| SAST | CodeQL (java-kotlin, build-mode none) | 0 alerts; opt-in via `ENABLE_CODEQL` repo variable | `security.yml` `codeql` job; currently disabled/unavailable, see [October 21 audit](../ci/ci-failure-audit-20261021.md) |
| API contract | springdoc export | committed `contracts/openapi.json` identical to generated | `contract-check` job |
| API compatibility | openapi-diff 2.1.7 (`--fail-on-incompatible`) | no backward-incompatible change vs base branch | `contract-check` job (PR only) |
| Boot smoke | container boot vs PG 18 + Redis 8 | `/actuator/health` = UP within 60s | `ci-backend.yml` `docker` job |

2026-10-21: the [observed-failure repair](../ci/backend-pipeline.md#observed-ci-failures-and-scoped-repairs-2026-10-21) aligns the SpotBugs plugin/engine runtime; the table's existing Checkstyle and ArchUnit versions are synchronized with the parent POM. No required check, concurrency policy, test selection or coverage threshold changed. CodeQL remains disabled: its previous ingestion failure and current Code Scanning API 403 are recorded in the [audit](../ci/ci-failure-audit-20261021.md); it is not an available enforced gate.

## Secret-scan scope checkpoint — 2026-10-14

The full-history target above is unchanged, but `fetch-depth: 0` makes history available without selecting it for scanning. The pinned Gitleaks action chooses revisions by event; [observed dev run 38503702085](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38503702085) used version 8.24.3 and `--log-opts=-1` (one commit). A green event-selected scan is not proof of a clean full history.

The [October 14 local audit](../agile/sprint-04/log/2026-10-14.md#2026-10-14---ppsyrius---http-scaling-and-demo-dashboard-457) scanned 375 commits with 8.24.3 and 8.28.0 and found 24 pre-existing matches: 20 confirmed SHA-256 entries and four old Sonar badge-token matches still needing disposition. Working changes passed separately. No new exclusion, history rewrite or acceptance decision was made; NFR-02 remains only partially verified against its full-history target.

2026-10-21 disposition: the twenty file digests were recomputed against baseline commit `211cfb7d` and receive exact historical fingerprints in `.gitleaksignore`, not a path/rule-wide exclusion. Matching CI's full-history/all-ref scan over 458 commits, 8.24.3 reports 24 findings without the fingerprint file and four with it; 8.28.0 also retains the four badge-token findings. Both still exit 2 as configured by CI. No token was reused, rotated or accepted as safe; no history was rewritten. The [audit](../ci/ci-failure-audit-20261021.md) records evidence and the remaining blocker.

## Dynamic application security testing (DAST)

`dast-baseline.yml` runs one passive OWASP ZAP 2.17.0 session (image pinned by digest) against the dev site at `vars.FRONTEND_URL_DEV`. [The helper](../../.github/scripts/dast-baseline.py) validates the target before pulling ZAP, generates a native automation plan for ten required anonymous GET checks, and verifies recorded responses plus passive processing after the scan. It runs weekly (Monday 03:41 UTC), on dispatch and on PRs that edit the workflow, helper or DAST tests. Findings are **advisory**, not a required merge gate; broken targets, scanner failures and incomplete coverage fail the job. Preflight has a 30-second wall-clock deadline (TERM, then KILL after 5 seconds) and a one-minute step backstop; the form-disabled static crawl and passive-queue wait are each limited to 3 minutes, the container to 10 minutes, and the job to 20 minutes.

2026-10-22 deduplication: the [API job in run 39733851080](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39733851080/job/119674860951) already included both `/api/v1/products` and `/` in its generated `zap.yaml`. Both matrix jobs crawled the root and reported the same six Medium/Low alert types. Initial deduplication retained one API-seeded invocation rather than a second image pull and overlapping crawl; the coverage hardening below replaces that wrapper with an explicit plan. The job-count reduction itself is not a newly measured billing saving or broader DAST coverage.

2026-10-22 coverage hardening: replace discovery-only baseline wrapping with an explicit ZAP automation plan, retaining one scanner process and passive analysis. Preflight uses two redirect-free GETs within its wall-clock deadline: `/` must return the SPA mount/module HTML and `/api/v1/products` must return the successful `Result` catalog envelope. A nonempty catalog must provide a product with a valid UUID and title. A healthy empty catalog still starts ZAP with eight probes; both existing-product checks remain `UNAVAILABLE` and overall coverage remains incomplete (8/10 at best), without inventing a fixture or claiming those checks passed. No data is seeded or changed by the scanner. Keyword probes use `ai=false` to avoid paid semantic re-ranking.

| Required GET | Expected response |
|---|---|
| `/` | 200, SPA HTML |
| `/api/v1/products` | 200, `COMMON_200_000` catalog page |
| `/api/v1/products?page=2&size=1` | 200, page 2 with size 1 (items may be empty) |
| `/api/v1/products?category=...` | 200, catalog page; product category or `dast-unlisted-category` when absent/empty |
| `/api/v1/products?keyword=...&ai=false` | 200, catalog page; product-title keyword or `BlocBuy` when empty |
| `/api/v1/products/{existingId}` | 200, detail for that product; unavailable if the catalog is empty |
| `/products/{existingId}` | 200, SPA deep-link HTML; unavailable if the catalog is empty |
| `/api/v1/products?page=0` | 400, `COMMON_400_002` without successful data |
| `/api/v1/orders/me` | 401, `COMMON_401_001` without successful data |
| `/api/v1/admin/products` | 401, `COMMON_401_001` without successful data |

The static spider has a separate allowlist for the SPA, product pages and assets; it cannot discover arbitrary API/OAuth/payment paths, and form processing/POST submission are disabled. The requestor alone sends the selected API probes. `coverage.json` lists checks plus actual visited URLs, methods, statuses and content types from ZAP's HAR export; static assets and unfetched site-tree nodes never substitute for a required request. A missing request, wrong status/content type/envelope, wrong detail/pagination, non-GET traffic or scanner/report failure makes the run `INCOMPLETE`. Findings remain advisory even when coverage is complete: `PASS` means the required HTTP response was observed, not that an application security property was exhaustively proved.

2026-10-22 self-review fixes: bound preflight independently of urllib's socket-inactivity timeout; preserve useful partial scanning for an empty catalog; and require runtime evidence rather than treating report presence or zero alerts as proof of passive analysis. The plan explicitly enables the minimum cookie/header/CSP/disclosure policy (rule IDs 10010, 10011, 10015, 10020, 10021, 10035, 10036, 10038, 10054, 10055, 10063 and 90004). Standalone scripts in the same ZAP process snapshot installed/enabled rules before requests and after the queue wait. Completion requires all minimum rules enabled, unchanged enabled-rule thresholds, fresh per-rule `stats.pscan.<id>.time` counters for every initially enabled rule, a drained queue, no recorded passive-scanner errors and no body-size skips. Set `maxAlertsPerRule: 0` because a positive cap automatically disables rules after enough findings; runtime remains bounded by the existing scan deadlines. A present zero-millisecond counter is valid; a missing counter is not. This proves rule invocation and process completion, not that every rule applies to every response or finds all vulnerabilities. A scan with no alerts can still pass; disabled rules cannot masquerade as a clean scan.

The `zap-baseline` artifact retains HTML/JSON/Markdown findings, `coverage.json`/`coverage.md`, `passive-start.json`/`passive-finish.json`, preflight metadata, the request manifest and the generated plan for 30 days. Raw HAR response bodies/headers/cookies are not uploaded and are removed after verification. The summary distinguishes incomplete coverage and unverified passive processing from advisory alert counts. SPA HTML checks do not execute JavaScript, search/filter checks validate responses rather than full query semantics, anonymous denials do not verify authenticated ownership/RBAC, and no active, authenticated or payment-provider testing is claimed. Runtime `pscanrulesBeta` installation remains unpinned; the image digest alone does not pin every add-on.

2026-10-22 local verification after self-review fixes: the Python suite passed 60 fast tests, with four opt-in native ZAP tests skipped; the workflow runs its fast DAST subset before contacting the live target. A real slow-drip preflight was terminated using a scaled two-second GNU timeout and produced no scan plan. Running the four [native tests](../../.github/tests/dast_runtime_test.py) explicitly against the checksum-verified ZAP 2.17.0 bundle passed in 69 s on Windows/Java 25: complete 10/10 coverage with passive execution verified; useful empty-catalog scanning at 8/10 with both details unavailable; rejection of an HTTP-200 HTML API fallback; and rejection of disabled passive rules even with ZAP exit 0 and ten valid responses. No OAuth link/form submission occurred. These are harness checks, not measurements of the deployed application, the Linux container or hosted billing. Native tests are opt-in so ordinary policy checks do not install/start ZAP:

```powershell
$env:DAST_ZAP_HOME = '<path to the ZAP_2.17.0 cross-platform bundle>'
python -B -m unittest discover -s .github/tests -p 'dast_runtime_test.py' -v
```

2026-10-22 Docker revalidation: after the Linux daemon became available, the exact workflow image `ghcr.io/zaproxy/zaproxy@sha256:5ccfff9a411743ab2615136a2edd7416bec9ce41572fa94e935df38c14adb2b6` passed 39 fast DAST tests (four runtime tests initially skipped) and, separately, all four committed runtime tests in 60 s. Environment: Docker Desktop 29.8.2, Linux x86_64, ZAP's Java 17/Python 3.11.2 and unprivileged `zap` UID 1000. Six additional loopback-only smoke cases used the workflow's preflight/verify CLIs, `/zap/wrk` paths and `zap.sh -cmd -addoninstall pscanrulesBeta -autorun /zap/wrk/zap-plan.yaml` command; no production code changes were needed.

| Linux smoke case | Observed result |
|---|---|
| Healthy fixture | ZAP/verification exit 0; 10/10 responses; 67/67 enabled passive rules ran; queue drained |
| Empty catalog | ZAP exit 0, verification exit 1; 8/10 responses; both details unavailable; passive processing verified |
| API HTML fallback | ZAP exit 0, verification exit 1; 9/10 valid responses |
| All passive rules disabled | ZAP exit 0, verification exit 1 despite 10/10 responses and zero alerts |
| Invalid script engine | ZAP/verification exit 1; incomplete scan, not a false clean result |
| Slowly streaming preflight | Actual 30-second deadline: exit 124 after 30.02 s; no scan plan; verification exit 1 |

Additional Linux operational checks passed: a root-owned mode-777 work directory accepted writes from `zap` UID 1000; a TERM-resistant worker hit a scaled three-second container deadline plus one-second kill grace, and the workflow-equivalent EXIT trap removed the surviving container. These test-only shortened bounds exercise termination/cleanup, not a measured ten-minute timeout. Reports and summaries were retained in the approved local temporary directory, raw HAR was removed, and the checked-in sources were mounted read-only. Fixture alerts are not deployed findings; the subsequent hosted verification below records the selected dev responses and artifact upload. Hosted billing savings remain unverified.

Reproduce the committed runtime suite inside the pinned image from the repository root (PowerShell):

```powershell
$github = Join-Path (Get-Location).Path '.github'
$image = 'ghcr.io/zaproxy/zaproxy@sha256:5ccfff9a411743ab2615136a2edd7416bec9ce41572fa94e935df38c14adb2b6'
docker run --rm --init --mount "type=bind,source=$github,target=/workspace/.github,readonly" --workdir /workspace --env DAST_ZAP_HOME=/zap $image python3 -B -m unittest discover -s .github/tests -p 'dast_runtime_test.py' -v
```

2026-10-22 hosted verification after hardening: [PR run 39768665359](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39768665359) passed against `https://d1b3np88z657cp.cloudfront.net` for PR head `c5a87998d1250b4ff088f2b80b3f3ca5c48938ae` (tested merge revision `64b76cdf51b3bf5e8646620b6e0d2a3fd630aaff`). All 10 required response checks passed; all 67 enabled passive rules had fresh execution counters and unchanged enabled thresholds. Queue, scanner-error and body-skip counters were zero. The hosted fast suite passed 39 tests with four opt-in fixture tests skipped; the live ZAP scan ran separately. One job took 84 seconds, including about 33 seconds for the image pull; these timings are not a controlled billing benchmark.

The downloaded [zap-baseline artifact](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39768665359/artifacts/12421027383) contained all ten expected files, including HTML/JSON/Markdown reports, coverage and passive-rule snapshots, without a raw HAR. Its SHA-256 independently matched GitHub's recorded digest, `9868cedbcd5d51398ff08727df0546e99efb57c7f8bd73897bb8027090f93047`. The inventory recorded 90 same-origin GET responses (87 distinct URLs); seven unfetched placeholders did not count as HTTP coverage. The optional `/robots.txt` and `/sitemap.xml` requests returned 403; the required invalid-input and anonymous-denial probes returned their expected 400/401 responses.

Findings were 0 High, 2 Medium, 4 Low and 6 Informational; the Medium/Low alert references match the existing CSP/header findings tracked in #610. This run verifies the PR trigger, artifact upload and selected anonymous dev responses, not a merge/default-branch rollout. Schedule/dispatch execution, browser behavior, full search/filter semantics, active/authenticated/payment testing and hosted billing savings remain unverified.

2026-10-22 first run ([PR #607 run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39733450177)), identical for both surfaces because CloudFront applies one response-headers policy (`infra/terraform/frontend-stack/cloudfront.tf`, `aws_cloudfront_response_headers_policy.security`):

| Risk | Alert (ZAP rule) | Cause |
|---|---|---|
| Medium | CSP: Wildcard Directive (10055) | `img-src 'self' https:` allows images from any HTTPS host |
| Medium | CSP: `style-src 'unsafe-inline'` (10055) | inline styles allowed |
| Low | Cross-Origin-Embedder/Opener/Resource-Policy missing (90004) | headers not set |
| Low | Permissions-Policy not set (10063) | header not set |
| Informational | cache-control and storability notes (10015, 10049, 10050), modern web app (10109) | no action |

HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `frame-ancestors 'none'`, `object-src 'none'` and `upgrade-insecure-requests` are present, so ZAP reports none of the classic header failures. The Medium and Low findings are edge-policy changes tracked in #610; this scan does not change them.

## Deliberate Non-Gates

- **No hard Maven coverage check** (`jacoco:check`). Coverage judged on *new code* in SonarQube, where ratcheting is possible; a flat repo-wide number punishes the skeleton phase.
- **No OWASP Dependency-Check**. Trivy fs covers Maven CVEs without NVD API-key friction.
- **No in-image HEALTHCHECK**. JRE base carries no curl/wget; orchestrator probes use `/actuator/health` (port 8589).
- **No licence gate (yet)**. `sbom.yml` produces CycloneDX SBOMs and Trivy licence inventories for `backend/` and `frontend/` on release tags, weekly and on dispatch; on a pull request only when it carries the `run-sbom` label, so `gh pr list --label run-sbom --state all` lists the PRs it ran for. Licence categories are report-only; the job fails only when an inventory is missing or empty. Which licence categories should fail a build is a team decision that this inventory informs.

### SBOM and licence inventory — 2026-10-22

Data: artifact `sbom` of [run 39771223592](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39771223592) on PR #609.

**Scope.** The backend SBOM lists the compile and runtime dependencies of `backend/pom.xml` and its ten modules: the seven modules of the monolith deployed to dev and the three SWE5001 services. Trivy leaves out test-scope and `optional` dependencies. The frontend SBOM lists the production dependency closure of `frontend/pnpm-lock.yaml`; Trivy leaves out devDependencies. Trivy lists a Maven library once for every module that uses it (659 library entries for 206 artifacts), so the counts below are distinct packages, without the project's own 11 Maven artifacts.

**Completeness**, checked once against each package manager:

- Backend: `mvn dependency:list` over the same modules resolves 198 third-party compile and runtime artifacts. The SBOM has 195 of them. The other three are declared `optional`: Lombok, SpotBugs annotations and Spring Boot DevTools. None of them is in the executable `blocbuy-backend-exec.jar`. Every one of the jar's 180 third-party libraries is in the SBOM, except `spring-boot-jarmode-tools`, which the Spring Boot plugin adds at packaging time.
- Frontend: the production closure computed from the lockfile has 96 package versions of 94 packages, and the SBOM has exactly these. TypeScript is in it only as an optional peer dependency of `valibot`; it is a build tool and is not bundled.

| Part | Third-party packages | With a licence finding | Restricted | Reciprocal | Notice | Unencumbered | Unknown |
|---|---|---|---|---|---|---|---|
| backend (Maven) | 195 | 195 | 4 | 4 | 190 | 1 | 1 |
| frontend (pnpm) | 94 | 75 | 0 | 0 | 72 | 2 | 1 |

A package counts under the category of each of its licences, so the columns add up to more than the packages. No package is in the forbidden category.

- **Restricted and reciprocal**: the same four dual-licensed artifacts, usable under either licence: `logback-classic` and `logback-core` (EPL-2.0 or LGPL-2.1) and `jakarta.annotation-api` and `jakarta.servlet-api` (EPL-2.0 or GPL-2.0 with the Classpath Exception).
- **Unencumbered**: `HdrHistogram` (CC0-1.0 or BSD-2-Clause), `isbot` (Unlicense) and `tslib` (0BSD).
- **Unknown**: `reactive-streams` (MIT-0) and the `@fontsource/inter` font (OFL-1.1). Both allow use and redistribution, but Trivy does not classify them.
- **No licence finding**: 19 frontend packages, all `@typescript/typescript-<os>-<arch>`, TypeScript's native compilers for other platforms. The lockfile lists them, but pnpm installs only the runner's own, so there is no `package.json` to read a licence from.
- **Own artifacts**: Trivy reports them as Apache-2.0, inherited from the `spring-boot-starter-parent` POM. The project declares no licence, so they are left out of the counts.

## Exception Process

1. Prefer fixing the code.
2. Tool false positive → narrowest possible exclusion (`spotbugs-excludes.xml`, `.gitleaks.toml`, SonarQube "false positive" flag) **with a written reason in the config or SonarQube comment**.
3. Unfixable vulnerability (no patched version) → documented acceptance in the PR + linked tracking issue with a revisit date.
