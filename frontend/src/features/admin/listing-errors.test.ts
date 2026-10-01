import { describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";

import { refusalOf } from "./listing-errors";

function apiError(status: number, code: string, errors?: unknown) {
  return new ApiError({ status, code, message: "server message", errors });
}

describe("refusalOf", () => {
  it("turns a stale listing version into the conflict, with the server's version when sent", () => {
    expect(refusalOf(apiError(412, "CATALOG_412_002", { currentVersion: 5 }))).toEqual({
      kind: "conflict",
      serverVersion: 5,
    });
    expect(refusalOf(apiError(412, "CATALOG_412_002"))).toEqual({
      kind: "conflict",
      serverVersion: null,
    });
  });

  it("asks for a reload when the listing changed state", () => {
    expect(refusalOf(apiError(409, "CATALOG_409_002"))).toEqual({
      kind: "message",
      message: "This listing changed state. Reload to see what you can do now.",
    });
  });

  it("explains a draft that cannot be deleted", () => {
    expect(refusalOf(apiError(409, "CATALOG_409_004"))).toEqual({
      kind: "message",
      message: "Not available. This draft already has price or bloc history.",
    });
  });

  it("hands a revoked role to the route boundary", () => {
    expect(refusalOf(apiError(403, "COMMON_403_001"))).toEqual({ kind: "forbidden" });
  });

  it("asks an expired session to sign in again", () => {
    expect(refusalOf(apiError(401, "COMMON_401_001"))).toEqual({ kind: "session" });
  });

  it("maps field violations onto their fields", () => {
    const error = apiError(400, "COMMON_400_002", [
      { field: "listing.title", message: "must not be blank" },
    ]);
    expect(refusalOf(error)).toEqual({ kind: "fields", fields: { title: "must not be blank" } });
  });

  // #323 (BA-6): a field-specific domain 400 carries the same `{field, message}` entries as bean
  // validation, so it lands on the same fields.
  it("maps a catalog domain 400's field path onto its field", () => {
    const error = apiError(400, "CATALOG_400_002", [
      { field: "listing.originalPrice", message: "Must have at most two decimal places" },
    ]);
    expect(refusalOf(error)).toEqual({
      kind: "fields",
      fields: { originalPrice: "Must have at most two decimal places" },
    });
  });

  it("keeps a top-level product number path for the form to show in its alert", () => {
    const error = apiError(400, "CATALOG_400_002", [
      { field: "productNo", message: "Must be 1 to 64 letters" },
    ]);
    expect(refusalOf(error)).toEqual({
      kind: "fields",
      fields: { productNo: "Must be 1 to 64 letters" },
    });
  });

  it("gives other refusals plain words, never a raw code", () => {
    expect(refusalOf(apiError(400, "CATALOG_400_002"))).toEqual({
      kind: "message",
      message: "The server refused this listing. Check the fields and try again.",
    });
    expect(refusalOf(apiError(500, "COMMON_500_001"))).toEqual({
      kind: "message",
      message: "Something went wrong on our side. Try again.",
    });
    expect(refusalOf(new TypeError("Failed to fetch"))).toEqual({
      kind: "message",
      message: "Something went wrong on our side. Try again.",
    });
  });
});
