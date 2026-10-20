import { expect, test } from "./fixtures/api";

const brokenCover = "https://images.example.test/missing-order-cover.jpg";

// #514: use an actual failed image request in Chromium, not a synthetic DOM error, and keep
// screenshots in the Playwright report. The account data is stubbed; no live orders are read.
for (const width of [390, 1280]) {
  test(`a broken order image leaves only the striped tile at ${width}px`, async ({
    page,
    api,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    api.state.me = { userId: "0e2e0000-0000-4000-8000-0000000000b1", nickname: "E2E Buyer" };
    api.state.orders.push({
      orderId: "0e2e0000-0000-4000-8000-0000000000a1",
      orderNo: "BB-E2E-BROKEN-IMAGE",
      productTitle: "Sunscreen SPF50+ 50ml",
      productImage: brokenCover,
      quantity: 1,
      totalAmount: "12.50",
      currency: "sgd",
      status: "confirmed",
    });
    await page.route(brokenCover, (route) => route.fulfill({ status: 404, body: "Not found" }));
    const failedImage = page.waitForResponse(brokenCover);

    await page.goto("/account");
    const orders = page.getByRole("list", { name: "My orders" });
    await expect(orders.getByRole("heading", { name: "Sunscreen SPF50+ 50ml" })).toBeVisible();
    await orders.scrollIntoViewIfNeeded();
    expect((await failedImage).status()).toBe(404);

    const tile = orders.locator("[data-image-fallback]");
    await expect(tile).toBeVisible();
    await expect(tile).toHaveAttribute("aria-hidden", "true");
    await expect(orders.locator("img")).toHaveCount(0);
    await expect(tile).toHaveCSS("width", "56px");
    await expect(tile).toHaveCSS("height", "56px");
    await expect(tile).toHaveCSS("background-image", /^repeating-linear-gradient\(/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      width,
    );

    const screenshot = testInfo.outputPath(`account-image-fallback-${width}.png`);
    await page.screenshot({ path: screenshot, fullPage: true });
    await testInfo.attach(`account-image-fallback-${width}`, {
      path: screenshot,
      contentType: "image/png",
    });
  });
}
