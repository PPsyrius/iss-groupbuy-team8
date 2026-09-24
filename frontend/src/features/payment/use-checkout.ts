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

export const lastPaymentKey = "blocbuy.payment.last";
const attemptPrefix = "blocbuy.payment.attempt.";
// PaymentErrorCode.PAYMENT_STATE_INVALID (HTTP 409): the frozen intent can no longer be checked
// out (quote expired, or a session would outlive it), so re-sending the same intentId never works.
const staleIntentCode = "PAYMENT_409_001";

function isStaleIntent(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 409 || error.code === staleIntentCode);
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

export function useCheckout(userId: string | undefined) {
  const enabled = usePaymentAvailability();
  const intent = useMutation(createPaymentIntentMutation());
  const checkout = useMutation(createPaymentCheckoutMutation());
  const navigate = useNavigate();
  const busy = useRef(false);
  const [isPending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function begin(operation: IntentRequest["operation"], businessId: string) {
    if (busy.current || !userId || !enabled) return;
    busy.current = true;
    setPending(true);
    setError(null);
    const key = `${attemptPrefix}${userId}.${operation}.${businessId}`;
    try {
      let intentId = sessionStorage.getItem(key);
      if (!intentId) {
        const created = unwrapResult(
          await intent.mutateAsync({
            body: { operation, businessId },
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
        return;
      }
      const destination = new URL(result.checkoutUrl);
      if (destination.protocol !== "https:" || destination.hostname !== "checkout.stripe.com") {
        throw new Error("Invalid payment destination");
      }
      window.location.assign(destination.href);
    } catch (checkoutError) {
      const knownPayment = sessionStorage.getItem(`${key}.payment`);
      if (knownPayment) {
        await navigate({ to: "/payments/$paymentId", params: { paymentId: knownPayment } });
        return;
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
    } finally {
      busy.current = false;
      setPending(false);
    }
  }

  return { begin, isPending, error, enabled };
}
