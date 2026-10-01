import { existsSync } from "node:fs";

import type { Route } from "@playwright/test";

import type { AdminListingResponse, ListingContent } from "@/api/generated/types.gen";

import { expect, test } from "./fixtures/api";

// The admin console journeys (admin plan A2-03, ADR 0011). Three run on every target, `vite
// preview` in CI included, on the shared `api` stubs: anonymous → /login, a buyer → the 403 card,
// and an admin (stubbed) through create → publish → a stale save → Reload. The jsdom route tests
// pin each screen against the MSW admin mock; these prove the same journey through the built app.
// The fourth, @admin, needs a real admin session on dev and skips until one is seeded (§6 Q11).
const isDev = process.env.E2E_TARGET === "dev";
const ADMIN_STATE = "e2e/.auth/admin.json";
const hasAdminState = existsSync(ADMIN_STATE);
const CODEGEN =
  "pnpm exec playwright codegen --save-storage=e2e/.auth/admin.json https://d1b3np88z657cp.cloudfront.net/login";

const OK = "COMMON_200_000";
const ACCESS_DENIED = "You don’t have access to the admin console";
const ADA = { userId: "0e2e0000-0000-4000-8000-0000000000ad", nickname: "Ada" };

const envelope = (data?: unknown) => ({ code: OK, message: "ok", data });

function fail(route: Route, status: number, code: string, message: string, errors?: unknown) {
  return route.fulfill({ status, json: { code, message, errors } });
}

// One listing in the in-spec store, as the backend holds it.
interface Row {
  id: string;
  productNo: string;
  listing: ListingContent;
  status: "draft" | "on_sale" | "off_sale";
  version: number;
}

const view = (row: Row): AdminListingResponse => ({
  ...row,
  pricingMode: "dual",
  soldCount: 0,
  teamTerms: { targetSize: null, lifetimeSeconds: null, configured: false },
  latestPolicy: null,
});

const detail = (row: Row): AdminListingResponse => ({
  ...view(row),
  references: { deals: 0, quotes: 0, policies: 0 },
  deletable: row.status === "draft",
});

function bodyOf<T>(route: Route): Partial<T> {
  // oxlint-disable-next-line typescript/no-unsafe-type-assertion
  return (route.request().postDataJSON() ?? {}) as Partial<T>;
}

