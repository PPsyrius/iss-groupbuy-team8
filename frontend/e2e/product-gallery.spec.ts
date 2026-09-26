import { expect, test } from "@playwright/test";

const PRODUCT_ID = "a0000000-0000-7000-8000-0000000e2e04";
const images = Array.from({ length: 20 }, (_, i) => `/gallery-shot-${i + 1}.svg`);

for (const width of [390, 1280]) {
  test(`20 product shots remain bounded and keyboard accessible at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route(/\/api\/v1\/auth\/me(\?|$)/, (route) =>
      route.fulfill({ status: 401, json: { code: "AUTH_401", message: "unauthenticated" } }),
    );
    await page.route(`**/api/v1/products/${PRODUCT_ID}`, (route) =>
      route.fulfill({
        json: {
          code: "COMMON_200_000",
          message: "ok",
          data: {
            id: PRODUCT_ID,
            title: "Thai jasmine rice 10 kg",
            category: "groceries",
            individualPrice: 89,
            groupPrice: 71,
            currency: "sgd",
            stock: 40,
            images,
          },
        },
      }),
    );
    await page.route("**/gallery-shot-*.svg", (route) =>
      route.fulfill({
        contentType: "image/svg+xml",
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="620" height="420"><rect width="620" height="420" fill="#eee5d8"/></svg>',
      }),
    );

    await page.goto(`/products/${PRODUCT_ID}`);
    const gallery = page.getByRole("region", { name: "Product images" });
    const controls = gallery.getByRole("button").filter({ visible: true });
    const strip = gallery.getByRole("list").filter({ visible: true });
    await expect(controls).toHaveCount(20);
    await expect(gallery.getByRole("img", { name: "Product shot 1" })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      width,
    );
    expect(await strip.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);

    const captionBox = await gallery.getByText("product shot 1 of 20").boundingBox();
    const stripBox = await strip.boundingBox();
    expect(captionBox).not.toBeNull();
    expect(stripBox).not.toBeNull();
    expect(stripBox!.y).toBeGreaterThanOrEqual(captionBox!.y + captionBox!.height);
    const firstBox = await controls.first().boundingBox();
    expect(firstBox!.width).toBeGreaterThanOrEqual(44);
    expect(firstBox!.height).toBeGreaterThanOrEqual(44);

    await controls.first().focus();
    // Tab presses must complete in order to test the browser's actual keyboard focus path.
    // oxlint-disable-next-line no-await-in-loop
    for (let i = 1; i < 20; i += 1) await page.keyboard.press("Tab");
    await expect(controls.last()).toBeFocused();
    await expect(controls.last()).toBeInViewport();
    await page.keyboard.press("Enter");
    await expect(gallery.getByText("product shot 20 of 20")).toBeVisible();
    await expect(gallery.getByRole("img", { name: "Product shot 20" })).toHaveAttribute(
      "src",
      images[19]!,
    );
    await expect(controls.last()).toHaveAttribute("aria-pressed", "true");
    await expect(gallery.getByRole("img", { name: "Product shot 20" })).toHaveAttribute(
      "loading",
      "eager",
    );
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
      width,
    );

    // Pointer users can scroll back to any shot too; Playwright scrolls the target into view.
    await controls.first().click();
    await expect(gallery.getByText("product shot 1 of 20")).toBeVisible();
    await expect(controls.first()).toHaveAttribute("aria-pressed", "true");
  });
}
