import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
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
});

describe("/products/$productId gallery", () => {
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
    renderRoute(`/products/${knownId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await userEvent.click(start!);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