// The admin catalog an admin session talks to, held in the spec: listing writes are versioned
// (expectedVersion, 412 CATALOG_412_002 with `currentVersion` when stale, #286), and
// `bumpOnNextWrite` plays another admin saving first.
function adminStore() {
  const rows: Row[] = [];
  let armed = false;
  const find = (id: string) => rows.find((r) => r.id === id);
  // Versioned write: refuse a stale one with the version the server holds now.
  const stale = async (route: Route, row: Row, expected: unknown) => {
    if (armed) {
      armed = false;
      row.version += 1;
    }
    if (expected === row.version) return false;
    await fail(route, 412, "CATALOG_412_002", "Listing version precondition failed", {
      currentVersion: row.version,
    });
    return true;
  };
  const routes: [string, RegExp, (route: Route, id: string) => Promise<void>][] = [
    [
      "GET",
      /^admin\/products$/u,
      (route) =>
        route.fulfill({
          json: envelope({ items: rows.map(view), total: rows.length, page: 1, size: 20 }),
        }),
    ],
    [
      "POST",
      /^admin\/products$/u,
      async (route) => {
        const { listing } = bodyOf<{ listing: ListingContent }>(route);
        const n = rows.length + 1;
        const row: Row = {
          id: `0e2e0000-0000-7000-a000-${String(n).padStart(12, "0")}`,
          productNo: `BB-${100_000 + n}`,
          listing: listing ?? { title: "", originalPrice: "0", currency: "sgd", stock: 0 },
          status: "draft",
          version: 0,
        };
        rows.unshift(row);
        await route.fulfill({ json: envelope(view(row)) });
      },
    ],
    [
      "GET",
      /^admin\/products\/([^/]+)$/u,
      async (route, id) => {
        const row = find(id);
        await (row
          ? route.fulfill({ json: envelope(detail(row)) })
          : fail(route, 404, "CATALOG_404_001", "Product not found"));
      },
    ],
    [
      "PUT",
      /^admin\/products\/([^/]+)$/u,
      async (route, id) => {
        const row = find(id);
        const body = bodyOf<{ listing: ListingContent; expectedVersion: number }>(route);
        if (!row) return fail(route, 404, "CATALOG_404_001", "Product not found");
        if (await stale(route, row, body.expectedVersion)) return;
        row.listing = body.listing ?? row.listing;
        row.version += 1;
        await route.fulfill({ json: envelope(view(row)) });
      },
    ],
    [
      "PUT",
      /^admin\/products\/([^/]+)\/status$/u,
      async (route, id) => {
        const row = find(id);
        const body = bodyOf<{ status: Row["status"]; expectedVersion: number }>(route);
        if (!row) return fail(route, 404, "CATALOG_404_001", "Product not found");
        if (await stale(route, row, body.expectedVersion)) return;
        row.status = body.status ?? row.status;
        row.version += 1;
        await route.fulfill({ json: envelope(view(row)) });
      },
    ],
    // After the detail route: the fixture takes the last match, and "counts" would read as an id.
    [
      "GET",
      /^admin\/products\/counts$/u,
      (route) =>
        route.fulfill({
          json: envelope({
            draft: rows.filter((r) => r.status === "draft").length,
            onSale: rows.filter((r) => r.status === "on_sale").length,
            offSale: rows.filter((r) => r.status === "off_sale").length,
            openDeals: 0,
          }),
        }),
    ],
    // No listing in this journey has a bloc price rule: the read answers 409, "No rule yet".
    [
      "GET",
      /^products\/([^/]+)\/team-price-policy$/u,
      (route) => fail(route, 409, "CATALOG_409_001", "Team price is unavailable"),
    ],
  ];
  return {
    rows,
    routes,
    bumpOnNextWrite: () => {
      armed = true;
    },
  };
}

