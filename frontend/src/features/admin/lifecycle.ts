import type { ListingReferencesResponse } from "@/api/generated/types.gen";

import { toCents } from "./money";

// What an admin may do to a listing in its current state (admin plan A1-07). Draft → on sale ↔
// off sale; there is no way back to draft, and only a draft is deleted. Anything published shows
// why Delete is not available instead of a greyed button (`deleteBlocked`). Publish also needs a
// positive price and positive stock on the *saved* listing, so it stays disabled while the form
// holds unsaved edits: the dialog confirms the values the server will freeze. A draft with history
// is not deletable either: the single admin read says so in `deletable` (#300, admin plan A3-05).
// Page items carry no flag, so the row menu keeps Delete draft and the DELETE answers 409.
export type LifecycleKind =
  | "publish"
  | "takeOffSale"
  | "putBackOnSale"
  | "delete"
  | "deleteBlocked";

export type LifecycleAction = { kind: LifecycleKind; enabled: boolean };

export function actionsFor(
  status: string | undefined,
  facts: { dirty: boolean; stock: number; price: string; deletable?: boolean | undefined },
): LifecycleAction[] {
  switch (status?.toLowerCase()) {
    case "draft": {
      const sellable = facts.stock > 0 && (toCents(facts.price) ?? 0n) > 0n;
      return [
        { kind: "publish", enabled: sellable && !facts.dirty },
        { kind: facts.deletable === false ? "deleteBlocked" : "delete", enabled: true },
      ];
    }
    case "on_sale":
      return [
        { kind: "takeOffSale", enabled: true },
        { kind: "deleteBlocked", enabled: true },
      ];
    case "off_sale":
      return [
        { kind: "putBackOnSale", enabled: true },
        { kind: "deleteBlocked", enabled: true },
      ];
    default:
      return [];
  }
}

const counted: [keyof ListingReferencesResponse, string, string][] = [
  ["deals", "bloc record", "bloc records"],
  ["quotes", "quote", "quotes"],
  ["policies", "price rule", "price rules"],
];

// "26 bloc records, 61 quotes and 3 price rules" (artboard 4b.3); zeros are left out.
function historyOf(references: ListingReferencesResponse | undefined) {
  const parts = counted.flatMap(([field, one, many]) => {
    const n = references?.[field] ?? 0;
    return n > 0 ? [`${n} ${n === 1 ? one : many}`] : [];
  });
  const last = parts.pop();
  if (last === undefined) return undefined;
  return parts.length > 0 ? `${parts.join(", ")} and ${last}` : last;
}

// Why Delete is not available (admin plan A3-05): the retained history from the single read's
// `references` (#300), which the backend's DELETE veto counts, including expired and deleted rows.
// Without counts a published listing keeps the A1-07 reason.
export function deleteBlockedReason(
  status: string | undefined,
  references: ListingReferencesResponse | undefined,
): string {
  const history = historyOf(references);
  if (status?.toLowerCase() === "draft")
    return history === undefined
      ? "Not available. This draft can no longer be deleted."
      : `Not available. This draft has ${history}.`;
  return history === undefined
    ? "Not available. This listing has been published; take it off sale instead."
    : `Not available. This listing has been published and has ${history}.`;
}
