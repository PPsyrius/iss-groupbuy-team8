import { describe, expect, it } from "vitest";

import type { MyTeamResponse } from "@/api/generated/types.gen";
import { clockReading } from "@/features/catalog/deal-time";

import { blocChip, blocCountdown, blocCounts, blocFilter } from "./bloc-status";

const now = Date.parse("2026-09-17T11:00:00+08:00");
const inMinutes = (m: number) => new Date(now + m * 60_000).toISOString();
const team = (over: Partial<MyTeamResponse>): MyTeamResponse => ({
  dealId: "d1",
  status: "open",
  expireTime: inMinutes(5 * 60),
  ...over,
});

describe("blocFilter", () => {
  it.each([
    ["open", "active"],
    ["OPEN", "active"],
    ["succeeded", "completed"],
    ["SUCCEEDED", "completed"],
    ["expired", "refunded"],
    ["failed", "refunded"],
    ["canceled", "refunded"],
    ["CANCELLED", "refunded"],
    ["something-new", "active"],
    [undefined, "active"],
  ])("files %s under %s", (status, filter) => {
    expect(blocFilter(status)).toBe(filter);
  });
});

describe("blocCounts", () => {
  it("counts each filter over mixed statuses", () => {
    const teams = ["open", "open", "succeeded", "expired", "failed", "weird"].map((status) =>
      team({ status }),
    );
    expect(blocCounts(teams)).toEqual({ active: 3, completed: 1, refunded: 2 });
  });
});

describe("blocChip", () => {
  it("says Filling for an open bloc with time to spare", () => {
    expect(blocChip(team({}), now)).toEqual({ label: "Filling", color: "default" });
  });

  it("says Closing soon under one hour", () => {
    expect(blocChip(team({ expireTime: inMinutes(52) }), now)).toEqual({
      label: "Closing soon",
      color: "warning",
    });
  });

  it("says Ended for an open bloc past its deadline", () => {
    expect(blocChip(team({ expireTime: inMinutes(-1) }), now).label).toBe("Ended");
  });

  it("says Filled for a succeeded bloc and Closed for a closed one, never Refunded", () => {
    expect(blocChip(team({ status: "succeeded" }), now)).toEqual({
      label: "Filled",
      color: "success",
    });
    expect(blocChip(team({ status: "expired" }), now)).toEqual({
      label: "Closed",
      color: "default",
    });
  });
});

describe("blocCountdown", () => {
  it("prints days and hours from a day out, as the artboard does", () => {
    expect(blocCountdown(team({ expireTime: inMinutes(28 * 60 + 10) }), now)?.text).toBe(
      "1d 4h left",
    );
  });

  it("falls back to the listing countdown under a day", () => {
    expect(blocCountdown(team({ expireTime: inMinutes(52) }), now)).toEqual({
      text: "52:00 left",
      urgent: true,
    });
  });

  it("is null without a readable deadline", () => {
    expect(blocCountdown(team({ expireTime: "nope" }), now)).toBeNull();
  });
});

// A row renders with `clockReading(deadline, realNow)` instead of the real clock; its chip and
// countdown must not notice, including at the one-day and one-hour lines and at the deadline.
describe("blocChip and blocCountdown at the row's clock reading", () => {
  it.each([
    ["the one-day line", 24 * 3_600_000],
    ["the one-hour line", 3_600_000],
    ["the deadline", 0],
  ])("print what the real time prints for two minutes either side of %s", (_, beforeMs) => {
    const deadlineMs = now + 2 * 3_600_000 * 24 + 17_000;
    const open = team({ expireTime: new Date(deadlineMs).toISOString() });
    for (let sec = -120; sec <= 120; sec += 1) {
      const at = deadlineMs - beforeMs + sec * 1000;
      const reading = clockReading(deadlineMs, at);
      expect(blocCountdown(open, reading)).toEqual(blocCountdown(open, at));
      expect(blocChip(open, reading)).toEqual(blocChip(open, at));
    }
  });
});
