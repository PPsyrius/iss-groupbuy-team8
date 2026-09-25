import { describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";
import type { QuoteResponse } from "@/api/generated/types.gen";
import { quoteFailure, readQuote } from "@/features/payment/quote";

const quote = {
  id: "0192f0a0-0000-7000-8000-000000000001",
  productId: "0192f0a0-0000-7000-8000-000000000010",
  policyVersion: 1,
  amount: "71.00",
  currency: "sgd",
  issuedAt: "2026-09-24T10:00:00Z",
  expiresAt: "2026-09-24T10:15:00Z",
};

// `exactOptionalPropertyTypes`: an absent field is removed, never set to `undefined`.
function without(key: "id" | "currency") {
  const copy: QuoteResponse = { ...quote };
  delete copy[key];
  return copy;
}

describe("readQuote", () => {
  it("parses the decimal-string amount and the expiry", () => {
    expect(readQuote(quote)).toEqual({
      id: quote.id,
      amount: 71,
      currency: "sgd",
      expiresMs: Date.parse(quote.expiresAt),
    });
  });

  it("rejects a quote without an id, a positive amount, or a readable expiry", () => {
    expect(readQuote(without("id"))).toBeNull();
    expect(readQuote({ ...quote, amount: "abc" })).toBeNull();
    expect(readQuote({ ...quote, amount: "0.00" })).toBeNull();
    expect(readQuote({ ...quote, expiresAt: "soon" })).toBeNull();
  });

  it("defaults the currency to sgd, the only currency a v1 quote carries", () => {
    expect(readQuote(without("currency"))?.currency).toBe("sgd");
  });
});

describe("quoteFailure", () => {
  it("reads HTTP 409 and the team-price-unavailable code as no price set", () => {
    expect(quoteFailure(new ApiError({ code: "X", message: "", status: 409 }))).toBe("unavailable");
    expect(quoteFailure(new ApiError({ code: "CATALOG_409_001", message: "" }))).toBe(
      "unavailable",
    );
  });

  it("treats every other failure as retryable", () => {
    expect(quoteFailure(new ApiError({ code: "X", message: "", status: 500 }))).toBe("error");
    expect(quoteFailure(new TypeError("offline"))).toBe("error");
  });
});
