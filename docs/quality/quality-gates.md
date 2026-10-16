# Quality Gates

The numbers the pipeline enforces. Change to any row = team decision, recorded here with a date.

| Gate | Tool | Threshold | Enforced at |
|---|---|---|---|
| Formatting | Spotless + google-java-format (AOSP) | 100% formatted | `validate` phase, CI `build-quality` step 1 |
| Code style | Checkstyle 14.1.0 (Google checks) | 0 errors; line length 120 (D13) | `verify` phase |
| Static bugs | SpotBugs 4.10.4 | 0 findings, threshold Medium, effort Max | `verify` phase |
| Architecture | ArchUnit 1.5.0 | all current rules green | unit tests (`ArchitectureTest`) |
| Build gates | Maven Enforcer | Java [21,22), Maven ≥3.9, dependency convergence | `validate` phase |
| Coverage | JaCoCo 0.8.15 → SonarCloud Backend | ≥ 80% on new code (server quality gate) | `ci-backend.yml` `sonar-backend` |
| Code quality | SonarCloud Backend | quality gate green: no new blocker/critical, A ratings on new code | `ci-backend.yml` `sonar-backend` |
| Frontend coverage | Vitest V8 LCOV → SonarCloud Frontend | ≥ 80% on new code (server quality gate) | `ci-frontend.yml` `sonar-frontend` |
| Frontend code quality | SonarCloud Frontend TypeScript analysis | quality gate green | `ci-frontend.yml` `sonar-frontend` |
| Dependency CVEs | Trivy fs | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `security.yml` + weekly cron |
| Image CVEs | Trivy image | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `ci-backend.yml` `docker` job |
| Secrets | Gitleaks | Target: 0 leaks in full git history (unchanged) | `security.yml` event-selected scans; full-history gap below |
| SAST | CodeQL (java-kotlin, build-mode none) | 0 alerts; opt-in via `ENABLE_CODEQL` repo variable | `security.yml` `codeql` job |
| API contract | springdoc export | committed `contracts/openapi.json` identical to generated | `contract-check` job |
| API compatibility | openapi-diff 2.1.7 (`--fail-on-incompatible`) | no backward-incompatible change vs base branch | `contract-check` job (PR only) |
| Boot smoke | container boot vs PG 18 + Redis 8 | `/actuator/health` = UP within 60s | `ci-backend.yml` `docker` job |

## Secret-scan scope checkpoint — 2026-10-14

The full-history target above is unchanged, but `fetch-depth: 0` makes history available without selecting it for scanning. The pinned Gitleaks action chooses revisions by event; [observed dev run 38503702085](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38503702085) used version 8.24.3 and `--log-opts=-1` (one commit). A green event-selected scan is not proof of a clean full history.

The [October 14 local audit](../agile/sprint-04/log/2026-10-14.md#2026-10-14---ppsyrius---http-scaling-and-demo-dashboard-457) scanned 375 commits with 8.24.3 and 8.28.0 and found 24 pre-existing matches: 20 confirmed SHA-256 entries and four old Sonar badge-token matches still needing disposition. Working changes passed separately. No new exclusion, history rewrite or acceptance decision was made; NFR-02 remains only partially verified against its full-history target.

## Deliberate Non-Gates

- **No hard Maven coverage check** (`jacoco:check`). Coverage judged on *new code* in SonarQube, where ratcheting is possible; a flat repo-wide number punishes the skeleton phase.
- **No OWASP Dependency-Check**. Trivy fs covers Maven CVEs without NVD API-key friction.
- **No in-image HEALTHCHECK**. JRE base carries no curl/wget; orchestrator probes use `/actuator/health` (port 8589).

## Exception Process

1. Prefer fixing the code.
2. Tool false positive → narrowest possible exclusion (`spotbugs-excludes.xml`, `.gitleaks.toml`, SonarQube "false positive" flag) **with a written reason in the config or SonarQube comment**.
3. Unfixable vulnerability (no patched version) → documented acceptance in the PR + linked tracking issue with a revisit date.
