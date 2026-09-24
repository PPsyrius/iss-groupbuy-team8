export function refundLabel(status: string | undefined): string | undefined {
  switch (status) {
    case "SUCCEEDED":
      return "Refund sent to your original payment method";
    case "PENDING":
      return "Refund queued";
    case "PROCESSING":
      return "Refund processing";
    case "FAILED":
      return "Refund failed — support review needed";
    case "REVIEW":
      return "Refund needs review — do not pay again";
    default:
      return undefined;
  }
}
