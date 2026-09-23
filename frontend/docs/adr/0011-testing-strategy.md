# 0011. Testing strategy

Date: 2026-09-16

## Status

Accepted

## Context

The course grades evidence, so every UI PR must attach a screenshot or a Playwright trace, and
the walking skeleton is only done when a smoke test is green against the dev CloudFront. hey-api
generates MSW handlers from the same contract as the client, so component tests can run against
contract-shaped responses without hand-written mocks. `pnpm check` is the single gate and has to
stay fast; e2e needs a built `dist/` and a browser, which does not belong in it. Most tests are
agent-written, so the setup has to be the same in every file: one server, one setup, colocated
tests.

## Decision

Unit and component tests: Vitest 5.0.1 with `@testing-library/react` 16.3.3, jest-dom 7.0.1 and
happy-dom 20.14.5, `globals: true`, tests colocated as `src/**/*.test.{ts,tsx}`, coverage via v8
excluding generated files. `src/test/setup.ts` starts `setupServer(...createMswHandlers().all())`
from `src/test/msw.ts` with `onUnhandledRequest: 'error'`, resets handlers after each test, and
tests override per case with `server.use(http.get('*/api/v1/...', ...))`. End-to-end: Playwright
1.63.0 on chromium, `e2e/smoke.spec.ts`; locally `pnpm e2e` runs against `vite preview` of `dist/`
(`webServer`, port 4173); `E2E_TARGET=dev E2E_BASE_URL=<dev url>` runs against the distribution
with `retries: 1` and `trace: 'on-first-retry'`. `pnpm e2e` is deliberately outside `pnpm check`.
CI runs `e2e-preview` on pull requests and `e2e-dev` after `deploy-s3-dev` (T1-12). The login
journey (T2-06) uses a hand-seeded Playwright `storageState` and skips when the file is absent.

Alternatives rejected: jsdom (slower than happy-dom); Vitest browser mode (a second runner to
maintain); Cypress (a second e2e tool next to Playwright).

## Consequences

The loop was proven in T1-11 (`828d48b`, 2026-09-16): `Header` renders, the generated MSW handler
answers `me` with 401, the Testing Library query passes; 7 tests across 3 files at `2d040a2`.
`Header.test.tsx` wraps a minimal memory-history `RouterProvider` because TanStack `Link` throws
outside one (plan §12 note 27). `react-perf/jsx-no-new-function-as-prop` is off for test files.
`msw` needs `allowBuilds` in `pnpm-workspace.yaml` (ADR 0013). The brief's
`webServer: cond ? undefined : {...}` fails `tsc` under `exactOptionalPropertyTypes`, so the
config spreads the key in conditionally (note 32).

2026-09-17 (T1-13): the Playwright smoke exists at `frontend/e2e/smoke.spec.ts` with
`frontend/playwright.config.ts` and the `pnpm e2e` script. Locally (`pnpm build` then `pnpm e2e`,
no `E2E_TARGET`) it asserts only that `/` renders and the `BlocBuy` header link is visible against
`vite preview` of `dist/`; the dev-only `describe` (deep link returns 200 from the SPA fallback,
`/api/actuator/health` is `UP`) is skipped. Those dev-target assertions are gated: A1 (issue #99)
is not attached to the dev CloudFront distribution yet, so the deep-link test carries
`test.fixme(true, ...)` pointing at #99, and the dev distribution itself has no `dist/` deployed
until T1-14. `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` was run
and, as expected today, failed (root 403, health 503) — recorded as owed in sprint 2 log.

2026-09-23: `ci-frontend` run #165 passed `e2e-dev` after automated deployment. Independent live probes confirmed root, deep link and asset 200 plus API `UP`; after removing the stale deep-link `fixme`, the dev-target rerun passed 6 tests with only the two unseeded `@auth` tests skipped.
