# DevSecOps maturity self-assessment (DSOMM), 23 October 2026

This page covers the SWE5006 dev pipeline and environment (EC2, S3 and CloudFront) using *DevSecOps Engineering and Automation*, module 17, Appendix A (PDF pp. 6-9, slides 11-18). Its levels are 1 "basic understanding of security practices", 2 "adoption of basic security practices", 3 "high adoption of security practices" and 4 "advanced deployment of security practices at scale". Repository evidence is checked against `dev` at `f1ff3582`, including merged SBOM PR #609 and delivery-metrics PR #629.

**Rule.** A sub-dimension *reaches* level *n* when every practice the appendix lists for levels 1 to *n* has evidence for SWE5006 dev; an empty level counts as met. Practices met at a higher level are listed but do not raise the level. SWE5001 evidence is marked [5001] and does not count. No overall level is given: the model defines none, and the spread is the finding.

**Review scope.** This is an evidence refresh, not a new maturity assessment. The 17 level values below are inherited from the original author's rendered-PDF assessment and have not been independently regraded. Documentation/source checks and DORA-tool tests establish link/replay integrity; they do not validate DSOMM prerequisites, security-test outcomes or live AWS state. "Missing" means sufficient SWE5006 evidence was not identified at this checkpoint.

## Build and Deployment

