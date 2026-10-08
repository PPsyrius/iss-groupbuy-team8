import { describe, expect, it } from "vitest";

import {
  clockReading,
  formatCountdown,
  formatDeadline,
  formatDeadlineShort,
  formatDay,
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

describe("formatDay", () => {
  it("prints the Singapore day and month the way the Refunded row does", () => {
    expect(formatDay(Date.parse("2026-09-15T16:30:00Z"))).toBe("16 Sep");
  });
});

// `clockReading` is what a deadline row renders with instead of the real clock, so what matters is
// that every text derived from the deadline is the same for the reading as for the real time, at
// every second, and that the reading moves rarely.
const sgt = (local: string) => Date.parse(`${local}+08:00`);

// What a deadline row prints that depends on the clock: the countdown and the "ended" test the
// chips and Join read, and with `withDay` also the "Ends today" label.
type Print = (deadlineMs: number, nowMs: number) => string;
const countdown: Print = (deadlineMs, nowMs) =>
  JSON.stringify([formatCountdown(deadlineMs, nowMs), deadlineMs <= nowMs]);
const countdownAndDay: Print = (deadlineMs, nowMs) =>
  `${countdown(deadlineMs, nowMs)} ${formatDeadline(deadlineMs, nowMs)}`;

describe("clockReading", () => {
  const hour = 3_600_000;
  const second = 1000;

  // Real time as the clock reported it: floored to the second.
  const real = (ms: number) => Math.floor(ms / second) * second;

  // Walks the real time from `fromMs` to `toMs`, one second at a time starting 137 ms into a
  // second. Returns the readings, and the moments at which the reading printed something other
  // than the real time (none, when the reading is a faithful stand-in for the clock).
  function walk(deadlineMs: number, fromMs: number, toMs: number, print: Print = countdown) {
    const readings: number[] = [];
    const mismatches: { at: string; got: string; want: string }[] = [];
    for (let at = fromMs + 137; at <= toMs; at += second) {
      const reading = clockReading(deadlineMs, at);
      readings.push(reading);
      const got = print(deadlineMs, reading);
      const want = print(deadlineMs, real(at));
      if (got !== want) mismatches.push({ at: new Date(at).toISOString(), got, want });
    }
    return { readings, mismatches };
  }

  it("reads the real clock to the second when the caller has no deadline", () => {
    expect(clockReading(undefined, now + 999)).toBe(now);
    expect(clockReading(undefined, now + second)).toBe(now + second);
  });

  it("reads Singapore midnight when the caller has no readable deadline", () => {
    const midnight = sgt("2026-09-16T00:00:00");
    expect(clockReading(null, sgt("2026-09-16T00:00:00"))).toBe(midnight);
    expect(clockReading(null, sgt("2026-09-16T23:59:59.999"))).toBe(midnight);
    expect(clockReading(null, sgt("2026-09-17T00:00:00"))).toBe(midnight + 24 * hour);
  });

  it("keeps the hours-and-minutes text at exactly one hour and mm:ss just inside it", () => {
    expect(formatCountdown(now + hour, clockReading(now + hour, now))).toEqual({
      text: "1h 0m left",
      urgent: false,
    });
    expect(formatCountdown(now + hour - second, clockReading(now + hour - second, now))).toEqual({
      text: "59:59 left",
      urgent: true,
    });
    // A deadline off the whole second: 0.5 s over the hour is still "1h 0m", 0.5 s under is mm:ss.
    expect(formatCountdown(now + hour + 500, clockReading(now + hour + 500, now))).toEqual({
      text: "1h 0m left",
      urgent: false,
    });
    expect(formatCountdown(now + hour - 500, clockReading(now + hour - 500, now))).toEqual({
      text: "59:59 left",
      urgent: true,
    });
  });

  it("says Ended at the deadline, just after it, and for a deadline long gone", () => {
    for (const deadline of [now, now - 1, now - second, now - 10 * 24 * hour]) {
      expect(formatCountdown(deadline, clockReading(deadline, now))).toEqual({
        text: "Ended",
        urgent: false,
      });
      expect(hasEnded(new Date(deadline).toISOString(), clockReading(deadline, now))).toBe(true);
    }
  });

  it.each([
    ["on a whole second", sgt("2026-09-16T14:30:00")],
    ["a quarter second in", sgt("2026-09-16T14:30:00.250")],
    ["three quarters of a second in", sgt("2026-09-16T14:30:00.750")],
    ["a few seconds into a minute", sgt("2026-09-16T14:30:42")],
  ])("prints what the real time prints from 3 h out to 5 min past a deadline %s", (_, deadline) => {
    const { readings, mismatches } = walk(deadline, deadline - 3 * hour, deadline + 5 * 60_000);
    expect(readings.length).toBeGreaterThan(10_000);
    expect(mismatches).toEqual([]);
  });

  it("prints what the real time prints across the day line of the hours-and-days countdown", () => {
    const deadline = sgt("2026-09-17T14:30:15");
    const { mismatches } = walk(
      deadline,
      deadline - 24 * hour - 120_000,
      deadline - 24 * hour + 120_000,
    );
    expect(mismatches).toEqual([]);
  });

  it("changes about once a minute while the deadline is hours away, every second in the last hour", () => {
    const deadline = sgt("2026-09-16T14:30:42");
    const far = walk(deadline, deadline - 3 * hour, deadline - hour - second);
    expect(far.mismatches).toEqual([]);
    expect(new Set(far.readings).size).toBeGreaterThanOrEqual(120);
    expect(new Set(far.readings).size).toBeLessThanOrEqual(121);
    const last = walk(deadline, deadline - hour, deadline - second);
    expect(last.mismatches).toEqual([]);
    expect(new Set(last.readings).size).toBe(last.readings.length);
  });

  it("never changes after the deadline until Singapore midnight", () => {
    const deadline = sgt("2026-09-16T10:00:00.500");
    const after = walk(deadline, deadline + second, sgt("2026-09-16T23:59:59"), countdownAndDay);
    expect(after.mismatches).toEqual([]);
    expect(new Set(after.readings).size).toBe(1);
  });

  it("moves at Singapore midnight so 'Ends today' flips on time, deadline hours away", () => {
    const deadline = sgt("2026-09-17T09:30:00");
    const midnight = sgt("2026-09-17T00:00:00");
    const { mismatches } = walk(
      deadline,
      midnight - 3 * 60_000,
      midnight + 3 * 60_000,
      countdownAndDay,
    );
    expect(mismatches).toEqual([]);
    expect(clockReading(deadline, midnight - second)).not.toBe(clockReading(deadline, midnight));
    expect(formatDeadline(deadline, clockReading(deadline, midnight - second))).toBe(
      "Ends Thu 17 Sep, 9.30 am SGT",
    );
    expect(formatDeadline(deadline, clockReading(deadline, midnight))).toBe(
      "Ends today, 9.30 am SGT",
    );
  });

  it("moves at Singapore midnight so 'Ends today' flips on time, deadline behind it", () => {
    const deadline = sgt("2026-09-16T20:15:00");
    const midnight = sgt("2026-09-17T00:00:00");
    const { mismatches } = walk(
      deadline,
      midnight - 3 * 60_000,
      midnight + 3 * 60_000,
      countdownAndDay,
    );
    expect(mismatches).toEqual([]);
    expect(formatDeadline(deadline, clockReading(deadline, midnight - second))).toBe(
      "Ends today, 8.15 pm SGT",
    );
    expect(formatDeadline(deadline, clockReading(deadline, midnight))).toBe(
      "Ends Wed 16 Sep, 8.15 pm SGT",
    );
  });

  it("prints what the real time prints for a deadline in the last minute of a day", () => {
    const deadline = sgt("2026-09-16T23:59:30");
    const { mismatches } = walk(
      deadline,
      deadline - 2 * hour,
      deadline + 3 * 60_000,
      countdownAndDay,
    );
    expect(mismatches).toEqual([]);
  });
});
