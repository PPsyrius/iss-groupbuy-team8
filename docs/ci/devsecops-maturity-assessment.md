# DevSecOps maturity self-assessment (DSOMM), 22 October 2026

This page places the SWE5006 delivery pipeline and its dev environment (EC2, S3 and CloudFront) on the DevSecOps Maturity Model of *DevSecOps Engineering and Automation*, module 17. The practices per level are those of its Appendix A (PDF pp. 6-9, slides 11-18), whose columns are level 1 "basic understanding of security practices", level 2 "adoption of basic security practices", level 3 "high adoption of security practices" and level 4 "advanced deployment of security practices at scale". It is the team's own reading, not an audit; repository state on `dev` at `14996412`.

**Rule.** A sub-dimension *reaches* level *n* when every practice the appendix lists for levels 1 to *n* has evidence for SWE5006 dev; an empty level counts as met. Practices met at a higher level are listed but do not raise the level. SWE5001 evidence is marked [5001] and does not count. No overall level is given: the model defines none, and the spread is the finding.

## Build and Deployment

| Sub-dimension | Reached | Evidence | Missing for the next level |
|---|---|---|---|
| Build | 1 | L1 defined build process (`ci-backend.yml`, `ci-frontend.yml`). L2, partly: built and tested on GitHub-hosted runners with Testcontainers; actions pinned by commit SHA, the runtime base image and tool images by digest | L2: CI service images are pinned by tag only (`postgres:18`, `redis:8.10` in `ci-backend.yml`); SBOM (open PR #609) |
| Deployment | 1 | L1 defined deployment process (`deploy-existing-ec2`, `deploy-s3-dev`). L2, partly: trusted base image (official Temurin JRE by digest). L3, partly: feature toggles (`BLOCBUY_STRIPE_CHECKOUT_ENABLED`, `BLOCBUY_SCHEDULING_ENABLED`) | L2: no decommissioning process for dev ([5001] `env-down.sh`); dev configuration is a hand-edited file on the host (`deployment/backend/README.md`), not handed over per environment |
| Patch management | 1 | L1 patch policy: Dependabot daily for Maven, npm, Actions and Docker; [runtime base image maintenance](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01). L2, partly: reduced attack surface (`compose.yml`: read-only root, `cap_drop: ALL`, `no-new-privileges`; non-root user) | L2: no maximum lifetime for images |

## Culture and Organization

| Sub-dimension | Reached | Evidence | Missing for the next level |
|---|---|---|---|
| Design | 0 | L1, partly: security targets are communicated as NFR rows (NFR-01 to NFR-04, NFR-33 to NFR-37, NFR-68 to NFR-71 in the [NFR register](../quality/nfr-register.md)) | L1: no threat model of the SWE5006 system on technical level |
| Education and guidance | 0 | L1, partly: CODEOWNERS routes `.github/` changes to their owner | L1: no ad-hoc security training for developers, no security code review as such, no security consulting on request |
| Process | 0 | L3, partly: every change reaches `dev` through a pull request under the `dev` ruleset (required checks, up-to-date branches) | L1: no BCDR practice for critical components; dev database backup and restore are outside the pipeline ([operator guide](../deploy/backend-ec2-pipeline-operator-guide.md)) |

## Implementation

| Sub-dimension | Reached | Evidence | Missing for the next level |
|---|---|---|---|
| Application hardening | not assessed | The appendix names "application hardening level 1, 2, 3" without defining them. Controls in place: authentication on every protected route (NFR-68, `EndpointAuthSurfaceTest`), cookie flags (`CookieSecurityProfileTest`), per-user write rate limits (`WriteRateLimitIT`), secret redaction in configuration output (NFR-69) | A yardstick the course does not give |
| Development and source control | 1 | L1 source control protection (`dev` ruleset) and versioning (tags `v0.1.0-sprint1` to `sprint3`). L4, frontend only: local lint and style checks (`lefthook.yml`: oxfmt, oxlint, `tsc`) | L2: pre-commit checks exist for the frontend only; none for the backend |
| Infrastructure hardening | 0 | L1, partly: TLS at the edge (CloudFront); deployments reach EC2 through SSM ([operator guide](../deploy/backend-ec2-pipeline-operator-guide.md)); organisation guardrails ([AWS governance](../deploy/aws-account-governance/README.md)) | L1: SWE5006 has no separate production environment; encryption at rest not evidenced |

## Information Gathering

| Sub-dimension | Reached | Evidence | Missing for the next level |
|---|---|---|---|
| Logging | 0 | L3: centralised application logging (container output to CloudWatch, log group `/blocbuy/backend/dev`, [log observability](../deploy/backend-log-observability.md)) | L1: no centralised system logging, no logging of security events, no PII logging concept |
| Monitoring | 0 | L1, partly: application metrics exposed at `/actuator/prometheus`, but nothing on dev collects them | L1: no budget metrics (AWS Budgets not checked, [AWS governance](../deploy/aws-account-governance/README.md)); no system metrics for dev ([5001] ECS dashboard and alarms) |

## Test and Verification

| Sub-dimension | Reached | Evidence | Missing for the next level |
|---|---|---|---|
| Application tests | 3 | L2 security unit tests (`StripeWebhookBoundaryTest`, `StripeWebhookControllerTest`, `CookieSecurityProfileTest`). L3 security integration tests (`EndpointAuthSurfaceTest`, `WriteRateLimitIT`). L4, partly: smoke test (`perf/smoke/smoke.sh`) | L4: no coverage target for security tests |
| Consolidation | 2 | L1 quality gates ([quality gates](../quality/quality-gates.md)); false-positive treatment (Gitleaks allowlist and its test); HIGH and CRITICAL findings fail Trivy. L2 visualisation of defects (SonarCloud). L3, partly: vulnerability fixes reach development as Dependabot PRs | L3: medium-severity findings do not gate; no vulnerability management system (GitHub code scanning is not enabled) |
| Dynamic depth for applications | 1 | L1 simple scan: passive OWASP ZAP baseline of the SPA and catalog API, weekly and on dispatch (`dast-baseline.yml`, PR #607); its schedule has not run yet | L2: coverage of client-side dynamic components; more than one role |
| Dynamic depth for infrastructure | 1 | L1 test for exposed services: the ALB refuses requests without the CloudFront origin header, a documented manual check ([dev CloudFront guide](../deploy/dev-cloudfront-deployment.md)) | L2: no test of network segmentation ([5001] NFR-70); cloud configuration is scanned only as IaC (`trivy-config`) |
| Static depth for applications | 1 | L1 known vulnerabilities in server-side components (Trivy on Maven, Dependabot). L2, partly: static analysis of server-side code (SonarCloud, SpotBugs, Checkstyle). L3: static analysis of client-side code (SonarCloud Frontend, oxlint) and known vulnerabilities in client-side components (Trivy on `pnpm-lock.yaml`) | L2: no local security checks during development |
| Static depth for infrastructure | 1 | L1 stored secrets: Gitleaks on every PR. L2, partly: deployment resources tested (`trivy-config` scans the repository's IaC, Dockerfiles and Compose files); L3 is empty | L2: no image-lifetime check. The weekly full-history Gitleaks scan has failed since 21 September with four undisposed findings ([CI failure audit](ci-failure-audit-20261021.md)) |

## Summary

| Reached | Sub-dimensions |
|---|---|
| 3 | Application tests |
| 2 | Consolidation |
| 1 | Build, Deployment, Patch management, Development and source control, Dynamic depth for applications, Dynamic depth for infrastructure, Static depth for applications, Static depth for infrastructure |
| 0 | Design, Education and guidance, Process, Infrastructure hardening, Logging, Monitoring |
| not assessed | Application hardening |

The pipeline is strongest where it is automated: tests, gates and scans. The gaps are outside the pipeline: threat modelling, training, backup and recovery, a production environment, security logging and monitoring. The course briefing asks only for "minimal security considerations" for this project, so these gaps are recorded, not planned. The cheapest next steps on the table are a simple threat model of the money path (checkout, webhook, refund), digest-pinning the two CI service images, and merging the SBOM (#609).
