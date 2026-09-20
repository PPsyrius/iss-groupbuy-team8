import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import type { Receipt } from "@/api/generated/types.gen";
import { clearCompletedAttempt, lastPaymentKey } from "@/features/payment/use-checkout";
import { server } from "@/test/msw";
import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

const productId = "00000000-0000-4000-8000-000000000010";
const paymentId = "00000000-0000-4000-8000-000000000020";
const intentId = "00000000-0000-4000-8000-000000000030";
const dealId = "00000000-0000-4000-8000-000000000040";
const ok = (data: unknown) => HttpResponse.json({ code: OK_CODE, message: "ok", data });

beforeEach(() => {
  sessionStorage.clear();
  server.use(http.get("*/api/v1/payments/capabilities", () => ok({ enabled: true })));
});
afterEach(() => vi.restoreAllMocks());

it("shows a completed refund without claiming active bloc membership", async () => {
  serveSignedIn();
  receipt({
    status: "SUCCEEDED",
    refundStatus: "SUCCEEDED",
    refundPending: false,
    orderId: intentId,
  });
  renderRoute(`/payments/${paymentId}`);
  expect(await screen.findByRole("heading", { name: "Refund sent" })).toBeVisible();
  expect(screen.getByText(/Refund sent to your original payment method/)).toBeVisible();
  expect(screen.queryByText(/bloc membership is confirmed/)).not.toBeInTheDocument();
});

function product() {
  server.use(
    http.get("*/api/v1/products/:id", () =>
      ok({
        id: productId,
        title: "Stripe integration product",
        individualPrice: 20,
        groupPrice: 10,
        currency: "sgd",
        stock: 10,
        deal: { dealId, currentSize: 1, targetSize: 2, expireTime: "2099-01-01T00:00:00Z" },
      }),
    ),
  );
}

function receipt(data: Partial<Receipt>) {
  server.use(
    http.get(`*/api/v1/payments/${paymentId}`, () =>
      ok({ paymentId, amount: "10.00", currency: "sgd", ...data }),
    ),
  );
}

