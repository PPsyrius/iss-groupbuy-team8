import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { server } from "@/test/msw";
import { renderRoute, serveSignedIn } from "@/test/render-route";

beforeEach(() =>
  server.use(
    http.get("*/api/v1/payments/capabilities", () =>
      HttpResponse.json({ code: OK_CODE, data: { enabled: true } }),
    ),
  ),
);

describe("buyer order history", () => {
  it.each([
    ["SUCCEEDED", "Refund sent to your original payment method"],
    ["PENDING", "Refund queued"],
    ["PROCESSING", "Refund processing"],
    ["FAILED", "Refund failed — support review needed"],
    ["REVIEW", "Refund needs review — do not pay again"],
  ])("shows %s without confusing payment and refund", async (refundStatus, label) => {
    serveSignedIn();
    server.use(
      http.get("*/api/v1/orders", () =>
        HttpResponse.json({
          code: OK_CODE,
          data: {
            items: [
              {
                id: "order-1",
                orderNo: "BB-1",
                productTitle: "Local product",
                amount: "10.00",
                currency: "sgd",
                quantity: 1,
                status: "paid",
                paymentStatus: "SUCCEEDED",
                refundStatus,
                dealStatus: "expired",
                paymentId: "00000000-0000-4000-8000-000000000020",
              },
            ],
            total: 1,
            page: 1,
            size: 10,
          },
        }),
      ),
    );
    renderRoute("/account");
    expect(await screen.findByText(label)).toBeVisible();
    expect(screen.getByText("SGD 10.00 · Quantity 1")).toBeVisible();
    expect(screen.getByRole("link", { name: "Payment and refund details" })).toHaveAttribute(
      "href",
      "/payments/00000000-0000-4000-8000-000000000020",
    );
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("paginates through the generated API without sending a buyer ID", async () => {
    serveSignedIn();
    const queries: string[] = [];
    server.use(
      http.get("*/api/v1/orders", ({ request }) => {
        const url = new URL(request.url);
        queries.push(url.search);
        return HttpResponse.json({
          code: OK_CODE,
          data: { items: [], total: 11, page: Number(url.searchParams.get("page")), size: 10 },
        });
      }),
    );
    renderRoute("/account");
    expect(await screen.findByText("Page 1 · 11 orders")).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(await screen.findByText("Page 2 · 11 orders")).toBeVisible();
    expect(queries).toEqual(["?page=1&size=10", "?page=2&size=10"]);
    await userEvent.click(screen.getByRole("button", { name: "Previous" }));
    expect(await screen.findByText("Page 1 · 11 orders")).toBeVisible();
  });
  it("shows an error instead of a fake empty history", async () => {
    serveSignedIn();
    server.use(http.get("*/api/v1/orders", () => new HttpResponse(null, { status: 500 })));
    renderRoute("/account");
    expect(await screen.findByRole("alert")).toHaveTextContent("Unable to load orders");
    expect(screen.queryByText("You haven’t joined a bloc yet")).not.toBeInTheDocument();
  });
});
