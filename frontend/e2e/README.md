# Playwright journeys

`pnpm e2e` runs against `vite preview` of `dist/` (rebuilt when missing; see `playwright.config.ts`). `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` runs against the dev distribution. The current specs cover smoke, login, the stubbed buyer journey and the admin console (ADR 0011).

## Shared API stubs (`fixtures/api.ts`)

The production build drops `?msw=1`, so a spec that needs data imports `test` and `expect` from `./fixtures/api` and asks for the `api` fixture. It answers every `/api/v1` call from a per-test `api.state` (signed-in `me`, payments switch, listings, quotes, My blocs, orders, intents, checkout sessions with a fixed UUID payment id, receipt reads), records each call for `api.calls()`, and takes per-test handlers through `api.override()`. Unserved paths answer 501 `E2E_501_UNSTUBBED`. Tests that do not ask for `api` reach the real backend, which keeps the `@auth` half honest on dev.

## Buyer journey (`buyer-journey.spec.ts`)

A signed-in buyer, all on stubs: catalog → listing → quote sheet → Continue to payment → Stripe (a routed 302 back to `/payments/return`) → receipt Waiting then Paid → My blocs. It asserts that START carries the sheet's quote id (US26). It runs on every target and proves the frontend's part only.

## Admin console (`admin.spec.ts`)

Three journeys run on every target on the shared `api` stubs: an anonymous visitor at `/admin` goes to `/login?returnTo=%2Fadmin`; a signed-in buyer (`me` without the `admin` role) gets the 403 card without the storefront header; and a stubbed admin creates a draft, publishes it (the dialog names price, currency and stock), then meets the conflict banner after another admin's save and reloads. The admin endpoints answer from a small versioned store inside the spec; a request it does not serve answers 501 and fails the test.

The `@admin` half needs a real admin session on dev. The admin role must be granted in the dev DB (#68: grants do not travel with git); none is granted today (admin plan §6 Q11), so it skips. Once a Google account holds the role:

1. From `frontend/`, seed its storage state:

   ```bash
   pnpm exec playwright codegen --save-storage=e2e/.auth/admin.json https://d1b3np88z657cp.cloudfront.net/login
   ```

2. Run it; it loads the listings, creates one draft and deletes it again:

   ```bash
   E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @admin
   ```

## Login journey (`login.spec.ts`)

The anonymous half stubs `me` and the listing through the shared `api` fixture, so it runs against `vite preview`. It checks the `/account` guard, intercepts Google navigation without following it and opens the sign-in sheet from a listing.

The `@auth` half needs a storage state with a real session cookie. Seed it once by hand:

1. Confirm the Google console still lists `https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback` as an authorised redirect URI (B5, #98). A user-seeded signed-in dev run last passed on 2026-09-29; reruns need a fresh session.
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
