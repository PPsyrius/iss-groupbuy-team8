import { describe, expect, it } from "vitest";

import { receiptView } from "./receipt-view";

const paid = { paymentId: "p", status: "SUCCEEDED", amount: "17.89", currency: "sgd" };

describe("receiptView", () => {
  it("names a read failure without a payment claim", () => {
    const view = receiptView({ receipt: undefined, isError: true });
    expect(view.chip).toEqual({ label: "Unavailable", color: "danger" });
    expect(view.title).toBe("Unable to check payment");
    expect(view.moneyLead).toBe("Do not pay again to check the result.");
  });

  it("holds the seat claim until Stripe confirms while CREATED (Q24 A)", () => {
    const view = receiptView({ receipt: { status: "CREATED" }, isError: false });
    expect(view.chip).toEqual({ label: "Waiting", color: "default" });
    expect(view.title).toBe("Waiting for payment confirmation");
    expect(view.body).toMatch(/^Your seat is held once Stripe confirms\./);
    expect(view.moneyLead).toBe("No payment is taken twice.");
    expect(view.moneyDetail).toBe("Check My blocs before trying again.");
  });

  it("shows a paid receipt with its amount and the order's paid time", () => {
    const view = receiptView({
      receipt: { ...paid, orderId: "o" },
      isError: false,
      paidTime: "2026-09-16T11:46:00Z",
    });
    expect(view.chip).toEqual({ label: "Paid", color: "success" });
    expect(view.title).toBe("Payment received");
    expect(view.body).toBe("Your order has been saved and your bloc membership is confirmed.");
    expect(view.moneyLead).toBe("S$17.89 paid.");
    expect(view.moneyDetail).toBe("Paid · 16 Sep, 7.46 pm");
  });

  it("does not confirm an order that is not saved yet", () => {
    const view = receiptView({ receipt: paid, isError: false });
    expect(view.body).toMatch(/Order confirmation is not available yet/);
    expect(view.moneyDetail).toBeUndefined();
  });

  it("marks a sent refund as Refunded with the bank window", () => {
    const view = receiptView({
      receipt: { ...paid, refundStatus: "SUCCEEDED", orderId: "o" },
      isError: false,
    });
    expect(view.chip).toEqual({ label: "Refunded", color: "success" });
    expect(view.title).toBe("Refund sent");
    expect(view.moneyLead).toBe("S$17.89 refunded.");
    expect(view.moneyDetail).toBe("Timing depends on your bank, usually 5–10 business days.");
    expect(view.body).not.toMatch(/membership/);
  });

  it("marks a pending refund without claiming it was sent", () => {
    const view = receiptView({ receipt: { ...paid, refundPending: true }, isError: false });
    expect(view.chip).toEqual({ label: "Refund pending", color: "danger" });
    expect(view.body).toMatch(/not confirmation that the refund has been sent/);
  });

  it("sends a failed or reviewed refund to support", () => {
    for (const refundStatus of ["FAILED", "REVIEW"]) {
      const view = receiptView({ receipt: { ...paid, refundStatus }, isError: false });
      expect(view.chip).toEqual({ label: "Needs review", color: "danger" });
      expect(view.body).toMatch(/Keep your payment reference for support\.$/);
    }
  });

  it("separates a closed Checkout from a failed payment", () => {
    expect(receiptView({ receipt: { status: "CANCELED" }, isError: false })).toMatchObject({
      chip: { label: "Closed", color: "default" },
      title: "Checkout closed without payment",
      moneyLead: "No payment was taken.",
    });
    expect(receiptView({ receipt: { status: "FAILED" }, isError: false })).toMatchObject({
      chip: { label: "Failed", color: "danger" },
      title: "Payment not completed",
      moneyLead: "No payment was taken.",
    });
  });

  it("claims nothing about money for an unknown status", () => {
    const view = receiptView({ receipt: { status: "SOMETHING_NEW" }, isError: false });
    expect(view.chip).toEqual({ label: "SOMETHING_NEW", color: "default" });
    expect(view.moneyLead).toBe("Do not pay again to check the result.");
  });
});
