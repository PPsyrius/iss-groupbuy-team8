import { describe, expect, it } from "vitest";

import {
  formatCountdown,
  formatDeadline,
  formatDeadlineShort,
  formatDayTime,
  hasEnded,
  parseDeadline,
} from "./deal-time";

const now = Date.parse("2026-09-16T11:00:00+08:00"); // Wed 16 Sep, 11.00 am SGT

describe("formatCountdown", () => {
  it("prints hours and minutes when an hour or more remains", () => {
    expect(formatCountdown(now + (2 * 3600 + 14 * 60) * 1000, now)).toEqual({
      text: "2h 14m left",
      urgent: false,
    });
  });

  it("prints mm:ss and flags urgency under one hour", () => {
    expect(formatCountdown(now + (47 * 60 + 25) * 1000, now)).toEqual({
      text: "47:25 left",
      urgent: true,
    });
  });

  it("prints Ended at or after the deadline", () => {
    expect(formatCountdown(now, now)).toEqual({ text: "Ended", urgent: false });
    expect(formatCountdown(now - 1, now)).toEqual({ text: "Ended", urgent: false });
  });
});

describe("formatDeadline", () => {
  it("prints the absolute Singapore time with the artboard's punctuation", () => {
    expect(formatDeadline(Date.parse("2026-09-17T13:30:00Z"), now)).toBe(
      "Ends Thu 17 Sep, 9.30 pm SGT",
    );
  });

  it("says today when the deadline falls on the same Singapore day", () => {
    expect(formatDeadline(Date.parse("2026-09-16T12:15:00Z"), now)).toBe("Ends today, 8.15 pm SGT");
  });

  it("names the day across midnight", () => {
    expect(formatDeadline(Date.parse("2026-09-18T06:40:00Z"), now)).toBe(
      "Ends Fri 18 Sep, 2.40 pm SGT",
    );
  });
});

describe("formatDeadlineShort", () => {
  it("prints the triptych form", () => {
    expect(formatDeadlineShort(Date.parse("2026-09-17T13:30:00Z"))).toBe("Thu, 9.30 pm");
  });
});

describe("parseDeadline", () => {
  it("returns epoch ms for an ISO deadline", () => {
    expect(parseDeadline("2026-09-17T13:30:00Z")).toBe(Date.parse("2026-09-17T13:30:00Z"));
  });

  it("returns null for a missing, empty or unparseable deadline", () => {
    expect(parseDeadline(undefined)).toBeNull();
    expect(parseDeadline("")).toBeNull();
    expect(parseDeadline("not a date")).toBeNull();
  });
});

describe("hasEnded", () => {
  it("is true only for a parseable deadline at or before now", () => {
    expect(hasEnded("2026-09-16T03:00:00Z", now)).toBe(true);
    expect(hasEnded("2026-09-16T03:00:01Z", now)).toBe(false);
    expect(hasEnded(undefined, now)).toBe(false);
    expect(hasEnded("not a date", now)).toBe(false);
  });
});

describe("formatDayTime", () => {
  it("prints the Singapore day and time the way the My blocs paid line does", () => {
    expect(formatDayTime(Date.parse("2026-09-16T11:46:00Z"))).toBe("16 Sep, 7.46 pm");
  });

  it("uses Singapore's date, not UTC's, near midnight", () => {
    expect(formatDayTime(Date.parse("2026-09-16T16:05:00Z"))).toBe("17 Sep, 12.05 am");
  });
});
