import { existsSync } from "node:fs";

import { expect, listing, test } from "./fixtures/api";

// The login journey (plan T2-06, ADR 0011). Two halves:
// - anonymous: runs on every target, including `vite preview` in CI (no backend behind /api);
// - @auth: needs a Playwright storage state seeded once by hand after a real Google login
//   (`README.md` in this folder) and the dev CloudFront, because that is where the cookie is valid.
//   It skips, with the codegen command as the reason, when the file is absent.
const isDev = process.env.E2E_TARGET === "dev";
const AUTH_STATE = "e2e/.auth/dev.json";
const hasAuthState = existsSync(AUTH_STATE);
const CODEGEN =
  "pnpm exec playwright codegen --save-storage=e2e/.auth/dev.json https://d1b3np88z657cp.cloudfront.net/login";
// Login is a full-page navigation to the backend (ADR 0002, `src/features/session/return-to.ts`).
const LOGIN_START = "/api/v1/auth/oauth2/google";
const LOGOUT = "/api/v1/auth/logout";
const TOKEN_COOKIE = "blocbuy-token";

// A `Set-Cookie` that expires the cookie: `Max-Age` of zero or less, or an `Expires` in the
// past. A name or value check alone would also pass for a live cookie with an empty value
// (CodeRabbit r4052574005).
function expiresCookie(setCookie: string): boolean {
  const maxAge = /;\s*max-age=(-?\d+)/iu.exec(setCookie)?.[1];
  if (maxAge !== undefined) return Number(maxAge) <= 0;
  const expires = /;\s*expires=([^;]+)/iu.exec(setCookie)?.[1];
  return expires !== undefined && Date.parse(expires) < Date.now();
}
// One listing with an open bloc, served by the shared `api` stubs (`fixtures/api.ts`) so the
// gated-sheet journey runs on every target, `vite preview` included, instead of skipping when no
// backend answers /api (CodeRabbit r4046597269). The gate is frontend behaviour; the backend only
// has to say 401, which is the stubs' default `me`.
const LISTING = listing();
const LISTING_ID = LISTING.id ?? "";

test.describe("anonymous visitor", () => {
  test("/account sends an anonymous visitor to /login with the path they wanted", async ({
    page,
  }) => {
    await page.goto("/account");
    await expect(page).toHaveURL(/\/login\?returnTo=%2Faccount$/);
    await expect(page.getByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
    // The header hides its own Sign in control on the sign-in screen.
    await expect(page.getByRole("link", { name: "Sign in" })).toHaveCount(0);
  });

  test("Continue with Google starts the backend OAuth flow and parks the return path", async ({
    page,
  }) => {
    // Answer the top-level navigation with a same-origin stub page: the test proves the target
    // without ever reaching Google, and `sessionStorage` (per tab and origin) survives the hop.
    // A 204 used to leave the browser put, but from Chromium 156 (Playwright 1.64) it leaves the
    // page mid-navigation and `page.evaluate` never resolves.
    let started: string | undefined;
    await page.route(`**${LOGIN_START}*`, async (route) => {
      started = new URL(route.request().url()).pathname;
      await route.fulfill({
        status: 200,
        contentType: "text/html",
        body: "<!doctype html><title>OAuth stub</title>",
      });
    });
    await page.goto("/login");
    await page.getByRole("button", { name: "Continue with Google" }).click();
    await expect(page).toHaveURL(new RegExp(`${LOGIN_START}$`));
    expect(started).toBe(LOGIN_START);
    expect(await page.evaluate(() => sessionStorage.getItem("blocbuy.returnTo"))).toBe("/account");
  });

  test("Start a bloc on a listing opens the sign-in sheet", async ({ page, api }) => {
    api.state.listings.push(LISTING);

    // Client-side navigation from the catalog, so the dev target does not need A1 (#99).
    await page.goto("/");
    await page.locator(`a[href="/products/${LISTING_ID}"]`).first().click();
    await expect(page).toHaveURL(new RegExp(`/products/${LISTING_ID}$`));
    await page
      .getByRole("button", { name: /^Start a bloc/ })
      .filter({ visible: true })
      .first()
      .click();
    const sheet = page.getByRole("dialog", { name: "Sign in to hold your seat" });
    await expect(sheet).toBeVisible();
    await expect(sheet.getByRole("button", { name: "Continue with Google" })).toBeVisible();
    await sheet.getByRole("button", { name: "Not now" }).click();
    await expect(sheet).toBeHidden();
  });
});

test.describe("signed-in buyer @auth", () => {
  test.skip(
    !hasAuthState,
    `no ${AUTH_STATE}: seed it once by hand with \`${CODEGEN}\` after the B5 (#98) console check`,
  );
  test.skip(
    !isDev,
    "the storage state carries the dev CloudFront cookie; run with E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net",
  );
  test.use(hasAuthState ? { storageState: AUTH_STATE } : {});

  test("/account opens under the header that names the signed-in user", async ({ page }) => {
    await page.goto("/account");
    await expect(page).toHaveURL(/\/account$/);
    await expect(page.getByRole("heading", { level: 1, name: "My blocs" })).toBeVisible();
    const name = (await page.getByTestId("session-user").textContent())?.trim() ?? "";
    expect(name).not.toBe("");
  });

  // Last on purpose: it invalidates the server-side session, so the storage state has to be
  // seeded again before the next @auth run.
  test("Sign out returns the header to Sign in and drops the session cookie", async ({
    page,
    context,
  }) => {
    await page.goto("/");
    await expect(page.getByTestId("session-user")).toBeVisible();
    const [logout] = await Promise.all([
      page.waitForResponse((r) => new URL(r.url()).pathname === LOGOUT),
      page.getByRole("button", { name: "Sign out" }).click(),
    ]);
    expect(logout.ok()).toBe(true);
    const tokenSetCookie = (await logout.headersArray())
      .filter((h) => h.name.toLowerCase() === "set-cookie")
      .map((h) => h.value)
      .find((v) => v.startsWith(`${TOKEN_COOKIE}=`));
    expect(tokenSetCookie, `logout answers with a Set-Cookie for ${TOKEN_COOKIE}`).toBeDefined();
    expect(expiresCookie(tokenSetCookie ?? "")).toBe(true);
    await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
    expect((await context.cookies()).some((c) => c.name === TOKEN_COOKIE)).toBe(false);
  });
});
