import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { paymentsReturnSearchSchema } from "./payments-return-search";

describe("paymentsReturnSearchSchema", () => {
  it("drops an absent paymentId", () => {
    expect(v.parse(paymentsReturnSearchSchema, {})).toEqual({});
  });

  it.each([
    "550e8400-e29b-41d4-a716-446655440000", // v4
    "019263a0-6f1e-7c3a-9a1b-2e4f6d8c0a12", // v7
  ])("keeps the UUID %s", (paymentId) => {
    expect(v.parse(paymentsReturnSearchSchema, { paymentId })).toEqual({ paymentId });
  });

  it.each(["", "not-a-uuid", "550e8400e29b41d4a716446655440000", 42, null, true, {}])(
    "drops the malformed paymentId %j instead of throwing",
    (paymentId) => {
      const parsed = v.parse(paymentsReturnSearchSchema, { paymentId });
      expect(parsed.paymentId).toBeUndefined();
    },
  );
});
