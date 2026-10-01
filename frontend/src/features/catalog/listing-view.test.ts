import { describe, expect, it, vi } from "vitest";

import {
  blocHeadline,
  chooseJoin,
  chooseStart,
  inviteView,
  type ListingDealView,
  listingDealView,
} from "./listing-view";

const now = Date.parse("2026-09-30T04:00:00Z");
const later = "2026-09-30T08:00:00Z";
const earlier = "2026-09-30T02:00:00Z";

function view(overrides: Partial<ListingDealView> = {}): ListingDealView {
  return {
    dealId: "d1",
    ended: false,
    full: false,
    seatsLeft: 2,
    closesMs: Date.parse(later),
    takesMembers: true,
    ...overrides,
  };
}

describe("listingDealView", () => {
  it("reads an open bloc", () => {
    expect(
      listingDealView({ dealId: "d1", currentSize: 3, targetSize: 5, expireTime: later }, now),
    ).toEqual(view());
  });

  it("treats a listing without a bloc as closed and not full", () => {
    expect(listingDealView(null, now)).toEqual({
      dealId: undefined,
      ended: false,
      full: false,
      seatsLeft: 0,
      closesMs: null,
      takesMembers: false,
    });
  });

  it("marks a bloc full once every seat is taken", () => {
    const v = listingDealView(
      { dealId: "d1", currentSize: 5, targetSize: 5, expireTime: later },
      now,
    );
    expect(v).toMatchObject({ full: true, seatsLeft: 0, takesMembers: false });
  });

  it("stops taking members once the deadline passes", () => {
    const v = listingDealView(
      { dealId: "d1", currentSize: 1, targetSize: 5, expireTime: earlier },
      now,
    );
    expect(v).toMatchObject({ ended: true, seatsLeft: 4, takesMembers: false });
  });
});

describe("blocHeadline", () => {
  it("names an ended bloc before a full one", () => {
    expect(blocHeadline({ ended: true, full: true })).toBe("This bloc has ended");
    expect(blocHeadline({ ended: false, full: true })).toBe("This bloc has filled up");
    expect(blocHeadline({ ended: false, full: false })).toBeNull();
  });
});

describe("inviteView", () => {
  const base = { view: view(), signedIn: true, member: false, inviteDealId: "d1", hasInvite: true };

  it("makes an invite to the open bloc the primary Join", () => {
    expect(inviteView(base)).toEqual({
      shareable: false,
      listingOpen: true,
      invitedOpen: true,
      showBanner: true,
      inviteJoin: true,
    });
  });

  it("hides the banner and shares instead for a member of the open bloc", () => {
    expect(inviteView({ ...base, member: true })).toMatchObject({
      shareable: true,
      showBanner: false,
      inviteJoin: false,
    });
  });

  it("keeps the listing's own order for an invite to another bloc", () => {
    expect(inviteView({ ...base, inviteDealId: "d2" })).toMatchObject({
      invitedOpen: false,
      showBanner: true,
      inviteJoin: false,
    });
  });

  it("never shares for an anonymous buyer or a bloc that takes no one", () => {
    expect(inviteView({ ...base, member: true, signedIn: false }).shareable).toBe(false);
    expect(
      inviteView({ ...base, member: true, view: view({ takesMembers: false }) }),
    ).toMatchObject({ shareable: false, listingOpen: false });
  });
});

describe("chooseStart", () => {
  it("gates first, then opens the quote sheet while payments are on", () => {
    const onGated = vi.fn<() => void>();
    const openQuote = vi.fn<() => void>();
    expect(chooseStart({ onGated, enabled: true, openQuote })).toBe(onGated);
    expect(chooseStart({ onGated: undefined, enabled: true, openQuote })).toBe(openQuote);
    expect(chooseStart({ onGated: undefined, enabled: false, openQuote })).toBeUndefined();
  });
});

describe("chooseJoin", () => {
  const base = {
    onGated: undefined,
    enabled: true,
    membershipPending: false,
    member: false,
    view: view(),
  };

  it("joins the open bloc by its id", () => {
    const join = vi.fn<(dealId: string) => void>();
    chooseJoin({ ...base, join })?.();
    expect(join).toHaveBeenCalledWith("d1");
  });

  it("returns the sign-in gate for an anonymous buyer", () => {
    const onGated = vi.fn<() => void>();
    expect(chooseJoin({ ...base, onGated, join: vi.fn<(dealId: string) => void>() })).toBe(onGated);
  });

  it.each([
    ["payments are off", { enabled: false }],
    ["membership is still loading", { membershipPending: true }],
    ["the buyer is already a member", { member: true }],
    ["there is no bloc", { view: view({ dealId: undefined }) }],
    ["the bloc takes no one", { view: view({ takesMembers: false }) }],
  ])("offers no Join when %s", (_, overrides) => {
    expect(
      chooseJoin({ ...base, ...overrides, join: vi.fn<(dealId: string) => void>() }),
    ).toBeUndefined();
  });
});