| Sub-dimension | Inherited level | Linked evidence | Gap to the next level |
|---|---|---|---|
| Build | 1 | L1 defined build process ([backend CI](../../.github/workflows/ci-backend.yml), [frontend CI](../../.github/workflows/ci-frontend.yml)). L2, partly: GitHub-hosted runners and Testcontainers; SHA-pinned actions and [digest-pinned runtime/tool images](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01); merged [source SBOM workflow](../../.github/workflows/sbom.yml) and its [scope/evidence](../quality/quality-gates.md#validated-source-inventory-evidence--2026-10-22) | L2: CI service images remain tag-pinned (`postgres:18`, `redis:8.10` in backend CI); source inventories are not proof of complete packaged-runtime coverage |
| Deployment | 1 | L1 defined deployment process ([`deploy-existing-ec2`](../../.github/workflows/ci-backend.yml), [`deploy-s3-dev`](../../.github/workflows/ci-frontend.yml)). L2, partly: [official Temurin JRE by digest](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01). L3, partly: [Checkout/scheduling toggles](../../deployment/backend/README.md#change-the-scheduling-flag); [source dependency inventory](../quality/quality-gates.md#validated-source-inventory-evidence--2026-10-22) | L2: no evidenced dev decommissioning process ([5001] [`env-down.sh`](../../infra/swe5001/scripts/env-down.sh)); [dev configuration is host-managed](../../deployment/backend/README.md#secrets-dev-vs-prod), not handed over per environment |
| Patch management | 1 | L1 [daily Dependabot policy](../../.github/dependabot.yml) for Maven, npm, Actions and Docker; [runtime base image maintenance](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01). L2, partly: reduced attack surface ([Compose](../../deployment/backend/compose.yml): read-only root, `cap_drop: ALL`, `no-new-privileges`; [non-root runtime](../../backend/Dockerfile)) | L2: no enforced maximum image lifetime evidenced |

## Culture and Organization

| Sub-dimension | Inherited level | Linked evidence | Gap to the next level |
|---|---|---|---|
| Design | 0 | L1, partly: security targets are communicated as NFR rows (NFR-01 to NFR-04, NFR-33 to NFR-37, NFR-68 to NFR-72 in the [NFR register](../quality/nfr-register.md)) | L1: no sufficient technical threat model of the SWE5006 system evidenced |
| Education and guidance | 0 | L1, partly: [CODEOWNERS](../../.github/CODEOWNERS) routes `.github/` changes to their owner | L1: ad-hoc security training, a defined security-review practice and security consulting on request are not evidenced; ordinary ownership review alone does not establish them |
| Process | 0 | L3, partly: the `dev` ruleset requires PRs, [required checks and up-to-date branches](backend-pipeline.md#required-pr-checks) | L1: no evidenced BCDR practice for critical components; dev database backup and restore are outside the pipeline ([operator guide](../deploy/backend-ec2-pipeline-operator-guide.md)) |

## Implementation

| Sub-dimension | Inherited level | Linked evidence | Gap to the next level |
|---|---|---|---|
| Application hardening | not assessed | The appendix names "application hardening level 1, 2, 3" without defining them. Recorded controls: selected monolith controller authentication boundaries ([`EndpointAuthSurfaceTest`](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/EndpointAuthSurfaceTest.java), NFR-68), [cookie flag tests](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/config/CookieSecurityProfileTest.java), [per-user write-rate-limit tests](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/WriteRateLimitIT.java) and [configuration-secret redaction](../quality/nfr-register.md) (NFR-69) | A yardstick the course does not give; these tests do not establish authentication/authorization on every route |
| Development and source control | 1 | L1 [source-control protection](backend-pipeline.md#required-pr-checks) and versioning (tags `v0.1.0-sprint1` to `sprint3`). L4, partly: [configured frontend lint/style/type hooks](../../lefthook.yml) (oxfmt, oxlint, `tsc`), subject to [local runtime/dependency setup](../../frontend/AGENTS.md#commands); configuration is not proof that every contributor ran them | L2: configured pre-commit checks cover the frontend, not the backend |
| Infrastructure hardening | 0 | L1, partly: TLS at the edge (CloudFront); deployments reach EC2 through SSM ([operator guide](../deploy/backend-ec2-pipeline-operator-guide.md)); organisation guardrails ([AWS governance](../deploy/aws-account-governance/README.md)) | L1: SWE5006 has no separate production environment; encryption at rest not evidenced |

## Information Gathering

| Sub-dimension | Inherited level | Linked evidence | Gap to the next level |
|---|---|---|---|
| Logging | 0 | L3: centralised application logging ([container awslogs configuration](../../deployment/backend/compose.yml), log group `/blocbuy/backend/dev`, [dated observability evidence](../deploy/backend-log-observability.md)) | L1: centralised system logging, security-event logging and a PII logging concept are not sufficiently evidenced |
| Monitoring | 0 | L1, partly: [Actuator configuration](../../backend/blocbuy-bootstrap/src/main/resources/application.yml) exposes Prometheus metrics; dev collection is not evidenced | L1: budget metrics are unverified (AWS Budgets not checked, [AWS governance](../deploy/aws-account-governance/README.md)); dev system-metric evidence is missing ([5001] ECS dashboard and alarms do not count) |

## Test and Verification

| Sub-dimension | Inherited level | Linked evidence | Gap to the next level |
|---|---|---|---|
| Application tests | 3 | L2 security unit tests ([webhook boundary](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/StripeWebhookBoundaryTest.java), [webhook controller](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/StripeWebhookControllerTest.java), [cookie flags](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/config/CookieSecurityProfileTest.java)). L3 security integration tests ([controller authentication](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/EndpointAuthSurfaceTest.java), [write rate limits](../../backend/blocbuy-bootstrap/src/test/java/com/blocbuy/bootstrap/WriteRateLimitIT.java)). L4, partly: [smoke test](../../perf/smoke/smoke.sh) | L4: no separate coverage target for security tests evidenced |
| Consolidation | 2 | L1 [quality gates](../quality/quality-gates.md), [exact Gitleaks exclusions](../../.gitleaksignore) and [their regression tests](../../.github/tests/gitleaks_allowlist_test.py); [Trivy HIGH/CRITICAL gates](../../.github/workflows/security.yml). L2 defect visualisation through [SonarCloud jobs](backend-pipeline.md#pipeline-shape). L3, partly: [Dependabot](../../.github/dependabot.yml) proposes dependency fixes | L3: Medium findings do not gate; GitHub code scanning is unavailable/disabled ([audit](ci-failure-audit-20261021.md)); no additional vulnerability-management system evidenced |
| Dynamic depth for applications | 1 | L1 simple scan: [passive ZAP workflow](../../.github/workflows/dast-baseline.yml) and [hosted response/passive-processing evidence](../quality/quality-gates.md#dynamic-application-security-testing-dast) for SPA/catalog probes; weekly/manual execution configured, schedule not yet verified at this checkpoint | L2: JavaScript/client-side execution and multiple authenticated roles are not covered |
| Dynamic depth for infrastructure | 1 | L1 recorded origin-bypass check: the [dev CloudFront guide](../deploy/dev-cloudfront-deployment.md#4-target-group-alb-and-listener) checks direct ALB requests without the origin header; this is not a comprehensive exposed-service inventory | L2: no evidenced dev network-segmentation test; [5001] [NFR-70](../quality/nfr-register.md) is separate. [Cloud configuration scanning](../../.github/workflows/security.yml) tests IaC, not the deployed network |
| Static depth for applications | 1 | L1 server-side dependency vulnerabilities through [Trivy](../../.github/workflows/security.yml) and [Dependabot](../../.github/dependabot.yml). L2, partly: [SonarCloud, SpotBugs and Checkstyle](../quality/quality-gates.md). L3: [frontend SonarCloud](../../.github/workflows/ci-frontend.yml), [oxlint](../../frontend/package.json) and Trivy on `pnpm-lock.yaml` | L2: an agreed local security-scanning step during development is not evidenced |
| Static depth for infrastructure | 1 | L1 stored secrets: [event-selected Gitleaks scans](../../.github/workflows/security.yml). L2, partly: `trivy-config` scans IaC, Dockerfiles and Compose; L3 is empty | L2: no enforced image-lifetime check. The [full-history scope/disposition gap](../quality/quality-gates.md#secret-scan-scope-checkpoint--2026-10-14) retains four historical badge-token findings; green event-selected scans do not establish clean history |

## Summary of inherited levels

| Inherited level | Sub-dimensions |
|---|---|
| 3 | Application tests |
| 2 | Consolidation |
| 1 | Build, Deployment, Patch management, Development and source control, Dynamic depth for applications, Dynamic depth for infrastructure, Static depth for applications, Static depth for infrastructure |
| 0 | Design, Education and guidance, Process, Infrastructure hardening, Logging, Monitoring |
| not assessed | Application hardening |

The inherited assessment is strongest in automated tests, gates and scans. Candidate follow-ups are a money-path threat model (checkout, webhook, refund) and digest-pinning the two CI service images; these are not approved delivery commitments or a promise of a higher level. Recheck the rendered appendix and every prerequisite practice before revising a score.

## Delivery evidence: DSO 16, not a DSOMM score

Merged [PR #629](https://github.com/PPsyrius/iss-groupbuy-team8/pull/629) supplies the [22 October delivery snapshot](dora-metrics-20261022.md) and [frozen replay inputs](evidence/dora-metrics-20261022-inputs.json) for DSO 16 pp. 3-4. Use that canonical source for values, observation cutoff and limits; no newer window was collected here. These are dev delivery proxies, not production health or incident MTTR; performance targets remain unagreed. **Decision — 2026-10-23:** this evidence refresh does not regrade the inherited DSOMM assessment or accept security gaps.

## Recorded gaps and scoped defences

| Gap | Existing scoped defence | Remaining limit | Acceptance status |
|---|---|---|---|
| Image signing/attestation | [Digest-pinned runtime images](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01), [Trivy gates](../quality/quality-gates.md) and immutable-image deployment | Digests and scan results are not signatures, build provenance or attestation. | Pending team decision |
| Automated licence policy | [Source inventory, licence coverage and hash manifests](../quality/quality-gates.md#validated-source-inventory-evidence--2026-10-22) | Findings are advisory; no agreed enforcement policy, complete packaged-runtime inventory or legal approval is established. | Pending team decision |
| Active/authenticated DAST | [Passive anonymous SPA/catalog probes with coverage checks](../quality/quality-gates.md#dynamic-application-security-testing-dast) | These probes neither execute JavaScript nor test active attacks, multiple authenticated roles or payment-flow security. | Pending team decision |

These defences do not satisfy the missing practices. [#617](https://github.com/PPsyrius/iss-groupbuy-team8/issues/617) asks for accepted gaps, but no acceptance decision is recorded here. The team must either record an owner, rationale and date for each deferral or agree to revise that scope item to recorded gaps. This scope decision remains open; the table is not formal risk acceptance.
