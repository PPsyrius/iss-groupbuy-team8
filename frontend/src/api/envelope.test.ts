import { describe, expect, it } from "vitest";

import { ApiError, conflictVersion, fieldViolations, unwrapResult } from "./envelope";

describe("unwrapResult", () => {
  it("supplies safe diagnostics when an error envelope omits code and message", () => {
    expect(() => unwrapResult({})).toThrowError(
      expect.objectContaining({ code: "UNKNOWN", message: "Request failed" }),
    );
  });
  it("returns data when code is OK", () => {
    expect(
      unwrapResult({ code: "COMMON_200_000", data: { loginId: "u1" }, message: "ok" }),
    ).toEqual({
      loginId: "u1",
    });
  });
  it("throws ApiError carrying code, message and traceId when code is not OK", () => {
    expect(() =>
      unwrapResult({ code: "AUTH_401", message: "unauthenticated", traceId: "t-1" }),
    ).toThrowError(ApiError);
    let caught: unknown;
    try {
      unwrapResult({ code: "AUTH_401", message: "unauthenticated", traceId: "t-1" });
    } catch (e) {
      caught = e;
    }
    expect(caught).toMatchObject({ code: "AUTH_401", traceId: "t-1", message: "unauthenticated" });
  });
  it("throws when data is missing on an OK envelope of a non-void type", () => {
    expect(() =>
      unwrapResult({ code: "COMMON_200_000", message: "ok" }, { allowVoid: false }),
    ).toThrowError(ApiError);
  });
});

describe("ApiError.errors", () => {
  it("keeps the envelope's errors as sent", () => {
    const errors = [{ field: "listing.title", message: "must not be blank" }];
    let caught: unknown;
    try {
      unwrapResult({ code: "COMMON_400_001", message: "invalid", errors });
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(ApiError);
    expect(caught).toMatchObject({ errors });
  });
  it("is undefined when the envelope has none", () => {
    expect(new ApiError({ code: "X", message: "m" }).errors).toBeUndefined();
  });
});

describe("fieldViolations", () => {
  it("maps field violations by field with the listing. prefix stripped", () => {
    const e = new ApiError({
      code: "COMMON_400_001",
      message: "invalid",
      status: 400,
      errors: [
        { field: "listing.originalPrice", message: "must be positive" },
        { field: "expectedVersion", message: "must not be null" },
      ],
    });
    expect(fieldViolations(e)).toEqual({
      originalPrice: "must be positive",
      expectedVersion: "must not be null",
    });
  });
  it("ignores entries that are not {field, message} strings", () => {
    const e = new ApiError({
      code: "COMMON_400_001",
      message: "invalid",
      errors: [
        { field: "title" },
        "oops",
        null,
        { field: 1, message: "x" },
        { field: "stock", message: "too low" },
      ],
    });
    expect(fieldViolations(e)).toEqual({ stock: "too low" });
  });
  it("returns no violations for a non-array errors value or a non-ApiError", () => {
    expect(
      fieldViolations(new ApiError({ code: "X", message: "m", errors: { field: "a" } })),
    ).toEqual({});
    expect(fieldViolations(new Error("boom"))).toEqual({});
  });
});

function conflict(errors: unknown, over: { code?: string; status?: number } = {}) {
  return new ApiError({
    code: over.code ?? "CATALOG_412_002",
    message: "Listing version precondition failed",
    status: over.status ?? 412,
    errors,
  });
}

describe("conflictVersion", () => {
  it("reads currentVersion from a listing or policy 412", () => {
    expect(conflictVersion(conflict({ currentVersion: 5 }))).toBe(5);
    expect(conflictVersion(conflict({ currentVersion: 0 }, { code: "CATALOG_412_001" }))).toBe(0);
  });
  it("reads it from the code when the 412 arrived inside a 200 envelope", () => {
    expect(conflictVersion(conflict({ currentVersion: 3 }, { status: 200 }))).toBe(3);
  });
  it("is null without a usable version", () => {
    expect(conflictVersion(conflict(undefined))).toBeNull();
    expect(conflictVersion(conflict({ currentVersion: "5" }))).toBeNull();
    expect(conflictVersion(conflict({ currentVersion: -1 }))).toBeNull();
    expect(conflictVersion(conflict({ currentVersion: 1.5 }))).toBeNull();
    expect(conflictVersion(conflict([{ currentVersion: 5 }]))).toBeNull();
  });
  it("is null for anything but a 412", () => {
    expect(
      conflictVersion(conflict({ currentVersion: 5 }, { code: "CATALOG_409_002", status: 409 })),
    ).toBeNull();
    expect(conflictVersion(new Error("boom"))).toBeNull();
  });
});
