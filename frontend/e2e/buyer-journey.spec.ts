import type { MyTeamResponse, OrderSummaryResponse } from "@/api/generated/types.gen";

import { expect, listing, test } from "./fixtures/api";

// The buyer journey (backlog FB-03): catalog → listing → quote → Stripe Checkout → return →
// receipt Waiting then Paid → My blocs. Every /api/v1 call is answered by the shared stubs
// (`fixtures/api.ts`) and Stripe by a route here, so the spec runs on every target, `vite preview`
// in CI included, without a session or a real payment. What it proves is the frontend's part:
// START carries the quote the sheet showed (US26), the return hop reads the payment id, and the
// receipt trusts only BlocBuy's receipt, polled until Stripe's confirmation lands.
const LISTING = listing();
const PRODUCT_ID = LISTING.id ?? "";
const TITLE = LISTING.title ?? "";
const DEAL_ID = "0e2e0000-0000-4000-8000-0000000000d1";
const ORDER_ID = "0e2e0000-0000-4000-8000-0000000000a1";
const PRICE = "71.00";

// What the backend holds once Stripe's webhook has settled the START: the buyer leads a new bloc
// and owns its order.
function settledStart(now: number): { team: MyTeamResponse; order: OrderSummaryResponse } {
  const paidTime = new Date(now).toISOString();
  return {
    team: {
      dealId: DEAL_ID,
      productId: PRODUCT_ID,
      productTitle: TITLE,
      dealPrice: PRICE,
      currency: "sgd",
      targetSize: 5,
      currentSize: 1,
      status: "open",
      expireTime: new Date(now + 24 * 60 * 60 * 1000).toISOString(),
      role: "leader",
      joinTime: paidTime,
      orderId: ORDER_ID,
    },
    order: {
      orderId: ORDER_ID,
      orderNo: "BB-E2E-0001",
      dealId: DEAL_ID,
      productId: PRODUCT_ID,
      productTitle: TITLE,
      quantity: 1,
      unitPrice: PRICE,
      totalAmount: PRICE,
      currency: "sgd",
      status: "pending",
      paidTime,
      createTime: paidTime,
    },
  };
}

test("a signed-in buyer starts a bloc at the quoted price and sees it paid under My blocs", async ({
  page,
  api,
  baseURL,
}) => {
  const origin = baseURL ?? "";
  expect(origin, "playwright.config sets baseURL").not.toBe("");
  const { paymentId } = api.state;
  api.state.me = { userId: "0e2e0000-0000-4000-8000-0000000000b1", nickname: "E2E Buyer" };
  api.state.listings.push(LISTING);
  // The receipt is read before Stripe's webhook lands, then after.
  api.state.receipts.set(paymentId, [
    { paymentId, status: "CREATED", amount: PRICE, currency: "sgd" },
    {
      paymentId,
      status: "SUCCEEDED",
      outcome: "START",
      amount: PRICE,
      currency: "sgd",
      dealId: DEAL_ID,
      orderId: ORDER_ID,
    },
  ]);
  // Stripe Checkout: the buyer pays, the webhook settles the bloc and the order, and Stripe sends
  // the browser back to the success URL, a full page load of the app.
  let checkoutOpened = false;
  await page.route("https://checkout.stripe.com/**", async (route) => {
    checkoutOpened = true;
    const { team, order } = settledStart(Date.now());
    api.state.teams.push(team);
    api.state.orders.push(order);
    // WebKit interception cannot fulfill redirects. A controlled provider document
    // performs the same top-level return without changing the app's CSP.
    const returnUrl = new URL(`/payments/return?paymentId=${paymentId}`, origin).href;
    await route.fulfill({
      contentType: "text/html",
      body: `<!doctype html><title>Checkout stub</title><script>location.replace(${JSON.stringify(returnUrl)})</script>`,
    });
  });

  await page.goto("/");
  await expect(page).toHaveTitle("Listings · BlocBuy");
  await page.locator(`a[href="/products/${PRODUCT_ID}"]`).first().click();
  await expect(page).toHaveURL(new RegExp(`/products/${PRODUCT_ID}$`));
  await expect(page).toHaveTitle(`${TITLE} · BlocBuy`);
  await page
    .getByRole("button", { name: /^Start a bloc/ })
    .filter({ visible: true })
    .first()
    .click();

  const sheet = page.getByRole("dialog", { name: "Your bloc price" });
  await expect(sheet.getByText("S$71.00").first()).toBeVisible();
  await sheet.getByRole("button", { name: "Continue to payment" }).click();

  await expect(page).toHaveURL(new RegExp(`/payments/${paymentId}$`));
  expect(checkoutOpened).toBe(true);
  // US26: START carries the quote the sheet showed, so the server charges that amount.
  const [quoteId] = [...api.state.quotes.keys()];
  expect(api.calls("POST", /^payments\/intents$/u).map((c) => c.body)).toEqual([
    { operation: "START", businessId: PRODUCT_ID, quoteId },
  ]);

  await expect(
    page.getByRole("heading", { name: "Waiting for payment confirmation" }),
  ).toBeVisible();
  await expect(page).toHaveTitle("Waiting for payment confirmation · BlocBuy");
  // The receipt polls every 2 s; the second read is Stripe's confirmation.
  await expect(page.getByRole("heading", { name: "Payment received" })).toBeVisible({
    timeout: 10_000,
  });
  await expect(page.getByText(`S$${PRICE} paid.`)).toBeVisible();
  await expect(page).toHaveTitle("Payment received · BlocBuy");

  await page.getByRole("link", { name: "My blocs" }).first().click();
  await expect(page).toHaveURL(/\/account$/);
  await expect(page).toHaveTitle("My blocs · BlocBuy");
  await expect(
    page.getByRole("list", { name: "My blocs" }).getByRole("heading", { name: TITLE }),
  ).toBeVisible();
});
