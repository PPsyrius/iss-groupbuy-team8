# Backend CI Runbook

How the backend pipeline works and how to run every gate locally.
Plan and rationale: [devsecops-pipeline-plan.md](devsecops-pipeline-plan.md). Thresholds: [../quality/quality-gates.md](../quality/quality-gates.md).

## Pipeline Shape

PR / push to `dev` touching `backend/**` or `contracts/**`:

```
ci-backend.yml
├── build-quality    spotless:check → checkstyle → mvn verify (unit + ArchUnit + ITs + SpotBugs + JaCoCo)
├── contract-check   mvn verify -Popenapi-export → git diff contracts/ → openapi-diff --fail-on-incompatible (PR only, vs base branch)
├── sonar            (skipped until SONAR_HOST_URL repo variable is set)
└── docker           docker build → smoke test (boot vs PG 18 + Redis 8, probe /actuator/health) → Trivy image scan (exit-code 1)

security.yml  (PR + push + weekly cron Mon 02:17 UTC)
├── gitleaks         full-history secret scan
├── trivy-deps       dependency CVEs + config misconfig (HIGH/CRITICAL, unfixed only, exit-code 1)
└── codeql           (skipped until ENABLE_CODEQL=true repo variable is set; needs GHAS on private repos)
```

Required checks for merge (set in branch protection): `build-quality`, `contract-check`, `docker`.

## Run Every Gate Locally

From `backend/` (JDK 21 required — `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS):

```bash
./mvnw spotless:apply        # auto-fix formatting — run before committing
./mvnw spotless:check        # what CI checks
./mvnw checkstyle:check      # style lint
./mvnw spotbugs:check        # static bug analysis
./mvnw verify                # all of the above + tests + ArchUnit + JaCoCo
./mvnw verify -Popenapi-export   # regenerate ../contracts/openapi.json
```

`./mvnw verify` alone covers everything `build-quality` runs. CI runs the same wrapper — no "works on my machine".

## Fixing a Red Check

| Failing check | Fix |
|---|---|
| `spotless:check` | `./mvnw spotless:apply`, commit the diff |
| Checkstyle | read `target/checkstyle-result.xml` or console output; most are naming/Javadoc/brace issues the formatter cannot own |
| SpotBugs | finding printed in console + `target/spotbugsXml.xml`; fix the code — widening `spotbugs-excludes.xml` needs a written reason in the XML comment |
| ArchUnit | layering violation: move the dependency behind a domain port or the correct module |
| `contract-check` | drift: `./mvnw verify -Popenapi-export`, commit the changed `contracts/openapi.json`. Breaking change: revert the incompatible API change, or record a team-approved breaking-change exception in the PR |
| `sonar` | open the SonarQube PR decoration; fix or mark false-positive/won't-fix with a comment |
| `gitleaks` | rotate the leaked secret immediately, then remove from history (or add an allowlist entry with a reason — only for false positives) |
| `docker` (smoke test) | app failed to boot against PG/Redis or health never went UP — read the dumped `smoke-app` container logs; reproduce locally with the docker profile |
| `docker` (Trivy) / `trivy-deps` | bump the dependency/base image (Dependabot usually has a PR open already); if unfixed upstream, document the acceptance |

## Rules

- No `-DskipTests`, `-Dspotless.check.skip`, or `-Dspotbugs.skip` in CI for real builds (skips only inside the throwaway `contract-check` export run and the Docker build, where gates already ran).
- Skipping a red gate by merging anyway is a team decision recorded in the PR, never a solo act.
- Spotless AOSP (4-space) owns formatting; Checkstyle never re-litigates what the formatter produced.
