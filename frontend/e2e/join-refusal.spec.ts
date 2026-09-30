import { expect, listing, test } from "./fixtures/api";

// JOIN refusals (backlog FB-04): the server's admission refuses the JOIN intent, and the listing
// names the reason, says this attempt was not charged, and never opens Checkout. The copy is
// `joinRejections` in `src/features/payment/use-checkout.ts`; `payment.test.tsx` pins it per code
// in jsdom, and this spec proves the same through the built app on every target.
const LISTING = listing();
const PRODUCT_ID = LISTING.id ?? "";
const notChargedNow =
  "This attempt was not charged. Any bloc you have already paid into is safe and still listed under My blocs.";

const REFUSALS: [code: string, status: number, message: string][] = [
  ["GROUPBUY_409_004", 409, `This bloc filled up before you joined. ${notChargedNow}`],
  ["GROUPBUY_409_002", 409, `This bloc is no longer taking members. ${notChargedNow}`],
  ["GROUPBUY_409_003", 409, "You are already in this bloc. Nothing new has been charged."],
  ["GROUPBUY_404_001", 404, `This bloc no longer exists. ${notChargedNow}`],
];

for (const [code, status, message] of REFUSALS) {
  test(`a refused JOIN (${code}) names the reason and opens no Checkout`, async ({ page, api }) => {
    // Signed in, and not a member of the open bloc (`teams` is empty), so Join is enabled (FB-20).
    api.state.me = { userId: "0e2e0000-0000-4000-8000-0000000000b2", nickname: "E2E Joiner" };
    api.state.listings.push(LISTING);
    api.override("POST", /^payments\/intents$/u, (route) =>
      route.fulfill({ status, json: { code, message: "rejected" } }),
    );

    await page.goto(`/products/${PRODUCT_ID}`);
    const join = page
      .getByRole("button", { name: /^Join this bloc/ })
      .filter({ visible: true })
      .first();
    await expect(join).toBeEnabled();
    await join.click();

    await expect(page.getByRole("alert").filter({ hasText: message })).toBeVisible();
    expect(api.calls("POST", /^payments\/intents$/u).map((c) => c.body)).toEqual([
      { operation: "JOIN", businessId: LISTING.deal?.dealId },
    ]);
    expect(api.calls("POST", /^payments\/checkout-sessions$/u)).toEqual([]);
  });
}
