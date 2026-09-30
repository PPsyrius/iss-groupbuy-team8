import { describe, expect, it } from "vitest";

import { formatSgd, toCents } from "./money";

describe("toCents", () => {
  it("reads a decimal string as integer cents", () => {
    expect(toCents("12.8")).toBe(1280n);
    expect(toCents("89")).toBe(8900n);
    expect(toCents("46.80")).toBe(4680n);
  });
  it("reads zero", () => {
    expect(toCents("0.00")).toBe(0n);
  });
  it("rejects more than two decimals, signs and non-numbers", () => {
    expect(toCents("1.234")).toBeNull();
    expect(toCents("-1")).toBeNull();
    expect(toCents("")).toBeNull();
    expect(toCents("1e3")).toBeNull();
    expect(toCents(" 12")).toBeNull();
  });
});

describe("formatSgd", () => {
  it("prints S$ with two decimals", () => {
    expect(formatSgd("89")).toBe("S$89.00");
    expect(formatSgd("46.8")).toBe("S$46.80");
    expect(formatSgd("0.00")).toBe("S$0.00");
  });
  it("groups thousands", () => {
    expect(formatSgd("1234567.5")).toBe("S$1,234,567.50");
  });
  it("shows an unreadable amount as sent", () => {
    expect(formatSgd("abc")).toBe("abc");
  });
});
