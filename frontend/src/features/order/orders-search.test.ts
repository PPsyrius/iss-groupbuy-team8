import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { ordersSearchSchema } from "./orders-search";

describe("ordersSearchSchema", () => {
  it("defaults to the first page of ten", () => {
    expect(v.parse(ordersSearchSchema, {})).toEqual({ page: 1, size: 10 });
  });

  it("keeps a valid page and size", () => {
    expect(v.parse(ordersSearchSchema, { page: 3, size: 50 })).toEqual({ page: 3, size: 50 });
  });

  it.each([
    [{ page: 0 }, { page: 1, size: 10 }],
    [{ page: "x" }, { page: 1, size: 10 }],
    [{ size: 101 }, { page: 1, size: 10 }],
    [{ size: 0 }, { page: 1, size: 10 }],
  ])("falls back for %j instead of failing the route", (input, expected) => {
    expect(v.parse(ordersSearchSchema, input)).toEqual(expected);
  });
});
