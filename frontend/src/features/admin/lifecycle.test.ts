import { describe, expect, it } from "vitest";

import { actionsFor, deleteBlockedReason } from "./lifecycle";

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

  it("replaces Delete draft with its reason when the server says the draft is not deletable", () => {
    expect(actionsFor("draft", { ...saved, deletable: false }).map((a) => a.kind)).toEqual([
      "publish",
      "deleteBlocked",
    ]);
    expect(actionsFor("draft", { ...saved, deletable: true }).map((a) => a.kind)).toEqual([
      "publish",
      "delete",
    ]);
    // Page items carry no `deletable` (#300): the row menu keeps Delete draft and the DELETE
    // itself still answers 409 when the draft has history.
    expect(actionsFor("draft", { ...saved, deletable: undefined }).map((a) => a.kind)).toEqual([
      "publish",
      "delete",
    ]);
  });

  it("keeps the published reason whatever the server says about deletion", () => {
    expect(actionsFor("on_sale", { ...saved, deletable: true }).map((a) => a.kind)).toEqual([
      "takeOffSale",
      "deleteBlocked",
    ]);
  });

  it("offers nothing on an unknown status", () => {
    expect(actionsFor("archived", saved)).toEqual([]);
    expect(actionsFor(undefined, saved)).toEqual([]);
  });
});

describe("deleteBlockedReason", () => {
  it("names a published listing's history, as on 4b.3", () => {
    expect(deleteBlockedReason("on_sale", { deals: 26, quotes: 61, policies: 3 })).toBe(
      "Not available. This listing has been published and has 26 bloc records, 61 quotes and 3 price rules.",
    );
  });

  it.each([
    [{ deals: 1, quotes: 1, policies: 1 }, "1 bloc record, 1 quote and 1 price rule"],
    [{ deals: 0, quotes: 2, policies: 1 }, "2 quotes and 1 price rule"],
    [{ deals: 4, quotes: 0, policies: 0 }, "4 bloc records"],
    [{ quotes: 1 }, "1 quote"],
  ])("counts %o in the singular or plural and leaves out the zeros", (references, counts) => {
    expect(deleteBlockedReason("off_sale", references)).toBe(
      `Not available. This listing has been published and has ${counts}.`,
    );
  });

  it.each([
    ["no counts", undefined],
    ["all-zero counts", { deals: 0, quotes: 0, policies: 0 }],
  ])("falls back to the published reason with %s", (_why, references) => {
    expect(deleteBlockedReason("on_sale", references)).toBe(
      "Not available. This listing has been published; take it off sale instead.",
    );
  });

  it("names a draft's history, or says it can no longer be deleted", () => {
    expect(deleteBlockedReason("draft", { deals: 0, quotes: 2, policies: 0 })).toBe(
      "Not available. This draft has 2 quotes.",
    );
    expect(deleteBlockedReason("draft", { deals: 0, quotes: 0, policies: 0 })).toBe(
      "Not available. This draft can no longer be deleted.",
    );
    expect(deleteBlockedReason("DRAFT", undefined)).toBe(
      "Not available. This draft can no longer be deleted.",
    );
  });
});
