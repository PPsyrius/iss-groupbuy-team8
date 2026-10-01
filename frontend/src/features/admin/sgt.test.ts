import { describe, expect, it } from "vitest";

import { formatSgtDate } from "./sgt";

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
