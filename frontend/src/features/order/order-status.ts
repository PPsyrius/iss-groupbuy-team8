// Order lifecycle chip for the order history (UC8, #128). The wire carries the domain's lowercase
// codes (`OrderStatus.java`: pending, confirmed, cancelled); the contract types `status` as a
// plain string, so the upper-case enum name is accepted too and an unknown code is shown as is.
// An order is born `pending` once its payment is verified and ends `confirmed` (the bloc filled)
// or `cancelled` (it did not). No refund label: the order schema has no refund field, and the
// receipt reports refunds (option B on #128).
type ChipColor = "default" | "success" | "warning";

const labels: Record<string, { label: string; color: ChipColor }> = {
  pending: { label: "Waiting for the bloc", color: "warning" },
  confirmed: { label: "Confirmed", color: "success" },
  cancelled: { label: "Cancelled", color: "default" },
};

export function orderStatus(
  status: string | undefined,
): { label: string; color: ChipColor } | undefined {
  if (!status) return undefined;
  return labels[status.toLowerCase()] ?? { label: status, color: "default" };
}
