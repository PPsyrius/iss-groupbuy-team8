import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { getListingQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import type { ProductDetailResponse } from "@/api/generated/types.gen";
import { server } from "@/test/msw";
import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

const knownId = "5b1b9d5c-1a11-4e1e-9a10-000000000001";
const unknownId = "5b1b9d5c-1a11-4e1e-9a10-000000000404";

const listing = (overrides: Partial<ProductDetailResponse> = {}): ProductDetailResponse => ({
  id: knownId,
  title: "Thai jasmine rice 10 kg",
  category: "groceries",
  individualPrice: 89,
  groupPrice: 71,
  currency: "sgd",
  images: [],
  stock: 40,
  soldCount: 12,
  deal: { dealId: "deal-1", currentSize: 3, targetSize: 5, expireTime: "2099-01-01T00:00:00Z" },
  ...overrides,
});

// `exactOptionalPropertyTypes` forbids spreading an explicit `undefined`, so absent
// properties are removed instead of overwritten.
function without<T extends object>(value: T, ...keys: (keyof T)[]): T {
  const copy = { ...value };
  for (const key of keys) delete copy[key];
  return copy;
}

const notFoundBody = { code: "PRODUCT_404_001", message: "Listing not found" };

// One known listing; every other id answers the backend's not-found shape (plan §12 note 42).
// `renderRoute` installs the production response interceptor, which turns a non-2xx body into
// the `ApiError` the loader classifies.
function serveListing(
  product: ProductDetailResponse,
  notFound: () => Response = () => HttpResponse.json(notFoundBody, { status: 404 }),
) {
  server.use(
    http.get("*/api/v1/products/:id", ({ params }) =>
      params.id === product.id
        ? HttpResponse.json({ code: OK_CODE, message: "ok", data: product })
        : notFound(),
    ),
  );
}

describe("/products/$productId loader", () => {
  it("renders the not-found card, not the error alert, for an unknown id (HTTP 404)", async () => {
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${unknownId}`);
    expect(await screen.findByText(/can’t find that page/)).toBeInTheDocument();
    expect(screen.queryByText(/couldn’t load this listing/)).not.toBeInTheDocument();
  });

  it("treats a 200 envelope carrying a _404_ code as not found too", async () => {
    serveAnonymous();
    serveListing(listing(), () => HttpResponse.json(notFoundBody));
    renderRoute(`/products/${unknownId}`);
    expect(await screen.findByText(/can’t find that page/)).toBeInTheDocument();
  });

  it("renders the listing for a known id", async () => {
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("S$71.00").length).toBeGreaterThan(0);
  });

  it("renders the error alert when a 200 envelope carries no data", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
      ),
      http.get("*/api/v1/products/:id", () => HttpResponse.json({ code: OK_CODE, message: "ok" })),
    );
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText(/couldn’t load this listing/)).toBeInTheDocument();
    expect(screen.queryByText(/can’t find that page/)).not.toBeInTheDocument();
  });

  it("omits the Bloc closes cell when the deadline cannot be parsed", async () => {
    serveAnonymous();
    serveListing(
      listing({ deal: { dealId: "deal-1", currentSize: 3, targetSize: 5, expireTime: "soon" } }),
    );
    renderRoute(`/products/${knownId}`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(screen.queryByText("Bloc closes")).not.toBeInTheDocument();
    expect(screen.getByText("3 of 5 joined")).toBeInTheDocument();
  });
});

describe("/products/$productId open-bloc header", () => {
  it("says the bloc is filling while the deal is open", async () => {
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText("1 filling now")).toBeInTheDocument();
    expect(screen.getByText("Join one, or start your own")).toBeInTheDocument();
    expect(screen.queryByText(/bloc has ended/)).not.toBeInTheDocument();
  });

  it("does not describe an expired deal as filling", async () => {
    serveAnonymous();
    serveListing(
      listing({
        deal: {
          dealId: "deal-1",
          currentSize: 3,
          targetSize: 5,
          expireTime: "2020-01-01T00:00:00Z",
        },
      }),
    );
    renderRoute(`/products/${knownId}`);
    expect((await screen.findAllByText("Ended")).length).toBeGreaterThan(0);
    expect(screen.getByText("This bloc has ended")).toBeInTheDocument();
    expect(screen.queryByText("1 filling now")).not.toBeInTheDocument();
    expect(screen.queryByText("Join one, or start your own")).not.toBeInTheDocument();
  });

  it("does not describe a full bloc as filling or joinable", async () => {
    serveAnonymous();
    serveListing(
      listing({
        deal: {
          dealId: "deal-1",
          currentSize: 5,
          targetSize: 5,
          expireTime: "2099-01-01T00:00:00Z",
        },
      }),
    );
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText("Full")).toBeInTheDocument();
    expect(screen.getByText("This bloc has filled up")).toBeInTheDocument();
    expect(screen.queryByText("1 filling now")).not.toBeInTheDocument();
    expect(screen.queryByText("Join one, or start your own")).not.toBeInTheDocument();
  });
});

describe("/products/$productId gallery", () => {
  it("keeps the selected image and caption consistent when the image list shrinks", async () => {
    const images = [
      "https://example.test/rice-1.jpg",
      "https://example.test/rice-2.jpg",
      "https://example.test/rice-3.jpg",
    ];
    serveAnonymous();
    serveListing(listing({ images }));
    const { container, queryClient } = renderRoute(`/products/${knownId}`);
    await screen.findByText("product shot 1 of 3");
    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 3" })[0]!);
    expect(screen.getByText("product shot 3 of 3")).toBeInTheDocument();

    act(() => {
      queryClient.setQueryData(getListingQueryKey({ path: { id: knownId } }), {
        code: OK_CODE,
        message: "ok",
        data: listing({ images: images.slice(0, 2) }),
      });
    });

    expect(await screen.findByText("product shot 1 of 2")).toBeInTheDocument();
    expect(container.querySelector("main img")).toHaveAttribute("src", images[0]);
    for (const control of screen.getAllByRole("button", { name: "Show product shot 1" })) {
      expect(control).toHaveAttribute("aria-pressed", "true");
    }
  });

  it("starts at the first shot when navigating to another product", async () => {
    const images = ["https://example.test/rice-1.jpg", "https://example.test/rice-2.jpg"];
    serveAnonymous();
    serveListing(listing({ images }));
    const { container, router } = renderRoute(`/products/${knownId}`);
    await screen.findByText("product shot 1 of 2");
    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 2" })[0]!);
    expect(screen.getByText("product shot 2 of 2")).toBeInTheDocument();

    serveListing(listing({ id: unknownId, title: "Another rice listing", images }));
    await act(() =>
      router.navigate({ to: "/products/$productId", params: { productId: unknownId } }),
    );

    await screen.findByRole("heading", { level: 1, name: "Another rice listing" });
    expect(screen.getByText("product shot 1 of 2")).toBeInTheDocument();
    expect(container.querySelector("main img")).toHaveAttribute("src", images[0]);
  });

  it("drops an image that fails to load so the fallback tile shows", async () => {
    const src = "https://example.test/rice.jpg";
    serveAnonymous();
    serveListing(listing({ images: [src] }));
    const { container } = renderRoute(`/products/${knownId}`);
    await screen.findByText("product shot 1 of 1");
    const img = container.querySelector(`img[src="${src}"]`);
    if (!img) throw new Error("gallery image not rendered");
    fireEvent.error(img);
    await waitFor(() => expect(container.querySelector(`img[src="${src}"]`)).toBeNull());
    expect(screen.getByText("product shot 1 of 1")).toBeInTheDocument();
  });
});

describe("/products/$productId gated actions", () => {
  it("opens the sign-in sheet for an anonymous buyer and returns to the listing", async () => {
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await userEvent.click(start!);
    const dialog = await screen.findByRole("dialog", { name: "Sign in to hold your seat" });
    expect(dialog).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Not now" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("opens the sign-in sheet from the open bloc's Join as well", async () => {
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}`);
    await userEvent.click(await screen.findByRole("button", { name: /^Join/ }));
    expect(await screen.findByRole("dialog", { name: "Sign in to hold your seat" })).toBeVisible();
  });

  it("does not gate a signed-in buyer", async () => {
    serveSignedIn();
    serveListing(listing());
    servePayments();
    serveQuote();
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    expect(await screen.findByRole("dialog", { name: "Your bloc price" })).toBeVisible();
    expect(screen.queryByRole("dialog", { name: "Sign in to hold your seat" })).toBeNull();
  });
});

