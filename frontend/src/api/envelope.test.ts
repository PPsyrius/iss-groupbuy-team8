import { describe, expect, it } from "vitest";

import { ApiError, fieldViolations, unwrapResult } from "./envelope";

describe("unwrapResult", () => {
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
