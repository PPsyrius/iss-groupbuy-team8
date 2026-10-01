import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { listingsSearchSchema, tabToStatus } from "./listings-search";

describe("listingsSearchSchema", () => {
  it("defaults to the All tab, page 1 and 20 rows", () => {
    expect(v.parse(listingsSearchSchema, {})).toEqual({ tab: "all", page: 1, size: 20 });
  });

  it("keeps a known tab and in-range paging", () => {
    expect(v.parse(listingsSearchSchema, { tab: "off-sale", page: 3, size: 50 })).toEqual({
      tab: "off-sale",
      page: 3,
      size: 50,
    });
  });

  it("falls back on an unknown tab and out-of-range paging", () => {
    expect(v.parse(listingsSearchSchema, { tab: "on_sale", page: 0, size: 101 })).toEqual({
      tab: "all",
      page: 1,
      size: 20,
    });
  });
});

describe("tabToStatus", () => {
  it("maps each tab to the contract's status code, and All to no filter", () => {
    expect(tabToStatus("all")).toBeUndefined();
    expect(tabToStatus("draft")).toBe("draft");
    expect(tabToStatus("on-sale")).toBe("on_sale");
    expect(tabToStatus("off-sale")).toBe("off_sale");
  });
});
