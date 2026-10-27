import { createServer, type Server } from "node:http";

import { expect, listing, test } from "./fixtures/api";

const cdn = "https://dcsjrkmummxi7.cloudfront.net";
const cover = `${cdn}/security-test/cover.svg`;
const secondShot = `${cdn}/security-test/second.svg`;
const avatar = "https://lh3.googleusercontent.com/security-test/avatar.svg";
const unapproved = "https://unapproved.example.test/security-test/blocked.svg";
const svg =
  '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="200" height="200" fill="#eee5d8"/></svg>';
const permissions = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

async function listen(server: Server) {
  await new Promise<void>((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      server.off("error", reject);
      resolve();
    });
  });
  const address = server.address();
  if (address === null || typeof address === "string") throw new Error("Missing probe server port");
  return `http://127.0.0.1:${address.port}`;
}

async function close(server: Server) {
  server.closeAllConnections();
  if (server.listening) {
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
}

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
      browserName,
    }, testInfo) => {
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
      expect(response?.headers()["cross-origin-embedder-policy"]).toBe("credentialless");
      expect(response?.headers()["cross-origin-opener-policy"]).toBe("same-origin");
      expect(response?.headers()["cross-origin-resource-policy"]).toBe("same-origin");
      // Supporting engines must isolate; WebKit may gain support without making
      // a security improvement fail this compatibility test.
      const isolated = await page.evaluate(() => crossOriginIsolated);
      if (browserName !== "webkit") expect(isolated).toBe(true);
      testInfo.annotations.push({ type: "observed-isolation", description: String(isolated) });
      const denied = await page.evaluate(() => {
        const policy: unknown = Reflect.get(document, "featurePolicy");
        if (
          typeof policy !== "object" ||
          policy === null ||
          !("allowsFeature" in policy) ||
          typeof policy.allowsFeature !== "function"
        ) {
          return null;
        }
        const allowsFeature = policy.allowsFeature;
        return ["camera", "microphone", "geolocation", "payment", "usb"].map(
          (feature) => Reflect.apply(allowsFeature, policy, [feature]) === true,
        );
      });
      if (browserName === "chromium" || denied !== null) {
        expect(denied).toEqual([false, false, false, false, false]);
      }
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

  test("reviewed React Aria styles work while injected style blocks and attributes are refused", async ({
    page,
    api,
  }, testInfo) => {
    testInfo.annotations.push({
      type: "intentional-style-probe",
      description: "This test verifies rejection of two injected styles",
    });
    const violations: string[] = [];
    await page.exposeFunction("recordStyleViolation", (directive: string) =>
      violations.push(directive),
    );
    await page.addInitScript(() => {
      document.addEventListener("securitypolicyviolation", (event) => {
        const record: unknown = Reflect.get(window, "recordStyleViolation");
        if (typeof record === "function") void record(event.effectiveDirective);
      });
    });
    const product = listing();
    api.state.listings.push(product);
    await page.goto(`/products/${product.id}`);
    const start = page
      .getByRole("button", { name: /^Start a bloc/ })
      .filter({ visible: true })
      .first();
    // HeroUI buttons intentionally override touch-action with their own static class.
    // An unstyled pressable proves the hashed React Aria sheet itself was applied.
    await page.evaluate(() => {
      const pressable = document.createElement("span");
      pressable.id = "trusted-pressable";
      pressable.setAttribute("data-react-aria-pressable", "true");
      document.body.append(pressable);
    });
    await expect(page.locator("#trusted-pressable")).toHaveCSS(
      "touch-action",
      /^(?:pan-x pan-y pinch-zoom|manipulation)$/,
    );
    await start.click();
    const sheet = page.getByRole("dialog", { name: "Sign in to hold your seat" });
    await expect(sheet).toBeVisible();
    await sheet.getByRole("button", { name: "Not now" }).click();
    await expect(sheet).toBeHidden();
    expect(violations).toEqual([]);
    await page.evaluate(() => {
      const probe = document.createElement("div");
      probe.id = "style-probe";
      probe.setAttribute("style", "position: fixed");
      document.body.append(probe);
      const style = document.createElement("style");
      style.textContent = "#style-probe { position: absolute; }";
      document.head.append(style);
    });
    await expect
      .poll(() => violations)
      .toEqual(expect.arrayContaining(["style-src-attr", "style-src-elem"]));
    await expect(page.locator("#style-probe")).toHaveCSS("position", "static");
  });

  test("credentialless omits server-observed image cookies only after a credentialed baseline", async ({
    context,
    browserName,
  }, testInfo) => {
    // Different ports are cross-origin but same-site: SameSite/third-party-cookie
    // blocking cannot manufacture success. Both requests reach real HTTP servers,
    // and the credentialed baseline must pass before testing credentialless.
    const received = new Map<string, string>();
    const imageServer = createServer((request, response) => {
      if (request.url === "/seed") {
        response.writeHead(200, {
          "Set-Cookie": "image-secret=probe; Path=/; SameSite=Strict; HttpOnly",
          "Content-Type": "text/html",
        });
        response.end("<!doctype html><title>Cookie seed</title>");
      } else {
        received.set(request.url ?? "", request.headers.cookie ?? "");
        response.writeHead(200, { "Content-Type": "image/svg+xml", "Cache-Control": "no-store" });
        response.end(svg);
      }
    });
    const documentServer = createServer((request, response) => {
      // Identical documents except COEP. These fixtures isolate browser cookie
      // semantics; the app's canonical headers and public images are tested above.
      response.writeHead(200, {
        ...(request.url === "/candidate"
          ? { "Cross-Origin-Embedder-Policy": "credentialless" }
          : {}),
        "Cross-Origin-Opener-Policy": "same-origin",
        "Cross-Origin-Resource-Policy": "same-origin",
        "Set-Cookie": "session-probe=retained; Path=/; SameSite=Strict; HttpOnly",
        "Content-Type": request.url === "/cookie" ? "text/plain" : "text/html",
        "Cache-Control": "no-store",
      });
      response.end(
        request.url === "/cookie"
          ? (request.headers.cookie ?? "")
          : "<!doctype html><title>Credential policy probe</title>",
      );
    });
    try {
      const imageOrigin = await listen(imageServer);
      const documentOrigin = await listen(documentServer);
      expect(imageOrigin).not.toBe(documentOrigin);
      const probePage = await context.newPage();
      try {
        await probePage.goto(`${imageOrigin}/seed`);
        async function observe(mode: "baseline" | "candidate") {
          await probePage.goto(`${documentOrigin}/${mode}`);
          const isolated = await probePage.evaluate(() => crossOriginIsolated);
          if (mode === "baseline") expect(isolated).toBe(false);
          else if (browserName !== "webkit") expect(isolated).toBe(true);
          const resource = `/${mode}.svg`;
          expect(
            await probePage.evaluate(
              (url) =>
                new Promise<boolean>((resolve) => {
                  const image = new Image();
                  image.addEventListener("load", () => resolve(true), { once: true });
                  image.addEventListener("error", () => resolve(false), { once: true });
                  image.src = url;
                  document.body.append(image);
                }),
              imageOrigin + resource,
            ),
          ).toBe(true);
          expect(received.has(resource), "Image must reach the actual server").toBe(true);
          const cookie = received.get(resource) ?? "";
          if (mode === "baseline") expect(cookie).toContain("image-secret=probe");
          else if (isolated) expect(cookie).toBe("");
          const firstParty = await probePage.evaluate(async () => (await fetch("/cookie")).text());
          expect(firstParty).toContain("session-probe=retained");
          return {
            mode,
            isolated,
            imageCookieReceived: cookie.includes("image-secret=probe"),
            firstPartyCookieReceived: firstParty.includes("session-probe=retained"),
          };
        }
        // Sequential by design: baseline success is a prerequisite, not a
        // parallel request whose cookie state could race with the candidate.
        const observations = [await observe("baseline"), await observe("candidate")];
        await testInfo.attach("server-observed-credential-policy", {
          body: JSON.stringify({ browser: browserName, observations }, null, 2),
          contentType: "application/json",
        });
      } finally {
        await probePage.close();
      }
    } finally {
      await Promise.all([close(imageServer), close(documentServer)]);
    }
  });

  test("touch WebKit accepts the reviewed overscroll hash and keeps the sign-in sheet usable", async ({
    page,
    api,
  }, testInfo) => {
    test.skip(
      testInfo.project.name !== "webkit-touch",
      "covered in the opt-in touch WebKit project",
    );
    await page.addInitScript(() =>
      Object.defineProperty(navigator, "platform", { get: () => "iPhone" }),
    );
    const product = listing();
    api.state.listings.push(product);
    await page.goto(`/products/${product.id}`);
    await page
      .getByRole("button", { name: /^Start a bloc/ })
      .filter({ visible: true })
      .first()
      .tap();
    const sheet = page.getByRole("dialog", { name: "Sign in to hold your seat" });
    await expect(sheet).toBeVisible();
    // Explicit hash-acceptance probe, not a claim that mobile emulation exercises
    // React Aria's native iOS-only injection path on a physical device.
    await page.evaluate(() => {
      const style = document.createElement("style");
      style.id = "reviewed-overscroll-probe";
      style.textContent = "@layer {\n  * {\n    overscroll-behavior: contain;\n  }\n}";
      document.head.append(style);
    });
    await expect(page.locator("html")).toHaveCSS("overscroll-behavior", "contain");
    await sheet.getByRole("button", { name: "Not now" }).tap();
    await expect(sheet).toBeHidden();
    await page.locator("#reviewed-overscroll-probe").evaluate((element) => element.remove());
  });

  test("CORP preserves same-origin icons but blocks another origin embedding them", async ({
    page,
    context,
    baseURL,
  }) => {
    await page.goto("/login");
    const icon = new URL("/favicon.svg", baseURL).href;
    const response = await page.request.get(icon);
    expect(response.status()).toBe(200);
    expect(response.headers()["cross-origin-resource-policy"]).toBe("same-origin");
    const peer = await context.newPage();
    await peer.route("http://127.0.0.1:4173/corp-probe", (route) =>
      route.fulfill({
        contentType: "text/html",
        body: `<!doctype html><title>CORP probe</title><img src="${icon}" onload="document.title='loaded'" onerror="document.title='blocked'">`,
      }),
    );
    await peer.goto("http://127.0.0.1:4173/corp-probe");
    await expect(peer).toHaveTitle("blocked");
    await peer.close();
  });

  test("COOP severs a cross-origin opener without blocking top-level navigation", async ({
    page,
    context,
    baseURL,
  }) => {
    const peer = await context.newPage();
    await peer.route("http://127.0.0.1:4173/opener-probe", (route) =>
      route.fulfill({
        contentType: "text/html",
        body: "<!doctype html><title>Opener probe</title>",
      }),
    );
    await peer.goto("http://127.0.0.1:4173/opener-probe");
    const popupPromise = context.waitForEvent("page");
    await peer.evaluate((url) => window.open(url), new URL("/login", baseURL).href);
    const popup = await popupPromise;
    await popup.waitForLoadState();
    expect(await popup.evaluate(() => window.opener === null)).toBe(true);
    await expect(popup.getByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
    await popup.close();
    await peer.close();
    await page.goto("/login");
    await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
  });

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
