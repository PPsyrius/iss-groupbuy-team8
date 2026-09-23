import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { useCheckout } from "@/features/payment/use-checkout";
import { server } from "@/test/msw";

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-router")>();
  return { ...actual, useNavigate: () => vi.fn<() => Promise<void>>() };
});

type Checkout = ReturnType<typeof useCheckout>;

function Probe({
  userId,
  checkoutRef,
}: {
  userId: string | undefined;
  checkoutRef: { current: Checkout | null };
}) {
  const checkout = useCheckout(userId);
  useEffect(() => {
    checkoutRef.current = checkout;
  }, [checkout, checkoutRef]);
  return null;
}

const businessId = "00000000-0000-4000-8000-000000000010";
const intentId = "00000000-0000-4000-8000-000000000030";
const ok = (data: unknown) => HttpResponse.json({ code: OK_CODE, message: "ok", data });

let intents = 0;

function serveCheckout() {
  server.use(
    http.post("*/api/v1/payments/intents", () => {
      intents++;
      return ok({ intentId });
    }),
    http.post("*/api/v1/payments/checkout-sessions", () =>
      ok({
        paymentId: "00000000-0000-4000-8000-000000000020",
        status: "CREATED",
        checkoutUrl: "https://checkout.stripe.com/c/pay/test",
      }),
    ),
  );
}

function renderProbe(userId: string | undefined) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const checkoutRef: { current: Checkout | null } = { current: null };
  render(
    <QueryClientProvider client={queryClient}>
      <Probe userId={userId} checkoutRef={checkoutRef} />
    </QueryClientProvider>,
  );
  return checkoutRef;
}

async function readyCheckout(checkoutRef: { current: Checkout | null }) {
  await waitFor(() => expect(checkoutRef.current).not.toBeNull());
  const checkout = checkoutRef.current;
  if (!checkout) throw new Error("probe not mounted");
  return checkout;
}

beforeEach(() => {
  intents = 0;
  sessionStorage.clear();
  server.use(
    http.get("*/api/v1/auth/me", () =>
      HttpResponse.json({
        code: OK_CODE,
        message: "ok",
        data: { userId: "buyer-1", nickname: null, avatarUrl: null },
      }),
    ),
    http.get("*/api/v1/payments/capabilities", () => ok({ enabled: true })),
  );
});

describe("useCheckout begin guard", () => {
  it("shares one in-flight attempt across concurrent calls", async () => {
    serveCheckout();
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await Promise.all([checkout.begin("START", businessId), checkout.begin("START", businessId)]);
    });
    expect(intents).toBe(1);
    expect(assign).toHaveBeenCalledOnce();
  });

  it("returns early without a buyer", async () => {
    serveCheckout();
    const checkoutRef = renderProbe(undefined);
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("START", businessId);
    });
    expect(intents).toBe(0);
  });

  it("returns early when payments are unavailable", async () => {
    server.use(http.get("*/api/v1/payments/capabilities", () => ok({ enabled: false })));
    const checkoutRef = renderProbe("buyer-1");
    const checkout = await readyCheckout(checkoutRef);
    expect(checkout.enabled).toBe(false);
    await act(async () => {
      await checkout.begin("START", businessId);
    });
    expect(intents).toBe(0);
  });
});
