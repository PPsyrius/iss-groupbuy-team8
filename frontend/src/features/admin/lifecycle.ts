import { toCents } from "./money";

// What an admin may do to a listing in its current state (admin plan A1-07). Draft → on sale ↔
// off sale; there is no way back to draft, and only a draft is deleted. Anything published shows
// why Delete is not available instead of a greyed button (`deleteBlocked`). Publish also needs a
// positive price and positive stock on the *saved* listing, so it stays disabled while the form
// holds unsaved edits: the dialog confirms the values the server will freeze.
export type LifecycleKind =
  | "publish"
  | "takeOffSale"
  | "putBackOnSale"
  | "delete"
  | "deleteBlocked";

export type LifecycleAction = { kind: LifecycleKind; enabled: boolean };

export function actionsFor(
  status: string | undefined,
  facts: { dirty: boolean; stock: number; price: string },
): LifecycleAction[] {
  switch (status?.toLowerCase()) {
    case "draft": {
      const sellable = facts.stock > 0 && (toCents(facts.price) ?? 0n) > 0n;
      return [
        { kind: "publish", enabled: sellable && !facts.dirty },
        { kind: "delete", enabled: true },
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
