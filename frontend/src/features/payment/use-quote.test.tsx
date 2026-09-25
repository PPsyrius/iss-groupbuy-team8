import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { useEffect } from "react";
import { beforeEach, describe, expect, it } from "vitest";

import { installAuthInterceptor } from "@/api/client";
import { OK_CODE } from "@/api/envelope";
import { quoteStorageKey, useQuote } from "@/features/payment/use-quote";
import { server } from "@/test/msw";

// Non-2xx envelopes become `ApiError` only through the production interceptor.
installAuthInterceptor(new QueryClient());

type Quoting = ReturnType<typeof useQuote>;

function Probe({
  userId,
  product = productId,
  quotingRef,
}: {
  userId: string | undefined;
  product?: string;
  quotingRef: { current: Quoting | null };
}) {
  const quoting = useQuote(userId, product);
  useEffect(() => {
    quotingRef.current = quoting;
  }, [quoting, quotingRef]);
  return null;
}

const productId = "0192f0a0-0000-7000-8000-000000000010";
const otherProductId = "0192f0a0-0000-7000-8000-000000000011";
const userId = "buyer-1";
const ok = (data: unknown) => HttpResponse.json({ code: OK_CODE, message: "ok", data });
const inMinutes = (m: number) => new Date(Date.now() + m * 60_000).toISOString();
const quote = (id: string, expiresAt = inMinutes(15)) => ({
  id,
  productId,
  policyVersion: 1,
  amount: "71.00",
  currency: "sgd",
  issuedAt: new Date().toISOString(),
  expiresAt,
});

let issued = 0;
let reads = 0;

function serveQuotes(stored = quote("q-stored")) {
  server.use(
    http.post("*/api/v1/team-price-quotes", () => {
      issued++;
      return ok(quote(`q-${issued}`));
    }),
    http.get("*/api/v1/team-price-quotes/:quoteId", ({ params }) => {
      reads++;
      return params.quoteId === stored.id
        ? ok(stored)
        : HttpResponse.json({ code: "CATALOG_404_002", message: "not found" }, { status: 404 });
    }),
  );
}

function renderProbe(user: string | undefined) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const quotingRef: { current: Quoting | null } = { current: null };
  render(
    <QueryClientProvider client={queryClient}>
      <Probe userId={user} quotingRef={quotingRef} />
    </QueryClientProvider>,
  );
  return quotingRef;
}

async function run(quotingRef: { current: Quoting | null }, action: "load" | "refresh") {
  await waitFor(() => expect(quotingRef.current).not.toBeNull());
  await act(async () => {
    await quotingRef.current![action]();
  });
  return quotingRef.current!.state;
}

beforeEach(() => {
  issued = 0;
  reads = 0;
  sessionStorage.clear();
});

describe("useQuote", () => {
  it("issues a quote and remembers its id for a reload", async () => {
    serveQuotes();
    const state = await run(renderProbe(userId), "load");
    expect(state).toMatchObject({ status: "ready", quote: { id: "q-1", amount: 71 } });
    expect(sessionStorage.getItem(quoteStorageKey(userId, productId))).toBe("q-1");
  });

  it("re-reads a stored quote that is still live instead of issuing another", async () => {
    serveQuotes();
    sessionStorage.setItem(quoteStorageKey(userId, productId), "q-stored");
    const state = await run(renderProbe(userId), "load");
    expect(state).toMatchObject({ status: "ready", quote: { id: "q-stored" } });
    expect(reads).toBe(1);
    expect(issued).toBe(0);
  });

  it("issues a fresh quote when the stored one has expired", async () => {
    serveQuotes(quote("q-stored", inMinutes(-1)));
    sessionStorage.setItem(quoteStorageKey(userId, productId), "q-stored");
    const state = await run(renderProbe(userId), "load");
    expect(state).toMatchObject({ status: "ready", quote: { id: "q-1" } });
  });

  it("issues a fresh quote when the stored one is unknown", async () => {
    serveQuotes();
    sessionStorage.setItem(quoteStorageKey(userId, productId), "q-gone");
    const state = await run(renderProbe(userId), "load");
    expect(state).toMatchObject({ status: "ready", quote: { id: "q-1" } });
  });

  it("always issues on refresh", async () => {
    serveQuotes();
    sessionStorage.setItem(quoteStorageKey(userId, productId), "q-stored");
    const state = await run(renderProbe(userId), "refresh");
    expect(state).toMatchObject({ status: "ready", quote: { id: "q-1" } });
    expect(reads).toBe(0);
  });

  it("reports no price set when the policy is unavailable (409)", async () => {
    server.use(
      http.post("*/api/v1/team-price-quotes", () =>
        HttpResponse.json(
          { code: "CATALOG_409_001", message: "Team price is unavailable" },
          { status: 409 },
        ),
      ),
    );
    expect(await run(renderProbe(userId), "load")).toEqual({ status: "unavailable" });
  });

  it("reports a retryable error for any other failure", async () => {
    server.use(http.post("*/api/v1/team-price-quotes", () => HttpResponse.error()));
    expect(await run(renderProbe(userId), "load")).toEqual({ status: "error" });
  });

  it("forgets the previous listing's quote when the listing changes", async () => {
    serveQuotes();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const quotingRef: { current: Quoting | null } = { current: null };
    const probe = (product: string) => (
      <QueryClientProvider client={queryClient}>
        <Probe userId={userId} product={product} quotingRef={quotingRef} />
      </QueryClientProvider>
    );
    const { rerender } = render(probe(productId));
    expect(await run(quotingRef, "load")).toMatchObject({ status: "ready" });
    rerender(probe(otherProductId));
    await waitFor(() => expect(quotingRef.current!.state).toEqual({ status: "idle" }));
  });

  it("drops a late quote for the previous listing and still quotes the new one", async () => {
    let releaseFirst!: () => void;
    const firstHeld = new Promise<void>((resolve) => {
      releaseFirst = resolve;
    });
    server.use(
      http.post("*/api/v1/team-price-quotes", async () => {
        issued++;
        const id = `q-${issued}`;
        if (issued === 1) await firstHeld;
        return ok(quote(id));
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const quotingRef: { current: Quoting | null } = { current: null };
    const probe = (product: string) => (
      <QueryClientProvider client={queryClient}>
        <Probe userId={userId} product={product} quotingRef={quotingRef} />
      </QueryClientProvider>
    );
    const { rerender } = render(probe(productId));
    await waitFor(() => expect(quotingRef.current).not.toBeNull());
    let first!: Promise<void>;
    act(() => {
      first = quotingRef.current!.load();
    });
    await waitFor(() => expect(issued).toBe(1));
    rerender(probe(otherProductId));
    await waitFor(() => expect(quotingRef.current!.state).toEqual({ status: "idle" }));
    expect(await run(quotingRef, "load")).toMatchObject({
      status: "ready",
      quote: { id: "q-2" },
    });
    await act(async () => {
      releaseFirst();
      await first;
    });
    expect(quotingRef.current!.state).toMatchObject({ status: "ready", quote: { id: "q-2" } });
    expect(sessionStorage.getItem(quoteStorageKey(userId, productId))).toBe("q-1");
  });

  it("does nothing without a buyer", async () => {
    serveQuotes();
    expect(await run(renderProbe(undefined), "load")).toEqual({ status: "idle" });
    expect(issued).toBe(0);
  });
});
