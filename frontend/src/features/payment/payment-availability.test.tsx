import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { server } from "@/test/msw";
import { renderRoute, serveSignedIn } from "@/test/render-route";

const id = "00000000-0000-4000-8000-000000000010";
const ok = (data: unknown) => HttpResponse.json({ code: OK_CODE, data });

async function disabledPage(state: string, path: string) {
  serveSignedIn();
  const capability = vi.fn<() => Response>(() =>
    state === "missing"
      ? new HttpResponse(null, { status: 404 })
      : state === "offline"
        ? HttpResponse.error()
        : ok({ enabled: state === "invalid" ? "true" : false }),
  );
  const business = vi.fn<() => Response>(() => ok({}));
  server.use(
    http.get("*/api/v1/payments/capabilities", capability),
    http.post("*/api/v1/payments/intents", business),
    http.post("*/api/v1/payments/checkout-sessions", business),
    http.get(`*/api/v1/payments/${id}`, business),
    // The listing asks whether a signed-in buyer is in its bloc (T3-05 share block).
    http.get("*/api/v1/group-deals/me", () => ok({ items: [], total: 0, page: 1, size: 100 })),
    http.get(`*/api/v1/products/${id}`, () =>
      ok({
        id,
        title: "Available product",
        individualPrice: 20,
        groupPrice: 10,
        currency: "sgd",
        stock: 10,
        deal: { dealId: id, currentSize: 1, targetSize: 2, expireTime: "2099-01-01T00:00:00Z" },
      }),
    ),
  );
  const { queryClient } = renderRoute(path);
  await waitFor(() => expect(capability).toHaveBeenCalled());
  await waitFor(() => expect(queryClient.isFetching()).toBe(0));
  return business;
}

describe.each(["disabled", "missing", "offline", "invalid"])("Stripe capability %s", (state) => {
  it("keeps Start/Join disabled and the listing usable", async () => {
    const business = await disabledPage(state, `/products/${id}`);
    const buttons = screen.getAllByRole("button", { name: /Start a bloc|^Join/ });
    expect(buttons.filter((button) => !button.hasAttribute("disabled"))).toHaveLength(0);
    await userEvent.click(buttons[0]!);
    expect(screen.getByRole("heading", { name: "Available product" })).toBeVisible();
    expect(business).not.toHaveBeenCalled();
  });

  // /account carries no payment surface since 0b1f14d dropped the order-history slice.
  it("does not query or poll a direct receipt link", async () => {
    const business = await disabledPage(state, `/payments/${id}`);
    expect(await screen.findByRole("heading", { name: "Payments are unavailable" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Refresh status" })).not.toBeInTheDocument();
    expect(business).not.toHaveBeenCalled();
  });
});
