import { Button } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect } from "react";

import { unwrapResult } from "@/api/envelope";
import { getPaymentReceiptOptions } from "@/api/generated/@tanstack/react-query.gen";
import { refundLabel } from "@/features/payment/refund-status";
import { clearCompletedAttempt } from "@/features/payment/use-checkout";
import { usePaymentAvailability } from "@/features/payment/use-payment-availability";
import { useSession } from "@/features/session/use-session";

export const Route = createFileRoute("/_authenticated/payments/$paymentId")({
  component: PaymentReceipt,
});

function PaymentReceipt() {
  const { paymentId } = Route.useParams();
  const enabled = usePaymentAvailability();
  const session = useSession();
  const options = getPaymentReceiptOptions({ path: { id: paymentId } });
  const query = useQuery({
    ...options,
    queryKey: [
      {
        ...options.queryKey[0],
        tags: [session.status === "authenticated" ? session.userId : "anonymous"],
      },
    ],
    enabled,
    select: (r) => unwrapResult(r, { allowVoid: false }),
    retry: false,
    // A redirect is not proof of payment. Poll the webhook-confirmed database state only.
    refetchInterval: (q) =>
      (q.state.data?.data?.status === "CREATED" || q.state.data?.data?.refundPending) &&
      q.state.dataUpdateCount < 30
        ? 2000
        : false,
  });
  const receipt = query.data;
  const terminal = receipt?.status && receipt.status !== "CREATED";
  useEffect(() => {
    if (enabled && terminal) clearCompletedAttempt(paymentId);
  }, [enabled, terminal, paymentId]);
  if (!enabled)
    return (
      <main className="mx-auto flex max-w-2xl flex-col gap-5 p-6">
        <h1 className="text-2xl font-semibold">Payments are unavailable</h1>
        <p>
          Payment status cannot be checked in this environment. Keep your reference and do not pay
          again to check the result.
        </p>
        <Link to="/">Back to listings</Link>
      </main>
    );
  const paid = receipt?.status === "SUCCEEDED";
  const waiting = receipt?.status === "CREATED";
  const title = query.isError
    ? "Unable to check payment"
    : !receipt
      ? "Checking payment…"
      : receipt.refundStatus === "SUCCEEDED"
        ? "Refund sent"
        : paid
          ? "Payment received"
          : waiting
            ? "Waiting for payment confirmation"
            : receipt.status === "CANCELED"
              ? "Checkout closed without payment"
              : "Payment not completed";
  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-5 p-6">
      <h1 className="text-2xl font-semibold" aria-live="polite">
        {title}
      </h1>
      {query.isError ? (
        <p role="alert">
          We could not read your payment. Check your login and try again. Do not pay again to check
          the result.
        </p>
      ) : null}
      {waiting ? (
        <p>
          Your payment may still be processing. This page checks the backend, not the return URL.
          You can refresh the status without paying again.
        </p>
      ) : null}
      {receipt ? (
        <dl className="border-border bg-surface grid grid-cols-1 gap-2 rounded-lg border p-5 sm:grid-cols-[auto_1fr]">
          <dt>Payment reference</dt>
          <dd className="break-all">{receipt.paymentId}</dd>
          <dt>Amount</dt>
          <dd>
            {receipt.currency?.toUpperCase()} {receipt.amount}
          </dd>
          <dt>Payment status</dt>
          <dd>{receipt.status}</dd>
          {receipt.orderId ? (
            <>
              <dt>Order reference</dt>
              <dd className="break-all">{receipt.orderId}</dd>
            </>
          ) : null}
          {receipt.dealId ? (
            <>
              <dt>Bloc reference</dt>
              <dd className="break-all">{receipt.dealId}</dd>
            </>
          ) : null}
        </dl>
      ) : null}
      {receipt?.refundStatus === "SUCCEEDED" ? (
        <output>{refundLabel(receipt.refundStatus)}. Your bank may take time to show it.</output>
      ) : receipt?.refundStatus === "FAILED" || receipt?.refundStatus === "REVIEW" ? (
        <output>
          {refundLabel(receipt.refundStatus)}. Keep your payment reference for support.
        </output>
      ) : receipt?.refundPending ? (
        <output>
          A refund is pending. Payment was received, but this purchase could not be fulfilled or the
          bloc expired. This is not confirmation that the refund has been sent.
        </output>
      ) : paid && receipt.orderId ? (
        <output>Your order has been saved and your bloc membership is confirmed.</output>
      ) : paid ? (
        <output>
          Payment is recorded. Order confirmation is not available yet; do not pay again.
        </output>
      ) : null}
      <div className="flex items-center gap-5">
        <Button
          variant="outline"
          isPending={query.isFetching}
          onPress={() => {
            void query.refetch();
          }}
        >
          Refresh status
        </Button>
        <Link to="/" className="underline">
          Back to listings
        </Link>
        <Link to="/account" className="underline">
          My orders
        </Link>
      </div>
    </main>
  );
}
