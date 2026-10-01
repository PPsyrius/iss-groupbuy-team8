import { describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";

import {
  isNoRule,
  policyRefusalOf,
  policyState,
  replacesActiveNow,
  ruleChip,
  ruleTiming,
} from "./policy";

const from = "2026-08-31T16:00:00Z"; // 1 Sep 00:00 SGT
const until = "2026-09-30T16:00:00Z"; // 1 Oct 00:00 SGT, exclusive
const rule = { amount: "71.00", validFrom: from, validUntil: until, version: 3 };
const at = (iso: string) => Date.parse(iso);

describe("ruleTiming", () => {
  it("has no rule without a policy", () => {
    expect(ruleTiming(null, at(from))).toBe("none");
    expect(ruleTiming(undefined, at(from))).toBe("none");
  });

  it("starts inclusive and ends exclusive (§6 Q13)", () => {
    expect(ruleTiming(rule, at(from) - 1)).toBe("scheduled");
    expect(ruleTiming(rule, at(from))).toBe("active");
    expect(ruleTiming(rule, at(until) - 1)).toBe("active");
    expect(ruleTiming(rule, at(until))).toBe("expired");
  });

  it("treats an unreadable start as started and an unreadable end as open", () => {
    expect(ruleTiming({ ...rule, validFrom: "?" }, at(from) - 1)).toBe("active");
    expect(ruleTiming({ ...rule, validUntil: undefined }, at(until) + 1)).toBe("active");
  });
});

describe("policyState", () => {
  it("is unavailable on a draft or off sale, whatever the rule", () => {
    expect(policyState(null, "draft", at(from))).toBe("unavailable-draft");
    expect(policyState(rule, "off_sale", at(from))).toBe("unavailable-off-sale");
    expect(policyState(rule, "OFF_SALE", at(from))).toBe("unavailable-off-sale");
  });

  it("follows the rule's timing on sale", () => {
    expect(policyState(null, "on_sale", at(from))).toBe("none");
    expect(policyState(rule, "on_sale", at(from) - 1)).toBe("scheduled");
    expect(policyState(rule, "on_sale", at(from))).toBe("active");
    expect(policyState(rule, "on_sale", at(until))).toBe("expired");
  });
});

describe("ruleChip", () => {
  it("names the 4b.5 chips; the expired day is the day of the last valid minute", () => {
    expect(ruleChip("none", null)).toBe("No rule yet");
    expect(ruleChip("active", rule)).toBe("Active");
    expect(ruleChip("expired", rule)).toBe("Expired 30 Sep");
    expect(ruleChip("scheduled", rule)).toBe("Starts 1 Sep");
  });

  it("falls back to the bare state when the date is unreadable", () => {
    expect(ruleChip("expired", { ...rule, validUntil: "?" })).toBe("Expired");
    expect(ruleChip("scheduled", { ...rule, validFrom: undefined })).toBe("Scheduled");
  });
});

describe("replacesActiveNow", () => {
  it("warns only when an active rule would give way to a future start (§11 D3)", () => {
    const now = at(from) + 1000;
    expect(replacesActiveNow("active", now + 60_000, now)).toBe(true);
    expect(replacesActiveNow("active", now, now)).toBe(false);
    expect(replacesActiveNow("active", null, now)).toBe(false);
    expect(replacesActiveNow("expired", now + 60_000, now)).toBe(false);
    expect(replacesActiveNow("scheduled", now + 60_000, now)).toBe(false);
  });
});

const apiError = (code: string, status: number, errors?: unknown) =>
  new ApiError({ code, message: "x", status, ...(errors === undefined ? {} : { errors }) });

describe("isNoRule", () => {
  it("reads the policy read's 409 as no rule yet", () => {
    expect(isNoRule(apiError("CATALOG_409_001", 409))).toBe(true);
    expect(isNoRule(apiError("CATALOG_404_001", 404))).toBe(false);
    expect(isNoRule(new Error("x"))).toBe(false);
  });
});

describe("policyRefusalOf", () => {
  it("turns a stale policy version into the panel's conflict, with the server's version", () => {
    expect(policyRefusalOf(apiError("CATALOG_412_001", 412, { currentVersion: 4 }))).toEqual({
      kind: "conflict",
      serverVersion: 4,
    });
    expect(policyRefusalOf(apiError("CATALOG_412_001", 412))).toEqual({
      kind: "conflict",
      serverVersion: null,
    });
  });

  it("turns a 409 into the unavailable state", () => {
    expect(policyRefusalOf(apiError("CATALOG_409_001", 409))).toEqual({ kind: "unavailable" });
  });

  it("puts a domain 400's field paths on fields (BA-6, #323)", () => {
    const error = apiError("CATALOG_400_001", 400, [
      { field: "amount", message: "Must be strictly below the solo price" },
      { field: "validUntil", message: "Must be in the future" },
    ]);
    expect(policyRefusalOf(error)).toEqual({
      kind: "fields",
      fields: {
        amount: "Must be strictly below the solo price",
        validUntil: "Must be in the future",
      },
    });
  });

  it("names the rule in a 400 without field paths", () => {
    expect(policyRefusalOf(apiError("CATALOG_400_001", 400))).toEqual({
      kind: "message",
      message: "The server refused this rule. Check the fields and try again.",
    });
  });

  it("keeps the listing refusals for a revoked role, an ended session and a server failure", () => {
    expect(policyRefusalOf(apiError("COMMON_403_001", 403))).toEqual({ kind: "forbidden" });
    expect(policyRefusalOf(apiError("AUTH_401", 401))).toEqual({ kind: "session" });
    expect(policyRefusalOf(apiError("COMMON_500_001", 500))).toEqual({
      kind: "message",
      message: "Something went wrong on our side. Try again.",
    });
    expect(policyRefusalOf(new Error("network"))).toEqual({
      kind: "message",
      message: "Something went wrong on our side. Try again.",
    });
  });
});
