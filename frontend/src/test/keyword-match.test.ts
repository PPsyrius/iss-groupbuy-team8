import { describe, expect, it } from "vitest";

import { keywordTerms, matchesKeyword } from "./keyword-match";

const record = {
  title: "Thai jasmine rice 10 kg",
  subtitle: "New crop, vacuum packed",
  description: "Fragrant long grain from Surin.",
};

describe("keyword matcher (backend contract)", () => {
  it("splits on whitespace, lower-cases and drops empty terms", () => {
    expect(keywordTerms("  Rice   Thai ")).toEqual(["rice", "thai"]);
    expect(keywordTerms(undefined)).toEqual([]);
  });

  it("matches every term across title, subtitle and description", () => {
    expect(matchesKeyword(record, keywordTerms("rice vacuum"))).toBe(true);
    expect(matchesKeyword(record, keywordTerms("surin"))).toBe(true);
    expect(matchesKeyword(record, keywordTerms("rice durian"))).toBe(false);
    expect(matchesKeyword({ title: "Rice" }, keywordTerms("rice"))).toBe(true);
    expect(matchesKeyword({ title: "Rice" }, [])).toBe(true);
  });
});
