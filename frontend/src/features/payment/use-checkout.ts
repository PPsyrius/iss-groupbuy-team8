// Decision record: frontend/docs/adr/0014-stripe-hosted-checkout-server-owned-intent.md
import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useRef, useState } from "react";

import { ApiError, unwrapResult } from "@/api/envelope";
import {
  createPaymentCheckoutMutation,
  createPaymentIntentMutation,
} from "@/api/generated/@tanstack/react-query.gen";
import type { IntentRequest } from "@/api/generated/types.gen";
import { usePaymentAvailability } from "@/features/payment/use-payment-availability";
import { parkedRefFor } from "@/features/share/invite-ref";

export const lastPaymentKey = "blocbuy.payment.last";
const attemptPrefix = "blocbuy.payment.attempt.";
// PaymentErrorCode.PAYMENT_STATE_INVALID (HTTP 409): the frozen intent can no longer be checked
// out (quote expired, or a session would outlive it), so re-sending the same intentId never works.
const staleIntentCode = "PAYMENT_409_001";

// GroupbuyErrorCode: the server's JOIN admission (`lockJoinTerms`) runs when the intent is created
// and again when Checkout is prepared, before an earlier request is looked up; either way this
// attempt opened no payment, so the buyer is told why instead of being told to retry. The claim is
// scoped to this attempt: full and closed are checked before membership, so a buyer who already
// paid for a seat (another tab, an earlier visit) can get _004 or _002 too. The second sentence is
// the Errors artboard's paid-seat line (design-screens.md §4.5).
const notChargedNow =
  "This attempt was not charged. Any bloc you have already paid into is safe and still listed under My blocs.";
const joinRejections: Record<string, string> = {
  GROUPBUY_404_001: `This bloc no longer exists. ${notChargedNow}`,
  GROUPBUY_409_002: `This bloc is no longer taking members. ${notChargedNow}`,
  GROUPBUY_409_003: "You are already in this bloc. Nothing new has been charged.",
  GROUPBUY_409_004: `This bloc filled up before you joined. ${notChargedNow}`,
};

// CatalogErrorCode, from START with a quote id (US26, #183): the quote lapsed (_409_007), is not
// this buyer's quote for this listing (_404_002), or no longer undercuts the listing price
// (_409_001). No intent was frozen, so the sheet asks for a new price instead of a retry.
const quoteRejections = new Set(["CATALOG_409_007", "CATALOG_404_002", "CATALOG_409_001"]);

// "quote-rejected" leaves the quote sheet open on its "Get a new price" state; every other end
// (Stripe, the receipt, or the page-level alert) is "settled".
export type CheckoutOutcome = "settled" | "quote-rejected";

function isQuoteRejection(error: unknown): boolean {
  return error instanceof ApiError && quoteRejections.has(error.code);
}

function joinRejection(error: unknown): string | undefined {
  return error instanceof ApiError ? joinRejections[error.code] : undefined;
}

function isStaleIntent(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 409 || error.code === staleIntentCode);
}

// UC7 (#30): a JOIN of the bloc the buyer was invited to carries the parked invite ref; the server
// freezes the sharer on the intent. START never sends one (the server would ignore it). The ref
// stays parked after the intent exists: a stale intent (PAYMENT_409_001) or a failed or expired
// payment needs a replacement intent, and that one must carry the ref too.
// US26 (#183): START carries the quote the sheet showed, and the server charges that amount. JOIN
// pays the bloc's frozen price and the server refuses a quote there, so it never sends one.
function intentRequest(
  operation: IntentRequest["operation"],
  businessId: string,
  quoteId: string | undefined,
): IntentRequest {
  if (operation === "START")
    return quoteId ? { operation, businessId, quoteId } : { operation, businessId };
  const ref = parkedRefFor(businessId);
  return ref ? { operation, businessId, ref } : { operation, businessId };
}

function stripeCheckoutDestination(checkoutUrl: string): URL {
  const destination = new URL(checkoutUrl);
  if (destination.protocol !== "https:" || destination.hostname !== "checkout.stripe.com") {
    throw new Error("Invalid payment destination");
  }
  return destination;
}

