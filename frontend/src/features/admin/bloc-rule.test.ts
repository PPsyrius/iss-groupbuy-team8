import { describe, expect, it } from "vitest";

import { blocRuleLine } from "./bloc-rule";

// Instants in SGT (UTC+8, no daylight saving). Rule ends are exclusive, so a rule that runs
// through 30 Sep ends at 1 Oct 00:00 SGT.
const sgt = (local: string) => Date.parse(`${local}+08:00`);
const iso = (local: string) => new Date(sgt(local)).toISOString();
const now = sgt("2026-09-16T10:00:00");

const rule = (validFrom: string, validUntil: string, amount = "71") => ({
  amount,
  currency: "sgd",
  validFrom: iso(validFrom),
  validUntil: iso(validUntil),
  version: 3,
});

describe("blocRuleLine", () => {
  it("prints an active rule with its last valid day in SGT", () => {
    const latestPolicy = rule("2026-09-01T00:00:00", "2026-10-01T00:00:00");
    expect(blocRuleLine({ status: "on_sale", latestPolicy }, now)).toBe(
      "Bloc price S$71.00 · valid to 30 Sep",
    );
  });

  it("names the SGT day even when UTC is still on the day before", () => {
    // 1 Oct 07:59 SGT is 30 Sep 23:59 UTC; the rule's last minute is 1 Oct 07:59 SGT.
    const latestPolicy = rule("2026-09-01T00:00:00", "2026-10-01T08:00:00");
    expect(blocRuleLine({ status: "on_sale", latestPolicy }, now)).toBe(
      "Bloc price S$71.00 · valid to 1 Oct",
    );
  });

  it("counts the start as valid and the end as already expired", () => {
    const latestPolicy = rule("2026-09-16T10:00:00", "2026-09-17T00:00:00", "32");
    expect(blocRuleLine({ status: "on_sale", latestPolicy }, now)).toBe(
      "Bloc price S$32.00 · valid to 16 Sep",
    );
    const ended = rule("2026-09-01T00:00:00", "2026-09-16T10:00:00", "32");
    expect(blocRuleLine({ status: "on_sale", latestPolicy: ended }, now)).toBe(
      "Bloc price S$32.00 · expired 16 Sep",
    );
  });

  it("prints an expired rule by its last valid day, off sale too", () => {
    const latestPolicy = rule("2026-09-01T00:00:00", "2026-09-15T00:00:00", "32");
    expect(blocRuleLine({ status: "off_sale", latestPolicy }, now)).toBe(
      "Bloc price S$32.00 · expired 14 Sep",
    );
  });

  it("prints a future rule by its first day", () => {
    const latestPolicy = rule("2026-10-01T00:00:00", "2026-11-01T00:00:00", "9.9");
    expect(blocRuleLine({ status: "on_sale", latestPolicy }, now)).toBe(
      "Bloc price S$9.90 · starts 1 Oct",
    );
  });

  it("says so when a published listing has no rule", () => {
    expect(blocRuleLine({ status: "on_sale", latestPolicy: null }, now)).toBe(
      "No bloc price rule yet",
    );
    expect(blocRuleLine({ status: "off_sale" }, now)).toBe("No bloc price rule yet");
    expect(blocRuleLine({ status: "on_sale", latestPolicy: { version: 1 } }, now)).toBe(
      "No bloc price rule yet",
    );
  });

  it("marks a draft as not published, whatever it carries", () => {
    expect(blocRuleLine({ status: "draft", latestPolicy: null }, now)).toBe(
      "Draft · not published",
    );
    expect(blocRuleLine({ status: "DRAFT" }, now)).toBe("Draft · not published");
  });

  it("drops the date it cannot read", () => {
    const latestPolicy = { amount: "71.00", validFrom: "soon", validUntil: "later" };
    expect(blocRuleLine({ status: "on_sale", latestPolicy }, now)).toBe("Bloc price S$71.00");
  });
});
