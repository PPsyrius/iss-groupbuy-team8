import type { Receipt } from "@/api/generated/types.gen";
import { formatDayTime, parseDeadline } from "@/features/catalog/deal-time";
import { formatPrice } from "@/features/catalog/Price";

import { refundLabel } from "./refund-status";

// The receipt as a §4.5 terminal card (design-screens §7 Q23 A): the code chip carries the
// payment status, the money line the amount with the paid time or the §4.4 bank window, and the
// body what it means for the buyer. The receipt has no issue time, so the paid line comes from
// the buyer's own order (`paidTime`) when there is one.
type ChipColor = "default" | "success" | "danger";

export interface ReceiptView {
  chip: { label: string; color: ChipColor };
  title: string;
  body: string;
  moneyLead: string;
  moneyDetail?: string | undefined;
  // Set only on the plain paid view: the one confirmation the receipt brands (CR-7).
  confirmed?: true;
}

const doNotPayAgain = "Do not pay again to check the result.";
const bankWindow = "Timing depends on your bank, usually 5–10 business days.";

function amountOf(receipt: Pick<Receipt, "amount" | "currency">): string | undefined {
  const amount = Number(receipt.amount);
  if (receipt.amount === undefined || !Number.isFinite(amount)) return undefined;
  return formatPrice(amount, receipt.currency ?? "sgd");
}

function paidLine(paidTime: string | undefined): string | undefined {
  const ms = parseDeadline(paidTime);
  return ms === null ? undefined : `Paid · ${formatDayTime(ms)}`;
}

export function receiptView({
  receipt,
  isError,
  paidTime,
}: Readonly<{
  receipt: Receipt | undefined;
  isError: boolean;
  paidTime?: string | undefined;
}>): ReceiptView {
  if (isError)
    return {
      chip: { label: "Unavailable", color: "danger" },
      title: "Unable to check payment",
      body: "We could not read your payment. Check your login and try again.",
      moneyLead: doNotPayAgain,
    };
  if (!receipt)
    return {
      chip: { label: "Checking", color: "default" },
      title: "Checking payment…",
      body: "This page reads the payment from BlocBuy, not from the return link.",
      moneyLead: doNotPayAgain,
    };
  const amount = amountOf(receipt);
  if (receipt.refundStatus === "SUCCEEDED")
    return {
      chip: { label: "Refunded", color: "success" },
      title: "Refund sent",
      body: `${refundLabel("SUCCEEDED")}.`,
      moneyLead: amount ? `${amount} refunded.` : "Refund sent.",
      moneyDetail: bankWindow,
    };
  if (receipt.refundStatus === "FAILED" || receipt.refundStatus === "REVIEW")
    return {
      chip: { label: "Needs review", color: "danger" },
      title: "Payment received",
      body: `${refundLabel(receipt.refundStatus)}. Keep your payment reference for support.`,
      moneyLead: doNotPayAgain,
    };
  if (receipt.refundPending)
    return {
      chip: { label: "Refund pending", color: "danger" },
      title: "Refund on its way",
      body: "A refund is pending. Payment was received, but this purchase could not be fulfilled or the bloc expired. This is not confirmation that the refund has been sent.",
      moneyLead: amount ? `${amount} to be refunded.` : "Refund pending.",
      moneyDetail: bankWindow,
    };
  switch (receipt.status) {
    case "SUCCEEDED":
      return {
        chip: { label: "Paid", color: "success" },
        confirmed: true,
        title: "Payment received",
        body: receipt.orderId
          ? "Your order has been saved and your bloc membership is confirmed."
          : "Payment is recorded. Order confirmation is not available yet; do not pay again.",
        moneyLead: amount ? `${amount} paid.` : "Payment recorded.",
        moneyDetail: paidLine(paidTime),
      };
    case "CREATED":
      // Q24 A: the 500 card's tone, adapted to a payment Stripe has not confirmed yet.
      return {
        chip: { label: "Waiting", color: "default" },
        title: "Waiting for payment confirmation",
        body: "Your seat is held once Stripe confirms. This page checks BlocBuy, not the return link, and refreshes by itself for a minute.",
        moneyLead: "No payment is taken twice.",
        moneyDetail: "Check My blocs before trying again.",
      };
    case "CANCELED":
      return {
        chip: { label: "Closed", color: "default" },
        title: "Checkout closed without payment",
        body: "You left Stripe Checkout before paying. Start again from the listing if you still want a seat.",
        moneyLead: "No payment was taken.",
      };
    case "FAILED":
      return {
        chip: { label: "Failed", color: "danger" },
        title: "Payment not completed",
        body: "Stripe did not complete this payment. You can try again from the listing.",
        moneyLead: "No payment was taken.",
      };
    default:
      return {
        chip: { label: receipt.status ?? "Unknown", color: "default" },
        title: "Payment not completed",
        body: "This payment has not finished. Keep your payment reference for support.",
        moneyLead: doNotPayAgain,
      };
  }
}
