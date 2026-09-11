# Quality Gates — Backend

The numbers the pipeline enforces. Change to any row = team decision, recorded here with a date.

| Gate | Tool | Threshold | Enforced at |
|---|---|---|---|
| Formatting | Spotless + google-java-format (AOSP) | 100% formatted | `validate` phase, CI `build-quality` step 1 |
| Code style | Checkstyle 10.26.1 (Google checks) | 0 errors; line length 120 (D13) | `verify` phase |
| Static bugs | SpotBugs 4.10.4 | 0 findings, threshold Medium, effort Max | `verify` phase |
| Architecture | ArchUnit 1.4.2 | all 6 rules green | unit tests (`ArchitectureTest`) |
| Build gates | Maven Enforcer | Java [21,22), Maven ≥3.9, dependency convergence | `validate` phase |
| Coverage | JaCoCo 0.8.15 → SonarCloud | ≥ 80% on new code (server quality gate) | SonarCloud GitHub App |
| Code quality | SonarCloud | quality gate green: no new blocker/critical, A ratings on new code | SonarCloud GitHub App |
| Dependency CVEs | Trivy fs | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `security.yml` + weekly cron |
| Image CVEs | Trivy image | 0 unfixed HIGH/CRITICAL (`exit-code: 1`, `ignore-unfixed`) | `ci-backend.yml` `docker` job |
| Secrets | Gitleaks | 0 leaks in full git history | `security.yml` |
| SAST | CodeQL (java-kotlin, build-mode none) | 0 alerts; opt-in via `ENABLE_CODEQL` repo variable | `security.yml` `codeql` job |
| API contract | springdoc export | committed `contracts/openapi.json` identical to generated | `contract-check` job |
| API compatibility | openapi-diff 2.1.7 (`--fail-on-incompatible`) | no backward-incompatible change vs base branch | `contract-check` job (PR only) |
| Boot smoke | container boot vs PG 18 + Redis 8 | `/actuator/health` = UP within 60s | `ci-backend.yml` `docker` job |

## Deliberate Non-Gates

- **No hard Maven coverage check** (`jacoco:check`). Coverage judged on *new code* in SonarQube, where ratcheting is possible; a flat repo-wide number punishes the skeleton phase.
- **No OWASP Dependency-Check**. Trivy fs covers Maven CVEs without NVD API-key friction.
- **No in-image HEALTHCHECK**. JRE base carries no curl/wget; orchestrator probes use `/actuator/health` (port 8589).

## Exception Process

1. Prefer fixing the code.
2. Tool false positive → narrowest possible exclusion (`spotbugs-excludes.xml`, `.gitleaks.toml`, SonarQube "false positive" flag) **with a written reason in the config or SonarQube comment**.
3. Unfixable vulnerability (no patched version) → documented acceptance in the PR + linked tracking issue with a revisit date.
