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
export function quoteStorageKey(userId: string, productId: string) {
  return `blocbuy.quote.${userId}.${productId}`;
}

// Issuing is not idempotent (each call is a new snapshot), so a live stored quote is re-read
// before a new one is issued. `getPolicy` is deliberately not called: it needs
// `catalog:product:write`, which buyers never hold (backend/docs/design/team-price-quotes.md).
export function useQuote(userId: string | undefined, productId: string) {
  const queryClient = useQueryClient();
  const issue = useMutation(issueMutation());
  const busy = useRef(false);
  const [state, setState] = useState<QuoteState>({ status: "idle" });

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
    if (busy.current || !userId) return;
    busy.current = true;
    setState({ status: "loading" });
    const key = quoteStorageKey(userId, productId);
    try {
      let quote = fresh ? null : await readStored(key);
      if (!quote) {
        quote = readQuote(
          unwrapResult(await issue.mutateAsync({ body: { productId } }), { allowVoid: false }),
        );
        if (!quote) throw new Error("Malformed quote");
        sessionStorage.setItem(key, quote.id);
      }
      setState({ status: "ready", quote });
    } catch (error) {
      setState({ status: quoteFailure(error) });
    } finally {
      busy.current = false;
    }
  }

  return {
    state,
    load: () => obtain(false),
    refresh: () => obtain(true),
  };
}
