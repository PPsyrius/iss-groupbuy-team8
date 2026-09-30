import { describe, expect, it } from "vitest";

import { statusLabel } from "./listing-status";

describe("statusLabel", () => {
  it("maps the three listing codes", () => {
    expect(statusLabel("draft")).toBe("Draft");
    expect(statusLabel("on_sale")).toBe("On sale");
    expect(statusLabel("off_sale")).toBe("Off sale");
  });
  it("accepts the upper-case enum name", () => {
    expect(statusLabel("ON_SALE")).toBe("On sale");
  });
  it("never shows a raw code", () => {
    expect(statusLabel("archived")).toBe("Unknown");
    expect(statusLabel(undefined)).toBe("Unknown");
  });
});
