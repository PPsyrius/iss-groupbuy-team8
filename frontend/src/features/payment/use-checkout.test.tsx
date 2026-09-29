import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { useEffect } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { installAuthInterceptor } from "@/api/client";
import { OK_CODE } from "@/api/envelope";
import { useCheckout } from "@/features/payment/use-checkout";
import { parkInvite, readParkedInvite } from "@/features/share/invite-ref";
import { server } from "@/test/msw";

vi.mock("@tanstack/react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-router")>();
  return { ...actual, useNavigate: () => vi.fn<() => Promise<void>>() };
});

// The production interceptor turns non-2xx envelopes into `ApiError` (status + code); the hook's
// 409 handling is only reachable through it, so register it on the module-level client once.
installAuthInterceptor(new QueryClient());

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

async function signedInCheckout() {
  const checkoutRef = renderProbe("buyer-1");
  await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
  return checkoutRef;
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

  it("frees the cached intent after a 409 so the next attempt freezes a fresh one", async () => {
    const attemptKey = `blocbuy.payment.attempt.buyer-1.START.${businessId}`;
    serveCheckout();
    server.use(
      http.post("*/api/v1/payments/checkout-sessions", () =>
        HttpResponse.json(
          { code: "PAYMENT_409_001", message: "Payment state invalid" },
          { status: 409 },
        ),
      ),
    );
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("START", businessId);
    });
    expect(intents).toBe(1);
    expect(sessionStorage.getItem(attemptKey)).toBeNull();
    expect(sessionStorage.getItem(`${attemptKey}.request`)).toBeNull();
    await waitFor(() => expect(checkoutRef.current?.error).not.toBeNull());

    serveCheckout();
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId));
    });
    expect(intents).toBe(2);
    expect(assign).toHaveBeenCalledOnce();
  });

  it("keeps the cached intent after any other failure", async () => {
    const attemptKey = `blocbuy.payment.attempt.buyer-1.START.${businessId}`;
    serveCheckout();
    server.use(
      http.post("*/api/v1/payments/checkout-sessions", () =>
        HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 }),
      ),
    );
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("START", businessId);
      await checkout.begin("START", businessId);
    });
    expect(intents).toBe(1);
    expect(sessionStorage.getItem(attemptKey)).toBe(intentId);
    expect(sessionStorage.getItem(`${attemptKey}.request`)).not.toBeNull();
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

describe("useCheckout invite ref (UC7)", () => {
  const ref = "Qm9vbS1pbnZpdGUtcmVm_-";
  let bodies: unknown[] = [];

  function serveRecordedCheckout() {
    bodies = [];
    serveCheckout();
    server.use(
      http.post("*/api/v1/payments/intents", async ({ request }) => {
        bodies.push(await request.json());
        return ok({ intentId });
      }),
    );
  }

  it("sends the parked ref on a JOIN of the invited bloc and keeps it parked", async () => {
    serveRecordedCheckout();
    parkInvite({ productId: "p-1", dealId: businessId, ref });
    vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("JOIN", businessId);
    });
    expect(bodies).toEqual([{ operation: "JOIN", businessId, ref }]);
    expect(readParkedInvite()?.ref).toBe(ref);
  });

  it("sends the ref again on the intent that replaces a stale one", async () => {
    serveRecordedCheckout();
    server.use(
      http.post("*/api/v1/payments/checkout-sessions", () =>
        HttpResponse.json(
          { code: "PAYMENT_409_001", message: "Payment state invalid" },
          { status: 409 },
        ),
      ),
    );
    parkInvite({ productId: "p-1", dealId: businessId, ref });
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("JOIN", businessId);
    });
    await waitFor(() => expect(checkoutRef.current?.error).not.toBeNull());
    expect(bodies).toEqual([{ operation: "JOIN", businessId, ref }]);

    serveRecordedCheckout();
    vi.spyOn(window.location, "assign").mockImplementation(() => {});
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("JOIN", businessId));
    });
    expect(bodies).toEqual([{ operation: "JOIN", businessId, ref }]);
  });

  it("keeps the ref when the intent could not be frozen, so a retry still carries it", async () => {
    serveRecordedCheckout();
    server.use(http.post("*/api/v1/payments/intents", () => HttpResponse.error()));
    parkInvite({ productId: "p-1", dealId: businessId, ref });
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("JOIN", businessId);
    });
    expect(readParkedInvite()?.ref).toBe(ref);
  });

  it("never sends a ref on START or for another bloc", async () => {
    serveRecordedCheckout();
    parkInvite({ productId: "p-1", dealId: "00000000-0000-4000-8000-0000000000ff", ref });
    vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const checkoutRef = renderProbe("buyer-1");
    await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
    const checkout = await readyCheckout(checkoutRef);
    await act(async () => {
      await checkout.begin("JOIN", businessId);
    });
    parkInvite({ productId: "p-1", dealId: businessId, ref });
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId));
    });
    expect(bodies).toEqual([
      { operation: "JOIN", businessId },
      { operation: "START", businessId },
    ]);
    expect(readParkedInvite()?.ref).toBe(ref);
  });
});

