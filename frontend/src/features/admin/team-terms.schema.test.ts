import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { TeamTermsFormValues } from "./team-terms";
import { teamTermsSchema } from "./team-terms.schema";

const valid: TeamTermsFormValues = { seats: 5, hours: 2, minutes: 0 };

function problems(values: TeamTermsFormValues) {
  const out: Record<string, string> = {};
  for (const issue of v.safeParse(teamTermsSchema, values).issues ?? []) {
    const path = issue.path?.map((p) => String(p.key)).join(".") ?? "";
    out[path] = path in out ? `${out[path]} | ${issue.message}` : issue.message;
  }
  return out;
}

describe("teamTermsSchema", () => {
  it("accepts at least 2 seats and a lifetime from 40 minutes to 24 hours", () => {
    expect(problems(valid)).toEqual({});
    expect(problems({ seats: 2, hours: 0, minutes: 40 })).toEqual({});
    expect(problems({ seats: 500, hours: 24, minutes: 0 })).toEqual({});
  });

  it("needs a whole number of seats, at least 2", () => {
    for (const seats of [Number.NaN, 1, 0, 2.5]) {
      expect(problems({ ...valid, seats })).toEqual({ seats: "Enter at least 2 seats." });
    }
  });

  it("needs whole hours and minutes", () => {
    expect(problems({ ...valid, hours: Number.NaN })).toEqual({ hours: "Enter the hours." });
    expect(problems({ ...valid, minutes: Number.NaN })).toEqual({ minutes: "Enter 0 to 59." });
    expect(problems({ ...valid, minutes: 60 })).toEqual({ minutes: "Enter 0 to 59." });
    expect(problems({ ...valid, hours: -1 })).toEqual({ hours: "Enter the hours." });
  });

  it("keeps the lifetime between 40 minutes and 24 hours, on Hours", () => {
    const outside = "A bloc stays open from 40 minutes to 24 hours.";
    expect(problems({ ...valid, hours: 0, minutes: 39 })).toEqual({ hours: outside });
    expect(problems({ ...valid, hours: 24, minutes: 1 })).toEqual({ hours: outside });
    expect(problems({ ...valid, hours: 25, minutes: 0 })).toEqual({ hours: outside });
  });
});
