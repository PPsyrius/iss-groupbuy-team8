import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { OrderSummaryResponse } from "@/api/generated/types.gen";
import { renderWithRouter } from "@/test/render";

import { OrderHistory } from "./OrderHistory";

const rice: OrderSummaryResponse = {
  orderId: "0192f0a0-0000-7000-8000-000000000001",
  orderNo: "BB-2091",
  dealId: "0192f0a0-0000-7000-8000-0000000000d1",
  productId: "0b6f2c1e-6b1a-4f0e-9d1c-2a9c3e5f7a11",
  productTitle: "Thai jasmine rice 10 kg",
  productImage: "https://example.test/rice.jpg",
  quantity: 2,
  unitPrice: "71.00",
  totalAmount: "142.00",
  currency: "sgd",
  status: "pending",
  paidTime: "2026-09-16T11:46:00Z",
  cancelTime: null,
  createTime: "2026-09-16T11:45:00Z",
};

const milk: OrderSummaryResponse = {
  ...rice,
  orderId: "0192f0a0-0000-7000-8000-000000000002",
  orderNo: "BB-2092",
  productId: "0b6f2c1e-6b1a-4f0e-9d1c-2a9c3e5f7a12",
  productTitle: "Hokkaido milk, 12 cartons",
  productImage: null,
  quantity: 1,
  totalAmount: "39.60",
  status: "cancelled",
};

const { paidTime: _paid, ...unpaid } = rice;
const both = [rice, milk];
const riceOnly = [rice];
const milkOnly = [milk];
const unpaidOnly = [unpaid];

describe("OrderHistory", () => {
  it("lists one row per order with the product linked to its listing", async () => {
    await renderWithRouter(<OrderHistory orders={both} />);
    const rows = within(screen.getByRole("list", { name: "My orders" })).getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(screen.getByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveAttribute(
      "href",
      `/products/${rice.productId}`,
    );
  });

  it("shows quantity, total, order number, paid time and status on a row", async () => {
    await renderWithRouter(<OrderHistory orders={riceOnly} />);
    const row = screen.getByRole("listitem");
    expect(within(row).getByText("S$142.00")).toBeVisible();
    expect(within(row).getByText("2 seats · Order BB-2091")).toBeVisible();
    expect(within(row).getByText("Paid · 16 Sep, 7.46 pm")).toBeVisible();
    expect(within(row).getByText("Waiting for the bloc")).toBeVisible();
  });

  it("says one seat in the singular", async () => {
    await renderWithRouter(<OrderHistory orders={milkOnly} />);
    expect(screen.getByText("1 seat · Order BB-2092")).toBeVisible();
    expect(screen.getByText("Cancelled")).toBeVisible();
  });

  it("carries no refund label (option B on #128)", async () => {
    await renderWithRouter(<OrderHistory orders={both} />);
    expect(screen.queryByText(/refund/i)).toBeNull();
  });

  it("leaves out a paid line the order does not have", async () => {
    await renderWithRouter(<OrderHistory orders={unpaidOnly} />);
    expect(screen.queryByText(/^Paid/)).toBeNull();
  });
});
