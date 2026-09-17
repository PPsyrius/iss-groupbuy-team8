# Non-functional requirements as scenarios

## Why scenarios

"The system must be secure" cannot be ticked. A quality-attribute scenario can: it names the
stimulus, the expected response and the measure, so the acceptance criterion is a command someone
can run. This is the same six-part form taught in SWE5006 (source, stimulus, environment, artifact,
response, response measure); the issue template collapses artifact into response to keep it short.

Write one scenario per NFR issue and copy its one-line summary into the register row:

> when `<stimulus>`, the system `<response>`, measured by `<measure>`.

## Recognising NFR work

The task is an NFR when the user's words or the touched files include any of:

- security, auth, cookie, CSRF, CSP, HSTS, secret, token, OIDC, IAM, least privilege, CVE, Trivy,
  Gitleaks, CodeQL, SARIF, rate limit, lockout, validation hardening
- performance, latency, N+1, index, cache, page size, timeout, pool size, build time, CI time
- availability, rollback, health check, readiness, retry, circuit breaker, idempotency, lock
- observability, log, traceId, CloudWatch, metrics, Actuator, alert, runbook
- cost, retention, instance size, egress, free tier
- maintainability, ArchUnit, Checkstyle, SpotBugs, coverage, Spotless, pinning, Dependabot
- paths: `.github/`, `application*.yml`, `deployment/`, `infra/`, `Dockerfile*`, `docker-compose*`,
  CloudFront or S3 policy JSON, `pom.xml` plugin config, `vite.config.*`, `lefthook.yml`

If in doubt, it is an NFR. The cost of a spurious register row is one line.

## Worked examples from this project

These are real changes that merged without an issue. Each shows the scenario that should have
existed. Use them as the pattern for new ones.

### Security: session cookie attributes (PR #84)

- Source: a script injected into the SPA, or a cross-site page
- Stimulus: attempts to read or replay the session cookie
- Environment: dev behind CloudFront TLS; local over plain HTTP
- Response: the browser refuses to expose the cookie to script and does not send it cross-site
- Measure: `Set-Cookie` on `/api/v1/auth/*` carries `HttpOnly; Secure; SameSite=Lax` on dev;
  `Secure` deliberately off in the `local` profile with a comment saying why

### Performance of the pipeline: CI build time (PR #75)

- Source: a developer opening a PR
- Stimulus: push to a PR branch
- Environment: GitHub-hosted runner, warm cache
- Response: `build-quality` and `docker` jobs complete
- Measure: docker job under two minutes (was about five); recorded in the PR's before/after table

### Observability: request tracing (PR #45, `backend-log-observability.md`)

- Source: an operator investigating a failed request
- Stimulus: an API error response
- Environment: dev EC2 with CloudWatch agent
- Response: the response carries `X-Trace-Id` and the log line for that request is searchable
- Measure: `aws logs filter-log-events --filter-pattern "<traceId>"` returns the matching lines
  within the fourteen-day retention window

### Recoverability: automatic rollback (`ci-backend.yml`, `rollback-backend.yml`)

- Source: the deploy workflow
- Stimulus: `/actuator/health` not `UP` within the timeout after a new image starts
- Environment: dev EC2 via SSM
- Response: the previous image digest is restored
- Measure: health returns `UP` on the previous digest; the workflow run shows the rollback step

### Cost: log retention (`backend-log-observability.md`)

- Source: continuous log shipping
- Stimulus: log volume accumulates
- Environment: dev, INFO level
- Response: CloudWatch expires log events
- Measure: retention set to fourteen days on `/blocbuy/backend/dev`; monthly cost stays inside the
  free tier

### Maintainability: architecture layering (`ArchitectureTest.java`)

- Source: any commit
- Stimulus: a class imports across a forbidden COLA layer boundary
- Environment: `./mvnw verify` locally and in CI
- Response: the build fails
- Measure: all six ArchUnit rules green; zero exclusions without a written reason

### Robustness: request validation (#56, #57)

- Source: any client
- Stimulus: `GET /api/v1/products?page=0`
- Environment: any profile
- Response: `400` with a problem body, not `500`
- Measure: acceptance test asserts the status and the error code

## Attributes with no target yet

Runtime performance (latency, throughput) and usability have no measures anywhere in the repo.
When a task touches them, add a register row with status `target undefined` rather than inventing
a number. An honest "we have not set a latency target" is a better report sentence than a number
nobody measured.
