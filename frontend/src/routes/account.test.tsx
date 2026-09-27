import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import type { MyTeamResponse, OrderSummaryResponse } from "@/api/generated/types.gen";
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

const team = (n: number, over: Partial<MyTeamResponse> = {}): MyTeamResponse => ({
  dealId: `0192f0a0-0000-7000-8000-${String(n).padStart(12, "0")}`,
  productId: `0b6f2c1e-6b1a-4f0e-9d1c-${String(n).padStart(12, "0")}`,
  productTitle: `Bloc product ${n}`,
  productImage: null,
  dealPrice: "9.90",
  currency: "sgd",
  targetSize: 5,
  currentSize: 2,
  status: "open",
  expireTime: new Date(Date.now() + 5 * 3_600_000).toISOString(),
  successTime: null,
  role: "member",
  joinTime: "2026-09-16T11:46:00Z",
  orderId: null,
  ...over,
});

// Answers GET /group-deals/me with `teams` (and `total`, when the buyer has more than one page),
// recording each request's query.
function serveTeams(teams: MyTeamResponse[], total = teams.length) {
  const seen: URLSearchParams[] = [];
  server.use(
    http.get("*/api/v1/group-deals/me", ({ request }) => {
      const url = new URL(request.url);
      seen.push(url.searchParams);
      return HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { items: teams, total, page: 1, size: Number(url.searchParams.get("size")) },
      });
    }),
  );
  return seen;
}

const mixed = [
  team(1, { role: "leader" }),
  team(2),
  team(3, { status: "OPEN" }),
  team(4, { status: "succeeded", currentSize: 5, successTime: "2026-09-16T13:30:00Z" }),
  team(5, { status: "expired", expireTime: "2026-09-15T13:32:00Z" }),
  team(6, { status: "failed", expireTime: "2026-09-12T13:32:00Z" }),
];

