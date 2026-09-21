// The catalog's category chips, in artboard order (`BlocBuy Buyer Catalog.dc.html`, "All,
// Groceries, Household, Electronics, Beauty, Kids"). The contract publishes no category
// dictionary (CR-10); the values are the backend demo seed's strings
// (`backend/.../db/seed/R__demo_catalog_seed.sql`: `beauty`, `electronics`, `home`), which the
// mapper matches exactly, so the Household chip sends `home`. Groceries and Kids have no seed rows
// yet and render the empty state until the backend names them. This is the one place to change
// when the dictionary lands. `null` is the "All" chip.
export const CATEGORIES = [
  { value: null, label: "All" },
  { value: "groceries", label: "Groceries" },
  { value: "home", label: "Household" },
  { value: "electronics", label: "Electronics" },
  { value: "beauty", label: "Beauty" },
  { value: "kids", label: "Kids" },
] as const;

// Display label for a category value from the URL; unknown values (a hand-edited URL or a
// backend name this list does not know yet) fall back to the raw value.
export function categoryLabel(value: string): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value;
}
