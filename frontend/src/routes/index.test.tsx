import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import type { ProductSummaryResponse } from "@/api/generated/types.gen";
import { routeTree } from "@/routeTree.gen";
import { server } from "@/test/msw";

// The root layout mounts the devtools; they have no place in a happy-dom route test.
vi.mock("@tanstack/react-devtools", () => ({ TanStackDevtools: () => null }));
vi.mock("@tanstack/react-query-devtools", () => ({ ReactQueryDevtoolsPanel: () => null }));
vi.mock("@tanstack/react-router-devtools", () => ({ TanStackRouterDevtoolsPanel: () => null }));

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
      return HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { items: all.slice((page - 1) * size, page * size), total: all.length, page, size },
      });
    }),
  );
}

async function renderAt(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createRouter({
    routeTree,
    context: { queryClient: qc, session: null },
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  render(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

describe("/ (products list) loader", () => {
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

  it("keeps a page that is within range", async () => {
    serveListings([listing(1), listing(2), listing(3)]);
    const router = await renderAt("/?page=2&size=2");
    expect(await screen.findByText("Listing 3")).toBeInTheDocument();
    expect(router.state.location.search).toEqual({ page: 2, size: 2 });
    expect(screen.getByText(/Page 2 of 2/)).toBeInTheDocument();
  });
});