// Payments on, and every intent fails before a payment exists (the page-level alert path).
function servePayments() {
  server.use(
    http.get("*/api/v1/payments/capabilities", () =>
      HttpResponse.json({ code: OK_CODE, data: { enabled: true } }),
    ),
    http.post("*/api/v1/payments/intents", () => HttpResponse.error()),
  );
}

const quoteOne = "0192f0a0-0000-7000-8000-000000000001";

function serveQuote(
  answer: () => Response = () =>
    HttpResponse.json({
      code: OK_CODE,
      message: "ok",
      data: {
        id: quoteOne,
        productId: knownId,
        policyVersion: 1,
        amount: "71.00",
        currency: "sgd",
        issuedAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
      },
    }),
) {
  server.use(http.post("*/api/v1/team-price-quotes", answer));
}

describe("/products/$productId quote step", () => {
  it("quotes the bloc price, then starts checkout for this listing", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    let intentBody: unknown;
    servePayments();
    server.use(
      http.post("*/api/v1/payments/intents", async ({ request }) => {
        intentBody = await request.json();
        return HttpResponse.error();
      }),
    );
    serveQuote();
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    const dialog = await screen.findByRole("dialog", { name: "Your bloc price" });
    expect(await within(dialog).findByRole("timer")).toHaveTextContent(/left/);
    await userEvent.click(within(dialog).getByRole("button", { name: "Continue to payment" }));
    await waitFor(() =>
      expect(intentBody).toEqual({ operation: "START", businessId: knownId, quoteId: quoteOne }),
    );
    // The failed intent closes the sheet so the page-level alert is visible.
    expect(await screen.findByText(/We could not open Checkout/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("keeps the sheet open on a refused quote and prices again on request", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    servePayments();
    server.use(
      http.post("*/api/v1/payments/intents", () =>
        HttpResponse.json(
          { code: "CATALOG_409_007", message: "Team price quote has expired" },
          { status: 409 },
        ),
      ),
    );
    let issued = 0;
    serveQuote(() => {
      issued++;
      return HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: {
          id: `0192f0a0-0000-7000-8000-00000000000${issued}`,
          productId: knownId,
          policyVersion: 1,
          amount: "71.00",
          currency: "sgd",
          issuedAt: new Date().toISOString(),
          expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
        },
      });
    });
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    const dialog = await screen.findByRole("dialog", { name: "Your bloc price" });
    await userEvent.click(
      await within(dialog).findByRole("button", { name: "Continue to payment" }),
    );
    expect(await within(dialog).findByText("This price is no longer available")).toBeVisible();
    expect(screen.queryByText(/We could not open Checkout/)).toBeNull();
    await userEvent.click(within(dialog).getByRole("button", { name: "Get a new price" }));
    expect(
      await within(dialog).findByRole("button", { name: "Continue to payment" }),
    ).toBeVisible();
    expect(issued).toBe(2);
  });

  it("says no price is set when the listing has no team price policy", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(without(listing(), "deal", "groupPrice"));
    servePayments();
    serveQuote(() =>
      HttpResponse.json(
        { code: "CATALOG_409_001", message: "Team price is unavailable" },
        { status: 409 },
      ),
    );
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    expect(await screen.findByText(/No bloc price is set for this listing yet/)).toBeVisible();
  });
});

