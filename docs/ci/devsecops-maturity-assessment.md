# DevSecOps maturity self-assessment (DSOMM), 22 October 2026

This page places the SWE5006 delivery pipeline, the dev environment on EC2 with S3 and CloudFront, on the DevSecOps Maturity Model taught in *DevSecOps Engineering and Automation*, module 17 (DSOMM). Module 17 uses four levels (pp. 3-4):

1. basic understanding;
2. adoption of basic practices;
3. high adoption;
4. advanced deployment at scale.

Its appendix lists, per dimension and sub-dimension, the practices expected at each level (pp. 6-9). It is source material for report section 4 and for the deck.

**How to read the table.**

- A sub-dimension is at level *n* when every practice the course lists for level *n*, and below, has evidence in the repository or in Actions. Partial levels are marked "+" (some practices of the next level) or "−" (one practice of this level missing).
- The SWE5001 ECS environment is mentioned only where it goes further than dev.
- Items marked *on merge* depend on an open PR: #607 (DAST) and #609 (SBOM).
- This is the team's own reading, not an audit.

## Build and Deployment

| Sub-dimension | Level | Evidence | Next step |
|---|---|---|---|
| Build | 3 on merge (2 today) | **L1:** defined build (Maven, `ci-backend.yml`, `ci-frontend.yml`); artefacts built and tested on GitHub runners, Testcontainers for PostgreSQL and Redis. **L2:** pinning: every action by commit SHA, base images and tool images by digest, Trivy version fixed. **L3:** SBOM per release tag and weekly (`sbom.yml`, #608, on merge) | **L4:** sign images and code (e.g. cosign, signed commits); not planned (#621 records the signing decision) |
| Deployment | 2+ | **L1:** defined deployment (`deploy-existing-ec2` via SSM, `deploy-s3-dev`); decommissioning defined for SWE5001 sessions (`env-down.sh`). **L2:** configuration and secrets per environment (SSM Parameter Store, GitHub environments `backend-dev`); trusted base image (official Temurin JRE, digest-pinned). **L3, partly:** confidential parameters handed over at deploy time from Parameter Store; inventory of dependencies (SBOM, on merge); inventory of running artefacts (recorded image digests, immutable ECR tags). **L4, partly:** feature toggles (`BLOCBUY_STRIPE_CHECKOUT_ENABLED`, `BLOCBUY_SCHEDULING_ENABLED`, `topology`) | **L3:** rolling update (dev restarts one container in place; SWE5001 ECS already rolls); deploy the scanned image itself, because the delivery image is rebuilt after merge ([pipeline plan](devsecops-pipeline-plan.md)) |
| Patch management | 2 | **L1:** patch policy: daily Dependabot for Maven, npm (the pnpm lockfile), GitHub Actions and Docker, plus the [runtime base image maintenance](backend-pipeline.md#runtime-base-image-maintenance-2026-10-01) rule. **L2:** reduced attack surface: non-root user, read-only root filesystem, `cap_drop: ALL`, `no-new-privileges` (`deployment/backend/compose.yml`) | **L2−→L3:** a maximum image lifetime (rebuild and redeploy at least weekly even without a code change) |

## Culture and Organization

| Sub-dimension | Level | Evidence | Next step |
|---|---|---|---|
| Design | 1− | Security targets are communicated as NFR rows: NFR-01 to NFR-04, NFR-33 to NFR-37 and NFR-68 to NFR-70 (NFR-71 and NFR-72 on merge) in the [NFR register](../quality/nfr-register.md). No threat model of the SWE5006 system is in the repository (the SWE5001 report has a STRIDE table) | A simple technical threat model of the money path (checkout, webhook, refund) |
| Education and Guidance | 1 | Security code review: every PR needs an approving review; `.github/CODEOWNERS` routes pipeline and infrastructure changes to their owner. The course modules served as security training | A named security champion; a short secure-coding checklist in `CONTRIBUTING.md` |
| Process | 2 | **L1:** rollback defined (automatic on a failed health check; `rollback-backend.yml`). **L2:** every new version approved by review (`dev` ruleset); unauthorised installation prevented by AWS organisation guardrails ([SCPs](../deploy/aws-account-governance/README.md)). The SWE5001 track adds a promotion PR from `dev` to `prod` as a change process | Business continuity: database backup and restore for dev are outside the pipeline and not evidenced ([operator guide](../deploy/backend-ec2-pipeline-operator-guide.md)); run the rollback drill (#623) |

## Implementation

| Sub-dimension | Level | Evidence | Next step |
|---|---|---|---|
| Application hardening | 1+ | Authentication on every protected route (NFR-68, `EndpointAuthSurfaceTest`), role checks, CSRF header, session cookie flags (NFR-01), per-user rate limits on checkout, join and invites, security headers at the edge | Close the header findings of the first ZAP run (#610); decide actuator and OpenAPI exposure (#549) |
| Development and Source Control | 2− | **L1:** protected `dev` (ruleset: PRs only, required checks, up-to-date, review, no force-push); versioned tags `v0.1.0-sprintN`. **L2:** local linting and style (Spotless and Checkstyle in the build; frontend lint and format through `lefthook`) | Pre-commit secret scan (Gitleaks in `lefthook`); export the `dev` ruleset as code (#621) |
| Infrastructure hardening | 2 | **L1:** isolated network (backend on EC2 behind an ALB that accepts only CloudFront and a secret header; no public 8589); access control by IAM roles and GitHub OIDC with no stored AWS keys (NFR-04); HTTPS at the edge; S3 encryption at rest; AWS root MFA. **L2:** applications in containers; dependency sources checked by Trivy and Dependabot. **L3, partly:** IaC for the frontend edge (`infra/terraform/frontend-stack`) and the whole SWE5001 runtime; role-based authorisation in the application | **L1:** separate test and production environments for SWE5006 (prod defined, never applied); GitHub 2FA confirmed for all members (#621). **L2:** backups, egress filtering, encryption between CloudFront and the API (NFR-37) |

## Information Gathering

| Sub-dimension | Level | Evidence | Next step |
|---|---|---|---|
| Logging | 2− | **L1:** centralised logs: the container writes to CloudWatch Logs `/blocbuy/backend/dev`, kept 14 days ([log guide](../deploy/backend-log-observability.md)); every request carries a trace ID (NFR-09). **L2, partly:** application logs centralised and searchable | Log security events (failed sign-ins, refused webhooks, rate-limit refusals) as a named category; a PII logging rule |
| Monitoring | 1+ | **L1:** application metrics (`/actuator/prometheus`), system metrics (EC2 and CloudWatch). Delivery metrics: DORA snapshot (#613). **L2, on SWE5001 only:** alerts (dead-letter queue and queue age) and a metrics dashboard | Alerts and a dashboard for dev; budget metrics (AWS Budgets was not checked: [governance](../deploy/aws-account-governance/README.md)) |

## Test and Verification

| Sub-dimension | Level | Evidence | Next step |
|---|---|---|---|
| Application tests | 2 | **L1:** security unit tests for important components: webhook signature, `EndpointAuthSurfaceTest`, cookie profile, redaction of secrets (NFR-69). **L2:** security integration tests: rate limits over HTTP, the 20-buyer last-seat race in `StripeLocalIntegrationTest`. Smoke tests: container boot in CI, `e2e-dev` after each frontend deploy | Higher coverage of security-relevant modules (L3) |
| Consolidation | 2 | **L1:** quality gates defined ([quality gates](../quality/quality-gates.md)); false positives handled by the exception process. **L2:** HIGH and CRITICAL findings fail the build; findings visible in SonarCloud and SARIF artefacts; Dependabot PRs bring vulnerability fixes into normal development | **L3:** treat Medium findings; a vulnerability management view (GitHub code scanning is unavailable: [audit](ci-failure-audit-20261021.md)) |
| Dynamic depth for applications | 1 on merge (0 today) | **L1:** simple scan: passive ZAP baseline of the SPA and API, weekly (`dast-baseline.yml`, #606, on merge) | **L2:** cover the client-side components and more than one role (an authenticated scan against a seeded test user) |
| Dynamic depth for infrastructure | 1 | **L1:** exposed services tested: the ALB refuses direct access without the origin header ([dev CloudFront guide](../deploy/dev-cloudfront-deployment.md)); internal endpoints are not routed on SWE5001. Load tests: baselines in `docs/perf/`, k6 on SWE5001 | **L2:** test network segmentation (NFR-70 is unmet on SWE5001); Checkov for the SWE5006 Terraform stacks (#622) |
| Static depth for applications | 3+ | **L1:** server-side components with known vulnerabilities (Trivy on Maven). **L2:** static analysis of server-side code (SonarCloud, SpotBugs). **L3:** static analysis of client-side code (SonarCloud frontend); client-side components with known vulnerabilities (Trivy on the pnpm lockfile); duplication checked by SonarCloud. **L4, partly:** stylistic analysis (Spotless, Checkstyle, frontend lint); several analysers | CodeQL as a second analyser once code scanning is available; local security checks before commit |
| Static depth for infrastructure | 1+ | **L1:** stored secrets checked (Gitleaks on PRs, dev pushes and weekly full history). **L3, partly:** deployment resources checked for SWE5001 (Checkov, Trivy config, `terraform test`) | **L2:** image lifetime check; the same IaC checks for `infra/terraform` (#622) |

## Summary

Overall the pipeline sits at **level 2**, *adoption of basic security practices*, with three exceptions:

- Static application analysis and build reach level 3.
- Threat modelling (Design) and dynamic application testing are at level 1 or below. Dynamic testing reaches level 1 once #607 merges.
- The course expects only "minimal security considerations" for this project (briefing deck).

**Accepted gaps, with their compensating controls.**

| Gap | Compensating control |
|---|---|
| Image and commit signing | Immutable ECR tags, deployment by digest, protected `dev` with review |
| Licence policy | Inventory without a gate, until the team agrees one (#608) |
| Active and authenticated DAST | Passive baseline plus authentication-surface tests (NFR-68) |
| Separate SWE5006 production environment | The SWE5001 track exercises promotion to `prod` with a release gate |

## Delivery metrics (DSO 16)

DSO 16 pp. 3-4 asks for deployment frequency, deployment lead time, mean time to recover and change success rate, each with a current value and a target. They are measured from Actions history by `.github/scripts/dora-metrics.py` (#613); the snapshot and the targets are in that issue's document.
