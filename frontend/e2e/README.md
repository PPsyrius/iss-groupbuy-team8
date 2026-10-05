# Playwright journeys

`pnpm e2e` runs against `vite preview` of `dist/` (rebuilt when missing; see `playwright.config.ts`). `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e` runs against the dev distribution. The current specs cover smoke, login, the stubbed buyer journey and the admin console (ADR 0011).

## Shared API stubs (`fixtures/api.ts`)

The production build drops `?msw=1`, so a spec that needs data imports `test` and `expect` from `./fixtures/api` and asks for the `api` fixture. It answers every `/api/v1` call from a per-test `api.state` (signed-in `me`, payments switch, listings, quotes, My blocs, orders, intents, checkout sessions with a fixed UUID payment id, receipt reads), records each call for `api.calls()`, and takes per-test handlers through `api.override()`. Unserved paths answer 501 `E2E_501_UNSTUBBED`. Tests that do not ask for `api` reach the real backend, which keeps the `@auth` half honest on dev.

### Against dev (`E2E_TARGET=dev`)

The specs that take the `api` fixture (login's anonymous half, buyer-journey, join-refusal, quote, admin) stay on stubs against dev too: the catch-all route matches the `/api/v1/` path on any host and is registered before the first `goto`, so no request reaches the real backend, and an unserved path answers 501 instead of falling through. The buyer journey also routes `https://checkout.stripe.com/**`, so no Checkout Session is opened. In the `e2e-dev` job they make no writes on dev; only `smoke.spec.ts` and the `@auth`/`@admin` cases (which skip without a storage state) talk to the real backend.

## Buyer journey (`buyer-journey.spec.ts`)

A signed-in buyer, all on stubs: catalog → listing → quote sheet → Continue to payment → Stripe (a routed 302 back to `/payments/return`) → receipt Waiting then Paid → My blocs. It asserts that START carries the sheet's quote id (US26). It runs on every target and proves the frontend's part only.

## Admin console (`admin.spec.ts`)

Three journeys run on every target on the shared `api` stubs: an anonymous visitor at `/admin` goes to `/login?returnTo=%2Fadmin`; a signed-in buyer (`me` without the `admin` role) gets the 403 card without the storefront header; and a stubbed admin creates a draft, publishes it (the dialog names price, currency and stock), then meets the conflict banner after another admin's save and reloads. The admin endpoints answer from a small versioned store inside the spec; a request it does not serve answers 501 and fails the test.

The `@admin` half needs a real admin session, on the local full stack (`fullstack-localhost/`, admin plan FU-02) or on dev. It loads the listings, creates one draft and deletes it again. It skips unless `E2E_TARGET` and `E2E_BASE_URL` name the same target and that target's storage state exists: `e2e/.auth/admin-local.json` for local, `e2e/.auth/admin.json` for dev. Role grants are data, not migrations (#68), so each database needs its own.

Local run (no AWS access needed):

1. Put the Google OAuth credentials in `fullstack-localhost/.env` (see its `.env.example`); the OAuth client must list `http://localhost:8080/api/v1/auth/oauth2/google/callback` as an authorised redirect URI. From `frontend/`, start the stack and leave it running: `pnpm stack:prod`.
2. Sign in once at <http://localhost:8080/login> with the Google account that becomes admin, so its account row exists.
3. From the repository root, grant the role in the local database (`Blocbuy`, schema `domain`, user `blocbuy` unless `DB_USERNAME` overrides it in `.env`); the script prints `granted = 1` on the first run and `already_admin = 1` on a rerun:

   ```bash
   read -r -p 'admin email: ' ADMIN_EMAIL
   docker compose -f fullstack-localhost/compose.yaml exec -T postgres \
     psql -U blocbuy -d Blocbuy -v admin_email="$ADMIN_EMAIL" -f - < scripts/grant-dev-admin.sql
   unset ADMIN_EMAIL
   ```

4. From `frontend/`, seed the storage state; finish the Google login and close the browser:

   ```bash
   pnpm exec playwright codegen --save-storage=e2e/.auth/admin-local.json http://localhost:8080/login
   ```

5. Run it:

   ```bash
   E2E_TARGET=local E2E_BASE_URL=http://localhost:8080 pnpm e2e --grep @admin
   ```

Dev run: the admin role must be granted in the dev DB through the SSM tunnel (`docs/deploy/backend-dev-api-agent-verification.md` §1); none is granted today (admin plan §6 Q11). Then seed `e2e/.auth/admin.json` with `pnpm exec playwright codegen --save-storage=e2e/.auth/admin.json https://d1b3np88z657cp.cloudfront.net/login` and run `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @admin`.

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
