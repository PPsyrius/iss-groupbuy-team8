// The catalog's category chips, in artboard order (`BlocBuy Buyer Catalog.dc.html`, "All,
// Groceries, Household, Electronics, Beauty, Kids"). The backend's exact `category` strings are
// not published (CR-10): the lowercase labels are what the MSW seed and the dev seed data use, so
// this is the one place to change when the contract names them. `null` is the "All" chip.
export const CATEGORIES = [
  { value: null, label: "All" },
  { value: "groceries", label: "Groceries" },
  { value: "household", label: "Household" },
  { value: "electronics", label: "Electronics" },
  { value: "beauty", label: "Beauty" },
  { value: "kids", label: "Kids" },
] as const;

// Display label for a category value from the URL; unknown values (a hand-edited URL or a
// backend name this list does not know yet) fall back to the raw value.
export function categoryLabel(value: string): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value;
}
