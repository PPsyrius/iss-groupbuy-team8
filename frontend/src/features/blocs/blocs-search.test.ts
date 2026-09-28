import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { accountSearchSchema } from "./blocs-search";

describe("accountSearchSchema", () => {
  it("defaults to the Active chip and the first page of orders", () => {
    expect(v.parse(accountSearchSchema, {})).toEqual({ page: 1, size: 10, blocs: "active" });
  });

  it.each(["active", "completed", "refunded"])("keeps blocs=%s", (blocs) => {
    expect(v.parse(accountSearchSchema, { blocs }).blocs).toBe(blocs);
  });

  it.each(["bogus", 3, "REFUNDED"])("falls back to Active for blocs=%j", (blocs) => {
    expect(v.parse(accountSearchSchema, { blocs, page: 2 })).toEqual({
      page: 2,
      size: 10,
      blocs: "active",
    });
  });
});
