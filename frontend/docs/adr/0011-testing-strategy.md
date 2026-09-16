# 0011. Testing strategy

Date: 2026-09-16

## Status

Proposed

## Context

## Decision

## Consequences

2026-09-17 (T1-13): the Playwright smoke exists at `frontend/e2e/smoke.spec.ts` with
`frontend/playwright.config.ts` and the `pnpm e2e` script. Locally (`pnpm build` then `pnpm e2e`,
no `E2E_TARGET`) it asserts only that `/` renders and the `BlocBuy` header link is visible against
`vite preview` of `dist/`; the dev-only `describe` (deep link returns 200 from the SPA fallback,
`/api/actuator/health` is `UP`) is skipped. Those dev-target assertions are gated: A1 (issue #99)
is not attached to the dev CloudFront distribution yet, so the deep-link test carries
`test.fixme(true, ...)` pointing at #99, and the dev distribution itself has no `dist/` deployed
until T1-14. `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` was run
and, as expected today, failed (root 403, health 503) — recorded as owed in sprint 2 log.
