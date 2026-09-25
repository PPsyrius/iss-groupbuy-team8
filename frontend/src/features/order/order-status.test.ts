import { describe, expect, it } from "vitest";

import { orderStatus } from "./order-status";

describe("orderStatus", () => {
  it.each([
    ["pending", "Waiting for the bloc", "warning"],
    ["confirmed", "Confirmed", "success"],
    ["cancelled", "Cancelled", "default"],
  ] as const)("labels the wire code %s", (code, label, color) => {
    expect(orderStatus(code)).toEqual({ label, color });
  });

  it("accepts the enum name in upper case", () => {
    expect(orderStatus("CONFIRMED")).toEqual({ label: "Confirmed", color: "success" });
  });

  it("shows an unknown code as is rather than hiding the row's state", () => {
    expect(orderStatus("disputed")).toEqual({ label: "disputed", color: "default" });
  });

  it("returns nothing for a missing status", () => {
    expect(orderStatus(undefined)).toBeUndefined();
  });
});
