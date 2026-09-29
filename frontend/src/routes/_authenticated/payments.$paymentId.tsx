// Decision record: frontend/docs/adr/0014-stripe-hosted-checkout-server-owned-intent.md
import { Button } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";

import { unwrapResult } from "@/api/envelope";
import {
  getPaymentReceiptOptions,
  myOrderOptions,
} from "@/api/generated/@tanstack/react-query.gen";
import type { Receipt } from "@/api/generated/types.gen";
import { ErrorCard, primaryAction, secondaryAction } from "@/components/ErrorCard";
import { receiptView } from "@/features/payment/receipt-view";
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

// The §4.4 refund-statement references. The amount, paid time and bank window sit on the money
// line; the receipt carries no Stripe `pi_…` reference, so BlocBuy's own ids stand in.
function receiptReferences(receipt: Receipt | undefined): { label: string; value: string }[] {
  if (!receipt) return [];
  return [
    { label: "Payment reference", value: receipt.paymentId },
    { label: "Order reference", value: receipt.orderId },
    { label: "Bloc reference", value: receipt.dealId },
  ].filter((row): row is { label: string; value: string } => Boolean(row.value));
}

function MyBlocsLink({ className }: Readonly<{ className: string }>) {
  return (
    <Link to="/account" className={className}>
      My blocs
    </Link>
  );
}

// The receipt names the order and the bloc but not the product or a paid time, so both come from
// the buyer's own order (GET /orders/{orderId}). No order yet, or one the buyer cannot read, means
// no listing link and no paid line; "Back to listings" always remains.
function useReceiptOrder(orderId: string | undefined, userTag: string) {
  const options = myOrderOptions({ path: { orderId: orderId ?? "" } });
  return useQuery({
    ...options,
    queryKey: [{ ...options.queryKey[0], tags: [userTag] }],
    select: (r) => unwrapResult(r, { allowVoid: false }),
    enabled: Boolean(orderId),
    retry: false,
  });
}

function PaymentReceipt() {
  const { paymentId } = Route.useParams();
  return <PaymentReceiptForId key={paymentId} paymentId={paymentId} />;
}

function PaymentReceiptForId({ paymentId }: Readonly<{ paymentId: string }>) {
  const [pollingDeadline] = useState(() => Date.now() + RECEIPT_POLL_WINDOW_MS);
  const enabled = usePaymentAvailability();
  const session = useSession();
  const userTag = session.status === "authenticated" ? session.userId : "anonymous";
  const options = getPaymentReceiptOptions({ path: { id: paymentId } });
  const query = useQuery({
    ...options,
    queryKey: [{ ...options.queryKey[0], tags: [userTag] }],
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
  const order = useReceiptOrder(receipt?.orderId, userTag);
  if (!enabled)
    return (
      <ErrorCard
        code="Payments"
        title="Payments are unavailable"
        body="Payment status cannot be checked in this environment."
        moneyLead="Keep your reference and do not pay again to check the result."
      >
        <Link to="/" className={primaryAction}>
          Back to listings
        </Link>
      </ErrorCard>
    );
  const view = receiptView({ receipt, isError: query.isError, paidTime: order.data?.paidTime });
  // Refresh leads while the answer can still change; a settled receipt leads to My blocs (Q25 A).
  const refreshFirst =
    query.isError || !receipt || receipt.status === "CREATED" || Boolean(receipt.refundPending);
  const productId = order.data?.productId;
  return (
    <ErrorCard
      code={view.chip.label}
      color={view.chip.color}
      title={view.title}
      body={view.body}
      alert={query.isError}
      live
      moneyLead={view.moneyLead}
      moneyDetail={view.moneyDetail}
      references={receiptReferences(receipt)}
    >
      {refreshFirst ? (
        <Button
          variant="secondary"
          className="min-h-12 w-full text-base xl:w-auto xl:px-6"
          isPending={query.isFetching}
          onPress={() => {
            void query.refetch();
          }}
        >
          Refresh status
        </Button>
      ) : (
        <MyBlocsLink className={primaryAction} />
      )}
      {productId ? (
        <Link to="/products/$productId" params={{ productId }} className={secondaryAction}>
          See the listing
        </Link>
      ) : null}
      <Link to="/" className={secondaryAction}>
        Back to listings
      </Link>
      {refreshFirst ? <MyBlocsLink className={secondaryAction} /> : null}
    </ErrorCard>
  );
}
