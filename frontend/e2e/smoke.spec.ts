import { expect, test } from "@playwright/test";

const isDev = process.env.E2E_TARGET === "dev";

test("home renders the BlocBuy header", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("link", { name: "BlocBuy" })).toBeVisible();
});

test.describe("dev CloudFront only", () => {
  test.skip(!isDev, "needs the dev distribution");

  test("deep link is served by the SPA (A1 CloudFront Function)", async ({ page }) => {
    const res = await page.goto("/products/00000000-0000-0000-0000-000000000000");
    expect(res?.status()).toBe(200);
    await expect(page.getByRole("link", { name: "BlocBuy" })).toBeVisible();
  });

  test("/api/actuator/health is UP through CloudFront", async ({ request }) => {
    const res = await request.get("/api/actuator/health");
    expect(res.status()).toBe(200);
    expect((await res.json()).status).toBe("UP");
  });
});
