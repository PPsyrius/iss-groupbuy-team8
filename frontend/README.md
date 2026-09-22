# BlocBuy Frontend

React 19 client-side application for BlocBuy buyers. The admin console is planned separately in `docs/admin-plan.md`. The app is built with Vite and served from S3 through CloudFront; API requests use the same origin under `/api/*`.

## Requirements

- Node.js 24
- pnpm 12.4.2 via Corepack
- Backend on `http://localhost:8589` for local API access

Node and pnpm versions are pinned by `.nvmrc`, `.mise.toml`, `package.json`, and `.npmrc`.

## Setup

From `frontend/`:

```bash
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

Vite serves the frontend locally and proxies `/api` to `http://localhost:8589`. The backend can be started from the repository root with `docker compose up`.

The OpenAPI client is generated from `../contracts/openapi.json` during installation. Generated files under `src/api/generated/` are not committed.

## Mock mode and demo

For a backend-free local demo, run `pnpm dev` and open `http://localhost:5173/?msw=1` to browse sample listings. Add `&me=1` to sign in as the sample buyer Ada Lim and see blocs, orders and receipts; `&devtools=1` enables TanStack devtools. These flags are read on page load and work only in the dev server, not in production builds. Mock state resets on reload.

The listing IDs are `5b1b9d5c-1a11-4e1e-9a10-00000000000N` and the receipt IDs are `0192f0a0-0000-7000-9000-00000000000N`. At a 390px viewport, try:

1. Browse `/?msw=1`, filter with the Groceries chip, and open `/products/5b1b9d5c-1a11-4e1e-9a10-000000000001?msw=1`; Start a bloc opens the sign-in sheet when signed out.
2. Reopen that listing with `?msw=1&me=1`; Start a bloc opens the quote sheet, and Continue to payment opens a receipt that changes from Waiting to Paid after about four seconds.
3. Open `/account?msw=1&me=1` to see Active blocs and orders; try `&blocs=completed` or `&blocs=refunded`, then view `/payments/0192f0a0-0000-7000-9000-000000000009?msw=1&me=1` for a pending refund.
4. On the first listing, Join a bloc, wait for Paid, and use See the listing to find the share link; open the copied link in a new tab with `&msw=1` appended to see the invite landing.
5. On `/products/5b1b9d5c-1a11-4e1e-9a10-000000000002?msw=1&me=1`, Join returns a mocked 409 refusal: nothing is charged. A 401 on anonymous session checks is expected in the browser console.

## Quality gate

```bash
pnpm check
```

The gate regenerates the API client, type-checks, lints, verifies formatting, runs Vitest, and checks unused code with Knip.

End-to-end tests are separate:

```bash
pnpm build
pnpm e2e
```

Authenticated dev tests use a local Playwright storage state under `e2e/.auth/`; it must not be committed.

## Main stack

- React 19 + TypeScript 7 + Vite 8
- TanStack Router, Query, and Form
- HeroUI 3 + Tailwind CSS 4
- hey-api generated OpenAPI client
- Valibot
- Vitest + Testing Library + MSW
- Playwright
- oxlint, oxfmt, Knip, and Lefthook

Exact versions are pinned in `package.json`.

## Architecture rules

- Pure CSR; no SSR runtime.
- Browser API requests use relative `/api/*` paths.
- Do not add `VITE_*` configuration or application `import.meta.env` dependencies.
- Do not edit `src/api/generated/**` by hand.
- API response types come from the generated client.
- `useSession()` is the frontend authentication source of truth.
- API interceptors may update session state but do not redirect; route guards own redirects.
- React Compiler is enabled; avoid manual `useMemo`, `useCallback`, and `React.memo`.

See `AGENTS.md` for the complete implementation conventions.

## Documentation

- `docs/plan.md` — implementation plan and current task status
- `docs/backlog.md` — newer buyer tasks and follow-ups
- `docs/admin-plan.md` — deferred admin-console scope and backend asks
- `docs/adr/` — architecture decisions
- `docs/design.md` — design tokens
- `docs/design-screens.md` — buyer-screen implementation specification
- `docs/product.md` and `docs/design-brief.md` — product and original design inputs
- `docs/blueprint.md` and `docs/blueprint-review.md` — planning provenance

Deployment procedures are in `../docs/deploy/dev-cloudfront-deployment.md`.
