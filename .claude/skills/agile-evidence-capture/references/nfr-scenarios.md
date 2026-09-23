# Non-functional requirements as scenarios

## Format

An actionable NFR names the source, stimulus, environment, response, and measure. Put the full scenario in the issue and this summary in the register:

> when `<stimulus>`, the system `<response>`, measured by `<measure>`.

## Recognition

Treat work as an NFR when it concerns:

- security: auth, cookies, CSRF, CSP, HSTS, secrets, tokens, OIDC, IAM, CVEs, scanning, rate limits, validation
- performance or cost: latency, N+1 queries, indexes, caches, timeouts, pools, build time, retention, instance size, egress
- availability or recoverability: health checks, readiness, retries, rollback, idempotency, locks
- observability: logs, trace IDs, metrics, CloudWatch, Actuator, alerts, runbooks
- maintainability or DevSecOps: architecture gates, static analysis, coverage, formatting, pinning, Dependabot
- related paths: `.github/`, `application*.yml`, deployment, infrastructure, Docker, policy JSON, build-tool configuration

If the attribute matters but has no agreed measure, add a register row with status `target undefined` instead of inventing a target.

## Examples

### Security: session cookie

- Source: an injected script or cross-site page
- Stimulus: attempts to read or replay the session cookie
- Environment: dev behind TLS; local over HTTP
- Response: script cannot read the cookie and the browser does not send it cross-site
- Measure: dev `Set-Cookie` carries `HttpOnly; Secure; SameSite=Lax`; local documents why `Secure` is off

### Performance: CI duration

- Source: a developer opening a PR
- Stimulus: push to the PR branch
- Environment: GitHub-hosted runner with a warm cache
- Response: required build and image jobs complete
- Measure: recorded duration stays below the agreed threshold and the PR includes before/after evidence

### Observability: request tracing

- Source: an operator investigating a failed request
- Stimulus: an API error
- Environment: dev with the CloudWatch agent
- Response: the response carries a trace ID and matching logs are searchable
- Measure: `aws logs filter-log-events --filter-pattern "<traceId>"` returns the request within retention

### Recoverability: deployment rollback

- Source: the deployment workflow
- Stimulus: health does not become `UP` before timeout
- Environment: dev deployment
- Response: the previous image digest is restored
- Measure: health returns `UP` on the prior digest and the workflow records the rollback
