import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { loginSearchSchema, safeReturnTo } from "./login-search";

describe("loginSearchSchema", () => {
  it("defaults returnTo to /account and drops an absent error", () => {
    expect(v.parse(loginSearchSchema, {})).toEqual({ returnTo: "/account" });
  });

  it("keeps a same-origin path with its search", () => {
    expect(v.parse(loginSearchSchema, { returnTo: "/products/abc?x=1" })).toEqual({
      returnTo: "/products/abc?x=1",
    });
  });

  it.each([
    "//evil.example",
    "https://evil.example/",
    "javascript:alert(1)",
    "account",
    "",
    // `%5C` decoded: the URL parser reads a backslash as a slash, so this is `//evil.example`.
    "/\\evil.example",
    "/products/1\\..\\evil.example",
  ])("falls back to /account for %j", (returnTo) => {
    expect(v.parse(loginSearchSchema, { returnTo })).toEqual({ returnTo: "/account" });
  });

  it("keeps a non-empty provider error code and drops an empty one", () => {
    expect(v.parse(loginSearchSchema, { error: "access_denied" })).toEqual({
      returnTo: "/account",
      error: "access_denied",
    });
    expect(v.parse(loginSearchSchema, { error: "  " })).toEqual({ returnTo: "/account" });
  });
});

describe("safeReturnTo", () => {
  it("accepts only same-origin relative paths", () => {
    expect(safeReturnTo("/products/1")).toBe("/products/1");
    expect(safeReturnTo("//evil.example")).toBe("/account");
    expect(safeReturnTo("/\\evil.example")).toBe("/account");
    expect(safeReturnTo(null)).toBe("/account");
    expect(safeReturnTo(42)).toBe("/account");
  });
});
