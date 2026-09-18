# Playwright journeys

`pnpm e2e` runs every spec against `vite preview` of `dist/` (rebuilt when missing, see
`playwright.config.ts`); `E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e`
runs against the dev distribution. Only the smoke and login journeys live here (ADR 0011).

## Login journey (`login.spec.ts`)

The anonymous half needs nothing: the guard sends `/account` to `/login?returnTo=%2Faccount`, the
Google button navigates to `/api/v1/auth/oauth2/google` (intercepted, never followed), and, when
a backend answers `/api/v1/products`, Start a bloc on a listing opens the sign-in sheet.

The `@auth` half needs a storage state with a real session cookie. Seed it once by hand:

1. Precondition B5 (#98): the Google console lists
   `https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google/callback` as an authorised
   redirect URI, otherwise Google refuses the callback.
2. From `frontend/`:

   ```bash
   pnpm exec playwright codegen --save-storage=e2e/.auth/dev.json https://d1b3np88z657cp.cloudfront.net/login
   ```

   Press Continue with Google in the opened browser, finish the Google login, wait for the
   header to show your name, then close the browser. Playwright writes the cookies to
   `e2e/.auth/dev.json`.
3. Run the signed-in half:

   ```bash
   E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @auth
   ```

`e2e/.auth/*.json` is gitignored (repo `.gitignore`); only `.gitkeep` is committed. Never commit
the file or paste its contents anywhere: it is a live session. The last `@auth` test signs out,
which invalidates that session, so repeat step 2 before the next run. Without the file the
`@auth` tests report as skipped with the codegen command as the reason.
