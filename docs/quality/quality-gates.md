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

`dast-baseline.yml` runs a passive OWASP ZAP 2.17.0 baseline (`zap-baseline.py`, image pinned by digest) against the dev site at `vars.FRONTEND_URL_DEV`: the SPA (`/`) and the backend behind the same CloudFront origin (`/api/v1/products`). It runs weekly (Monday 03:41 UTC), on dispatch and on PRs that edit the workflow. It is **advisory**: the spider runs for at most 3 minutes, alerts come only from inspecting responses, nothing is attacked, and no result fails a check. Reports (HTML, JSON, Markdown) are kept 30 days as `zap-web` and `zap-api` artifacts and the Markdown report appears in the job summary.

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

## Exception Process

1. Prefer fixing the code.
2. Tool false positive → narrowest possible exclusion (`spotbugs-excludes.xml`, `.gitleaks.toml`, SonarQube "false positive" flag) **with a written reason in the config or SonarQube comment**.
3. Unfixable vulnerability (no patched version) → documented acceptance in the PR + linked tracking issue with a revisit date.