describe("/products/$productId quote sheet across listings", () => {
  it("closes the sheet when the buyer moves to another listing", async () => {
    sessionStorage.clear();
    serveSignedIn();
    const otherId = "5b1b9d5c-1a11-4e1e-9a10-000000000002";
    const products = [listing(), listing({ id: otherId, title: "D24 durian 1 kg" })];
    server.use(
      http.get("*/api/v1/products/:id", ({ params }) => {
        const product = products.find((p) => p.id === params.id);
        return product
          ? HttpResponse.json({ code: OK_CODE, message: "ok", data: product })
          : HttpResponse.json(notFoundBody, { status: 404 });
      }),
    );
    servePayments();
    serveQuote();
    const { router } = renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    const dialog = await screen.findByRole("dialog", { name: "Your bloc price" });
    expect(await within(dialog).findByRole("timer")).toHaveTextContent(/left/);
    // A history step (browser back or forward) lands on another listing under the open sheet.
    await router.navigate({ to: "/products/$productId", params: { productId: otherId } });
    expect(await screen.findByRole("heading", { name: "D24 durian 1 kg" })).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });
});

describe("/products/$productId listing variants", () => {
  it("renders a listing without a bloc and still starts checkout", async () => {
    serveSignedIn();
    serveListing(without(listing(), "deal"));
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Open blocs")).not.toBeInTheDocument();
  });

  it("renders a listing whose deal is null, as the API sends it without a bloc", async () => {
    serveSignedIn();
    serveListing(listing({ deal: null }));
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Open blocs")).not.toBeInTheDocument();
  });

  it("treats a bloc without sizes as empty", async () => {
    serveSignedIn();
    serveListing(listing({ deal: { dealId: "deal-1", expireTime: "2099-01-01T00:00:00Z" } }));
    server.use(
      http.get("*/api/v1/payments/capabilities", () =>
        HttpResponse.json({ code: OK_CODE, data: { enabled: true } }),
      ),
    );
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    expect(screen.getByRole("progressbar", { name: "0 of 1 joined" })).toBeInTheDocument();
  });

  it("renders a full bloc without offering another seat", async () => {
    serveAnonymous();
    serveListing(
      listing({
        deal: {
          dealId: "deal-1",
          currentSize: 5,
          targetSize: 5,
          expireTime: "2099-01-01T00:00:00Z",
        },
      }),
    );
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.getByText("5 of 5 joined")).toBeInTheDocument();
  });

  it("renders a bare listing without prices, stock, or media", async () => {
    serveAnonymous();
    serveListing({
      id: knownId,
      title: "Thai jasmine rice 10 kg",
      images: [],
    });
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/in stock/)).not.toBeInTheDocument();
    expect(screen.queryByText(/sold/)).not.toBeInTheDocument();
  });

  it("renders the individual price when there is no bloc price", async () => {
    serveAnonymous();
    serveListing(without(listing(), "groupPrice"));
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("S$89.00").length).toBeGreaterThan(0);
  });

  it("renders sold count without stock when stock is unknown", async () => {
    serveAnonymous();
    serveListing(without(listing(), "stock"));
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.getByText("12 sold")).toBeInTheDocument();
    expect(screen.queryByText(/in stock/)).not.toBeInTheDocument();
  });

  it("renders the bloc price without an individual price", async () => {
    serveAnonymous();
    serveListing(without(listing(), "individualPrice"));
    renderRoute(`/products/${knownId}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("S$71.00").length).toBeGreaterThan(0);
  });

  it("renders the crumb without a title cell when the listing has none", async () => {
    serveAnonymous();
    serveListing(without(listing(), "title"));
    const { container } = renderRoute(`/products/${knownId}`);
    await waitFor(() => expect(screen.getByText("Home")).toBeInTheDocument());
    expect(container.querySelector('[aria-current="page"]')).toBeNull();
  });

  it("falls back to the cover image when there are no gallery shots", async () => {
    serveAnonymous();
    serveListing(listing({ images: [], coverImage: "https://example.test/cover.jpg" }));
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText("product shot 1 of 1")).toBeInTheDocument();
  });

  it("switches product shots from the thumbnails", async () => {
    serveAnonymous();
    serveListing(
      listing({
        subtitle: "Fresh and fragrant",
        description: "Sun-dried in the central plains.",
        images: ["https://example.test/rice-1.jpg", "https://example.test/rice-2.jpg"],
      }),
    );
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText("product shot 1 of 2")).toBeInTheDocument();
    expect(screen.getByText("Fresh and fragrant")).toBeInTheDocument();
    expect(screen.getAllByText("Sun-dried in the central plains.")).toHaveLength(2);
    const thumbs = document.querySelectorAll('button[aria-label="Show product shot 2"]');
    expect(thumbs.length).toBeGreaterThan(1);
    for (const thumb of thumbs) fireEvent.click(thumb);
    await waitFor(() =>
      expect(
        [...document.querySelectorAll('button[aria-label="Show product shot 2"]')].every(
          (thumb) => thumb.getAttribute("aria-pressed") === "true",
        ),
      ).toBe(true),
    );
    expect(screen.getByText("product shot 2 of 2")).toBeInTheDocument();
  });

  it("shows the loading skeleton while the listing resolves", async () => {
    serveAnonymous();
    server.use(http.get("*/api/v1/products/:id", () => new Promise<never>(() => {})));
    const { container } = renderRoute(`/products/${knownId}`);
    await waitFor(() => expect(container.querySelector('[aria-busy="true"]')).not.toBeNull(), {
      timeout: 5000,
    });
  });

  it("retries the listing after a server error", async () => {
    serveAnonymous();
    server.use(
      http.get("*/api/v1/products/:id", () => HttpResponse.json({ code: OK_CODE, message: "ok" })),
    );
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByText(/couldn’t load this listing/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText(/couldn’t load this listing/)).toBeInTheDocument();
  });
});

function serveTeams(items: unknown[]) {
  server.use(
    http.get("*/api/v1/group-deals/me", () =>
      HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { items, total: items.length, page: 1, size: 100 },
      }),
    ),
  );
}

describe("/products/$productId invite link (UC7)", () => {
  const ref = "Qm9vbS1pbnZpdGUtcmVm_-";

  it("parks the ref, drops it from the URL, and sends it on the JOIN after sign-in", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const first = renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    expect(
      await screen.findByRole("heading", { name: "You’ve been invited to join this bloc" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/You’ll sign in with Google, then pay/)).toBeInTheDocument();
    expect(first.router.state.location.href).toBe(`/products/${knownId}`);
    expect(JSON.parse(sessionStorage.getItem("blocbuy.invite") ?? "null")).toEqual({
      productId: knownId,
      dealId: "deal-1",
      ref,
    });
    // Join is gated; the sheet brings the buyer back to the bare listing URL.
    await userEvent.click(screen.getByRole("button", { name: "Join this bloc · S$71.00" }));
    expect(await screen.findByRole("dialog", { name: "Sign in to hold your seat" })).toBeVisible();
    first.unmount();

    // Back from Google (a full page load): the ref is still parked in this tab.
    serveSignedIn();
    serveTeams([]);
    servePayments();
    let intentBody: unknown;
    server.use(
      http.post("*/api/v1/payments/intents", async ({ request }) => {
        intentBody = await request.json();
        return HttpResponse.error();
      }),
    );
    renderRoute(`/products/${knownId}`);
    const join = await screen.findByRole("button", { name: "Join this bloc · S$71.00" });
    await waitFor(() => expect(join).toBeEnabled());
    expect(screen.getByText(/You’ll pay the bloc price now/)).toBeInTheDocument();
    await userEvent.click(join);
    await waitFor(() =>
      expect(intentBody).toEqual({ operation: "JOIN", businessId: "deal-1", ref }),
    );
  });

  it("makes the invited bloc's Join the primary action and steps Start a bloc down", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    const invite = await screen.findByRole("region", { name: "Invite" });
    const join = within(invite).getByRole("button", { name: "Join this bloc · S$71.00" });
    expect(join).toHaveClass("button--secondary", "button--lg");
    const starts = screen.getAllByRole("button", { name: /Start a bloc/ });
    expect(starts.length).toBeGreaterThan(0);
    for (const start of starts) {
      expect(start).toHaveClass("button--outline");
      expect(start).not.toHaveClass("button--secondary");
    }
    // The primary Join comes before the page's other actions in reading order.
    expect(
      join.compareDocumentPosition(starts[0]!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("keeps Start a bloc primary without an invite", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    expect(start).toHaveClass("button--secondary");
    expect(screen.queryByRole("button", { name: /^Join this bloc ·/ })).toBeNull();
  });

  it("keeps Start a bloc primary when the invited bloc is full", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(
      listing({
        deal: {
          dealId: "deal-1",
          currentSize: 5,
          targetSize: 5,
          expireTime: "2099-01-01T00:00:00Z",
        },
      }),
    );
    renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    expect(
      await screen.findByRole("heading", { name: "The bloc you were invited to has closed" }),
    ).toBeInTheDocument();
    const [start] = screen.getAllByRole("button", { name: /Start a bloc/ });
    expect(start).toHaveClass("button--secondary");
    expect(screen.queryByRole("button", { name: /^Join this bloc ·/ })).toBeNull();
  });

  it("drops an invalid ref without parking it", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const { router } = renderRoute(`/products/${knownId}?deal=deal-1&ref=bad%20ref!`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(router.state.location.href).toBe(`/products/${knownId}`);
    expect(sessionStorage.getItem("blocbuy.invite")).toBeNull();
    expect(screen.queryByRole("region", { name: "Invite" })).toBeNull();
  });

  it("empties the parked invite when a newer link to the listing is invalid", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const first = renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(sessionStorage.getItem("blocbuy.invite")).not.toBeNull();
    first.unmount();

    const { router } = renderRoute(`/products/${knownId}?deal=deal-1&ref=other%20ref!`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(router.state.location.href).toBe(`/products/${knownId}`);
    expect(sessionStorage.getItem("blocbuy.invite")).toBeNull();
    expect(screen.queryByRole("region", { name: "Invite" })).toBeNull();
  });

  it("drops a link whose deal and ref are both invalid, and empties the parked invite", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    sessionStorage.setItem(
      "blocbuy.invite",
      JSON.stringify({ productId: knownId, dealId: "deal-1", ref }),
    );
    const { router } = renderRoute(`/products/${knownId}?deal=bad%20deal!&ref=bad%20ref!`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(router.state.location.href).toBe(`/products/${knownId}`);
    expect(sessionStorage.getItem("blocbuy.invite")).toBeNull();
  });

  it("points a buyer invited to a closed bloc at the open one", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    renderRoute(`/products/${knownId}?deal=deal-0&ref=${ref}`);
    expect(
      await screen.findByRole("heading", { name: "The bloc you were invited to has closed" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/joining the open bloc below/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Join this bloc ·/ })).toBeNull();
    const [start] = screen.getAllByRole("button", { name: /Start a bloc/ });
    expect(start).toHaveClass("button--secondary");
  });

  it("shows the share block to a buyer in the listing's open bloc", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    serveTeams([{ dealId: "deal-1", productId: knownId, status: "open", role: "leader" }]);
    server.use(
      http.post("*/api/v1/group-deals/:dealId/invites", ({ params }) =>
        HttpResponse.json({
          code: OK_CODE,
          message: "ok",
          data: {
            ref,
            dealId: params.dealId,
            productId: knownId,
            expiresAt: "2099-01-01T00:00:00Z",
          },
        }),
      ),
    );
    renderRoute(`/products/${knownId}`);
    expect(await screen.findByRole("heading", { name: "Share your bloc" })).toBeInTheDocument();
    const field = await screen.findByRole<HTMLInputElement>("textbox", { name: "Invite link" });
    expect(field.value).toContain(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    expect(screen.getByText("2 seats to go.")).toBeInTheDocument();
  });

  it("disables Join for a member of the open bloc and sends no intent (FB-20)", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    servePayments();
    serveTeams([{ dealId: "deal-1", productId: knownId, status: "open", role: "member" }]);
    let intents = 0;
    server.use(
      http.post("*/api/v1/payments/intents", () => {
        intents++;
        return HttpResponse.error();
      }),
    );
    renderRoute(`/products/${knownId}`);
    const bloc = await screen.findByRole("article", { name: "Open bloc" });
    const join = within(bloc).getByRole("button", { name: /join/i });
    await waitFor(() => expect(join).toHaveAccessibleDescription("You’re in this bloc"));
    expect(join).toBeDisabled();
    await userEvent.click(join);
    expect(intents).toBe(0);
  });

  it("keeps Join enabled for a signed-in buyer outside the open bloc (FB-20)", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    servePayments();
    serveTeams([{ dealId: "deal-9", productId: knownId, status: "open", role: "member" }]);
    renderRoute(`/products/${knownId}`);
    const bloc = await screen.findByRole("article", { name: "Open bloc" });
    const join = within(bloc).getByRole("button", { name: /join/i });
    await waitFor(() => expect(join).toBeEnabled());
    expect(join).not.toHaveAccessibleDescription();
  });

  it("holds Join until the buyer's membership is known (FB-20)", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    servePayments();
    const gate: { open?: () => void } = {};
    const answered = new Promise<void>((resolve) => {
      gate.open = resolve;
    });
    server.use(
      http.get("*/api/v1/group-deals/me", async () => {
        await answered;
        return HttpResponse.json({
          code: OK_CODE,
          message: "ok",
          data: { items: [], total: 0, page: 1, size: 100 },
        });
      }),
    );
    renderRoute(`/products/${knownId}`);
    const bloc = await screen.findByRole("article", { name: "Open bloc" });
    const join = within(bloc).getByRole("button", { name: /join/i });
    // Payments are on by now (Start a bloc is live); only the membership read is outstanding.
    const [start] = screen.getAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    expect(join).toBeDisabled();
    expect(join).not.toHaveAccessibleDescription();
    gate.open?.();
    await waitFor(() => expect(join).toBeEnabled());
  });

  it("keeps Join enabled when the membership read fails (FB-20)", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    servePayments();
    server.use(http.get("*/api/v1/group-deals/me", () => new HttpResponse(null, { status: 500 })));
    renderRoute(`/products/${knownId}`);
    const bloc = await screen.findByRole("article", { name: "Open bloc" });
    const join = within(bloc).getByRole("button", { name: /join/i });
    // The query client retries twice before the read settles as failed.
    await waitFor(() => expect(join).toBeEnabled(), { timeout: 5000 });
  });

  it("hides the share block from a buyer who is not in the bloc", async () => {
    sessionStorage.clear();
    serveSignedIn();
    serveListing(listing());
    let asked = false;
    server.use(
      http.get("*/api/v1/group-deals/me", () => {
        asked = true;
        return HttpResponse.json({
          code: OK_CODE,
          message: "ok",
          data: {
            items: [{ dealId: "deal-9", productId: knownId, status: "open", role: "member" }],
            total: 1,
            page: 1,
            size: 100,
          },
        });
      }),
    );
    renderRoute(`/products/${knownId}`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    await waitFor(() => expect(asked).toBe(true));
    expect(screen.queryByRole("heading", { name: "Share your bloc" })).toBeNull();
  });
});

function serveInvite(response: () => Response) {
  const asked: string[] = [];
  server.use(
    http.get("*/api/v1/invites/:ref", ({ params }) => {
      asked.push(String(params.ref));
      return response();
    }),
  );
  return asked;
}

const resolved = (data: Record<string, unknown>) => () =>
  HttpResponse.json({
    code: OK_CODE,
    message: "ok",
    data: { dealId: "deal-1", productId: knownId, expiresAt: "2099-01-01T00:00:00Z", ...data },
  });

// FB-19 (#230): the invite banner names the sharer from `GET /api/v1/invites/{ref}` (#201).
describe("/products/$productId invite sharer (FB-19)", () => {
  const ref = "Qm9vbS1pbnZpdGUtcmVm_-";

  it("leads with the sharer's nickname for the parked ref", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const asked = serveInvite(resolved({ sharerNickname: "Sarah Lim" }));
    renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    const invite = await screen.findByRole("region", { name: "Invite" });
    expect(
      within(invite).getByRole("heading", { name: "Sarah Lim started this bloc" }),
    ).toBeInTheDocument();
    expect(within(invite).getByText("and invited you to join it")).toBeInTheDocument();
    expect(within(invite).getByText("SL")).toBeInTheDocument();
    expect(asked).toContain(ref);
  });

  it("names the sharer when the invited bloc has closed", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    serveInvite(resolved({ dealId: "deal-0", sharerNickname: "Sarah Lim" }));
    renderRoute(`/products/${knownId}?deal=deal-0&ref=${ref}`);
    expect(
      await screen.findByRole("heading", { name: "Sarah Lim’s bloc has closed" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/joining the open bloc below/)).toBeInTheDocument();
  });

  it.each([
    ["the nickname is null", resolved({ sharerNickname: null })],
    ["the nickname is blank", resolved({ sharerNickname: "   " })],
    ["the ref resolves to another bloc", resolved({ dealId: "deal-9", sharerNickname: "Sarah" })],
    [
      "the ref is unknown",
      () =>
        HttpResponse.json(
          { code: "GROUPBUY_404_002", message: "Invite not found" },
          { status: 404 },
        ),
    ],
    ["the call fails", () => HttpResponse.error()],
  ])("keeps the generic lead when %s", async (_case, response) => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const asked = serveInvite(response);
    renderRoute(`/products/${knownId}?deal=deal-1&ref=${ref}`);
    expect(
      await screen.findByRole("heading", { name: "You’ve been invited to join this bloc" }),
    ).toBeInTheDocument();
    expect(asked).toContain(ref);
    expect(screen.queryByText(/started this bloc/)).toBeNull();
  });

  it("does not resolve anything without an invite", async () => {
    sessionStorage.clear();
    serveAnonymous();
    serveListing(listing());
    const asked = serveInvite(resolved({ sharerNickname: "Sarah Lim" }));
    renderRoute(`/products/${knownId}`);
    await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" });
    expect(asked).toEqual([]);
  });
});
