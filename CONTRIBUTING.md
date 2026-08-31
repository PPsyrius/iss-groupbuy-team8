# Contributing

## Before You Push

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # JDK 21 required (Enforcer rejects others)
./mvnw spotless:apply                               # format
./mvnw verify                                       # tests + ArchUnit + Checkstyle + SpotBugs + JaCoCo
```

Changed a controller or DTO? Also `./mvnw verify -Popenapi-export` and commit the updated `contracts/openapi.json`.

## Commits

Conventional Commits: `feat(scope): summary`, `fix(scope): summary`, `docs:`, `style:`, `chore:`.
Summary ≤ 72 chars, imperative. Body only when the *why* is not obvious.

## Pull Requests

- Target `dev`. Small PRs; one concern per PR.
- CI must be green: `build-quality`, `contract-check` are required checks. Red pipelines don't merge.
- Formatting-only changes land as their own commit (`style:`), never mixed with behavior changes.
- CODEOWNERS review required. PRs touching `.github/**`, `backend/Dockerfile`, or pipeline config need the devsecops label owner.
- New secrets/config: environment variables only, documented in `backend/README.md`. Never committed values (Gitleaks gates full history).

## Style & Architecture

- Formatting is machine-owned: Spotless + google-java-format AOSP (4-space). Don't hand-format.
- COLA layering enforced by ArchUnit — violations fail the build. Layer rules: `backend/blocbuy-bootstrap/src/test/java/com/blocbuy/architecture/ArchitectureTest.java`.
- Gates and thresholds: `docs/quality/quality-gates.md`. Pipeline runbook: `docs/ci/backend-pipeline.md`.
