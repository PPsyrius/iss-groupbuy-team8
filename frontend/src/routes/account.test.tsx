import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import type { OrderSummaryResponse } from "@/api/generated/types.gen";
import { queryClient } from "@/api/query-client";
import { server } from "@/test/msw";
import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

const order = (n: number): OrderSummaryResponse => ({
  orderId: `0192f0a0-0000-7000-8000-${String(n).padStart(12, "0")}`,
  orderNo: `BB-${2090 + n}`,
  productId: `0b6f2c1e-6b1a-4f0e-9d1c-${String(n).padStart(12, "0")}`,
  productTitle: `Order product ${n}`,
  productImage: null,
  quantity: 1,
  unitPrice: "71.00",
  totalAmount: "71.00",
  currency: "sgd",
  status: "confirmed",
  paidTime: "2026-09-16T11:46:00Z",
  cancelTime: null,
  createTime: "2026-09-16T11:45:00Z",
});

// Answers GET /orders/me with `total` orders, `size` per page, recording each request's query.
function serveOrders(total: number) {
  const seen: URLSearchParams[] = [];
  server.use(
    http.get("*/api/v1/orders/me", ({ request }) => {
      const url = new URL(request.url);
      seen.push(url.searchParams);
      const page = Number(url.searchParams.get("page") ?? 1);
      const size = Number(url.searchParams.get("size") ?? 20);
      const all = Array.from({ length: total }, (_, i) => order(i + 1));
      return HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { items: all.slice((page - 1) * size, page * size), total, page, size },
      });
    }),
  );
  return seen;
}

describe("/account behind the _authenticated guard", () => {
  it("sends anonymous visitors to /login with the wanted path", async () => {
    serveAnonymous();
    const { router } = renderRoute("/account");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({ returnTo: "/account" });
    expect(await screen.findByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
  });

  it("renders the account header and the empty-state card when the buyer has no orders", async () => {
    serveSignedIn({ userId: "u1", nickname: null, avatarUrl: null });
    serveOrders(0);
    const { router } = renderRoute("/account");
    expect(await screen.findByRole("heading", { name: "No orders yet" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "My blocs" })).toBeVisible();
    expect(screen.getByTestId("account-user")).toHaveTextContent("u1");
    expect(router.state.location.pathname).toBe("/account");
    expect(screen.getByRole("link", { name: "See what’s filling" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("list", { name: "My orders" })).toBeNull();
  });
});

describe("/account order history (T3-02, #128)", () => {
  it("lists the buyer's orders from GET /orders/me, ten per page by default", async () => {
    serveSignedIn();
    const seen = serveOrders(3);
    renderRoute("/account");
    const list = await screen.findByRole("list", { name: "My orders" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    expect(screen.getByRole("heading", { name: "My orders" })).toBeVisible();
    expect(seen[0]?.get("page")).toBe("1");
    expect(seen[0]?.get("size")).toBe("10");
    expect(screen.queryByRole("heading", { name: "No orders yet" })).toBeNull();
    // One page only: no pager
    expect(screen.queryByRole("navigation", { name: "Order pages" })).toBeNull();
  });

  it("pages through the orders with the page in the URL", async () => {
    serveSignedIn();
    serveOrders(12);
    const { router } = renderRoute("/account");
    const pager = await screen.findByRole("navigation", { name: "Order pages" });
    expect(within(pager).getByText("Page 1 of 2")).toBeVisible();
    await userEvent.click(within(pager).getByRole("link", { name: "Next" }));
    await waitFor(() => expect(router.state.location.search).toEqual({ page: 2 }));
    const list = await screen.findByRole("list", { name: "My orders" });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(2));
    expect(screen.getByText("Order product 11")).toBeVisible();
  });

  it("sends a page past the last one to the last page", async () => {
    serveSignedIn();
    serveOrders(12);
    const { router } = renderRoute("/account?page=9");
    await waitFor(() => expect(router.state.location.search).toEqual({ page: 2 }));
    expect(await screen.findByText("Order product 11")).toBeVisible();
  });

  it("keeps the header and offers a retry when the orders fail to load", async () => {
    serveSignedIn();
    let failing = true;
    server.use(
      http.get("*/api/v1/orders/me", () =>
        failing
          ? HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 })
          : HttpResponse.json({
              code: OK_CODE,
              message: "ok",
              data: { items: [order(1)], total: 1, page: 1, size: 10 },
            }),
      ),
    );
    // The app client retries a 500 twice with backoff; skip the wait, not the behaviour.
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retry: false } });
    try {
      renderRoute("/account");
      expect(await screen.findByText("We couldn’t load your orders")).toBeVisible();
      expect(screen.getByRole("heading", { name: "My blocs" })).toBeVisible();
      failing = false;
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByRole("list", { name: "My orders" })).toBeVisible();
    } finally {
      queryClient.setDefaultOptions(defaults);
    }
  });
});
