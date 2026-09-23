# 0005. SPA fallback via CloudFront Function

Date: 2026-09-16

## Status

Accepted

## Context

The router uses history-mode paths (`/products/<uuid>`, `/account`, `/auth/callback`). The S3
origin is a private bucket read through OAC, so any key that does not exist returns the S3 403,
which is what a deep link or a browser refresh hits. The day-1 probe (T1-01, 2026-09-16) confirmed
it: `GET /products/probe` on dev returned `403 application/xml`. The original runbook §7.3
described the fallback as optional and it was never attached. The same distribution serves
`/api/*` from the ALB, and those responses must keep their real status codes and bodies.

## Decision

A viewer-request CloudFront Function (`cloudfront-js-2.0`), `blocbuy-dev-spa-fallback`, with its
source versioned at `deployment/frontend/cloudfront/spa-fallback.js`, is attached to the DEFAULT
behavior only. Paths that end in `/` or contain no `.` are rewritten to `/index.html`; paths with
a file extension (hashed assets, `favicon.ico`, `/config.json`) pass through unchanged. The `/api/*`
behavior has no function association. No distribution-wide custom error responses are configured.
Runbook §14 (mandatory, supersedes §7.3) carries the create/attach commands and three probes:
a deep link returns `200 text/html`, a missing `/assets/*.js` still returns 403 or 404, and
`/api/v1/products?page=1` still returns the backend's 200.

Alternatives rejected: custom error responses mapping 403/404 to `index.html` (masks real
`/api/*` errors and returns the wrong status codes); the S3 website endpoint (public bucket, no
OAC); Lambda@Edge (cost and latency for a two-line rewrite).

## Consequences

Attaching the function is blocker A1 (#99), executed by the AWS account holder; T1-03 (2026-09-16)
verified only `node --check` on the source, no AWS command was run. Until A1 executes, deep links
on dev return 403 and the Playwright smoke's deep-link test carries
`test.fixme(true, 'A1 not attached: …/issues/99')` (T1-13, 2026-09-17); the test stays in the
file and is un-fixme'd when the T1-01 re-probe shows 200. A deep link to an unknown product is
served as `200` with `index.html` and the SPA renders its own not-found view (T2-02
`notFoundComponent`); a CSR app cannot return a real 404 there, and that is accepted. The function
rewrites `uri.endsWith('/')` to `/index.html` rather than appending `index.html`, because the
build has a single `index.html` at the root. T2-07 imports the function into
`infra/terraform/frontend-stack` (ADR 0007). Deep-link coverage on dev is the A1 evidence line in
the `e2e-dev` job once T1-14 has deployed a build.

2026-09-23: the live deep-link and asset probes return 200, so the temporary Playwright `fixme` was removed and future `e2e-dev` runs enforce the fallback behavior.
