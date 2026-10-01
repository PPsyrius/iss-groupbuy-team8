import { describe, expect, it } from "vitest";

import { policyFormDefaults, toPolicyRequest } from "./policy-form";
import { toInstant, toZoned } from "./sgt";

const now = Date.parse("2026-09-16T02:03:45Z"); // 16 Sep, 10.03 am SGT

describe("policyFormDefaults", () => {
  it("opens a first rule from this minute for 31 Singapore days", () => {
    const values = policyFormDefaults(null, now);
    expect(values.amount).toBe("");
    expect(values.validFrom && toInstant(values.validFrom)).toBe("2026-09-16T02:03:00.000Z");
    // Ends at midnight SGT starting 17 Oct, so the last valid minute is 16 Oct, 11.59 pm.
    expect(values.validUntil && toInstant(values.validUntil)).toBe("2026-10-16T16:00:00.000Z");
  });

  it("starts a revision from the latest rule's amount and its end while that is still ahead", () => {
    const values = policyFormDefaults(
      { amount: "71.00", validFrom: "2026-08-31T16:00:00Z", validUntil: "2026-09-30T16:00:00Z" },
      now,
    );
    expect(values.amount).toBe("71.00");
    expect(values.validUntil && toInstant(values.validUntil)).toBe("2026-09-30T16:00:00.000Z");
  });

  it("does not offer an end already past", () => {
    const values = policyFormDefaults(
      { amount: "74.00", validFrom: "2026-07-31T16:00:00Z", validUntil: "2026-09-14T16:00:00Z" },
      now,
    );
    expect(values.validUntil && toInstant(values.validUntil)).toBe("2026-10-16T16:00:00.000Z");
  });
});

describe("toPolicyRequest", () => {
  it("sends the trimmed amount, SGD, both instants and the loaded version", () => {
    expect(
      toPolicyRequest(
        {
          amount: " 69.00 ",
          validFrom: toZoned("2026-09-30T16:00:00Z"),
          validUntil: toZoned("2026-10-31T16:00:00Z"),
        },
        3,
      ),
    ).toEqual({
      amount: "69.00",
      currency: "sgd",
      validFrom: "2026-09-30T16:00:00.000Z",
      validUntil: "2026-10-31T16:00:00.000Z",
      expectedVersion: 3,
    });
  });

  it("refuses values the schema would not have let through", () => {
    expect(() =>
      toPolicyRequest(
        { amount: "1.00", validFrom: null, validUntil: toZoned("2026-10-31T16:00:00Z") },
        0,
      ),
    ).toThrow("both ends");
  });
});
