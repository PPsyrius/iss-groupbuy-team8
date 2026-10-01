// The one place that turns a listing status code into words; the console never shows a raw code
// (admin plan, Global Constraints). The wire carries `ProductStatus` lowercase codes; the contract
// types `status` as a plain string, so the upper-case enum name is accepted too.
type ListingStatus = "draft" | "on_sale" | "off_sale";

const labels: Record<ListingStatus, string> = {
  draft: "Draft",
  on_sale: "On sale",
  off_sale: "Off sale",
};

function isListingStatus(code: string): code is ListingStatus {
  return Object.hasOwn(labels, code);
}

export function statusLabel(code: string | undefined): string {
  const normalised = code?.toLowerCase();
  return normalised !== undefined && isListingStatus(normalised) ? labels[normalised] : "Unknown";
}
