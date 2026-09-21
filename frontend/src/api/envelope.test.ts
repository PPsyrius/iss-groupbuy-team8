import { describe, expect, it } from "vitest";

import { ApiError, unwrapResult } from "./envelope";

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
