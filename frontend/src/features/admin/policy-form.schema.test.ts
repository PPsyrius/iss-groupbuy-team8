import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { PolicyFormValues } from "./policy-form";
import { policyFormSchema } from "./policy-form.schema";
import { toZoned } from "./sgt";

const now = Date.parse("2026-09-16T02:03:00Z");
const schema = policyFormSchema({ originalPrice: "12.80", nowMs: now });

const valid: PolicyFormValues = {
  amount: "9.90",
  validFrom: toZoned("2026-09-16T02:03:00Z"),
  validUntil: toZoned("2026-10-16T16:00:00Z"),
};

function problems(values: PolicyFormValues, s = schema) {
  const out: Record<string, string> = {};
  for (const issue of v.safeParse(s, values).issues ?? []) {
    const path = issue.path?.map((p) => String(p.key)).join(".") ?? "";
    out[path] = path in out ? `${out[path]} | ${issue.message}` : issue.message;
  }
  return out;
}

describe("policyFormSchema", () => {
  it("accepts a rule below the solo price that ends in the future", () => {
    expect(problems(valid)).toEqual({});
    expect(problems({ ...valid, amount: " 12.79 " })).toEqual({});
  });

  it("needs a decimal amount above zero", () => {
    for (const amount of ["", "abc", "1.234", "-1", "1e3"]) {
      expect(problems({ ...valid, amount })).toEqual({
        amount: "Enter the bloc price in dollars and cents, like 71.00.",
      });
    }
    expect(problems({ ...valid, amount: "0.00" })).toEqual({
      amount: "Enter a price above S$0.00",
    });
  });

  it("keeps the bloc price strictly below the solo price (4b.4)", () => {
    for (const amount of ["12.80", "13.50"]) {
      expect(problems({ ...valid, amount })).toEqual({
        amount: "Must be strictly below the solo price of S$12.80.",
      });
    }
  });

  it("skips the solo price check when the solo price is unreadable", () => {
    expect(
      problems({ ...valid, amount: "99.00" }, policyFormSchema({ originalPrice: "", nowMs: now })),
    ).toEqual({});
  });

  it("needs both ends", () => {
    expect(problems({ ...valid, validFrom: null, validUntil: null })).toEqual({
      validFrom: "Choose when the rule starts.",
      validUntil: "Choose when the rule ends.",
    });
  });

  it("ends after the start of the rule (4b.4)", () => {
    expect(
      problems({
        ...valid,
        validFrom: toZoned("2026-09-16T16:00:00Z"),
        validUntil: toZoned("2026-09-16T16:00:00Z"),
      }),
    ).toEqual({ validUntil: "Must be after the start of the rule." });
  });

  it("ends in the future", () => {
    expect(
      problems({
        ...valid,
        validFrom: toZoned("2026-09-01T00:00:00Z"),
        validUntil: toZoned("2026-09-16T02:03:00Z"),
      }),
    ).toEqual({ validUntil: "Must be in the future." });
  });
});
