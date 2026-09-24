import { createFileRoute, Link, redirect } from "@tanstack/react-router";

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
  component: () => (
    <main className="mx-auto flex max-w-xl flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">Payment reference unavailable</h1>
      <p>
        Return using the same browser tab, or ask support to check your payment. Do not pay again to
        check its status.
      </p>
      <Link to="/">Back to listings</Link>
    </main>
  ),
});
