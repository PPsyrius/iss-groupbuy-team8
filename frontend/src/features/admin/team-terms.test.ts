import { describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";

import { termsChip, termsFormDefaults, termsRefusalOf, toTermsRequest } from "./team-terms";

describe("termsFormDefaults", () => {
  it("splits saved terms into seats, hours and minutes", () => {
    expect(termsFormDefaults({ targetSize: 5, lifetimeSeconds: 5400, configured: true })).toEqual({
      seats: 5,
      hours: 1,
      minutes: 30,
    });
  });

  it("leaves seats and hours blank when the terms were never set", () => {
    const values = termsFormDefaults({
      targetSize: null,
      lifetimeSeconds: null,
      configured: false,
    });
    expect(values.seats).toBeNaN();
    expect(values.hours).toBeNaN();
    expect(values.minutes).toBe(0);
    expect(termsFormDefaults(undefined).seats).toBeNaN();
  });
});

describe("toTermsRequest", () => {
  it("sends the lifetime in seconds with the listing version, and never a team price", () => {
    expect(toTermsRequest({ seats: 5, hours: 2, minutes: 15 }, 4)).toEqual({
      expectedVersion: 4,
      targetSize: 5,
      lifetimeSeconds: 8100,
    });
  });
});

describe("termsChip", () => {
  it("names whether the listing can start a bloc", () => {
    expect(termsChip({ configured: true })).toEqual({
      label: "Ready to start blocs",
      color: "success",
    });
    expect(termsChip({ configured: false })).toEqual({ label: "Not set", color: "default" });
    expect(termsChip(undefined)).toEqual({ label: "Not set", color: "default" });
  });
});

describe("termsRefusalOf", () => {
  it("puts the backend's field paths on the form's fields", () => {
    const error = new ApiError({
      status: 400,
      code: "COMMON_400_002",
      message: "Request validation failed",
      errors: [
        { field: "targetSize", message: "must be greater than or equal to 2" },
        { field: "lifetimeSeconds", message: "must be less than or equal to 86400" },
      ],
    });
    expect(termsRefusalOf(error)).toEqual({
      kind: "fields",
      fields: {
        seats: "must be greater than or equal to 2",
        hours: "must be less than or equal to 86400",
      },
    });
  });

  it("reads a stale listing version as the editor's conflict", () => {
    const error = new ApiError({
      status: 412,
      code: "CATALOG_412_002",
      message: "Listing version precondition failed",
      errors: { currentVersion: 6 },
    });
    expect(termsRefusalOf(error)).toEqual({ kind: "conflict", serverVersion: 6 });
  });

  it("names the terms in a refusal without fields", () => {
    const error = new ApiError({ status: 400, code: "COMMON_400_001", message: "Invalid request" });
    expect(termsRefusalOf(error)).toEqual({
      kind: "message",
      message: "The server refused these terms. Check the fields and try again.",
    });
  });
});
