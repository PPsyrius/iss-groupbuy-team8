import type { Page } from "@playwright/test";

import type { QuoteResponse } from "@/api/generated/types.gen";

import { expect, listing, test } from "./fixtures/api";

// The quote step (backlog FB-05): the sheet's price lives 15 minutes, an expired one disables
// payment and "Get a new price" re-issues; a listing without a bloc price shows none; and a quote
// the server refuses at START (US26) sends the buyer back to a fresh price without opening
// Checkout. `QuoteSheet.test.tsx` pins the copy in jsdom; this spec proves it in the built app.
const LISTING = listing();
const PRODUCT_ID = LISTING.id ?? "";
const QUOTE_TTL_MS = 15 * 60 * 1000;
const ME = { userId: "0e2e0000-0000-4000-8000-0000000000b3", nickname: "E2E Quoter" };

test.beforeEach(({ api }) => {
  api.state.me = ME;
  api.state.listings.push(LISTING);
});

function startBloc(page: Page) {
  return page
    .getByRole("button", { name: /^Start a bloc/ })
    .filter({ visible: true })
    .first()
    .click();
}

test("an expired quote disables payment and Get a new price re-issues it", async ({
  page,
  api,
}) => {
  // Quotes expire against the page's clock, which the spec moves; the fixture's default quote
  // would expire against Node's, so issue them here from the same clock.
  let clockNow = Date.now();
  api.override("POST", /^team-price-quotes$/u, (route) => {
    const quote: QuoteResponse = {
      id: crypto.randomUUID(),
      productId: PRODUCT_ID,
      policyVersion: 1,
      amount: "71.00",
      currency: "sgd",
      issuedAt: new Date(clockNow).toISOString(),
      expiresAt: new Date(clockNow + QUOTE_TTL_MS).toISOString(),
    };
    api.state.quotes.set(quote.id ?? "", quote);
    return route.fulfill({ json: { code: "COMMON_200_000", message: "ok", data: quote } });
  });
  await page.clock.install({ time: clockNow });

  await page.goto(`/products/${PRODUCT_ID}`);
  await startBloc(page);
  const sheet = page.getByRole("dialog", { name: "Your bloc price" });
  const pay = sheet.getByRole("button", { name: "Continue to payment" });
  await expect(pay).toBeVisible();

  clockNow += 16 * 60 * 1000;
  await page.clock.fastForward("16:00");
  await expect(sheet.getByText("This price has expired")).toBeVisible();
  await expect(pay).toBeHidden();

  await sheet.getByRole("button", { name: "Get a new price" }).click();
  await expect(pay).toBeVisible();
  await expect(sheet.getByText("This price has expired")).toBeHidden();
  expect(api.calls("POST", /^team-price-quotes$/u)).toHaveLength(2);
  expect(api.calls("POST", /^payments\/intents$/u)).toEqual([]);
});

test("a listing without a bloc price (CATALOG_409_001) shows no price and no payment", async ({
  page,
  api,
}) => {
  api.override("POST", /^team-price-quotes$/u, (route) =>
    route.fulfill({
      status: 409,
      json: { code: "CATALOG_409_001", message: "team price is unavailable" },
    }),
  );

  await page.goto(`/products/${PRODUCT_ID}`);
  await startBloc(page);
  const sheet = page.getByRole("dialog", { name: "Your bloc price" });
  await expect(sheet.getByText(/No bloc price is set for this listing yet/)).toBeVisible();
  await expect(sheet.getByText(/S\$\d/)).toHaveCount(0);
  await expect(sheet.getByRole("button", { name: "Continue to payment" })).toHaveCount(0);
  await expect(sheet.getByRole("button", { name: "Close" })).toBeVisible();
});

test("a quote refused at START (CATALOG_409_007) asks for a new price and opens no Checkout", async ({
  page,
  api,
}) => {
  api.override("POST", /^payments\/intents$/u, (route) =>
    route.fulfill({ status: 409, json: { code: "CATALOG_409_007", message: "quote expired" } }),
  );

  await page.goto(`/products/${PRODUCT_ID}`);
  await startBloc(page);
  const sheet = page.getByRole("dialog", { name: "Your bloc price" });
  await sheet.getByRole("button", { name: "Continue to payment" }).click();

  await expect(sheet.getByText("This price is no longer available")).toBeVisible();
  await expect(sheet.getByRole("button", { name: "Get a new price" })).toBeVisible();
  await expect(sheet.getByRole("button", { name: "Continue to payment" })).toHaveCount(0);
  const [quoteId] = [...api.state.quotes.keys()];
  expect(api.calls("POST", /^payments\/intents$/u).map((c) => c.body)).toEqual([
    { operation: "START", businessId: PRODUCT_ID, quoteId },
  ]);
  expect(api.calls("POST", /^payments\/checkout-sessions$/u)).toEqual([]);
});
