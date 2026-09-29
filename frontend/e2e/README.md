# Playwright journeys

`pnpm e2e` runs against `vite preview` of `dist/` (rebuilt when missing; see `playwright.config.ts`). `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` runs against the dev distribution. The current specs cover smoke, login and the stubbed buyer journey (ADR 0011).

## Shared API stubs (`fixtures/api.ts`)

The production build drops `?msw=1`, so a spec that needs data imports `test` and `expect` from `./fixtures/api` and asks for the `api` fixture. It answers every `/api/v1` call from a per-test `api.state` (signed-in `me`, payments switch, listings, quotes, My blocs, orders, intents, checkout sessions with a fixed UUID payment id, receipt reads), records each call for `api.calls()`, and takes per-test handlers through `api.override()`. Unserved paths answer 501 `E2E_501_UNSTUBBED`. Tests that do not ask for `api` reach the real backend, which keeps the `@auth` half honest on dev.

## Buyer journey (`buyer-journey.spec.ts`)

A signed-in buyer, all on stubs: catalog → listing → quote sheet → Continue to payment → Stripe (a routed 302 back to `/payments/return`) → receipt Waiting then Paid → My blocs. It asserts that START carries the sheet's quote id (US26). It runs on every target and proves the frontend's part only.

## Login journey (`login.spec.ts`)

The anonymous half stubs `me` and the listing through the shared `api` fixture, so it runs against `vite preview`. It checks the `/account` guard, intercepts Google navigation without following it and opens the sign-in sheet from a listing.

The `@auth` half needs a storage state with a real session cookie. Seed it once by hand:

1. Confirm the Google console still lists `https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback` as an authorised redirect URI (B5, #98). A user-seeded signed-in dev run passed on 2026-09-24; reruns need a fresh session.
2. From `frontend/`:

   ```bash
   pnpm exec playwright codegen --save-storage=e2e/.auth/dev.json https://d1b3np88z657cp.cloudfront.net/login
   ```

    Finish the Google login, wait for the header to show your name and close the browser; Playwright saves the cookie to `e2e/.auth/dev.json`.
3. Run the signed-in half:

   ```bash
   E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @auth
   ```

`e2e/.auth/*.json` is gitignored; never commit or paste a live session file. The last `@auth` test signs out, so reseed before another signed-in run. Without the file, those tests skip and report the codegen command.
