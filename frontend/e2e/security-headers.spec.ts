import { expect, listing, test } from "./fixtures/api";

const cdn = "https://dcsjrkmummxi7.cloudfront.net";
const cover = `${cdn}/security-test/cover.svg`;
const secondShot = `${cdn}/security-test/second.svg`;
const avatar = "https://lh3.googleusercontent.com/security-test/avatar.svg";
const unapproved = "https://unapproved.example.test/security-test/blocked.svg";
const svg =
  '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="#eee5d8"/></svg>';
const permissions = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

test.describe("preview security-header policy", () => {
  // Dev only receives this policy after an authorized Terraform apply, not an app deployment.
  test.skip(
    process.env.E2E_TARGET === "dev",
    "candidate policy is verified in preview before AWS apply",
  );

  for (const width of [390, 1280]) {
    test(`approved product images and a stubbed Google avatar load at ${width}px`, async ({
      page,
      api,
    }) => {
      await page.setViewportSize({ width, height: 900 });
      const product = listing({ coverImage: cover, images: [cover, secondShot] });
      api.state.listings.push(product);
      api.state.me = { userId: "security-test-buyer", nickname: "Policy buyer", avatarUrl: avatar };
      await page.route(`${cdn}/security-test/*`, (route) =>
        route.fulfill({ contentType: "image/svg+xml", body: svg }),
      );
      await page.route(avatar, (route) =>
        route.fulfill({ contentType: "image/svg+xml", body: svg }),
      );

      const response = await page.goto("/");
      expect(response?.headers()["permissions-policy"]).toBe(permissions);
      const denied = await page.evaluate(() => {
        const policy: unknown = Reflect.get(document, "featurePolicy");
        if (
          typeof policy !== "object" ||
          policy === null ||
          !("allowsFeature" in policy) ||
          typeof policy.allowsFeature !== "function"
        ) {
          throw new Error("Browser feature-policy API is unavailable");
        }
        const allowsFeature = policy.allowsFeature;
        return ["camera", "microphone", "geolocation", "payment", "usb"].map(
          (feature) => Reflect.apply(allowsFeature, policy, [feature]) === true,
        );
      });
      expect(denied).toEqual([false, false, false, false, false]);
      await expect(page.getByTestId("session-user")).toHaveText("Policy buyer");
      const catalogImage = page.locator(`img[src="${cover}"]`);
      const avatarImage = page.locator(`header img[src="${avatar}"]`);
      await expect(catalogImage).toBeVisible();
      await expect(avatarImage).toBeVisible();
      await expect
        .poll(() => catalogImage.evaluate((image: HTMLImageElement) => image.naturalWidth))
        .toBeGreaterThan(0);
      await expect
        .poll(() => avatarImage.evaluate((image: HTMLImageElement) => image.naturalWidth))
        .toBeGreaterThan(0);

      await page.locator(`a[href="/products/${product.id}"]`).first().click();
      const gallery = page.getByRole("region", { name: "Product images" });
      await gallery
        .getByRole("button", { name: "Show product shot 2" })
        .filter({ visible: true })
        .click();
      const selected = gallery.getByRole("img", { name: "Product shot 2" });
      await expect(selected).toHaveAttribute("src", secondShot);
      await expect
        .poll(() => selected.evaluate((image: HTMLImageElement) => image.naturalWidth))
        .toBeGreaterThan(0);
    });
  }

  test("an unapproved HTTPS image is blocked by CSP before any request", async ({ page, api }) => {
    api.state.me = null;
    let requested = false;
    await page.route(unapproved, (route) => {
      requested = true;
      return route.fulfill({ contentType: "image/svg+xml", body: svg });
    });
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
    const violation = await page.evaluate(
      (url) =>
        new Promise<{ directive: string; blockedURI: string }>((resolve) => {
          document.addEventListener("securitypolicyviolation", (event) => {
            if (event.blockedURI === url) {
              resolve({ directive: event.effectiveDirective, blockedURI: event.blockedURI });
            }
          });
          const image = new Image();
          image.addEventListener(
            "load",
            () => resolve({ directive: "not-blocked", blockedURI: url }),
            {
              once: true,
            },
          );
          image.src = url;
          document.body.append(image);
        }),
      unapproved,
    );
    expect(violation).toEqual({ directive: "img-src", blockedURI: unapproved });
    expect(requested).toBe(false);
  });
});
