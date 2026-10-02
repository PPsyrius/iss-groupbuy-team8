import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import type { ProductSummaryResponse } from "@/api/generated/types.gen";
import { queryClient } from "@/api/query-client";
import { keywordTerms, matchesKeyword } from "@/test/keyword-match";
import { server } from "@/test/msw";
import { renderRoute, serveAnonymous } from "@/test/render-route";

const listing = (n: number): ProductSummaryResponse => ({
  id: `5b1b9d5c-1a11-4e1e-9a10-${String(n).padStart(12, "0")}`,
  title: `Listing ${n}`,
  category: "groceries",
  individualPrice: 10 + n,
  groupPrice: 8 + n,
  currency: "sgd",
});

// Mirrors the backend: `page` is an offset, so a page past the end answers no items and the
// full `total`.
function serveListings(all: ProductSummaryResponse[]) {
  server.use(
    http.get("*/api/v1/auth/me", () =>
      HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
    ),
    http.get("*/api/v1/products", ({ request }) => {
      const url = new URL(request.url);
      const page = Number(url.searchParams.get("page") ?? 1);
      const size = Number(url.searchParams.get("size") ?? 20);
      const category = url.searchParams.get("category");
      const terms = keywordTerms(url.searchParams.get("keyword"));
      const hits = all.filter(
        (p) => (!category || p.category === category) && matchesKeyword(p, terms),
      );
      return HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { items: hits.slice((page - 1) * size, page * size), total: hits.length, page, size },
      });
    }),
  );
}

async function renderAt(path: string) {
  return renderRoute(path).router;
}

describe("/ (products list) loader", () => {
  it("still renders anonymously when /auth/me fails outright", async () => {
    serveListings([listing(1)]);
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 }),
      ),
    );
    await renderAt("/");
    expect(await screen.findByText("Listing 1")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in" })).toBeInTheDocument();
  });

  it("redirects a page past the last one to the last page, keeping size and category", async () => {
    serveListings([listing(1), listing(2), listing(3)]);
    const router = await renderAt("/?page=999&size=20&category=groceries");
    await waitFor(() =>
      expect(router.state.location.search).toEqual({ page: 1, size: 20, category: "groceries" }),
    );
    expect(await screen.findByText("Listing 3")).toBeInTheDocument();
    expect(screen.getByText(/Page 1 of 1/)).toBeInTheDocument();
  });

  it("stays on page 1 when there are no listings", async () => {
    serveListings([]);
    const router = await renderAt("/?page=1&size=20");
    expect(await screen.findByText("Nothing on sale yet")).toBeInTheDocument();
    expect(router.state.location.search).toEqual({ page: 1, size: 20 });
    expect(screen.getByText(/Page 1 of 1/)).toBeInTheDocument();
  });

  it("filters by the keyword from the URL and shows it in the search field", async () => {
    serveListings([listing(1), { ...listing(2), title: "Thai jasmine rice 10 kg" }, listing(3)]);
    const router = await renderAt("/?keyword=rice&category=groceries");
    expect(await screen.findByText("Thai jasmine rice 10 kg")).toBeInTheDocument();
    expect(screen.queryByText("Listing 1")).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Search listings" })).toHaveValue("rice");
    expect(screen.getByText("Groceries")).toHaveAttribute("aria-current", "page");
    expect(router.state.location.search).toEqual({
      page: 1,
      size: 20,
      category: "groceries",
      keyword: "rice",
    });
  });

  it("offers to clear the search when a keyword matches nothing", async () => {
    serveListings([listing(1)]);
    await renderAt("/?keyword=durian&category=groceries&size=12");
    expect(await screen.findByText("No listings match “durian”")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Clear search" })).toHaveAttribute(
      "href",
      "/?page=1&size=12&category=groceries",
    );
  });

  it("keeps a page that is within range", async () => {
    serveListings([listing(1), listing(2), listing(3)]);
    const router = await renderAt("/?page=2&size=2");
    expect(await screen.findByText("Listing 3")).toBeInTheDocument();
    expect(router.state.location.search).toEqual({ page: 2, size: 2 });
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument();
  });
});

describe("/ (products list) errors", () => {
  it("shows the listings alert on a 500, and Try again recovers", async () => {
    serveListings([listing(1)]);
    let failing = true;
    server.use(
      http.get("*/api/v1/products", () =>
        failing
          ? HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 })
          : HttpResponse.json({
              code: OK_CODE,
              message: "ok",
              data: { items: [listing(1)], total: 1, page: 1, size: 20 },
            }),
      ),
    );
    // The app client retries a 500 twice with backoff; skip the wait, not the behaviour.
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retry: false } });
    try {
      await renderAt("/");
      expect(await screen.findByText("We couldn’t load the listings")).toBeVisible();
      expect(
        screen.getByText(/Your account and any bloc you have joined are unaffected/),
      ).toBeVisible();
      expect(screen.queryByText("Listing 1")).toBeNull();
      failing = false;
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByText("Listing 1")).toBeVisible();
      expect(screen.queryByText("We couldn’t load the listings")).toBeNull();
    } finally {
      queryClient.setDefaultOptions(defaults);
    }
  });

  it("renders the 404 card through the route tree for an unknown path", async () => {
    serveAnonymous();
    await renderAt("/no-such-page");
    expect(await screen.findByRole("heading", { name: "We can’t find that page" })).toBeVisible();
    expect(screen.getByText("Nothing has been charged.")).toBeVisible();
    expect(screen.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");
  });
});
