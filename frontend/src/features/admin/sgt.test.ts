import { describe, expect, it } from "vitest";

import {
  formatSgtDate,
  formatSgtMinute,
  formatWindow,
  sgtDayStart,
  sgtMinute,
  toInstant,
  toZoned,
} from "./sgt";

describe("formatSgtDate", () => {
  it("prints the 4a.1 date line's long Singapore date", () => {
    expect(formatSgtDate(Date.parse("2026-09-16T02:00:00Z"))).toBe("Wednesday 16 September 2026");
  });

  it("uses the Singapore day, not the device's", () => {
    // 11.30 pm UTC on the 15th is 7.30 am on the 16th in Singapore.
    expect(formatSgtDate(Date.parse("2026-09-15T23:30:00Z"))).toBe("Wednesday 16 September 2026");
    expect(formatSgtDate(Date.parse("2026-09-15T15:59:00Z"))).toBe("Tuesday 15 September 2026");
  });
});

describe("toZoned and toInstant", () => {
  it("reads an instant as Singapore wall time and writes it back unchanged", () => {
    const zoned = toZoned("2026-08-31T16:00:00Z");
    expect(zoned?.timeZone).toBe("Asia/Singapore");
    expect([zoned?.year, zoned?.month, zoned?.day, zoned?.hour, zoned?.minute]).toEqual([
      2026, 9, 1, 0, 0,
    ]);
    expect(zoned && toInstant(zoned)).toBe("2026-08-31T16:00:00.000Z");
  });

  it("round-trips the last minute of a day and midnight", () => {
    for (const iso of ["2026-09-30T15:59:00.000Z", "2026-09-30T16:00:00.000Z"]) {
      const zoned = toZoned(iso);
      expect(zoned && toInstant(zoned)).toBe(iso);
    }
    expect(toZoned("2026-09-30T15:59:00Z")?.hour).toBe(23);
  });

  it("is null for a missing or unreadable instant", () => {
    expect(toZoned(undefined)).toBeNull();
    expect(toZoned("")).toBeNull();
    expect(toZoned("not a date")).toBeNull();
  });
});

describe("sgtMinute and sgtDayStart", () => {
  it("drops the seconds of now", () => {
    const zoned = sgtMinute(Date.parse("2026-09-16T02:03:45.678Z"));
    expect(toInstant(zoned)).toBe("2026-09-16T02:03:00.000Z");
  });

  it("starts a Singapore day some days later, at midnight SGT", () => {
    // 11.30 pm UTC on the 15th is already the 16th in Singapore; 31 days on is 17 Oct.
    expect(toInstant(sgtDayStart(Date.parse("2026-09-15T23:30:00Z"), 31))).toBe(
      "2026-10-16T16:00:00.000Z",
    );
  });
});

describe("formatSgtMinute", () => {
  it("prints the 4b.4 picker's day and time", () => {
    expect(formatSgtMinute(Date.parse("2026-09-16T16:00:00Z"))).toBe("17 Sep 2026, 12.00 am");
    expect(formatSgtMinute(Date.parse("2026-09-16T15:59:00Z"))).toBe("16 Sep 2026, 11.59 pm");
    expect(formatSgtMinute(Date.parse("2026-09-16T04:05:00Z"))).toBe("16 Sep 2026, 12.05 pm");
  });
});

describe("formatWindow", () => {
  it("prints the 4b.3 window, the end shown as its last valid minute (§6 Q13)", () => {
    expect(formatWindow("2026-08-31T16:00:00Z", "2026-09-30T16:00:00Z")).toBe(
      "Valid 1 Sep 2026, 12.00 am → 30 Sep 2026, 11.59 pm SGT",
    );
  });

  it("is null when either end is unreadable", () => {
    expect(formatWindow(undefined, "2026-09-30T16:00:00Z")).toBeNull();
    expect(formatWindow("2026-08-31T16:00:00Z", "soon")).toBeNull();
  });
});