// Only non-secret references are stored. Ownership and all amounts are checked by the server.
// Keeping the same intent + key after a timeout prevents a retry creating another charge.
export function clearCompletedAttempt(paymentId: string) {
  for (const key of Object.keys(sessionStorage)) {
    if (key.startsWith(attemptPrefix) && sessionStorage.getItem(`${key}.payment`) === paymentId) {
      sessionStorage.removeItem(key);
      sessionStorage.removeItem(`${key}.request`);
      sessionStorage.removeItem(`${key}.payment`);
    }
  }
}

// `onRejected` runs after the server refuses the purchase itself (a full, closed or missing bloc),
// so the page can refetch the listing and show the bloc's current state.
export function useCheckout(userId: string | undefined, onRejected?: () => void) {
  const enabled = usePaymentAvailability();
  const intent = useMutation(createPaymentIntentMutation());
  const checkout = useMutation(createPaymentCheckoutMutation());
  const navigate = useNavigate();
  const busy = useRef(false);
  const [isPending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function begin(
    operation: IntentRequest["operation"],
    businessId: string,
    quoteId?: string,
  ): Promise<CheckoutOutcome> {
    if (busy.current || !userId || !enabled) return "settled";
    busy.current = true;
    setPending(true);
    setError(null);
    // An intent freezes one quote's amount, so a new quote needs its own intent: the attempt key
    // names the quote and a retry of the same quote still reuses its intent and idempotency key.
    const quoted = operation === "START" && quoteId ? `.${quoteId}` : "";
    const key = `${attemptPrefix}${userId}.${operation}.${businessId}${quoted}`;
    try {
      let intentId = sessionStorage.getItem(key);
      if (!intentId) {
        const created = unwrapResult(
          await intent.mutateAsync({
            body: intentRequest(operation, businessId, quoteId),
            headers: { "X-BlocBuy-Request": "checkout" },
          }),
          { allowVoid: false },
        );
        if (!created.intentId) throw new Error("Missing purchase reference");
        intentId = created.intentId;
        sessionStorage.setItem(key, intentId);
      }
      const requestKey = sessionStorage.getItem(`${key}.request`) ?? crypto.randomUUID();
      sessionStorage.setItem(`${key}.request`, requestKey);
      const result = unwrapResult(
        await checkout.mutateAsync({
          body: { intentId },
          headers: { "X-BlocBuy-Request": "checkout", "Idempotency-Key": requestKey },
        }),
        { allowVoid: false },
      );
      if (!result.paymentId) throw new Error("Missing payment reference");
      sessionStorage.setItem(lastPaymentKey, result.paymentId);
      sessionStorage.setItem(`${key}.payment`, result.paymentId);
      if (result.status !== "CREATED" || !result.checkoutUrl) {
        await navigate({ to: "/payments/$paymentId", params: { paymentId: result.paymentId } });
        return "settled";
      }
      window.location.assign(stripeCheckoutDestination(result.checkoutUrl).href);
      return "settled";
    } catch (checkoutError) {
      const knownPayment = sessionStorage.getItem(`${key}.payment`);
      if (knownPayment) {
        await navigate({ to: "/payments/$paymentId", params: { paymentId: knownPayment } });
        return "settled";
      }
      if (operation === "START" && isQuoteRejection(checkoutError)) return "quote-rejected";
      const rejection = joinRejection(checkoutError);
      if (rejection) {
        // The intent and idempotency key stay: a refusal at Checkout can follow a lost response
        // that already opened a payment, and a fresh intent would bypass the server's one
        // blocking attempt per intent.
        setError(rejection);
        onRejected?.();
        return "settled";
      }
      if (isStaleIntent(checkoutError)) {
        // No payment was ever created for this intent, so freeing both keys lets the next
        // begin() freeze a fresh intent under a fresh idempotency key. Every other failure
        // (timeouts included) keeps them: a retry must not create a second charge.
        sessionStorage.removeItem(key);
        sessionStorage.removeItem(`${key}.request`);
      }
      setError(
        "We could not open Checkout. Please retry the same action. Do not make another payment if you have already paid.",
      );
      return "settled";
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { begin, isPending, error, enabled };
}
