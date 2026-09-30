import type { ActiveDealResponse } from "@/api/generated/types.gen";

import { hasEnded, parseDeadline } from "./deal-time";

// Pure view state for the Listing Detail page (`routes/products.$productId.tsx`), kept out of the
// component so each rule can be read and tested on its own (SonarCloud S3776, #296).

export interface ListingDealView {
  dealId: string | undefined;
  // Same clock and rule as the DealProgress row's "Ended" chip, so the header never calls an
  // expired bloc "filling" (CodeRabbit r4042898931).
  ended: boolean;
  // Same rule as the row's "Full" chip, so a JOIN that lost the last seat does not leave the
  // header saying "filling" over a disabled Join (CodeRabbit, PR #200).
  full: boolean;
  seatsLeft: number;
  closesMs: number | null;
  // The bloc can still take members: not ended and at least one seat left.
  takesMembers: boolean;
}

// A listing without a bloc carries `deal: null` (#243), so every read is optional.
export function listingDealView(
  deal: ActiveDealResponse | null | undefined,
  now: number,
): ListingDealView {
  const joined = deal?.currentSize ?? 0;
  const ended = hasEnded(deal?.expireTime, now);
  const seatsLeft = Math.max(0, (deal?.targetSize ?? 0) - joined);
  return {
    dealId: deal?.dealId,
    ended,
    full: deal ? joined >= Math.max(1, deal.targetSize ?? 1) : false,
    seatsLeft,
    closesMs: parseDeadline(deal?.expireTime),
    takesMembers: !ended && seatsLeft > 0,
  };
}

// The "Open blocs" heading's note: why the bloc takes no one, or `null` while it is filling.
export function blocHeadline(view: Pick<ListingDealView, "ended" | "full">): string | null {
  if (view.ended) return "This bloc has ended";
  if (view.full) return "This bloc has filled up";
  return null;
}

export interface InviteView {
  // A signed-in buyer who is in this listing's open bloc, while it can still take members.
  shareable: boolean;
  // The listing has an open bloc that takes members.
  listingOpen: boolean;
  // The buyer's invite is for that open bloc.
  invitedOpen: boolean;
  // Show the invite banner: an invite for this listing, to a buyer who is not already sharing.
  showBanner: boolean;
  // An invite to the open bloc makes its Join the visit's primary action (Invite artboard 5b.1),
  // so "Start a bloc" steps down a rank.
  inviteJoin: boolean;
}

export function inviteView({
  view,
  signedIn,
  member,
  inviteDealId,
  hasInvite,
}: Readonly<{
  view: ListingDealView;
  signedIn: boolean;
  member: boolean;
  inviteDealId: string | undefined;
  hasInvite: boolean;
}>): InviteView {
  const shareable = signedIn && member && view.takesMembers;
  const listingOpen = view.dealId !== undefined && view.takesMembers;
  const invitedOpen = listingOpen && inviteDealId === view.dealId;
  return {
    shareable,
    listingOpen,
    invitedOpen,
    showBanner: hasInvite && !shareable,
    inviteJoin: invitedOpen && !shareable,
  };
}

// START goes through the quote sheet first (T3-01): the sign-in gate for an anonymous buyer,
// otherwise the sheet while payments are available.
export function chooseStart({
  onGated,
  enabled,
  openQuote,
}: Readonly<{
  onGated: (() => void) | undefined;
  enabled: boolean;
  openQuote: () => void;
}>): (() => void) | undefined {
  if (onGated) return onGated;
  return enabled ? openQuote : undefined;
}

// A member of the open bloc has no Join (FB-20): the server would refuse it with
// GROUPBUY_409_003, and `DealProgress` names the reason instead. Join also waits while that
// membership read is in flight (CodeRabbit, PR #271); a failed read leaves Join on, since the
// server's refusal charges nothing and a broken side query should not block every buyer.
export function chooseJoin({
  onGated,
  enabled,
  membershipPending,
  member,
  view,
  join,
}: Readonly<{
  onGated: (() => void) | undefined;
  enabled: boolean;
  membershipPending: boolean;
  member: boolean;
  view: Pick<ListingDealView, "dealId" | "takesMembers">;
  join: (dealId: string) => void;
}>): (() => void) | undefined {
  if (onGated) return onGated;
  const { dealId } = view;
  if (!enabled || membershipPending || member || !dealId || !view.takesMembers) return undefined;
  return () => join(dealId);
}
