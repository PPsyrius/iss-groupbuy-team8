import { existsSync } from "node:fs";

import type { Route } from "@playwright/test";

import type { AdminListingResponse, ListingContent } from "@/api/generated/types.gen";

import { expect, test } from "./fixtures/api";

// The admin console journeys (admin plan A2-03, ADR 0011). Three run on every target, `vite
// preview` in CI included, on the shared `api` stubs: anonymous → /login, a buyer → the 403 card,
// and an admin (stubbed) through create → publish → a stale save → Reload. The jsdom route tests
// pin each screen against the MSW admin mock; these prove the same journey through the built app.
// The fourth, @admin, needs a real admin session on dev or on the local full stack
// (`fullstack-localhost/`, admin plan FU-02) and skips until one is seeded (§6 Q11).
// Each target keeps its own storage state, so a dev cookie never reaches localhost or back.
const ADMIN_TARGETS = {
  dev: { state: "e2e/.auth/admin.json", origin: "https://d1b3np88z657cp.cloudfront.net" },
  local: { state: "e2e/.auth/admin-local.json", origin: "http://localhost:8080" },
} as const;
const target = process.env.E2E_TARGET;
// Cookies ignore the port, so a localhost state would also reach `vite preview` on :4173; the run
// needs the matching base URL as well as the target.
const adminTarget =
  (target === "dev" || target === "local") &&
  process.env.E2E_BASE_URL?.replace(/\/$/u, "") === ADMIN_TARGETS[target].origin
    ? ADMIN_TARGETS[target]
    : undefined;
const hasAdminState = adminTarget !== undefined && existsSync(adminTarget.state);
const codegen = ({ state, origin }: { state: string; origin: string }) =>
  `pnpm exec playwright codegen --save-storage=${state} ${origin}/login`;

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
  test("preserves unsupported legacy image URLs and refuses saving until corrected", async ({
    page,
    api,
  }, testInfo) => {
    api.state.me = { ...ADA, roles: ["admin"] };
    const store = adminStore();
    const id = "0e2e0000-0000-4000-8000-000000000099";
    store.rows.push({
      id,
      productNo: "LEGACY-IMAGE",
      status: "draft",
      version: 0,
      listing: {
        title: "Legacy image listing",
        originalPrice: "12.50",
        currency: "sgd",
        stock: 5,
        coverImage: "https://legacy.example/cover.jpg",
        images: ["https://legacy.example/shot.jpg"],
      },
    });
    for (const [method, path, answer] of store.routes) {
      api.override(method, path, (route, call) => answer(route, path.exec(call.path)?.[1] ?? ""));
    }
    await page.goto(`/admin/listings/${id}`);
    const warning = page.getByRole("alert").filter({ hasText: "Existing URLs are kept" });
    await expect(warning).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Cover image URL" })).toHaveValue(
      "https://legacy.example/cover.jpg",
    );
    await page.getByRole("button", { name: "Save draft" }).click();
    expect(api.calls("PUT", /^admin\/products\/[^/]+$/u)).toHaveLength(0);
    // Keep one Chromium UI artifact. WebKit's screenshot instrumentation injects
    // an unapproved style block; that is not an application styling regression.
    if (testInfo.project.name === "chromium") {
      await page.screenshot({
        path: testInfo.outputPath("legacy-image-warning.png"),
        fullPage: true,
      });
    }
    await page
      .getByRole("textbox", { name: "Cover image URL" })
      .fill("https://dcsjrkmummxi7.cloudfront.net/cover.jpg");
    await page
      .getByRole("textbox", { name: "Image URL 1", exact: true })
      .fill("https://dcsjrkmummxi7.cloudfront.net/shot.jpg");
    await expect(warning).toHaveCount(0);
    await page.getByRole("button", { name: "Save draft" }).click();
    await expect.poll(() => api.calls("PUT", /^admin\/products\/[^/]+$/u).length).toBe(1);
  });

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

// A real admin on the dev distribution or the local full stack. It needs a storage state from a
// Google account that holds the admin role in that target's DB (README.md), so it skips without one.
test.describe("admin on a real backend @admin", () => {
  test.skip(
    adminTarget === undefined,
    `needs a real backend: run with E2E_TARGET=local E2E_BASE_URL=${ADMIN_TARGETS.local.origin} or E2E_TARGET=dev E2E_BASE_URL=${ADMIN_TARGETS.dev.origin}`,
  );
  test.skip(
    adminTarget !== undefined && !hasAdminState,
    adminTarget
      ? `no ${adminTarget.state}: grant admin in the ${target} DB, then seed it with \`${codegen(adminTarget)}\``
      : "",
  );
  test.use(hasAdminState && adminTarget ? { storageState: adminTarget.state } : {});

  test("loads the listings, then creates a draft and deletes it", async ({ page }) => {
    const title = `E2E draft ${Date.now()}`;
    // The draft is real (and the dev DB is shared), so a failure before the UI deletes it falls
    // back to deleting it through the API.
    let productId: string | undefined;
    let deleted = false;
    try {
      await page.goto("/admin/listings");
      await expect(page.getByRole("heading", { level: 1, name: "Listings" })).toBeVisible();
      await expect(page.getByText(/^Showing \d+–\d+ of \d+$/u)).toBeVisible();

      await page.goto("/admin/listings/new");
      await page.getByRole("textbox", { name: "Title", exact: true }).fill(title);
      await page.getByRole("textbox", { name: "Solo price" }).fill("1.00");
      await page.getByRole("button", { name: "Save draft" }).click();
      await expect(page.getByText(/· draft · v0$/u)).toBeVisible();
      productId = /\/admin\/listings\/([^/?#]+)$/u.exec(new URL(page.url()).pathname)?.[1];

      await page
        .getByRole("region", { name: "Status" })
        .getByRole("button", { name: "Delete draft" })
        .click();
      const dialog = page.getByRole("alertdialog", { name: "Delete this draft?" });
      await dialog.getByRole("button", { name: "Delete draft" }).click();
      await expect(page.getByText("Draft deleted")).toBeVisible();
      deleted = true;
      // A deleted draft lands on the Draft tab (A1-07); only default search values are stripped.
      await expect(page).toHaveURL(/\/admin\/listings\?tab=draft$/u);
      await expect(page.getByText(title)).toHaveCount(0);
    } finally {
      if (productId && !deleted) {
        await page.request
          .delete(`/api/v1/admin/products/${productId}?expectedVersion=0`)
          .catch(() => undefined);
      }
    }
  });
});
