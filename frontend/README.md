# BlocBuy frontend

The BlocBuy web app: a phone-first single-page app where buyers browse listings, start or join a bloc (a group buy that unlocks the bloc price once it fills), pay through Stripe Checkout, and follow their blocs, orders and receipts. It is React 19 with Vite, TanStack Router and Query, and HeroUI, served as one static build behind CloudFront; every request goes to the same-origin `/api`. Conventions for changing the code are in [`AGENTS.md`](AGENTS.md).

## Prerequisites

- Node.js 24 (`engines.node` is `>=24 <25`; `.nvmrc` says `24`).
- pnpm 12.4.2 through Corepack: run `corepack enable` once and the `packageManager` field in `package.json` selects the version.

## Commands

Run these from `frontend/`.

| Command | What it does |
| --- | --- |
| `pnpm install` | Installs dependencies and generates the API client into `src/api/generated/` (never committed). |
| `pnpm dev` | Starts Vite on http://localhost:5173 and proxies `/api` to `http://localhost:8589` (start the backend with `docker compose up` at the repo root, or use mock mode below). |
| `pnpm check` | The only gate: API client generation, typecheck, oxlint, oxfmt, Vitest and knip. |
| `pnpm build` | Builds the delivery artifact into `dist/`. |
| `pnpm e2e` | Runs the Playwright smoke and login journeys against a `vite preview` of `dist/` (it builds first when `dist/index.html` is missing; rebuild after source changes). |

The signed-in `@auth` journeys run against the dev environment only:

```sh
E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @auth
```

They need a signed-in storage state in `e2e/.auth/dev.json`, seeded once by hand with the Playwright codegen command the skipped test prints. That file holds a live session cookie: it is gitignored and must never be committed.

## Mock mode

Mock mode runs the app with no backend. It works only under `pnpm dev`: the production build swaps the mock and devtools modules for no-ops, so these flags do nothing on a built site. The flags are read once when the page loads, so add them to the first URL you open.

- `?msw=1` starts the in-browser mock API with sample listings, browsing anonymously.
- `&me=1` signs in as the sample buyer Ada Lim, with payments on and a set of blocs, orders and receipts.
- `&devtools=1` mounts the TanStack Query and Router devtools. They are off by default because their trigger covers the BlocBuy wordmark on a phone.

## Demo script

Run `pnpm dev` and open each URL in a phone-width window (390 px). The listing ids are `5b1b9d5c-1a11-4e1e-9a10-00000000000N` and the receipt ids `0192f0a0-0000-7000-9000-00000000000N`.

1. Catalog: http://localhost:5173/?msw=1, then tap the Groceries chip to filter.
2. Signed-out gate: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000001?msw=1, then Start a bloc opens the sign-in sheet.
3. Closing soon: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000002?msw=1 (the durian bloc, under an hour left). No bloc: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000004?msw=1 (a listing with no bloc price).
4. Sign-in page: http://localhost:5173/login?msw=1&returnTo=/products/5b1b9d5c-1a11-4e1e-9a10-000000000001
5. Start a bloc and pay: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000001?msw=1&me=1, then Start a bloc shows the quote sheet and Continue to payment opens receipt `…001`. It reads Waiting for about 4 seconds, then Paid, as if Stripe had confirmed the payment.
6. A paid receipt: http://localhost:5173/payments/0192f0a0-0000-7000-9000-000000000005?msw=1&me=1
7. My blocs: http://localhost:5173/account?msw=1&me=1 (Active), then http://localhost:5173/account?msw=1&me=1&blocs=completed and http://localhost:5173/account?msw=1&me=1&blocs=refunded. The orders below the blocs match them: open blocs are waiting, the filled one is confirmed, the closed ones are cancelled.
8. Refunds: http://localhost:5173/payments/0192f0a0-0000-7000-9000-000000000009?msw=1&me=1 (refund pending) and http://localhost:5173/payments/0192f0a0-0000-7000-9000-000000000008?msw=1&me=1 (refunded).
9. Not found: http://localhost:5173/does-not-exist?msw=1
10. A refused join: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000002?msw=1&me=1, then Join answers "This bloc filled up before you joined. Nothing has been charged." Only this durian bloc refuses; Join on any other listing goes through the same mock checkout to a Waiting, then Paid receipt.
11. Share a bloc: http://localhost:5173/products/5b1b9d5c-1a11-4e1e-9a10-000000000001?msw=1&me=1, then Join, wait for Paid and tap "See the listing": the share block shows your invite link. Paste the copied link into a new tab with `&msw=1` added to open the invite landing.

Demo notes:

- Browsing anonymously logs two expected `401 (Unauthorized)` console errors per page load: the session check answering "not signed in".
- The refused join in step 10 logs one expected `409 (Conflict)`: that refusal is the mocked answer.
- Without a backend and without `?msw=1`, the API calls are retried for about 3 seconds before the error states appear.
- The mock keeps its state until the page reloads; reload the URL to start the script over.

## Documentation

- [`docs/plan.md`](docs/plan.md): the frontend plan, task list and decision notes.
- [`docs/design-screens.md`](docs/design-screens.md): the screen-by-screen design reference.
- [`docs/adr/`](docs/adr/): the frontend architecture decision records.

## Assets

The Inter typeface is self-hosted through `@fontsource/inter` under the SIL Open Font License 1.1. The favicon, app icons and the BlocBuy wordmark are made by the project team.