describe("teammate product page payment integration", () => {
  it.each(["START", "JOIN"])("%s sends identifiers only, then opens Stripe", async (operation) => {
    serveSignedIn();
    product();
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const bodies: unknown[] = [];
    server.use(
      http.post("*/api/v1/payments/intents", async ({ request }) => {
        bodies.push(await request.json());
        expect(request.headers.get("X-BlocBuy-Request")).toBe("checkout");
        return ok({ intentId });
      }),
      http.post("*/api/v1/payments/checkout-sessions", async ({ request }) => {
        bodies.push(await request.json());
        expect(request.headers.get("X-BlocBuy-Request")).toBe("checkout");
        expect(request.headers.get("Idempotency-Key")).toMatch(/^[\da-f-]{36}$/);
        return ok({
          paymentId,
          status: "CREATED",
          checkoutUrl: "https://checkout.stripe.com/c/pay/test",
        });
      }),
    );
    renderRoute(`/products/${productId}`);
    const buttons = await screen.findAllByRole("button", {
      name: operation === "START" ? /Start a bloc/ : /^Join/,
    });
    await waitFor(() => expect(buttons[0]).toBeEnabled());
    await userEvent.click(buttons[0]!);
    await waitFor(() => expect(assign).toHaveBeenCalledOnce());
    expect(bodies).toEqual([
      { operation, businessId: operation === "START" ? productId : dealId },
      { intentId },
    ]);
    expect(sessionStorage.getItem(lastPaymentKey)).toBe(paymentId);
    for (const button of screen.getAllByRole("button", { name: /Buy alone/ }))
      expect(button).toBeDisabled();
  });

  it("retries a lost Checkout response using the same intent and idempotency key", async () => {
    serveSignedIn();
    product();
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    let intents = 0;
    const keys: (string | null)[] = [];
    server.use(
      http.post("*/api/v1/payments/intents", () => {
        intents++;
        return ok({ intentId });
      }),
      http.post("*/api/v1/payments/checkout-sessions", ({ request }) => {
        keys.push(request.headers.get("Idempotency-Key"));
        return keys.length === 1
          ? HttpResponse.error()
          : ok({
              paymentId,
              status: "CREATED",
              checkoutUrl: "https://checkout.stripe.com/c/pay/test",
            });
      }),
    );
    renderRoute(`/products/${productId}`);
    const [button] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(button).toBeEnabled());
    // Two synchronous clicks must share one in-flight attempt.
    fireEvent.click(button!);
    fireEvent.click(button!);
    await screen.findByRole("alert");
    await userEvent.click(button!);
    await waitFor(() => expect(assign).toHaveBeenCalledOnce());
    expect(intents).toBe(1);
    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  it("refuses a non-Stripe checkout URL", async () => {
    serveSignedIn();
    product();
    receipt({ status: "CREATED" });
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    server.use(
      http.post("*/api/v1/payments/intents", () => ok({ intentId })),
      http.post("*/api/v1/payments/checkout-sessions", () =>
        ok({ paymentId, status: "CREATED", checkoutUrl: "https://example.com/not-stripe" }),
      ),
    );
    renderRoute(`/products/${productId}`);
    const [start] = await screen.findAllByRole("button", { name: /Start a bloc/ });
    await waitFor(() => expect(start).toBeEnabled());
    await userEvent.click(start!);
    await screen.findByRole("heading", { name: "Waiting for payment confirmation" });
    expect(assign).not.toHaveBeenCalled();
  });
});

describe("backend-confirmed payment result", () => {
  it("does not infer payment from the return URL; refresh shows the saved order", async () => {
    serveSignedIn();
    receipt({ status: "CREATED" });
    renderRoute(`/payments/${paymentId}?paid=true`);
    await screen.findByRole("heading", { name: "Waiting for payment confirmation" });
    expect(screen.queryByText(/order has been saved/)).not.toBeInTheDocument();
    receipt({ status: "SUCCEEDED", orderId: "saved-order", dealId });
    await userEvent.click(screen.getByRole("button", { name: "Refresh status" }));
    expect(await screen.findByText("saved-order")).toBeInTheDocument();
    expect(screen.getByText(dealId)).toBeInTheDocument();
  });

  it("shows a pending refund without claiming it was sent", async () => {
    serveSignedIn();
    receipt({ status: "SUCCEEDED", refundPending: true });
    renderRoute(`/payments/${paymentId}`);
    expect(await screen.findByText(/A refund is pending/)).toHaveTextContent(
      "not confirmation that the refund has been sent",
    );
  });

  it("shows canceled unpaid Checkout, not successful payment", async () => {
    serveSignedIn();
    receipt({ status: "CANCELED" });
    renderRoute(`/payments/${paymentId}`);
    await screen.findByRole("heading", { name: "Checkout closed without payment" });
    expect(screen.queryByText("Payment received")).not.toBeInTheDocument();
  });

  it("does not leak receipt data to an anonymous visitor", async () => {
    serveAnonymous();
    const read = vi.fn<() => Response>(() => ok({ paymentId, status: "SUCCEEDED" }));
    server.use(http.get("*/api/v1/payments/:id", read));
    const { router } = renderRoute(`/payments/${paymentId}`);
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(read).not.toHaveBeenCalled();
  });

  it("handles an unavailable or somebody else's payment without a false success", async () => {
    serveSignedIn();
    server.use(
      http.get(`*/api/v1/payments/${paymentId}`, () =>
        HttpResponse.json({ code: "PAYMENT_404_001", message: "Not found" }, { status: 404 }),
      ),
    );
    renderRoute(`/payments/${paymentId}`);
    await screen.findByRole("heading", { name: "Unable to check payment" });
    expect(screen.queryByText("Payment received")).not.toBeInTheDocument();
  });

  it("restores the same-tab reference on return", async () => {
    serveSignedIn();
    receipt({ status: "SUCCEEDED", orderId: "saved-order" });
    sessionStorage.setItem(lastPaymentKey, paymentId);
    const { router } = renderRoute("/payments/return");
    await screen.findByText("saved-order");
    expect(router.state.location.pathname).toBe(`/payments/${paymentId}`);
  });

  it("does not guess a payment when the return reference is absent", async () => {
    serveSignedIn();
    renderRoute("/payments/return");
    expect(
      await screen.findByRole("heading", { name: "Payment reference unavailable" }),
    ).toBeInTheDocument();
  });

  it("uses the Checkout-specific return reference instead of another tab's last payment", async () => {
    serveSignedIn();
    receipt({ status: "SUCCEEDED", orderId: "correct-order" });
    sessionStorage.setItem(lastPaymentKey, intentId);
    const { router } = renderRoute(`/payments/return?paymentId=${paymentId}`);
    expect(await screen.findByText("correct-order")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/payments/${paymentId}`);
  });

  it("clears only the completed attempt, not another pending checkout", () => {
    const key = "blocbuy.payment.attempt.u1.START.product";
    sessionStorage.setItem(key, intentId);
    sessionStorage.setItem(`${key}.request`, "key");
    sessionStorage.setItem(`${key}.payment`, paymentId);
    sessionStorage.setItem("blocbuy.payment.attempt.other", "keep");
    clearCompletedAttempt(paymentId);
    expect(sessionStorage.getItem(key)).toBeNull();
    expect(sessionStorage.getItem(`${key}.request`)).toBeNull();
    expect(sessionStorage.getItem("blocbuy.payment.attempt.other")).toBe("keep");
  });
});
