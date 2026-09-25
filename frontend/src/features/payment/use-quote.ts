// Decision record: frontend/docs/adr/0014-stripe-hosted-checkout-server-owned-intent.md
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";

import { unwrapResult } from "@/api/envelope";
import { getQuoteOptions, issueMutation } from "@/api/generated/@tanstack/react-query.gen";
import { type Quote, quoteFailure, readQuote } from "@/features/payment/quote";

export type QuoteState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "ready"; quote: Quote }
  | { status: "unavailable" }
  | { status: "error" };

// Only the quote id is stored (no amount): a reload re-reads the owner-scoped snapshot, so the
// sheet never shows a price the server did not just confirm.
const idle: QuoteState = { status: "idle" };

export function quoteStorageKey(userId: string, productId: string) {
  return `blocbuy.quote.${userId}.${productId}`;
}

// Issuing is not idempotent (each call is a new snapshot), so a live stored quote is re-read
// before a new one is issued. `getPolicy` is deliberately not called: it needs
// `catalog:product:write`, which buyers never hold (backend/docs/design/team-price-quotes.md).
// State is tagged with the buyer and product it was obtained for: the listing route keeps this
// hook mounted across `$productId` changes, so a quote (or a late reply) for the previous
// listing reads as idle instead of pricing the new one (CodeRabbit r4102738802).
export function useQuote(userId: string | undefined, productId: string) {
  const queryClient = useQueryClient();
  const issue = useMutation(issueMutation());
  const inFlight = useRef<string | null>(null);
  const [tagged, setTagged] = useState<{ key: string; state: QuoteState }>({
    key: "",
    state: idle,
  });
  const current = quoteStorageKey(userId ?? "", productId);

  async function readStored(key: string): Promise<Quote | null> {
    const quoteId = sessionStorage.getItem(key);
    if (!quoteId) return null;
    try {
      const stored = readQuote(
        unwrapResult(
          await queryClient.fetchQuery({ ...getQuoteOptions({ path: { quoteId } }), staleTime: 0 }),
          { allowVoid: false },
        ),
      );
      return stored && stored.expiresMs > Date.now() ? stored : null;
    } catch {
      // Unknown or foreign ids answer 404; either way a fresh quote replaces it.
      return null;
    }
  }

  async function obtain(fresh: boolean) {
    if (!userId || inFlight.current === current) return;
    const key = current;
    inFlight.current = key;
    setTagged({ key, state: { status: "loading" } });
    // A reply lands only while its own request is still the latest one.
    const settle = (state: QuoteState) =>
      setTagged((prev) => (prev.key === key ? { key, state } : prev));
    try {
      let quote = fresh ? null : await readStored(key);
      if (!quote) {
        quote = readQuote(
          unwrapResult(await issue.mutateAsync({ body: { productId } }), { allowVoid: false }),
        );
        if (!quote) throw new Error("Malformed quote");
        sessionStorage.setItem(key, quote.id);
      }
      settle({ status: "ready", quote });
    } catch (error) {
      settle({ status: quoteFailure(error) });
    } finally {
      if (inFlight.current === key) inFlight.current = null;
    }
  }

  return {
    state: tagged.key === current ? tagged.state : idle,
    load: () => obtain(false),
    refresh: () => obtain(true),
  };
}
