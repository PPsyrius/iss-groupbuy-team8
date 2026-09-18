import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { productsSearchSchema } from "./products-search";

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
});