describe("useCheckout quote honouring (US26, #183)", () => {
  const quoteA = "0192f0a0-0000-7000-8000-00000000000a";
  const quoteB = "0192f0a0-0000-7000-8000-00000000000b";
  let bodies: unknown[] = [];

  function serveRecordedCheckout(checkoutStatus = 200) {
    bodies = [];
    serveCheckout();
    server.use(
      http.post("*/api/v1/payments/intents", async ({ request }) => {
        intents++;
        bodies.push(await request.json());
        return ok({ intentId });
      }),
    );
    if (checkoutStatus !== 200) {
      server.use(
        http.post("*/api/v1/payments/checkout-sessions", () =>
          HttpResponse.json(
            { code: "COMMON_500_000", message: "boom" },
            { status: checkoutStatus },
          ),
        ),
      );
    }
  }

  it("sends the shown quote on START so the server charges that price", async () => {
    serveRecordedCheckout();
    vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const checkoutRef = await signedInCheckout();
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId, quoteA));
    });
    expect(bodies).toEqual([{ operation: "START", businessId, quoteId: quoteA }]);
  });

  it("never sends a quote on JOIN, which pays the bloc's frozen price", async () => {
    serveRecordedCheckout();
    vi.spyOn(window.location, "assign").mockImplementation(() => {});
    const checkoutRef = await signedInCheckout();
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("JOIN", businessId, quoteA));
    });
    expect(bodies).toEqual([{ operation: "JOIN", businessId }]);
  });

  it("reuses the intent for a retry of the same quote but freezes a new one for a new quote", async () => {
    serveRecordedCheckout(500);
    const checkoutRef = await signedInCheckout();
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId, quoteA));
    });
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId, quoteA));
    });
    expect(intents).toBe(1);
    await act(async () => {
      await readyCheckout(checkoutRef).then((c) => c.begin("START", businessId, quoteB));
    });
    expect(intents).toBe(2);
    expect(bodies).toEqual([
      { operation: "START", businessId, quoteId: quoteA },
      { operation: "START", businessId, quoteId: quoteB },
    ]);
  });

  it("keeps a catalog refusal on JOIN on the page-level alert", async () => {
    serveCheckout();
    server.use(
      http.post("*/api/v1/payments/intents", () =>
        HttpResponse.json(
          { code: "CATALOG_409_001", message: "Team price is unavailable" },
          { status: 409 },
        ),
      ),
    );
    const checkoutRef = await signedInCheckout();
    let outcome: string | undefined;
    await act(async () => {
      outcome = await readyCheckout(checkoutRef).then((c) => c.begin("JOIN", businessId));
    });
    expect(outcome).toBe("settled");
    await waitFor(() => expect(checkoutRef.current?.error).toMatch(/We could not open Checkout/));
  });

  it.each([
    ["CATALOG_409_007", 409, "Team price quote has expired"],
    ["CATALOG_404_002", 404, "Team price quote not found"],
    ["CATALOG_409_001", 409, "Team price is unavailable"],
  ])(
    "reports a refused quote (%s) to the sheet instead of the page",
    async (code, status, message) => {
      serveCheckout();
      server.use(
        http.post("*/api/v1/payments/intents", () =>
          HttpResponse.json({ code, message }, { status }),
        ),
      );
      const onRejected = vi.fn<() => void>();
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      const checkoutRef: { current: Checkout | null } = { current: null };
      function RejectingProbe() {
        const checkout = useCheckout("buyer-1", onRejected);
        useEffect(() => {
          checkoutRef.current = checkout;
        }, [checkout]);
        return null;
      }
      render(
        <QueryClientProvider client={queryClient}>
          <RejectingProbe />
        </QueryClientProvider>,
      );
      await waitFor(() => expect(checkoutRef.current?.enabled).toBe(true));
      let outcome: string | undefined;
      await act(async () => {
        outcome = await readyCheckout(checkoutRef).then((c) =>
          c.begin("START", businessId, quoteA),
        );
      });
      expect(outcome).toBe("quote-rejected");
      expect(checkoutRef.current?.error).toBeNull();
      expect(onRejected).not.toHaveBeenCalled();
    },
  );
});