describe("/account behind the _authenticated guard", () => {
  it("sends anonymous visitors to /login with the wanted path", async () => {
    serveAnonymous();
    const { router } = renderRoute("/account");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({ returnTo: "/account" });
    expect(await screen.findByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
  });

  it("renders the account header and one empty card when the buyer has no blocs and no orders", async () => {
    serveSignedIn({ userId: "u1", nickname: null, avatarUrl: null });
    serveOrders(0);
    serveTeams([]);
    const { router } = renderRoute("/account");
    expect(
      await screen.findByRole("heading", { name: "You haven’t joined a bloc yet" }),
    ).toBeVisible();
    expect(screen.queryByRole("heading", { name: "No orders yet" })).toBeNull();
    expect(screen.queryByRole("group", { name: "Show blocs" })).toBeNull();
    expect(screen.getByRole("heading", { name: "My blocs" })).toBeVisible();
    // The site header names the buyer (userId when the nickname is null); the page adds no second
    // name row (§4.4).
    expect(screen.getByTestId("session-user")).toHaveTextContent("u1");
    expect(screen.getAllByText("u1")).toHaveLength(1);
    expect(router.state.location.pathname).toBe("/account");
    expect(screen.getByRole("link", { name: "See what’s filling" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("list", { name: "My orders" })).toBeNull();
  });
});

describe("/account order history (T3-02, #128)", () => {
  it("lists the buyer's orders from GET /orders/me, ten per page by default", async () => {
    serveSignedIn();
    serveTeams([]);
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
    serveTeams([]);
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
    serveTeams([]);
    serveOrders(12);
    const { router } = renderRoute("/account?page=9");
    await waitFor(() => expect(router.state.location.search).toEqual({ page: 2 }));
    expect(await screen.findByText("Order product 11")).toBeVisible();
  });

  it("keeps the header and offers a retry when the orders fail to load", async () => {
    serveSignedIn();
    serveTeams([]);
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
      expect(await screen.findByText("We couldn’t load your blocs and orders")).toBeVisible();
      expect(screen.getByRole("heading", { name: "My blocs" })).toBeVisible();
      failing = false;
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByRole("list", { name: "My orders" })).toBeVisible();
    } finally {
      queryClient.setDefaultOptions(defaults);
    }
  });
});

describe("/account my blocs (T3-03, #31)", () => {
  it("fetches the latest 100 blocs once and counts each chip from mixed statuses", async () => {
    serveSignedIn();
    serveOrders(1);
    const seen = serveTeams(mixed);
    renderRoute("/account");
    const chips = await screen.findByRole("group", { name: "Show blocs" });
    expect(within(chips).getByText("Active").closest("[aria-current]")).toHaveTextContent(
      "Active 3",
    );
    expect(within(chips).getByRole("link", { name: "Completed 1" })).toBeVisible();
    expect(within(chips).getByRole("link", { name: "Refunded 2" })).toBeVisible();
    const list = screen.getByRole("list", { name: "My blocs" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    expect(within(list).getByText("You started this")).toBeVisible();
    expect(seen).toHaveLength(1);
    expect(seen[0]?.get("page")).toBe("1");
    expect(seen[0]?.get("size")).toBe("100");
    // Blocs first, then the orders
    expect(screen.getByRole("list", { name: "My orders" })).toBeVisible();
    expect(screen.queryByText(/Showing your latest/)).toBeNull();
  });

  it("switches chips through ?blocs= without refetching", async () => {
    serveSignedIn();
    serveOrders(1);
    const seen = serveTeams(mixed);
    const { router } = renderRoute("/account");
    await userEvent.click(await screen.findByRole("link", { name: "Refunded 2" }));
    await waitFor(() => expect(router.state.location.search).toEqual({ blocs: "refunded" }));
    const list = screen.getByRole("list", { name: "My blocs" });
    await waitFor(() => expect(within(list).getAllByRole("listitem")).toHaveLength(2));
    expect(within(list).getAllByText(/refund due$/)).toHaveLength(2);
    expect(screen.getByText(/^Refunds are issued automatically/)).toBeVisible();
    await userEvent.click(screen.getByRole("link", { name: "Completed 1" }));
    await waitFor(() => expect(router.state.location.search).toEqual({ blocs: "completed" }));
    expect(await screen.findByText("Filled")).toBeVisible();
    expect(seen).toHaveLength(1);
  });

  it("falls back to Active for an unknown ?blocs= value", async () => {
    serveSignedIn();
    serveOrders(1);
    serveTeams(mixed);
    const { router } = renderRoute("/account?blocs=bogus");
    const list = await screen.findByRole("list", { name: "My blocs" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(3);
    await waitFor(() => expect(router.state.location.search).toEqual({}));
  });

  it("says so when a filter has no blocs, and keeps the orders empty card", async () => {
    serveSignedIn();
    serveOrders(0);
    serveTeams([team(1)]);
    renderRoute("/account?blocs=completed");
    expect(await screen.findByText("No completed blocs")).toBeVisible();
    expect(screen.getByRole("heading", { name: "No orders yet" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "You haven’t joined a bloc yet" })).toBeNull();
  });

  it("notes that only the latest 100 blocs are shown when the buyer has more", async () => {
    serveSignedIn();
    serveOrders(1);
    serveTeams(
      Array.from({ length: 100 }, (_, i) => team(i + 1)),
      130,
    );
    renderRoute("/account");
    expect(await screen.findByText("Showing your latest 100 blocs")).toBeVisible();
  });

  it("offers a retry when the blocs fail to load, then recovers", async () => {
    serveSignedIn();
    serveOrders(1);
    let failing = true;
    server.use(
      http.get("*/api/v1/group-deals/me", () =>
        failing
          ? HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 })
          : HttpResponse.json({
              code: OK_CODE,
              message: "ok",
              data: { items: mixed, total: mixed.length, page: 1, size: 100 },
            }),
      ),
    );
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retry: false } });
    try {
      renderRoute("/account");
      expect(await screen.findByText("We couldn’t load your blocs and orders")).toBeVisible();
      expect(screen.getByRole("heading", { name: "My blocs" })).toBeVisible();
      failing = false;
      await userEvent.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByRole("list", { name: "My blocs" })).toBeVisible();
    } finally {
      queryClient.setDefaultOptions(defaults);
    }
  });
});
