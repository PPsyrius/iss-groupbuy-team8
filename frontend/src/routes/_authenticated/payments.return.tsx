import { createFileRoute, Link, redirect } from "@tanstack/react-router";

import { ErrorCard, primaryAction, secondaryAction } from "@/components/ErrorCard";
import { paymentsReturnSearchSchema } from "@/features/payment/payments-return-search";
import { lastPaymentKey } from "@/features/payment/use-checkout";

export const Route = createFileRoute("/_authenticated/payments/return")({
  validateSearch: paymentsReturnSearchSchema,
  beforeLoad: ({ search }) => {
    // URL is only a reference, never proof of success; the receipt enforces ownership server-side.
    // Storage supports older already-issued local Checkout links without the new parameter.
    const paymentId = search.paymentId ?? sessionStorage.getItem(lastPaymentKey);
    if (paymentId && /^[0-9a-f-]{36}$/i.test(paymentId)) {
      throw redirect({ to: "/payments/$paymentId", params: { paymentId }, replace: true });
    }
  },
  component: ReferenceUnavailable,
});

// §4.6 fallback on the §4.5 card (design-screens §7 Q26 A): a neutral `Payment` chip, no HTTP
// code, and the same do-not-pay-again line.
function ReferenceUnavailable() {
  return (
    <ErrorCard
      code="Payment"
      title="Payment reference unavailable"
      body="Return using the same browser tab, or ask support to check your payment."
      moneyLead="Do not pay again to check its status."
    >
      <Link to="/" className={primaryAction}>
        Browse listings
      </Link>
      <Link to="/account" className={secondaryAction}>
        My blocs
      </Link>
    </ErrorCard>
  );
}