test.describe("anonymous visitor", () => {
  test("/admin sends an anonymous visitor to /login with the path they wanted", async ({
    page,
    api,
  }) => {
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/login\?returnTo=%2Fadmin$/);
    await expect(page.getByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
    expect(api.calls(undefined, /^admin\//u)).toEqual([]);
  });
});

test.describe("signed-in buyer", () => {
  test("/admin shows the 403 card without the storefront header", async ({ page, api }) => {
    api.state.me = { ...ADA, roles: ["buyer"] };
    // The session's roles decide (A3-01), so the guard never asks; were it to, the backend says no.
    api.override("GET", /^admin\//u, (route) =>
      fail(route, 403, "COMMON_403_001", "Permission denied"),
    );

    await page.goto("/admin");
    await expect(page.getByRole("heading", { name: ACCESS_DENIED })).toBeVisible();
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");
    await expect(page.getByTestId("session-user")).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Sign out" })).toHaveCount(0);
    expect(api.calls(undefined, /^admin\//u)).toEqual([]);
  });
});

test.describe("admin, stubbed", () => {
  test("creates a draft, publishes it, and reloads after another admin's save", async ({
    page,
    api,
  }) => {
    api.state.me = { ...ADA, roles: ["admin"] };
    const store = adminStore();
    const unstubbed: string[] = [];
    page.on("response", (r) => {
      if (r.status() === 501) unstubbed.push(r.url());
    });
    for (const [method, path, answer] of store.routes) {
      api.override(method, path, (route, call) => answer(route, path.exec(call.path)?.[1] ?? ""));
    }

    // 4b.1: a new draft from the three fields that will freeze.
    await page.goto("/admin/listings/new");
    await page
      .getByRole("textbox", { name: "Title", exact: true })
      .fill("Hokkaido milk, 12 cartons");
    await page.getByRole("textbox", { name: "Solo price" }).fill("46.80");
    await page.getByRole("textbox", { name: "Stock" }).fill("45");
    await page.getByRole("button", { name: "Save draft" }).click();
    const id = store.rows[0]?.id ?? "";
    await expect(page).toHaveURL(new RegExp(`/admin/listings/${id}$`));
    await expect(page.getByText("BB-100001 · draft · v0")).toBeVisible();

    // 4b.2: Publish names the saved values that freeze, then the listing is On sale at v1.
    await page.getByRole("button", { name: "Publish", exact: true }).click();
    const dialog = page.getByRole("alertdialog", { name: "Publish this listing?" });
    await expect(dialog).toContainText("These three fields freeze for good");
    await expect(dialog).toContainText("S$46.80");
    await expect(dialog).toContainText("SGD");
    await expect(dialog).toContainText("45");
    await dialog.getByRole("button", { name: "Publish listing" }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText("BB-100001 · on sale · v1 · 0 sold")).toBeVisible();
    expect(api.calls("PUT", /\/status$/u).map((c) => c.body)).toEqual([
      { status: "on_sale", expectedVersion: 0 },
    ]);

    // 4b.4: someone else saves v2 first, so this save is refused and the banner offers Reload.
    store.bumpOnNextWrite();
    await page.getByRole("textbox", { name: "Subtitle" }).fill("Fresh from Hokkaido");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText("This listing was updated by someone else")).toBeVisible();
    await expect(
      page.getByText(
        "You loaded v1; the server now has v2. Reload to see the latest version — your unsaved changes will be lost.",
      ),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Save changes" })).toBeDisabled();
    await page.getByRole("button", { name: "Reload" }).click();
    await expect(page.getByText("BB-100001 · on sale · v2 · 0 sold")).toBeVisible();
    await expect(page.getByText("This listing was updated by someone else")).toBeHidden();
    await expect(page.getByRole("textbox", { name: "Subtitle" })).toHaveValue("");

    // Every request the console made was answered by a stub.
    expect(unstubbed).toEqual([]);
  });
});

// A real admin on the dev distribution. It needs a storage state from a Google account that holds
// the admin role in the dev DB (README.md); none is granted today (§6 Q11), so it skips.
test.describe("admin on dev @admin", () => {
  test.skip(
    !hasAdminState,
    `no ${ADMIN_STATE}: grant admin on the dev DB, then seed it with \`${CODEGEN}\``,
  );
  test.skip(
    !isDev,
    "the storage state carries the dev CloudFront cookie; run with E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net",
  );
  test.use(hasAdminState ? { storageState: ADMIN_STATE } : {});

  test("loads the listings, then creates a draft and deletes it", async ({ page }) => {
    const title = `E2E draft ${Date.now()}`;
    await page.goto("/admin/listings");
    await expect(page.getByRole("heading", { level: 1, name: "Listings" })).toBeVisible();
    await expect(page.getByText(/^Showing \d+–\d+ of \d+$/u)).toBeVisible();

    await page.goto("/admin/listings/new");
    await page.getByRole("textbox", { name: "Title", exact: true }).fill(title);
    await page.getByRole("textbox", { name: "Solo price" }).fill("1.00");
    await page.getByRole("button", { name: "Save draft" }).click();
    await expect(page.getByText(/· draft · v0$/u)).toBeVisible();

    await page
      .getByRole("region", { name: "Status" })
      .getByRole("button", { name: "Delete draft" })
      .click();
    const dialog = page.getByRole("alertdialog", { name: "Delete this draft?" });
    await dialog.getByRole("button", { name: "Delete draft" }).click();
    await expect(page.getByText("Draft deleted")).toBeVisible();
    await expect(page).toHaveURL(/\/admin\/listings$/u);
    await expect(page.getByText(title)).toHaveCount(0);
  });
});
