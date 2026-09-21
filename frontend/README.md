# BlocBuy Frontend

React 19 client-side application for the BlocBuy buyer and admin interfaces. The app is built with Vite and served from S3 through CloudFront; API requests use the same origin under `/api/*`.

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
- `docs/adr/` — architecture decisions
- `docs/design.md` — design tokens
- `docs/design-screens.md` — buyer-screen implementation specification
- `docs/blueprint.md` and `docs/blueprint-review.md` — planning provenance

Deployment procedures are in `../docs/deploy/dev-cloudfront-deployment.md`.
