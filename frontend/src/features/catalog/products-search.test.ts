import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { listingSearchSchema, productsSearchSchema } from "./products-search";

describe("productsSearchSchema", () => {
  it("defaults page to 1 and size to 20", () => {
    expect(v.parse(productsSearchSchema, {})).toEqual({ page: 1, size: 20 });
  });

  it("keeps in-range values and a trimmed category", () => {
    expect(v.parse(productsSearchSchema, { page: 3, size: 24, category: " food " })).toEqual({
      page: 3,
      size: 24,
      category: "food",
    });
  });

  it("falls back on out-of-range or non-numeric page and size", () => {
    expect(v.parse(productsSearchSchema, { page: 0, size: 500 })).toEqual({ page: 1, size: 20 });
    expect(v.parse(productsSearchSchema, { page: "x", size: 2.5 })).toEqual({ page: 1, size: 20 });
  });

  it("treats an empty category as no filter", () => {
    expect(v.parse(productsSearchSchema, { category: "  " }).category).toBeUndefined();
  });

  it("keeps a trimmed keyword and treats an empty one as no filter", () => {
    expect(v.parse(productsSearchSchema, { keyword: " jasmine rice " }).keyword).toBe(
      "jasmine rice",
    );
    expect(v.parse(productsSearchSchema, { keyword: "" }).keyword).toBeUndefined();
  });

  it("cuts a keyword to the contract's 64 characters", () => {
    const long = "x".repeat(70);
    expect(v.parse(productsSearchSchema, { keyword: long }).keyword).toBe("x".repeat(64));
  });
});

describe("listingSearchSchema (UC7 invite link)", () => {
  const ref = "Qm9vbS1pbnZpdGUtcmVm_-";
  const deal = "5b1b9d5c-1a11-4e1e-9a10-000000000001";

  it("keeps a deal and a ref that match the contract's patterns", () => {
    expect(v.parse(listingSearchSchema, { deal, ref: ` ${ref} ` })).toEqual({ deal, ref });
  });

  it("is empty without an invite", () => {
    expect(v.parse(listingSearchSchema, {})).toEqual({});
  });

  it("drops a ref outside the pattern instead of failing the route", () => {
    expect(v.parse(listingSearchSchema, { deal, ref: "bad ref!" }).ref).toBeUndefined();
    expect(v.parse(listingSearchSchema, { deal, ref: "x".repeat(65) }).ref).toBeUndefined();
    expect(v.parse(listingSearchSchema, { deal, ref: "" }).ref).toBeUndefined();
  });

  it("drops a non-string value the router parsed from the query", () => {
    expect(v.parse(listingSearchSchema, { deal: 42, ref: { a: 1 } })).toEqual({
      deal: undefined,
      ref: undefined,
    });
  });

  it("drops a deal id with characters no id carries", () => {
    expect(v.parse(listingSearchSchema, { deal: "../x", ref }).deal).toBeUndefined();
  });
});
