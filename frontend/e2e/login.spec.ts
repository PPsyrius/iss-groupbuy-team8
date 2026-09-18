import { existsSync } from "node:fs";

import { expect, test } from "@playwright/test";

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

// `data.items[0].id` of the `pageListings` envelope, read defensively: the response is untyped
// here and the test only needs one id to open.
function firstListingId(body: unknown): string | undefined {
  if (typeof body !== "object" || body === null || !("data" in body)) return undefined;
  const data: unknown = body.data;
  if (typeof data !== "object" || data === null || !("items" in data)) return undefined;
  const items: unknown = data.items;
  if (!Array.isArray(items) || items.length === 0) return undefined;
  const first: unknown = items[0];
  if (typeof first !== "object" || first === null || !("id" in first)) return undefined;
  return typeof first.id === "string" ? first.id : undefined;
}

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
    // Answer the top-level navigation with 204 so the browser stays put: the test proves the
    // target without ever reaching Google.
    let started: string | undefined;
    await page.route(`**${LOGIN_START}*`, async (route) => {
      started = new URL(route.request().url()).pathname;
      await route.fulfill({ status: 204 });
    });
    await page.goto("/login");
    await page.getByRole("button", { name: "Continue with Google" }).click();
    await expect.poll(() => started).toBe(LOGIN_START);
    expect(await page.evaluate(() => sessionStorage.getItem("blocbuy.returnTo"))).toBe("/account");
  });

  test("Start a bloc on a listing opens the sign-in sheet", async ({ page, request }) => {
    // `vite preview` proxies /api to localhost:8589; without a backend there is no listing to open.
    const res = await request.get("/api/v1/products?page=1&size=1");
    test.skip(res.status() !== 200, "no backend behind this target: nothing to gate");
    const id = firstListingId(await res.json());
    test.skip(!id, "backend has no listings");

    // Client-side navigation from the catalog, so the dev target does not need A1 (#99).
    await page.goto("/");
    await page.locator(`a[href="/products/${id}"]`).first().click();
    await expect(page).toHaveURL(new RegExp(`/products/${id}$`));
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

  test("header and /account show the signed-in user", async ({ page }) => {
    await page.goto("/account");
    await expect(page).toHaveURL(/\/account$/);
    const name = (await page.getByTestId("session-user").textContent())?.trim() ?? "";
    expect(name).not.toBe("");
    await expect(page.getByTestId("account-user")).toHaveText(name);
  });

  // Last on purpose: it invalidates the server-side session, so the storage state has to be
  // seeded again before the next @auth run.
  test("Sign out returns the header to Sign in and drops the session cookie", async ({
    page,
    context,
  }) => {
    await page.goto("/");
    await expect(page.getByTestId("session-user")).toBeVisible();
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
    const token = (await context.cookies()).find((c) => c.name === "blocbuy-token");
    expect(token?.value ?? "").toBe("");
  });
});
