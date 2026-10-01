import { describe, expect, it } from "vitest";

import { actionsFor } from "./lifecycle";

const saved = { dirty: false, stock: 80, price: "46.80" };

describe("actionsFor", () => {
  it.each([
    ["draft", ["publish", "delete"]],
    ["on_sale", ["takeOffSale", "deleteBlocked"]],
    ["off_sale", ["putBackOnSale", "deleteBlocked"]],
    ["ON_SALE", ["takeOffSale", "deleteBlocked"]],
  ])("offers the %s listing its actions", (status, kinds) => {
    expect(actionsFor(status, saved).map((a) => a.kind)).toEqual(kinds);
  });

  it("enables every action on a saved, sellable listing", () => {
    for (const status of ["draft", "on_sale", "off_sale"]) {
      expect(actionsFor(status, saved).every((a) => a.enabled)).toBe(true);
    }
  });

  it.each([
    ["unsaved edits", { ...saved, dirty: true }],
    ["no stock", { ...saved, stock: 0 }],
    ["a zero price", { ...saved, price: "0.00" }],
    ["an unreadable price", { ...saved, price: "" }],
  ])("keeps Publish disabled with %s", (_why, facts) => {
    expect(actionsFor("draft", facts)).toEqual([
      { kind: "publish", enabled: false },
      { kind: "delete", enabled: true },
    ]);
  });

  it("offers nothing on an unknown status", () => {
    expect(actionsFor("archived", saved)).toEqual([]);
    expect(actionsFor(undefined, saved)).toEqual([]);
  });
});
