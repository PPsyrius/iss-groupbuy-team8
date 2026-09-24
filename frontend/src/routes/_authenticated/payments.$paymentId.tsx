// Decision record: frontend/docs/adr/0014-stripe-hosted-checkout-server-owned-intent.md
import { Button } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";

import { unwrapResult } from "@/api/envelope";
import { getPaymentReceiptOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { Receipt } from "@/api/generated/types.gen";
import { refundLabel } from "@/features/payment/refund-status";
import { clearCompletedAttempt } from "@/features/payment/use-checkout";
import { usePaymentAvailability } from "@/features/payment/use-payment-availability";
import { useSession } from "@/features/session/use-session";

export const Route = createFileRoute("/_authenticated/payments/$paymentId")({
  component: PaymentReceipt,
});

const RECEIPT_POLL_INTERVAL_MS = 2000;
const RECEIPT_POLL_WINDOW_MS = RECEIPT_POLL_INTERVAL_MS * 30;

export function receiptPollingInterval({
  receipt,
  error,
  deadline,
  now = Date.now(),
}: Readonly<{
  receipt: Pick<Receipt, "status" | "refundPending"> | undefined;
  error: unknown;
  deadline: number;
  now?: number;
}>): number | false {
  if (error || now >= deadline) return false;
  return receipt?.status === "CREATED" || receipt?.refundPending ? RECEIPT_POLL_INTERVAL_MS : false;
}

function getPaymentTitle({
  isError,
  receipt,
  paid,
  waiting,
}: {
  isError: boolean;
  receipt?: Pick<Receipt, "status" | "refundStatus"> | undefined;
  paid: boolean;
  waiting: boolean;
}): string {
  if (isError) return "Unable to check payment";
  if (!receipt) return "Checking payment…";
  if (receipt.refundStatus === "SUCCEEDED") return "Refund sent";
  if (paid) return "Payment received";
  if (waiting) return "Waiting for payment confirmation";
  if (receipt.status === "CANCELED") return "Checkout closed without payment";
  return "Payment not completed";
}

function ReceiptDetails({ receipt }: Readonly<{ receipt: Receipt }>) {
  return (
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
  );
}

function RefundStatusBanner({
  receipt,
  paid,
}: Readonly<{ receipt: Receipt | undefined; paid: boolean }>) {
  if (!receipt) return null;
  if (receipt.refundStatus === "SUCCEEDED")
    return (
      <output>{refundLabel(receipt.refundStatus)}. Your bank may take time to show it.</output>
    );
  if (receipt.refundStatus === "FAILED" || receipt.refundStatus === "REVIEW")
    return (
      <output>{refundLabel(receipt.refundStatus)}. Keep your payment reference for support.</output>
    );
  if (receipt.refundPending)
    return (
      <output>
        A refund is pending. Payment was received, but this purchase could not be fulfilled or the
        bloc expired. This is not confirmation that the refund has been sent.
      </output>
    );
  if (paid && receipt.orderId)
    return <output>Your order has been saved and your bloc membership is confirmed.</output>;
  if (paid)
    return (
      <output>
        Payment is recorded. Order confirmation is not available yet; do not pay again.
      </output>
    );
  return null;
}

function PaymentReceipt() {
  const { paymentId } = Route.useParams();
  return <PaymentReceiptForId key={paymentId} paymentId={paymentId} />;
}

function PaymentReceiptForId({ paymentId }: Readonly<{ paymentId: string }>) {
  const [pollingDeadline] = useState(() => Date.now() + RECEIPT_POLL_WINDOW_MS);
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
      receiptPollingInterval({
        receipt: q.state.data?.data,
        error: q.state.error,
        deadline: pollingDeadline,
      }),
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
  const title = getPaymentTitle({ isError: query.isError, receipt, paid, waiting });
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
      {receipt ? <ReceiptDetails receipt={receipt} /> : null}
      <RefundStatusBanner receipt={receipt} paid={paid} />
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
