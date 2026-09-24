import { describe, expect, it } from "vitest";

import { refundLabel } from "./refund-status";

describe("refundLabel", () => {
  it.each([
    ["SUCCEEDED", "Refund sent to your original payment method"],
    ["PENDING", "Refund queued"],
    ["PROCESSING", "Refund processing"],
    ["FAILED", "Refund failed — support review needed"],
    ["REVIEW", "Refund needs review — do not pay again"],
  ])("maps %s to its buyer-facing copy", (status, label) => {
    expect(refundLabel(status)).toBe(label);
  });

  it.each([undefined, "NONE", "OTHER"])("returns undefined for %s", (status) => {
    expect(refundLabel(status)).toBeUndefined();
  });
});
